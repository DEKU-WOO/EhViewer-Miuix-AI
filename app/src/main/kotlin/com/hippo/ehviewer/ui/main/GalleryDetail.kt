package com.hippo.ehviewer.ui.main

import android.widget.Toast
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.SadAndroid
import com.ehviewer.core.ui.util.LocalWindowLayout
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.detailThumbGenerator
import com.ehviewer.core.ui.util.thenIf
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.AiEnqueueResult
import com.hippo.ehviewer.client.AiTranslationManager
import com.hippo.ehviewer.client.EhUtils
import kotlinx.coroutines.flow.filter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GalleryDetailHeaderInfoCard(
    detail: GalleryDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = detail.run {
    Column(
        modifier = modifier
            .clip(SquircleShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.65f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language.orEmpty(),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = size.orEmpty(),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = pluralStringResource(id = R.plurals.page_count, pages, pages),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiuixIcons.Favorites,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = favoriteCount.toString(),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
            }
        }
        if (!posted.isNullOrEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = posted.orEmpty(),
                modifier = Modifier.align(Alignment.End),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope)
fun GalleryDetailHeaderCard(
    info: GalleryInfo,
    onInfoCardClick: () -> Unit,
    onUploaderChipClick: () -> Unit,
    onBlockUploaderIconClick: () -> Unit,
    onCategoryChipClick: () -> Unit,
    modifier: Modifier = Modifier,
    onCoverClick: (() -> Unit)? = null,
) = Card(modifier = modifier) {
    Row(
        modifier = Modifier.fillMaxWidth(),
    ) {
        with(detailThumbGenerator) {
            EhThumbCard(
                key = remember(info.gid) { info },
                modifier = Modifier.size(
                    dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_width),
                    dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height),
                ).thenIf(onCoverClick != null) {
                    clickable(role = Role.Button, onClick = onCoverClick!!)
                },
            )
        }
        val isWide = LocalWindowLayout.current.isWideContent
        Spacer(modifier = Modifier.width(if (isWide) dimensionResource(id = com.hippo.ehviewer.R.dimen.keyline_margin) else 12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .height(dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height))
                .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            (info as? GalleryDetail)?.let {
                GalleryDetailHeaderInfoCard(
                    detail = it,
                    onClick = onInfoCardClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            } ?: Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val categoryText = EhUtils.getCategory(info.category).uppercase()
                Row(
                    modifier = Modifier
                        .clip(SquircleShape(8.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainer)
                        .clickable(role = Role.Button, onClick = onCategoryChipClick)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = categoryText,
                        overflow = TextOverflow.Visible,
                        softWrap = false,
                        maxLines = 1,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                info.uploader?.takeIf { it.isNotEmpty() }?.let { uploaderText ->
                    val canBlock = uploaderText != "(Disowned)"
                    Row(
                        modifier = Modifier
                            .clip(SquircleShape(8.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canBlock) {
                            IconButton(
                                onClick = onBlockUploaderIconClick,
                                modifier = Modifier.size(28.dp),
                                cornerRadius = 8.dp,
                                minHeight = 28.dp,
                                minWidth = 28.dp,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Blocklist,
                                    contentDescription = stringResource(id = R.string.block_uploader),
                                    modifier = Modifier.size(15.dp),
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 28.dp)
                                .clickable(role = Role.Button, onClick = onUploaderChipClick)
                                .padding(
                                    start = if (canBlock) 2.dp else 10.dp,
                                    end = 10.dp,
                                    top = 5.dp,
                                    bottom = 5.dp,
                                ),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = uploaderText,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                maxLines = 1,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryDetailErrorTip(error: String, onClick: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    Icon(
        imageVector = EhIcons.Big.Default.SadAndroid,
        contentDescription = stringResource(id = R.string.action_retry),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        tint = MiuixTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.size(8.dp))
    Text(
        text = error,
        modifier = Modifier.widthIn(max = 228.dp),
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
    )
}

/** 详情页标题的 AI 翻译状态。 */
@Stable
class GalleryDetailTitleState(
    val title: String,
    val translationEnabled: Boolean,
    val translated: Boolean,
    val onTranslateOrToggle: () -> Unit,
)

/**
 * 详情页标题（带 AI 翻译）。
 *
 * - 自动模式（[AI_TRANSLATION_MODE_AUTO]）下仅做轻量入队，请求由 [AiTranslationManager]
 *   的全局队列在后台完成，页面销毁不会中断翻译，译写入库后由数据库 Flow 驱动刷新；
 * - 手动模式下点击翻译按钮触发；
 * - 已翻译后再次触发可在原文与译文之间切换显示。
 */
@Composable
fun rememberGalleryDetailTitleState(gid: Long, original: String): GalleryDetailTitleState {
    val enabled by remember { Settings.aiTranslationEnabled.valueFlow() }
        .collectAsState(initial = Settings.aiTranslationEnabled.value)
    val triggerMode by remember { Settings.aiTranslationMode.valueFlow() }
        .collectAsState(initial = Settings.aiTranslationMode.value)
    val translation by remember(gid) {
        AiTranslationManager.translationFlow(gid)
    }.collectAsState(initial = null)
    val translated = translation
        ?.takeIf { it.originalTitle == original }
        ?.translatedTitle
        ?.takeIf { it.isNotBlank() && it != original }
    var showTranslated by remember(gid) { mutableStateOf(true) }
    var reportFailure by remember(gid) { mutableStateOf(false) }
    val context = LocalContext.current

    // 仅入队，不在此处等待网络结果，避免页面销毁时取消正在进行的翻译
    LaunchedEffect(gid, enabled, triggerMode, original) {
        if (enabled && triggerMode == AI_TRANSLATION_MODE_AUTO) {
            AiTranslationManager.enqueueTranslation(gid, original)
        }
    }

    LaunchedEffect(gid) {
        AiTranslationManager.failures
            .filter { it.gid == gid }
            .collect { failure ->
                if (reportFailure) {
                    reportFailure = false
                    Toast.makeText(context, failure.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    return GalleryDetailTitleState(
        title = if (showTranslated) translated ?: original else original,
        translationEnabled = enabled,
        translated = translated != null,
        onTranslateOrToggle = {
            if (translated != null) {
                showTranslated = !showTranslated
            } else {
                when (val result = AiTranslationManager.enqueueTranslation(gid, original)) {
                    is AiEnqueueResult.Rejected ->
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    AiEnqueueResult.AlreadyChinese ->
                        Toast.makeText(context, "标题已是中文，无需翻译", Toast.LENGTH_SHORT).show()
                    AiEnqueueResult.Queued, AiEnqueueResult.AlreadyQueued -> reportFailure = true
                }
            }
        },
    )
}
