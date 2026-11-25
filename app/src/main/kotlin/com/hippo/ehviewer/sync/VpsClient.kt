package com.hippo.ehviewer.sync

import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.Settings
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 精简的跨端历史记录，只保留恢复阅读进度与补全元数据所需字段。 */
@Serializable
data class SyncHistoryEntry(
    val gid: Long,
    val token: String = "",
    val pages: Int = 0,
    @SerialName("last_page") val lastPage: Int = 0,
    @SerialName("updated_at") val updatedAt: Long = 0L,
)

/** 云端全量设置快照元信息。 */
@Serializable
data class CloudSettingsRecord(
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("backup_date") val backupDate: String = "",
    @SerialName("profile_count") val profileCount: Int = 0,
    @SerialName("updated_at") val updatedAt: Long = 0L,
    val settings: JsonElement? = null,
    // 兼容不同服务端/历史版本使用的快照字段名
    @SerialName("settings_json") val settingsJsonValue: JsonElement? = null,
    @SerialName("config_json") val configJson: JsonElement? = null,
    @SerialName("configJson") val configJsonCamel: JsonElement? = null,
    val config: JsonElement? = null,
    val data: JsonElement? = null,
) {
    /**
     * 兼容服务端把设置存成 JSON 字符串、嵌套对象或 `config_json` 等外层字段的多种形式。
     * 逐层解包字符串，直到得到可直接解析的设置对象文本。
     */
    val settingsJson: String
        get() {
            val candidates = listOf(settings, settingsJsonValue, configJson, configJsonCamel, config, data)
            candidates.forEach { element ->
                unwrapSettings(element)?.let { return it }
            }
            return "{}"
        }

    private fun unwrapSettings(element: JsonElement?): String? = when (element) {
        null -> null
        is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
        is JsonArray -> element.takeIf { it.isNotEmpty() }?.toString()
        is JsonObject -> {
            val nested = element["settings"] ?: element["settings_json"] ?: element["config_json"]
                ?: element["configJson"] ?: element["config"] ?: element["data"]
            if (nested != null) {
                unwrapSettings(nested)
            } else {
                element.takeIf { it.isNotEmpty() }?.toString()
            }
        }
    }
}

/**
 * 自建 VPS 轻量同步 API 客户端。
 *
 * 所有请求都按需触发，不做任何后台轮询；连接参数直接复用「备份与恢复」页面里的配置。
 */
object VpsClient {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun configured(): Boolean = Settings.vpsServerUrl.value.isNotBlank()

    private fun baseUrl(): String = Settings.vpsServerUrl.value.trim().trimEnd('/')

    private fun applyAuth(builder: io.ktor.client.request.HttpRequestBuilder) {
        val token = Settings.vpsToken.value.trim()
        if (token.isNotEmpty()) {
            builder.header(HttpHeaders.Authorization, "Bearer $token")
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyJson() {
        contentType(ContentType.Application.Json)
        timeout { requestTimeoutMillis = 20_000 }
        applyAuth(this)
    }

    private fun url(path: String): String = baseUrl() + path

    private fun ensureConfigured() {
        if (!configured()) throw IOException("未配置 VPS 服务器地址")
    }

    suspend fun postHistory(entry: SyncHistoryEntry) {
        ensureConfigured()
        val response = EhApplication.ktorClient.post(url("/sync/history")) {
            applyJson()
            setBody(json.encodeToString(SyncHistoryEntry.serializer(), entry))
        }
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value}")
        }
    }

    suspend fun fetchHistory(since: Long): List<SyncHistoryEntry> {
        ensureConfigured()
        val response = EhApplication.ktorClient.get(url("/sync/history?since=$since")) {
            timeout { requestTimeoutMillis = 20_000 }
            applyAuth(this)
        }
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value}")
        }
        val text = response.bodyAsText()
        val array = parseArray(text,"history")
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(SyncHistoryEntry.serializer(), element) }.getOrNull()
        }
    }

    suspend fun postSettings(
        settings: JsonElement,
        deviceId: String,
        deviceName: String,
        backupDate: String,
        profileCount: Int,
    ) {
        ensureConfigured()
        val record = CloudSettingsRecord(
            deviceId = deviceId,
            deviceName = deviceName,
            backupDate = backupDate,
            profileCount = profileCount,
            updatedAt = System.currentTimeMillis(),
            settings = settings,
        )
        val response = EhApplication.ktorClient.post(url("/sync/settings")) {
            applyJson()
            setBody(json.encodeToString(CloudSettingsRecord.serializer(), record))
        }
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value}")
        }
    }

    suspend fun fetchSettings(): List<CloudSettingsRecord> {
        ensureConfigured()
        val response = EhApplication.ktorClient.get(url("/sync/settings")) {
            timeout { requestTimeoutMillis = 25_000 }
            applyAuth(this)
        }
        if (!response.status.isSuccess()) {
            throw IOException("HTTP ${response.status.value}")
        }
        val array = parseArray(response.bodyAsText(), "settings")
        return array.mapNotNull { element ->
            runCatching { json.decodeFromJsonElement(CloudSettingsRecord.serializer(), element) }.getOrNull()
        }
    }

    /** 先探测 /ping 可达性，再请求 /sync/history 校验 Token 鉴权是否通过。 */
    suspend fun testConnection() {
        ensureConfigured()
        val pingUrl = url("/ping")
        val ping = EhApplication.ktorClient.get(pingUrl) {
            timeout { requestTimeoutMillis = 15_000 }
            applyAuth(this)
        }
        ping.bodyAsText()
        if (!ping.status.isSuccess()) {
            throw IOException("GET $pingUrl HTTP ${ping.status.value}")
        }
        val historyUrl = url("/sync/history?since=0")
        val history = EhApplication.ktorClient.get(historyUrl) {
            timeout { requestTimeoutMillis = 15_000 }
            applyAuth(this)
        }
        history.bodyAsText()
        if (!history.status.isSuccess()) {
            throw IOException("GET $historyUrl HTTP ${history.status.value}")
        }
    }

    /** 兼容裸数组与 `{items:[...]}` / `{data:[...]}` / `{data:{settings:[...]}}` 等包装。 */
    private fun parseArray(text: String, key: String): JsonArray {
        if (text.isBlank()) return JsonArray(emptyList())
        val root = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return JsonArray(emptyList())
        return when (root) {
            is JsonArray -> root
            is JsonObject -> {
                when (val direct = root[key] ?: root["items"] ?: root["data"] ?: root["snapshots"]) {
                    is JsonArray -> direct
                    is JsonObject -> (
                        direct[key] ?: direct["items"] ?: direct["snapshots"] ?: direct["records"]
                        ) as? JsonArray ?: JsonArray(emptyList())
                    else -> JsonArray(emptyList())
                }
            }
            else -> JsonArray(emptyList())
        }
    }
}
