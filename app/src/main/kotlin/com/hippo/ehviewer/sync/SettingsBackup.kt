package com.hippo.ehviewer.sync

import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.AiProfileStore
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 全量偏好设置的 JSON 序列化。
 *
 * 导出时直接读取底层 DataStore 的全部键值，因此所有可手动修改的设置项、
 * 导航项顺序与开关、AI 翻译的多套 ApiProfile 与模型列表都会自动包含在内，
 * 新增设置项时无需再维护一份字段白名单。
 */
object SettingsBackup {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 下载目录 SAF 授权 URI 与当前 VPS 连接参数在跨设备恢复时必须跳过，避免权限/连接失效。 */
    private val fullRestoreSkipPrefixes = listOf("image_", "vps_")
    private const val DEVICE_ID_KEY = "sync_device_id"

    suspend fun exportJson(): String {
        val obj = LinkedHashMap<String, JsonElement>()
        Settings.exportEntries().forEach { (key, value) ->
            runCatching { encodeValue(value) }.getOrNull()?.let { obj[key] = it }
        }
        return json.encodeToString(JsonObject.serializer(), JsonObject(obj))
    }

    /** 全量恢复：只覆盖备份中存在的键，并跳过本机专属的连接/授权字段。 */
    suspend fun restoreAll(raw: String): Boolean {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return false
        val entries = LinkedHashMap<String, Any>()
        root.forEach { (key, element) ->
            if (key == DEVICE_ID_KEY) return@forEach
            if (fullRestoreSkipPrefixes.any { key.startsWith(it) }) return@forEach
            decodeValue(element)?.let { entries[key] = it }
        }
        Settings.importEntries(entries)
        return true
    }

    /** 仅恢复 API 设置：按方案名称合并/更新，保留本机 Cookie、布局与性能参数。 */
    suspend fun restoreApiProfiles(raw: String): Int {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return 0
        val profilesJson = root["ai_profiles"]?.let(::decodeValue) as? String ?: return 0
        val incoming = AiProfileStore.decode(profilesJson)
        if (incoming.isEmpty()) return 0
        val merged = AiProfileStore.profiles().toMutableList()
        incoming.forEach { profile ->
            val index = merged.indexOfFirst { it.name == profile.name }
            if (index >= 0) {
                merged[index] = merged[index].copy(
                    apiUrl = profile.apiUrl,
                    apiKey = profile.apiKey,
                    models = profile.models,
                    selectedModel = profile.selectedModel,
                )
            } else {
                val id = if (merged.any { it.id == profile.id }) UUID.randomUUID().toString() else profile.id
                merged += profile.copy(id = id)
            }
        }
        Settings.aiProfiles.value = AiProfileStore.encode(merged)
        return incoming.size
    }

    /**
     * 带类型标签编码，避免 JSON 数字在恢复时被拆成 Int/Long 造成 DataStore 类型不匹配。
     */
    private fun encodeValue(value: Any): JsonElement? {
        val (type, payload) = when (value) {
            is Boolean -> "boolean" to JsonPrimitive(value)
            is Int -> "int" to JsonPrimitive(value)
            is Long -> "long" to JsonPrimitive(value)
            is Float -> "float" to JsonPrimitive(value)
            is Double -> "double" to JsonPrimitive(value)
            is String -> "string" to JsonPrimitive(value)
            is Set<*> -> "string_set" to JsonArray(value.filterIsInstance<String>().map(::JsonPrimitive))
            else -> return null
        }
        return JsonObject(mapOf("t" to JsonPrimitive(type), "v" to payload))
    }

    private fun decodeValue(element: JsonElement): Any? {
        val obj = element as? JsonObject ?: return null
        val type = (obj["t"] as? JsonPrimitive)?.contentOrNull ?: return null
        val payload = obj["v"] ?: return null
        val primitive = payload as? JsonPrimitive
        return when (type) {
            "boolean" -> primitive?.booleanOrNull
            "int" -> primitive?.intOrNull
            "long" -> primitive?.longOrNull
            "float" -> primitive?.floatOrNull
            "double" -> primitive?.doubleOrNull
            "string" -> primitive?.contentOrNull
            "string_set" -> (payload as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
            else -> null
        }
    }
}
