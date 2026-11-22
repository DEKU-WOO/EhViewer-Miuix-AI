package com.hippo.ehviewer.client

import com.ehviewer.core.database.EhDatabase
import com.ehviewer.core.database.model.GalleryTranslationEntity
import com.ehviewer.core.database.roomDb
import com.ehviewer.core.util.logcat
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** 入队结果：入队是纯内存操作，真正的网络请求由全局队列在后台完成。 */
sealed interface AiEnqueueResult {
    /** 已加入全局翻译队列，译文稍后写入数据库并通过 Flow 驱动 UI 刷新。 */
    data object Queued : AiEnqueueResult

    /** 该画廊已经在队列中或正在翻译，无需重复入队。 */
    data object AlreadyQueued : AiEnqueueResult

    /** 标题主体已是简体中文，无需翻译。 */
    data object AlreadyChinese : AiEnqueueResult

    /** 无法入队（未启用 / 未配置 / 标题为空等），[message] 可直接展示给用户。 */
    data class Rejected(val message: String) : AiEnqueueResult
}

/** 翻译失败事件：UI 可据此只为用户手动触发的翻译弹出提示。 */
data class AiTranslationFailure(val gid: Long, val message: String)

/** 测试连接结果：成功时携带耗时，失败时携带可直接展示的状态码与错误信息。 */
sealed interface AiConnectionTestResult {
    data class Success(val elapsedMs: Long) : AiConnectionTestResult
    data class Failure(val message: String) : AiConnectionTestResult
}

/**
 * 画廊标题 AI 翻译的全局调度器。
 *
 * 列表卡片只做轻量 [enqueueTranslation]，请求本身运行在应用级协程作用域中：
 * 卡片滑出屏幕、Composable 被回收都不会取消正在进行的翻译，结果统一写入 Room，
 * 再由 [translationFlow] 驱动 UI 重组显示，从根本上避免快速滑动造成的漏翻。
 */
object AiTranslationManager {
    /** 聚批等待窗口：把快速滑动时密集入队的标题合并成一次请求。 */
    private const val BATCH_WINDOW_MS = 200L

    /** 单次批量请求最多合并的标题条数。 */
    private const val BATCH_SIZE = 5

    /** 全局最大并发请求数，避免瞬间高并发触发 API 限流（429）。 */
    private const val MAX_CONCURRENT_REQUESTS = 2

    /** 系统提示词：明确要求翻译日文/英文主体并保留社团方括号信息。 */
    private const val SYSTEM_PROMPT =
        "你是专业的漫画/画廊标题翻译器。请将画廊标题中的日文或英文主体自然翻译为简体中文" +
            "（保留社团/作者等方括号信息），仅当主体本身已经是纯简体中文时才原样返回。" +
            "不要解释、不要添加引号、不要输出与译文无关的内容。"

    private val httpClient get() = EhApplication.ktorClient

    private val json = Json { ignoreUnknownKeys = true }

    /** 全局队列作用域：与应用同生命周期，不随任何卡片的 LaunchedEffect 取消。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val queue = Channel<TranslationTask>(Channel.UNLIMITED)

    /** 去重集合：同一 gid 在翻译完成或被判定失败前不会重复入队。 */
    private val inFlightSet = ConcurrentHashMap.newKeySet<Long>()

    private val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    private val _failures = MutableSharedFlow<AiTranslationFailure>(extraBufferCapacity = 64)

    /** 翻译失败事件流，供 UI 为手动触发的翻译提示原因。 */
    val failures: SharedFlow<AiTranslationFailure> = _failures.asSharedFlow()

    private val db by lazy { roomDb<EhDatabase>("eh.db") }

    /** 剥离 `[...]`、`【...】`、`(...)`、`（...）` 等社团/作者/翻译组标签。 */
    private val BRACKET_PATTERN = Regex("\\[[^\\]]*]|【[^】]*】|\\([^)]*\\)|（[^）]*）")

    private val KANA_PATTERN = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF\\u31F0-\\u31FF\\uFF66-\\uFF9D]")

    private val HANGUL_PATTERN = Regex("[\\uAC00-\\uD7AF\\u1100-\\u11FF]")

    /** 连续 2 个以上拉丁字母视为英文单词主体。 */
    private val LATIN_WORD_PATTERN = Regex("[A-Za-z]{2,}")

    /** 简体中文独有的常用字，用于判断汉字主体是否已是简体中文。 */
    private val SIMPLIFIED_ONLY_CHARS = buildSet {
        "们这说东车时过对开关电话语实际发现见观爱亲为从众义务欢乐书买卖张机场马鸟鱼龙头风飞汉译图组档压级丝术纯洁线织统还进运达选标样计让认识论讲讨应该种类别网络视频转换复载记忆简"
            .forEach { add(it) }
    }

    private data class TranslationTask(val gid: Long, val originalTitle: String)

    private class AiRequestException(
        message: String,
        val httpStatus: Int? = null,
        val contentBlocked: Boolean = false,
    ) : Exception(message) {
        /**
         * Key 失效/欠费/风控（401/403）、请求不被接受（400）或内容安全策略拦截时，
         * 当前方案继续尝试已无意义，直接顺延到下一个配置方案。
         */
        val shouldRotateProfile: Boolean
            get() = httpStatus == 401 || httpStatus == 403 || httpStatus == 400 || contentBlocked
    }

    init {
        scope.launch { batchLoop() }
    }

    /**
     * 轻量入队：只校验配置并入内存队列，立即返回，绝不阻塞 UI 或绑定调用方生命周期。
     */
    fun enqueueTranslation(gid: Long, originalTitle: String): AiEnqueueResult {
        if (gid <= 0L) return AiEnqueueResult.Rejected("无效的画廊 ID")
        if (!Settings.aiTranslationEnabled.value) return AiEnqueueResult.Rejected("AI 翻译未启用")
        if (originalTitle.isBlank()) return AiEnqueueResult.Rejected("标题为空，无需翻译")
        if (AiProfileStore.activeProfile() == null) return AiEnqueueResult.Rejected("未配置 API 方案")
        if (!needsTranslation(originalTitle)) return AiEnqueueResult.AlreadyChinese
        if (!inFlightSet.add(gid)) return AiEnqueueResult.AlreadyQueued
        val sent = queue.trySend(TranslationTask(gid, originalTitle))
        if (sent.isFailure) {
            inFlightSet.remove(gid)
            return AiEnqueueResult.Rejected("翻译队列不可用")
        }
        return AiEnqueueResult.Queued
    }

    /** 订阅某个画廊的译文，数据库写入后自动推送新值。 */
    fun translationFlow(gid: Long): Flow<GalleryTranslationEntity?> =
        flow { emitAll(db.galleryTranslationDao().flowTranslation(gid)) }.flowOn(Dispatchers.IO)

    /**
     * 判断标题是否需要送入 AI 翻译。
     *
     * 关键点：不能因为标题里出现汉字（或 `[中国翻訳]` 之类的标签）就认定它是中文——
     * 大量日文标题同样由汉字构成。这里先剥离社团/作者等括号信息，再看主体：
     * - 含平假名、片假名、韩文或连续英文单词 → 必须翻译；
     * - 纯汉字且带简体特征字 → 认为已是简体中文，跳过；
     * - 纯汉字但没有简体特征（可能是日文汉字标题）→ 保守地送去翻译。
     */
    private fun needsTranslation(title: String): Boolean {
        val body = BRACKET_PATTERN.replace(title, " ").trim()
        if (body.isEmpty()) return false
        if (KANA_PATTERN.containsMatchIn(body)) return true
        if (HANGUL_PATTERN.containsMatchIn(body)) return true
        if (LATIN_WORD_PATTERN.containsMatchIn(body)) return true
        return body.none { it in SIMPLIFIED_ONLY_CHARS }
    }

    /** 聚批循环：收到任务后短暂等待，把窗口内到达的标题合并成一次请求。 */
    private suspend fun batchLoop() {
        for (first in queue) {
            val batch = ArrayList<TranslationTask>(BATCH_SIZE)
            batch += first
            delay(BATCH_WINDOW_MS)
            while (batch.size < BATCH_SIZE) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch += next
            }
            scope.launch { processBatch(batch) }
        }
    }

    private suspend fun processBatch(tasks: List<TranslationTask>) {
        // 过滤掉数据库中已有相同原文译文的条目，避免重复请求
        val pending = tasks.filterNot { task ->
            val cached = findCached(task)
            if (cached != null) inFlightSet.remove(task.gid)
            cached != null
        }
        if (pending.isEmpty()) return

        val configs = resolveConfigs()
        if (configs.isEmpty()) {
            pending.forEach { failTask(it, "未配置 API 方案或 Key") }
            return
        }

        if (pending.size == 1) {
            translateSingle(pending.first(), configs)
            return
        }

        // 批量请求同样遵循「单轮双层容错」：每个「方案 + 模型」组合最多尝试一次，
        // 遇到限流/拥堵/超时换下一个模型，Key 失效/请求被拒/内容拦截则直接顺延下一个方案。
        val attempted = HashSet<String>()
        var translated: Map<Long, String>? = null
        var lastError = "未知错误"
        batchAttempt@ for (config in configs) {
            for (model in config.models) {
                val modelConfig = config.copy(model = model)
                attempted += comboKey(modelConfig)
                try {
                    translated = requestBatchTranslation(modelConfig, pending)
                    break@batchAttempt
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiRequestException) {
                    lastError = e.message ?: "未知错误"
                    // 当前方案 Key 失效/请求被拒/内容被拦截：剩余模型不再尝试，顺延下一个方案
                    if (e.shouldRotateProfile) break
                } catch (e: Exception) {
                    lastError = e.message?.takeIf { it.isNotBlank() } ?: (e::class.simpleName ?: "未知错误")
                }
            }
        }

        val result = translated
        if (result == null) {
            // 批次整体失败时禁止静默丢弃：拆成单条继续轮换，已尝试过的组合不再重复请求，
            // 确保只有真正违规的那一条失败，其余正常标题仍能译出。
            logcat("AiTranslation") { "批量翻译失败：$lastError，降级为逐条翻译" }
            pending.forEach { translateSingle(it, configs, attempted) }
            return
        }

        pending.forEach { task ->
            val text = result[task.gid]
            if (text.isNullOrBlank()) {
                // 批量响应里缺少这一条，用剩余未尝试的组合单独补译
                translateSingle(task, configs, attempted)
            } else {
                saveTranslation(task, text)
            }
        }
    }

    /**
     * 单条翻译的「有限单轮」双层自动容错调度：
     * 1. 同方案内按顺序轮换 [ApiConfig.models]：429 / 503 / 超时等失败后换下一个模型；
     * 2. 当前方案的模型全部失败，或遇到 401/403/400/内容安全策略拦截时，顺延到下一个方案；
     * 3. 每个「方案 + 模型」组合最多尝试一次，任一组合成功立即返回，全部失败立即结束。
     */
    private suspend fun translateSingle(
        task: TranslationTask,
        configs: List<ApiConfig> = resolveConfigs(),
        skipKeys: Set<String> = emptySet(),
    ) {
        if (configs.isEmpty()) {
            failTask(task, "未配置 API 方案或 Key")
            return
        }
        var lastError: String? = null
        profileLoop@ for ((profileIndex, config) in configs.withIndex()) {
            if (profileIndex > 0) {
                logcat("AiTranslation") { "切换备用 API 方案（第 ${profileIndex + 1} 个）" }
            }
            for (model in config.models) {
                val modelConfig = config.copy(model = model)
                // 该组合在本轮任务中已经尝试过，严格不重复请求
                if (comboKey(modelConfig) in skipKeys) continue
                if (model != config.models.first()) {
                    logcat("AiTranslation") { "轮换备用模型：$model" }
                }
                try {
                    val translated = semaphore.withPermit {
                        requestAiTranslation(modelConfig, task.originalTitle)
                    }
                    if (translated.isBlank()) throw AiRequestException("模型未返回翻译内容")
                    saveTranslation(task, translated)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AiRequestException) {
                    lastError = e.message ?: "未知错误"
                    // Key 失效/欠费/风控/内容拦截：本方案剩余模型不必再试，直接顺延下一个方案
                    if (e.shouldRotateProfile) continue@profileLoop
                } catch (e: Exception) {
                    lastError = e.message?.takeIf { it.isNotBlank() } ?: (e::class.simpleName ?: "未知错误")
                    // 请求超时等网络异常同样视为当前模型不可用，换下一个模型
                }
            }
        }
        failTask(task, lastError ?: "所有已配置的方案与模型均不可用")
    }

    private suspend fun findCached(task: TranslationTask): String? =
        runCatching { db.galleryTranslationDao().getTranslation(task.gid) }
            .getOrNull()
            ?.takeIf { it.originalTitle == task.originalTitle }
            ?.translatedTitle

    private suspend fun saveTranslation(task: TranslationTask, translated: String) {
        val saved = runCatching {
            db.galleryTranslationDao().insertTranslation(
                GalleryTranslationEntity(
                    gid = task.gid,
                    originalTitle = task.originalTitle,
                    translatedTitle = translated,
                ),
            )
        }.isSuccess
        inFlightSet.remove(task.gid)
        if (!saved) _failures.tryEmit(AiTranslationFailure(task.gid, "译文写入数据库失败"))
    }

    private fun failTask(task: TranslationTask, message: String) {
        inFlightSet.remove(task.gid)
        _failures.tryEmit(AiTranslationFailure(task.gid, message))
    }

    /** 单套候选配置：接口地址、Key、按优先级排序的模型列表与当前尝试的模型。 */
    private data class ApiConfig(
        val apiUrl: String,
        val apiKey: String,
        val models: List<String>,
        val model: String = models.first(),
    )

    /**
     * 按「当前激活方案优先、其余方案依原顺序」生成候选配置列表，
     * 供双层自动容错轮换使用。模型列表按「选中模型优先、其余备用模型依原顺序」排序。
     */
    private fun resolveConfigs(): List<ApiConfig> {
        val all = AiProfileStore.profiles()
        val active = AiProfileStore.activeProfile()
        val ordered = if (active == null) all else listOf(active) + all.filterNot { it.id == active.id }
        return ordered.mapNotNull { profile ->
            val apiUrl = profile.apiUrl.trim().trimEnd('/')
            val apiKey = profile.apiKey.trim()
            val selected = profile.selectedModel.trim()
            val models = (listOf(selected) + profile.models.map { it.trim() })
                .filter { it.isNotBlank() }
                .distinct()
            if (apiUrl.isBlank() || apiKey.isBlank() || models.isEmpty()) {
                null
            } else {
                ApiConfig(apiUrl, apiKey, models)
            }
        }
    }

    /** 唯一标识一个「方案 + 模型」组合，用于保证单次任务内每个组合最多尝试一次。 */
    private fun comboKey(config: ApiConfig): String = "${config.apiUrl}|${config.apiKey}|${config.model}"

    /**
     * 用给定的接口地址、Key 与模型发送一次轻量请求，验证当前配置是否可用。
     *
     * 失败时直接把 HTTP 状态码和响应片段交还调用方展示，避免用户只能看到笼统错误。
     */
    suspend fun testConnection(apiUrl: String, apiKey: String, model: String): AiConnectionTestResult {
        val url = apiUrl.trim().trimEnd('/')
        val key = apiKey.trim()
        val targetModel = model.trim()
        if (url.isBlank()) return AiConnectionTestResult.Failure("未配置 API 地址")
        if (key.isBlank()) return AiConnectionTestResult.Failure("未配置 API Key")
        if (targetModel.isBlank()) return AiConnectionTestResult.Failure("未选择模型")

        val start = System.currentTimeMillis()
        return try {
            val response = if (isOpenAiCompatible(url)) {
                postOpenAi(url, key, targetModel, "ping", maxTokens = 16)
            } else {
                postGemini(url, key, targetModel, "ping")
            }
            val body = response.bodyAsText()
            if (response.status.isSuccess()) {
                AiConnectionTestResult.Success(System.currentTimeMillis() - start)
            } else {
                AiConnectionTestResult.Failure("HTTP ${response.status.value}：${body.take(200)}")
            }
        } catch (e: Exception) {
            val reason = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            AiConnectionTestResult.Failure(reason)
        }
    }

    private suspend fun requestAiTranslation(config: ApiConfig, title: String): String {
        val userPrompt = "请翻译下面的画廊标题：\n$title"
        return if (isOpenAiCompatible(config.apiUrl)) {
            requestOpenAiTranslation(config, userPrompt, SYSTEM_PROMPT)
        } else {
            requestGeminiTranslation(config, userPrompt, SYSTEM_PROMPT)
        }
    }

    private suspend fun requestBatchTranslation(
        config: ApiConfig,
        tasks: List<TranslationTask>,
    ): Map<Long, String> {
        val userPrompt = buildBatchPrompt(tasks)
        val raw = semaphore.withPermit {
            if (isOpenAiCompatible(config.apiUrl)) {
                requestOpenAiTranslation(config, userPrompt, SYSTEM_PROMPT)
            } else {
                requestGeminiTranslation(config, userPrompt, SYSTEM_PROMPT)
            }
        }
        return parseBatchTranslation(raw, tasks)
    }

    private fun buildBatchPrompt(tasks: List<TranslationTask>): String {
        val payload = buildString {
            append('{')
            tasks.forEachIndexed { index, task ->
                if (index > 0) append(',')
                append(jsonEncode((index + 1).toString()))
                append(':')
                append(jsonEncode(task.originalTitle))
            }
            append('}')
        }
        return """
            请把下面 JSON 中每个编号对应的画廊标题翻译为简体中文，只输出同样编号的 JSON 对象，例如 {"1": "译文"}。
            不要输出解释、代码块标记或多余文字。
            $payload
        """.trimIndent()
    }

    private fun parseBatchTranslation(raw: String, tasks: List<TranslationTask>): Map<Long, String> {
        val element = parseJsonPayload(raw)
        val result = HashMap<Long, String>()
        when (element) {
            is JsonObject -> tasks.forEachIndexed { index, task ->
                val value = (element[(index + 1).toString()] as? JsonPrimitive)?.contentOrNull
                    ?: (element[task.gid.toString()] as? JsonPrimitive)?.contentOrNull
                if (!value.isNullOrBlank()) result[task.gid] = value.trim()
            }

            is JsonArray -> element.forEachIndexed { index, item ->
                val task = tasks.getOrNull(index) ?: return@forEachIndexed
                val value = (item as? JsonPrimitive)?.contentOrNull
                if (!value.isNullOrBlank()) result[task.gid] = value.trim()
            }

            else -> throw IllegalStateException("批量翻译响应格式异常")
        }
        if (result.isEmpty()) throw IllegalStateException("批量翻译响应为空")
        return result
    }

    /** 兼容模型把 JSON 包在 ```json 代码块或附加说明文字里的情况。 */
    private fun parseJsonPayload(raw: String): JsonElement {
        val text = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val first = text.indexOfFirst { it == '{' || it == '[' }
        val last = text.indexOfLast { it == '}' || it == ']' }
        val payload = if (first >= 0 && last > first) text.substring(first, last + 1) else text
        return json.parseToJsonElement(payload)
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

    private suspend fun postOpenAi(
        apiUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemPrompt: String? = null,
        maxTokens: Int? = null,
    ): HttpResponse {
        val endpoint = if (apiUrl.endsWith("/chat/completions")) apiUrl else "$apiUrl/chat/completions"
        val maxTokensField = maxTokens?.let { ",\n                \"max_tokens\": $it" }.orEmpty()
        val messages = buildString {
            append('[')
            if (systemPrompt != null) {
                append("{\"role\": \"system\", \"content\": ${jsonEncode(systemPrompt)}},")
            }
            append("{\"role\": \"user\", \"content\": ${jsonEncode(prompt)}}")
            append(']')
        }
        val requestBody = """
            {
                "model": ${jsonEncode(model)},
                "messages": $messages,
                "temperature": 0.3$maxTokensField
            }
        """.trimIndent()

        return httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            // 共享 ktorClient 的全局请求超时仅 10s，翻译请求单独放宽
            timeout { requestTimeoutMillis = 30_000 }
            setBody(requestBody)
        }
    }

    private suspend fun postGemini(
        apiUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        systemPrompt: String? = null,
    ): HttpResponse {
        val targetModel = model.ifBlank { "gemini-1.5-flash" }
        // 兼容形如 https://generativelanguage.googleapis.com/v1beta 或带模型的完整 URL
        val endpoint = if (apiUrl.contains("/models/")) {
            "$apiUrl:generateContent?key=$apiKey"
        } else {
            "$apiUrl/models/$targetModel:generateContent?key=$apiKey"
        }
        val systemField = systemPrompt?.let {
            """
        "systemInstruction": {
            "parts": [{"text": ${jsonEncode(it)}}]
        },
            """.trimIndent()
        }.orEmpty()

        val requestBody = """
            {
                $systemField
                "contents": [
                    {
                        "parts": [
                            {"text": ${jsonEncode(prompt)}}
                        ]
                    }
                ]
            }
        """.trimIndent()

        return httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            // 共享 ktorClient 的全局请求超时仅 10s，翻译请求单独放宽
            timeout { requestTimeoutMillis = 30_000 }
            setBody(requestBody)
        }
    }

    private suspend fun requestOpenAiTranslation(
        config: ApiConfig,
        prompt: String,
        systemPrompt: String,
    ): String {
        val response = postOpenAi(config.apiUrl, config.apiKey, config.model, prompt, systemPrompt)
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw AiRequestException("HTTP ${response.status.value}：${body.take(200)}", response.status.value)
        }

        val jsonObject = json.parseToJsonElement(body).jsonObject
        val choice = (jsonObject["choices"] as? JsonArray)?.getOrNull(0) as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val content = (message?.get("content") as? JsonPrimitive)?.content?.trim().orEmpty()
        if (content.isEmpty()) {
            val finishReason = (choice?.get("finish_reason") as? JsonPrimitive)?.content
            if (finishReason == "content_filter") {
                // 内容安全策略拦截：换模型/换方案才有意义
                throw AiRequestException("内容被模型安全策略拦截", contentBlocked = true)
            }
            throw IllegalStateException("模型未返回翻译内容")
        }
        return content
    }

    private suspend fun requestGeminiTranslation(
        config: ApiConfig,
        prompt: String,
        systemPrompt: String,
    ): String {
        val response = postGemini(config.apiUrl, config.apiKey, config.model, prompt, systemPrompt)
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw AiRequestException("HTTP ${response.status.value}：${body.take(200)}", response.status.value)
        }

        val jsonObject = json.parseToJsonElement(body).jsonObject
        val candidate = (jsonObject["candidates"] as? JsonArray)?.getOrNull(0) as? JsonObject
        val part = ((candidate?.get("content") as? JsonObject)?.get("parts") as? JsonArray)?.getOrNull(0) as? JsonObject
        val content = (part?.get("text") as? JsonPrimitive)?.content?.trim().orEmpty()
        if (content.isEmpty()) {
            val blockReason = ((jsonObject["promptFeedback"] as? JsonObject)?.get("blockReason") as? JsonPrimitive)?.content
                ?: ((candidate?.get("finishReason")) as? JsonPrimitive)?.content
            if (blockReason != null) {
                // 内容安全策略拦截：换模型/换方案才有意义
                throw AiRequestException("内容被模型安全策略拦截（$blockReason）", contentBlocked = true)
            }
            throw IllegalStateException("模型未返回翻译内容")
        }
        return content
    }

    private fun jsonEncode(value: String): String {
        return Json.encodeToString(kotlinx.serialization.serializer(), value)
    }
}
