package com.hippo.ehviewer.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.BlurredBar
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.component.blurBackdropSource
import com.ehviewer.core.ui.component.glassRim
import com.ehviewer.core.ui.component.rememberBlurBackdrop
import com.ehviewer.core.ui.util.readableWidth
import com.ehviewer.core.util.launch
import com.ehviewer.core.util.withIOContext
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.client.AiConnectionTestResult
import com.hippo.ehviewer.client.AiProfileStore
import com.hippo.ehviewer.client.AiTranslationManager
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.Screen
import com.hippo.ehviewer.ui.main.NavigationIcon
import com.hippo.ehviewer.ui.tools.awaitConfirmationOrCancel
import com.hippo.ehviewer.ui.tools.awaitInputText
import com.hippo.ehviewer.ui.tools.dialog
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlin.coroutines.resume
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/** 添加模型弹窗里的免费模型快捷填充项。 */
private val suggestedModels = listOf(
    "gemini-3.5-flash-lite",
    "gemini-3-flash-preview",
    "Qwen/Qwen3-8B",
)

@Destination<RootGraph>
@Composable
fun AnimatedVisibilityScope.AiTranslationScreen(navigator: DestinationsNavigator) = Screen(navigator) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberBlurBackdrop()
    val context = LocalContext.current

    // 整页数据都从当前激活方案派生，切换下拉后自动刷新下方所有卡片
    val profilesJson by Settings.aiProfiles.collectAsState()
    val activeProfileId by Settings.aiActiveProfileId.collectAsState()
    val profiles = remember(profilesJson) { AiProfileStore.decode(profilesJson) }
    val activeProfile = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.firstOrNull()

    val newProfileTitle = stringResource(R.string.settings_ai_translation_profile_new)
    val renameProfileTitle = stringResource(R.string.settings_ai_translation_profile_rename)
    val profileNameHint = stringResource(R.string.settings_ai_translation_profile_name_hint)
    val profileNameEmpty = stringResource(R.string.settings_ai_translation_profile_name_empty)
    val profileNameExists = stringResource(R.string.settings_ai_translation_profile_name_exists)
    val keepOneProfile = stringResource(R.string.settings_ai_translation_profile_keep_one)
    val addModelTitle = stringResource(R.string.settings_ai_translation_model_add)
    val modelHint = stringResource(R.string.settings_ai_translation_model_hint)
    val modelEmpty = stringResource(R.string.settings_ai_translation_model_empty)
    val modelExists = stringResource(R.string.settings_ai_translation_model_exists)
    val keepOneModel = stringResource(R.string.settings_ai_translation_model_keep_one)
    val cacheCleared = stringResource(R.string.settings_ai_translation_cache_cleared)
    var testing by remember { mutableStateOf(false) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    fun newProfile() = launch {
        val name = awaitInputText(title = newProfileTitle, hint = profileNameHint) { text ->
            val trimmed = text.trim()
            if (trimmed.isEmpty()) raise(profileNameEmpty)
            if (AiProfileStore.profiles().any { it.name == trimmed }) raise(profileNameExists)
        }
        AiProfileStore.addProfile(name.trim())
    }

    fun renameProfile() = launch {
        val current = AiProfileStore.activeProfile() ?: return@launch
        val name = awaitInputText(
            initial = current.name,
            title = renameProfileTitle,
            hint = profileNameHint,
        ) { text ->
            val trimmed = text.trim()
            if (trimmed.isEmpty()) raise(profileNameEmpty)
            if (AiProfileStore.profiles().any { it.name == trimmed && it.id != current.id }) raise(profileNameExists)
        }
        AiProfileStore.renameProfile(current.id, name.trim())
    }

    fun deleteProfile() = launch {
        val current = AiProfileStore.activeProfile() ?: return@launch
        if (AiProfileStore.profiles().size <= 1) {
            toast(keepOneProfile)
            return@launch
        }
        awaitConfirmationOrCancel(
            title = R.string.settings_ai_translation_profile_delete,
            confirmText = R.string.delete,
            text = {
                MiuixText(
                    text = stringResource(R.string.settings_ai_translation_profile_delete_message, current.name),
                )
            },
        )
        AiProfileStore.deleteProfile(current.id)
    }

    fun addModel() = launch {
        val current = AiProfileStore.activeProfile() ?: return@launch
        val model = dialog<String> { cont ->
            var error by remember { mutableStateOf<String?>(null) }
            val fieldState = rememberTextFieldState()
            WindowDialog(
                show = true,
                modifier = Modifier.glassRim(),
                onDismissRequest = { cont.cancel() },
                title = addModelTitle,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    TextField(
                        state = fieldState,
                        label = modelHint,
                        lineLimits = TextFieldLineLimits.SingleLine,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestedModels.forEach { id ->
                            MiuixText(
                                text = id,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .clip(SquircleShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainer)
                                    .clickable {
                                        fieldState.setTextAndPlaceCursorAtEnd(id)
                                        error = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                    if (error != null) {
                        Spacer(Modifier.height(4.dp))
                        MiuixText(
                            text = error!!,
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            text = stringResource(android.R.string.cancel),
                            onClick = { cont.cancel() },
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            text = stringResource(R.string.add),
                            onClick = {
                                val id = fieldState.text.toString().trim()
                                when {
                                    id.isEmpty() -> error = modelEmpty
                                    current.models.contains(id) -> error = modelExists
                                    else -> cont.resume(id)
                                }
                            },
                        )
                    }
                }
            }
        }
        AiProfileStore.updateProfile(current.id) { profile ->
            profile.copy(models = profile.models + model, selectedModel = model)
        }
    }

    fun deleteSelectedModel() = launch {
        val current = AiProfileStore.activeProfile() ?: return@launch
        if (current.models.size <= 1) {
            toast(keepOneModel)
            return@launch
        }
        val remaining = current.models - current.selectedModel
        AiProfileStore.updateProfile(current.id) { profile ->
            profile.copy(models = remaining, selectedModel = remaining.first())
        }
    }

    fun testConnection() = launch {
        if (testing) return@launch
        testing = true
        try {
            val profile = AiProfileStore.activeProfile() ?: return@launch
            val message = when (val result = AiTranslationManager.testConnection(profile.apiUrl, profile.apiKey, profile.selectedModel)) {
                is AiConnectionTestResult.Success -> context.getString(R.string.settings_ai_translation_test_success, result.elapsedMs)
                is AiConnectionTestResult.Failure -> context.getString(R.string.settings_ai_translation_test_failure, result.message)
            }
            toast(message)
        } finally {
            testing = false
        }
    }

    fun clearTranslationCache() = launch {
        awaitConfirmationOrCancel(
            confirmText = R.string.clear_all,
            title = R.string.settings_ai_translation_cache_title,
        ) {
            MiuixText(text = stringResource(R.string.settings_ai_translation_cache_confirm))
        }
        withIOContext { EhDB.clearTranslations() }
        toast(cacheCleared)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            BlurredBar(
                backdrop = backdrop,
                scrollBehavior = scrollBehavior,
            ) {
                TopAppBar(
                    title = stringResource(id = R.string.settings_ai_translation),
                    navigationIcon = { NavigationIcon() },
                    scrollBehavior = scrollBehavior,
                    color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                .blurBackdropSource(backdrop),
            contentAlignment = Alignment.TopCenter,
        ) {
            SettingsScrollColumn(
                scrollBehavior = scrollBehavior,
                contentPadding = paddingValues,
            ) {
                SmallTitle(text = stringResource(id = R.string.settings_ai_translation))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    SwitchPreference(
                        title = stringResource(id = R.string.settings_ai_translation_enable),
                        summary = stringResource(id = R.string.settings_ai_translation_enable_summary),
                        state = Settings.aiTranslationEnabled.asMutableState(),
                    )
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    val mode = Settings.aiTranslationMode.asMutableState()
                    OverlayDropdownPreference(
                        title = stringResource(id = R.string.settings_ai_translation_mode),
                        items = listOf(
                            stringResource(id = R.string.settings_ai_translation_mode_auto),
                            stringResource(id = R.string.settings_ai_translation_mode_manual),
                        ),
                        selectedIndex = mode.value.coerceIn(0, 1),
                        showValue = true,
                        onSelectedIndexChange = { mode.value = it },
                    )
                }

                SmallTitle(text = stringResource(id = R.string.settings_ai_translation_profiles_title))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (profiles.isNotEmpty()) {
                        OverlayDropdownPreference(
                            title = stringResource(id = R.string.settings_ai_translation_profile),
                            items = profiles.map { it.name },
                            selectedIndex = profiles.indexOfFirst { it.id == activeProfile?.id }.coerceAtLeast(0),
                            showValue = true,
                            onSelectedIndexChange = { index ->
                                profiles.getOrNull(index)?.let { AiProfileStore.setActiveProfile(it.id) }
                            },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = if (profiles.isEmpty()) 16.dp else 0.dp,
                                bottom = 16.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileActionButton(
                            text = newProfileTitle,
                            onClick = ::newProfile,
                            modifier = Modifier.weight(1f),
                        )
                        ProfileActionButton(
                            text = renameProfileTitle,
                            onClick = ::renameProfile,
                            modifier = Modifier.weight(1f),
                        )
                        ProfileActionButton(
                            text = stringResource(R.string.settings_ai_translation_profile_delete),
                            onClick = ::deleteProfile,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                SmallTitle(text = stringResource(id = R.string.settings_ai_translation_interface_title))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    val profile = activeProfile
                    if (profile != null) {
                        // 上下各留 12.dp，配合元素自身 4.dp 达到与设置项一致的 16.dp 呼吸边距
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            // 切换方案时重建输入框，保证文本状态跟随当前方案
                            key(profile.id) {
                                ProfileTextField(
                                    title = stringResource(R.string.settings_ai_translation_api_url),
                                    value = profile.apiUrl,
                                    onValueChange = { text ->
                                        AiProfileStore.updateProfile(profile.id) { it.copy(apiUrl = text) }
                                    },
                                )
                                ProfileTextField(
                                    title = stringResource(R.string.settings_ai_translation_api_key),
                                    value = profile.apiKey,
                                    password = true,
                                    onValueChange = { text ->
                                        AiProfileStore.updateProfile(profile.id) { it.copy(apiKey = text) }
                                    },
                                )
                                OverlayDropdownPreference(
                                    title = stringResource(R.string.settings_ai_translation_model_select),
                                    items = profile.models,
                                    selectedIndex = profile.models.indexOf(profile.selectedModel).coerceAtLeast(0),
                                    showValue = true,
                                    onSelectedIndexChange = { index ->
                                        profile.models.getOrNull(index)?.let { model ->
                                            AiProfileStore.updateProfile(profile.id) { it.copy(selectedModel = model) }
                                        }
                                    },
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(text = addModelTitle, onClick = ::addModel)
                                    TextButton(
                                        text = stringResource(R.string.settings_ai_translation_model_delete),
                                        onClick = ::deleteSelectedModel,
                                    )
                                }
                                TextButton(
                                    text = stringResource(
                                        if (testing) {
                                            R.string.settings_ai_translation_testing
                                        } else {
                                            R.string.settings_ai_translation_test
                                        },
                                    ),
                                    onClick = ::testConnection,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }

                SmallTitle(text = stringResource(id = R.string.settings_ai_translation_cache_title))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    val cacheCount by remember { EhDB.translationCountFlow }.collectAsState(0)
                    BasicComponent(
                        title = stringResource(R.string.settings_ai_translation_cache_title),
                        summary = stringResource(R.string.settings_ai_translation_cache_summary, cacheCount),
                        onClick = { clearTranslationCache() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    var hidden by remember { mutableStateOf(password) }
    val fieldState = rememberTextFieldState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    // 输入内容异步写回当前配置方案，保证退出页面或切换方案时不丢失修改
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { currentOnValueChange(it) }
    }
    TextField(
        state = fieldState,
        label = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        outputTransformation = if (password && hidden) {
            OutputTransformation { replace(0, length, "\u2022".repeat(length)) }
        } else {
            null
        },
        trailingIcon = if (password) {
            {
                IconButton(onClick = { hidden = !hidden }) {
                    Icon(
                        imageVector = if (hidden) MiuixIcons.Show else MiuixIcons.Hide,
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        },
    )
}

/** 配置方案操作按钮：等宽单行，避免在卡片内折行。 */
@Composable
private fun ProfileActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与「添加模型 / 删除当前模型 / 恢复默认」保持一致的圆角矩形按钮，而不是全圆角胶囊
    TextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
    )
}
