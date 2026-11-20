package com.hippo.ehviewer.client

import com.ehviewer.core.database.EhDatabase
import com.ehviewer.core.database.model.GalleryTranslationEntity
import com.ehviewer.core.database.roomDb
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/** AI 翻译结果：成功时携带译文，失败时携带可直接展示给用户的错误原因。 */
sealed interface AiTranslationResult {
    data class Success(val title: String) : AiTranslationResult
    data class Failure(val message: String) : AiTranslationResult
}

object AiTranslationManager {
    private val httpClient get() = EhApplication.ktorClient

    private val json = Json { ignoreUnknownKeys = true }
    private val translatingGids = ConcurrentHashMap.newKeySet<Long>()

    private val db by lazy { roomDb<EhDatabase>("eh.db") }

    /**
     * 获取画廊标题译文。命中本地缓存时直接返回；否则发起请求并写回缓存。
     * 任何失败都返回 [AiTranslationResult.Failure]，调用方据此提示用户，避免静默失败。
     */
    suspend fun getOrTranslateTitle(gid: Long, originalTitle: String): AiTranslationResult {
        if (!Settings.aiTranslationEnabled.value) {
            return AiTranslationResult.Failure("AI 翻译未启用")
        }
        if (originalTitle.isBlank()) {
            return AiTranslationResult.Failure("标题为空，无需翻译")
        }

        val apiUrl = Settings.aiTranslationApiUrl.value.trim().trimEnd('/')
        val apiKey = Settings.aiTranslationApiKey.value.trim()
        val model = Settings.aiTranslationModel.value.trim()
        if (apiUrl.isBlank()) return AiTranslationResult.Failure("未配置 API 地址")
        if (apiKey.isBlank()) return AiTranslationResult.Failure("未配置 API Key")

        // 1. 查询本地 Room 数据库缓存
        runCatching { db.galleryTranslationDao().getTranslation(gid) }
            .getOrNull()
            ?.takeIf { it.originalTitle == originalTitle }
            ?.let { return AiTranslationResult.Success(it.translatedTitle) }

        // 2. 防抖 / 并发保护：同一 gid 正在翻译时不重复发请求
        if (!translatingGids.add(gid)) {
            return AiTranslationResult.Failure("该画廊正在翻译中，请稍候")
        }

        return try {
            val translated = requestAiTranslation(apiUrl, apiKey, model, originalTitle)
            if (translated.isBlank()) {
                AiTranslationResult.Failure("模型未返回翻译内容")
            } else {
                runCatching {
                    db.galleryTranslationDao().insertTranslation(
                        GalleryTranslationEntity(
                            gid = gid,
                            originalTitle = originalTitle,
                            translatedTitle = translated,
                        ),
                    )
                }
                AiTranslationResult.Success(translated)
            }
        } catch (e: Exception) {
            AiTranslationResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: (e::class.simpleName ?: "未知错误"))
        } finally {
            translatingGids.remove(gid)
        }
    }

    private suspend fun requestAiTranslation(apiUrl: String, apiKey: String, model: String, prompt: String): String {
        val text = "请将以下漫画/画廊标题准确翻译为简洁流畅的简体中文。不要解释，直接输出翻译结果：\n$prompt"

        return if (isOpenAiCompatible(apiUrl)) {
            requestOpenAiTranslation(apiUrl, apiKey, model, text)
        } else {
            requestGeminiTranslation(apiUrl, apiKey, model, text)
        }
    }

    /**
     * 判断是否走 OpenAI 兼容协议。
     *
     * Google 的 OpenAI 兼容层地址形如 `https://generativelanguage.googleapis.com/v1beta/openai`，
     * 它同时包含 Google 域名与 `/openai` 路径，必须优先按 OpenAI 协议处理，否则会被误判为
     * Gemini 原生接口并拼出 404 的 endpoint。
     */
    private fun isOpenAiCompatible(apiUrl: String): Boolean {
        val url = apiUrl.lowercase()
        if (url.contains("/openai")) return true
        if (url.contains("generativelanguage.googleapis.com")) return false
        return true
    }

    private suspend fun requestOpenAiTranslation(apiUrl: String, apiKey: String, model: String, prompt: String): String {
        val endpoint = if (apiUrl.endsWith("/chat/completions")) apiUrl else "$apiUrl/chat/completions"
        val requestBody = """
            {
                "model": "$model",
                "messages": [
                    {"role": "user", "content": ${jsonEncode(prompt)}}
                ],
                "temperature": 0.3
            }
        """.trimIndent()

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            // 共享 ktorClient 的全局请求超时仅 10s，翻译请求单独放宽
            timeout { requestTimeoutMillis = 30_000 }
            setBody(requestBody)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}：${body.take(200)}")
        }

        val jsonObject = json.parseToJsonElement(body).jsonObject
        val message = jsonObject["choices"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("message")?.jsonObject
        return message?.get("content")?.jsonPrimitive?.content?.trim().orEmpty()
    }

    private suspend fun requestGeminiTranslation(apiUrl: String, apiKey: String, model: String, prompt: String): String {
        val targetModel = model.ifBlank { "gemini-1.5-flash" }
        // 兼容形如 https://generativelanguage.googleapis.com/v1beta 或带模型的完整 URL
        val endpoint = if (apiUrl.contains("/models/")) {
            "$apiUrl:generateContent?key=$apiKey"
        } else {
            "$apiUrl/models/$targetModel:generateContent?key=$apiKey"
        }

        val requestBody = """
            {
                "contents": [
                    {
                        "parts": [
                            {"text": ${jsonEncode(prompt)}}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            // 共享 ktorClient 的全局请求超时仅 10s，翻译请求单独放宽
            timeout { requestTimeoutMillis = 30_000 }
            setBody(requestBody)
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw IllegalStateException("HTTP ${response.status.value}：${body.take(200)}")
        }

        val jsonObject = json.parseToJsonElement(body).jsonObject
        val candidate = jsonObject["candidates"]?.jsonArray?.getOrNull(0)?.jsonObject
        val part = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.getOrNull(0)?.jsonObject
        return part?.get("text")?.jsonPrimitive?.content?.trim().orEmpty()
    }

    private fun jsonEncode(value: String): String {
        return Json.encodeToString(kotlinx.serialization.serializer(), value)
    }
}
