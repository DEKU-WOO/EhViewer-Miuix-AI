package com.hippo.ehviewer.client

import com.hippo.ehviewer.Settings
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** 旧版单一配置的默认值，同时作为新建方案与迁移时的兜底值。 */
const val DEFAULT_AI_API_URL = "https://api.openai.com/v1"
const val DEFAULT_AI_MODEL_ID = "gpt-4o-mini"
const val DEFAULT_AI_PROFILE_NAME = "默认配置"

/** 迁移尚未落盘时兜底方案使用的固定 id，避免同一进程内反复生成不同方案。 */
private const val LEGACY_PROFILE_ID = "legacy-default"

/** 单套 AI 接口配置方案：一个接口地址、一个 Key 与一组可切换的模型。 */
@Serializable
data class ApiProfile(
    val id: String,
    val name: String,
    val apiUrl: String = DEFAULT_AI_API_URL,
    val apiKey: String = "",
    val models: List<String> = listOf(DEFAULT_AI_MODEL_ID),
    val selectedModel: String = DEFAULT_AI_MODEL_ID,
)

/** 导出/导入剪贴板时使用的备份结构，携带激活方案以便一键还原。 */
@Serializable
data class AiProfileBackup(
    val version: Int = 1,
    val activeProfileId: String = "",
    val profiles: List<ApiProfile> = emptyList(),
)

/**
 * 多套 AI 配置方案的持久化入口。
 *
 * 方案列表以 JSON 存放在单个偏好键中，激活方案单独存放 id；读写都经过本对象，
 * 保证 [ApiProfile.selectedModel] 始终落在 [ApiProfile.models] 内。
 */
object AiProfileStore {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val profileListSerializer = ListSerializer(ApiProfile.serializer())

    fun decode(raw: String): List<ApiProfile> = runCatching {
        json.decodeFromString(profileListSerializer, raw)
    }.getOrElse { emptyList() }.map { it.normalized() }

    fun encode(profiles: List<ApiProfile>): String = json.encodeToString(profileListSerializer, profiles)

    /**
     * 当前所有方案。
     *
     * 偏好尚未完成迁移时（读取到空白的方案 JSON）返回一份由旧版单一配置派生的内存兜底方案，
     * 且不写入磁盘——迁移写入是异步的，若这里抢先落盘会覆盖掉待迁移的旧配置。
     */
    fun profiles(): List<ApiProfile> {
        val decoded = decode(Settings.aiProfiles.value)
        return decoded.ifEmpty { listOf(legacyFallback()) }
    }

    private fun legacyFallback(): ApiProfile {
        val model = Settings.aiTranslationModel.value.ifBlank { DEFAULT_AI_MODEL_ID }
        return ApiProfile(
            id = LEGACY_PROFILE_ID,
            name = DEFAULT_AI_PROFILE_NAME,
            apiUrl = Settings.aiTranslationApiUrl.value.ifBlank { DEFAULT_AI_API_URL },
            apiKey = Settings.aiTranslationApiKey.value,
            models = listOf(model),
            selectedModel = model,
        )
    }

    fun activeProfile(): ApiProfile? {
        val list = profiles()
        val activeId = Settings.aiActiveProfileId.value
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    fun setActiveProfile(id: String) {
        Settings.aiActiveProfileId.value = id
    }

    /** 新建方案并立即切换为激活方案。 */
    fun addProfile(name: String): ApiProfile {
        val profile = ApiProfile(id = newId(), name = name)
        Settings.aiProfiles.value = encode(profiles() + profile)
        Settings.aiActiveProfileId.value = profile.id
        return profile
    }

    fun renameProfile(id: String, name: String) = updateProfile(id) { it.copy(name = name) }

    fun updateProfile(id: String, transform: (ApiProfile) -> ApiProfile) {
        val list = profiles()
        if (list.none { it.id == id }) return
        Settings.aiProfiles.value = encode(list.map { if (it.id == id) transform(it) else it })
    }

    /** 删除方案；至少保留一套，成功删除时会把激活方案切到剩余的第一套。 */
    fun deleteProfile(id: String): Boolean {
        val list = profiles()
        if (list.size <= 1) return false
        val remaining = list.filterNot { it.id == id }
        if (remaining.size == list.size) return false
        Settings.aiProfiles.value = encode(remaining)
        if (Settings.aiActiveProfileId.value == id) {
            Settings.aiActiveProfileId.value = remaining.first().id
        }
        return true
    }

    fun exportBackup(): String = json.encodeToString(
        AiProfileBackup.serializer(),
        AiProfileBackup(
            activeProfileId = Settings.aiActiveProfileId.value,
            profiles = profiles(),
        ),
    )

    /**
     * 从 JSON 恢复方案，返回导入的套数（0 表示内容无效）。
     *
     * 既接受 [AiProfileBackup] 结构，也兼容直接粘贴方案数组的形式。
     */
    fun importBackup(raw: String): Int {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0
        val backup = runCatching {
            json.decodeFromString(AiProfileBackup.serializer(), trimmed)
        }.getOrNull()
        val imported = backup?.profiles ?: decode(trimmed)
        val profiles = imported.map { it.normalized() }
        if (profiles.isEmpty()) return 0

        val activeId = backup?.activeProfileId?.takeIf { id -> profiles.any { it.id == id } }
            ?: profiles.first().id
        Settings.aiProfiles.value = encode(profiles)
        Settings.aiActiveProfileId.value = activeId
        return profiles.size
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun ApiProfile.normalized(): ApiProfile {
        val cleanedModels = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val safeModels = cleanedModels.ifEmpty { listOf(DEFAULT_AI_MODEL_ID) }
        return copy(
            id = id.ifBlank { newId() },
            name = name.ifBlank { DEFAULT_AI_PROFILE_NAME },
            models = safeModels,
            selectedModel = selectedModel.takeIf { it in safeModels } ?: safeModels.first(),
        )
    }
}
