package com.hippo.ehviewer.client

import com.ehviewer.core.database.EhDatabase
import com.ehviewer.core.database.model.GalleryTranslationEntity
import com.ehviewer.core.database.roomDb
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

object AiTranslationManager {
    private val httpClient get() = EhApplication.ktorClient

    private val json = Json { ignoreUnknownKeys = true }
    private val translatingGids = ConcurrentHashMap.newKeySet<Long>()

    private val db by lazy { roomDb<EhDatabase>("eh.db") }

    suspend fun getOrTranslateTitle(gid: Long, originalTitle: String): String {
        if (!Settings.aiTranslationEnabled.value) {
            return originalTitle
        }
        if (originalTitle.isBlank()) return originalTitle

        // 1. 查询本地 Room 数据库缓存
        val cached = db.galleryTranslationDao().getTranslation(gid)
        if (cached != null && cached.originalTitle == originalTitle) {
            return cached.translatedTitle
        }

        // 2. 防抖 / 并发保护：如果正在翻译该 gid，直接返回原标题等待缓存或下次刷新
        if (!translatingGids.add(gid)) {
            return originalTitle
        }

        return try {
            val translated = requestAiTranslation(originalTitle)
            if (translated.isNotBlank()) {
                db.galleryTranslationDao().insertTranslation(
                    GalleryTranslationEntity(
                        gid = gid,
                        originalTitle = originalTitle,
                        translatedTitle = translated,
                    ),
                )
                translated
            } else {
                originalTitle
            }
        } catch (e: Exception) {
            originalTitle
        } finally {
            translatingGids.remove(gid)
        }
    }

    private suspend fun requestAiTranslation(text: String): String {
        val apiUrl = Settings.aiTranslationApiUrl.value.trimEnd('/')
        val apiKey = Settings.aiTranslationApiKey.value
        val model = Settings.aiTranslationModel.value

        val prompt = "请将以下漫画/画廊标题准确翻译为简洁流畅的简体中文。不要解释，直接输出翻译结果：\n$text"

        // 判断是 Google Gemini 接口还是 OpenAI 兼容接口
        return if (apiUrl.contains("generativelanguage.googleapis.com", ignoreCase = true) || apiUrl.contains("gemini", ignoreCase = true)) {
            requestGeminiTranslation(apiUrl, apiKey, model, prompt)
        } else {
            requestOpenAiTranslation(apiUrl, apiKey, model, prompt)
        }
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
            if (apiKey.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            setBody(requestBody)
        }.bodyAsText()

        val jsonObject = json.parseToJsonElement(response).jsonObject
        val choices = jsonObject["choices"]?.jsonArray
        val firstChoice = choices?.getOrNull(0)?.jsonObject
        val message = firstChoice?.get("message")?.jsonObject
        return message?.get("content")?.jsonPrimitive?.content?.trim() ?: ""
    }

    private suspend fun requestGeminiTranslation(apiUrl: String, apiKey: String, model: String, prompt: String): String {
        val targetModel = if (model.isBlank()) "gemini-1.5-flash" else model
        // 兼容形如 https://generativelanguage.googleapis.com/v1beta 或带模型的完整 URL
        val endpoint = if (apiUrl.contains("models/")) {
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
            setBody(requestBody)
        }.bodyAsText()

        val jsonObject = json.parseToJsonElement(response).jsonObject
        val candidates = jsonObject["candidates"]?.jsonArray
        val firstCandidate = candidates?.getOrNull(0)?.jsonObject
        val content = firstCandidate?.get("content")?.jsonObject
        val parts = content?.get("parts")?.jsonArray
        val firstPart = parts?.getOrNull(0)?.jsonObject
        return firstPart?.get("text")?.jsonPrimitive?.content?.trim() ?: ""
    }

    private fun jsonEncode(value: String): String {
        return Json.encodeToString(kotlinx.serialization.serializer(), value)
    }
}
