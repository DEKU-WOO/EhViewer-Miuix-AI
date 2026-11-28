package com.hippo.ehviewer.ui.main

import android.widget.Toast
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryDetail
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.TagNamespace
import com.ehviewer.core.ui.component.RatingWidgetReuse
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.big.SadAndroid
import com.ehviewer.core.ui.theme.activeAccentColor
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 统一详情页与列表卡片的语言解析优先级：
 * 1) 优先读取画廊标签中的 `language:` 标签（命中 `language:chinese` 时始终优先返回 `"ZH"`）；
 * 2) 其次复用列表侧 `info.simpleLanguage`（含标题 `[中国翻訳]` / `[汉化]` 正则推导结果）；
 * 3) 仅在无语言标签与简写语言时才回退读取原始 `Language` 元数据字段。
 */
private fun resolveDetailDisplayLanguage(info: GalleryInfo): String? {
    val detail = info as? GalleryDetail
    if (detail != null) {
        val langTags = detail.tagGroups
            .firstOrNull { it.nameSpace == TagNamespace.Language }
            ?.tags
            ?.map { "language:${it.text}" }
            .orEmpty()
        if ("language:chinese" in langTags) {
            return "ZH"
        }
        val tagIndex = GalleryInfo.S_LANG_TAGS.indexOfFirst { it in langTags }
        if (tagIndex != -1) {
            return GalleryInfo.S_LANGS[tagIndex]
        }
    }
    info.simpleLanguage?.takeIf { it.isNotBlank() }?.let { return it }
    return detail?.language?.takeIf { it.isNotBlank() }
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
    rating: Float = info.rating,
    onRatingClick: (() -> Unit)? = null,
    onCoverClick: (() -> Unit)? = null,
) = Card(modifier = modifier) {
    val detail = info as? GalleryDetail
    val metaStyle = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp, lineHeight = 16.sp)
    val subMetaStyle = MiuixTheme.textStyles.footnote2.copy(fontSize = 12.sp, lineHeight = 15.sp)

    Row(modifier = Modifier.fillMaxWidth()) {
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
        Column(
            modifier = Modifier
                .weight(1f)
                .height(dimensionResource(id = com.hippo.ehviewer.R.dimen.gallery_detail_thumb_height))
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // 右侧上半区：详情页专属扩展信息（左文件大小 + 右收藏数 ♡）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .thenIf(detail != null) {
                        clickable(role = Role.Button, onClick = onInfoCardClick)
                    }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = detail?.size.orEmpty(),
                    style = metaStyle,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                )
                if (detail != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiuixIcons.Favorites,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = detail.favoriteCount.toString(),
                            style = metaStyle,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 右侧下半区：与主页列表卡片 GalleryInfoListItem 1:1 空间对齐的三行结构
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 1) 上传者行：左对齐显示上传者名称，保留点击搜索上传者与长按屏蔽上传者功能
                val uploaderText = info.uploader.orEmpty()
                val canInteractUploader = uploaderText.isNotEmpty() && !info.disowned && uploaderText != "(Disowned)"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .thenIf(canInteractUploader) {
                            combinedClickable(
                                role = Role.Button,
                                onClick = onUploaderChipClick,
                                onLongClick = onBlockUploaderIconClick,
                            )
                        }
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = uploaderText,
                        modifier = Modifier.alpha(if (info.disowned || uploaderText == "(Disowned)") 0.5f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = metaStyle,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }

                // 2) 星级与语言页数行：左侧 5 颗可点击评分星标，右侧对齐 [ZH] 语言胶囊 + 总页数
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .thenIf(onRatingClick != null) {
                                clickable(role = Role.Button, onClick = onRatingClick!!)
                            }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        RatingWidgetReuse(
                            rating = rating,
                            ratingSize = 17.dp,
                            ratingInterval = 1.5.dp,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    resolveDetailDisplayLanguage(info)?.let { lang ->
                        if (lang == "ZH") {
                            val zhAccent = activeAccentColor()
                            Text(
                                text = lang,
                                style = metaStyle,
                                fontWeight = FontWeight.Bold,
                                color = zhAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(zhAccent.copy(alpha = 0.16f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                        } else {
                            Text(
                                text = lang,
                                style = metaStyle,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (info.pages != 0) {
                        Text(
                            text = "${info.pages}P",
                            style = metaStyle,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                    }
                }

                // 3) 分类与时间行：左侧保留分类专属识别色徽章与点击筛选，右侧单行对齐上传时间
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val categoryColor = EhUtils.getCategoryColor(info.category)
                    val categoryText = EhUtils.getCategory(info.category).uppercase()
                    Box(
                        modifier = Modifier
                            .clip(SquircleShape(6.dp))
                            .background(categoryColor)
                            .clickable(role = Role.Button, onClick = onCategoryChipClick)
                            .padding(vertical = 2.dp, horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = categoryText,
                            maxLines = 1,
                            softWrap = false,
                            color = EhUtils.getCategoryTextColor(categoryColor),
                            style = subMetaStyle,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = info.posted.orEmpty(),
                        maxLines = 1,
                        softWrap = false,
                        style = subMetaStyle,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    )
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
    val onTranslateOrToggle: () -> Unit,
)

/**
 * 详情页标题（带 AI 翻译）。
 *
 * - 自动模式（[AI_TRANSLATION_MODE_AUTO]）下仅做轻量入队，请求由 [AiTranslationManager]
 *   的全局队列在后台完成，页面销毁不会中断翻译，译写入库后由数据库 Flow 驱动刷新；
 * - 点击标题可手动入队翻译，已翻译后再次点击可在原文与译文之间切换显示。
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
