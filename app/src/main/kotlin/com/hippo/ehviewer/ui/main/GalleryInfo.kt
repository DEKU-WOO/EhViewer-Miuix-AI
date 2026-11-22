package com.hippo.ehviewer.ui.main

import android.widget.Toast
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ehviewer.core.i18n.R
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.model.GalleryInfo.Companion.NOT_FAVORITED
import com.ehviewer.core.ui.component.CrystalCard
import com.ehviewer.core.ui.component.GalleryListCardRating
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.util.SharedElementBox
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.listThumbGenerator
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.AiEnqueueResult
import com.hippo.ehviewer.client.AiTranslationManager
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.util.FavouriteStatusRouter
import kotlinx.coroutines.flow.filter
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope)
fun GalleryInfoListItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    info: GalleryInfo,
    showPages: Boolean,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    /** 历史页专用：在卡片上额外提示「上次看到第 X 页」。 */
    showLastPageHint: Boolean = false,
    isInFavScene: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = CrystalCard(
    modifier = modifier,
    onClick = onClick,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
) {
    Row {
        with(listThumbGenerator) {
            EhThumbCard(
                key = info,
                modifier = Modifier.aspectRatio(DEFAULT_RATIO),
            )
        }
        Column(modifier = Modifier.padding(start = 8.dp, top = 2.dp, end = 4.dp, bottom = 4.dp)) {
            GalleryTitleText(
                info = info,
                maxLines = 3,
                style = MiuixTheme.textStyles.body1,
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = info.uploader.orEmpty(),
                    modifier = Modifier.alignByBaseline().alpha(if (info.disowned) 0.5f else 1f),
                    style = MiuixTheme.textStyles.footnote1,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (isInFavScene) {
                    info.favoriteNote?.let {
                        Text(
                            text = it,
                            modifier = Modifier.alignByBaseline(),
                            fontStyle = FontStyle.Italic,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                } else {
                    val showFav by FavouriteStatusRouter.collectAsState(info) { it != NOT_FAVORITED }
                    if (showFav) {
                        Icon(
                            imageVector = MiuixIcons.FavoritesFill,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp).align(Alignment.CenterVertically),
                            tint = EhUtils.favoriteIconColor,
                        )
                        info.favoriteName?.let {
                            Text(
                                text = it,
                                modifier = Modifier.alignByBaseline(),
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Place the rating near the uploader text as there's more visual space
                GalleryListCardRating(rating = info.rating, modifier = Modifier.padding(top = 1.dp, bottom = 3.dp))
                Spacer(modifier = Modifier.weight(1f))
                val downloaded by DownloadManager.collectContainDownloadInfo(info.gid)
                if (downloaded) {
                    Icon(
                        imageVector = MiuixIcons.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
                info.simpleLanguage?.let { lang ->
                    if (lang == "ZH") {
                        // 中文画廊：在页数左侧渲染醒目的圆角胶囊徽章，便于未限定语言时快速识别汉化资源
                        Text(
                            text = lang,
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE53935),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x26E53935))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    } else {
                        // 其他语言（EN / KO / RU 等）保持原有朴素灰色纯文本
                        Text(text = lang, style = MiuixTheme.textStyles.footnote1)
                    }
                }
                if (info.pages != 0 && showPages) {
                    val readProgress = if (showProgress) {
                        remember { EhDB.getReadProgressFlow(info.gid) }.collectAsState(0).value
                    } else {
                        0
                    }
                    Text(
                        text = if (readProgress > 0) "${readProgress + 1}/${info.pages}P" else "${info.pages}P",
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
            Row(
                modifier = Modifier.widthIn(max = 1000.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val categoryColor = EhUtils.getCategoryColor(info.category)
                val categoryText = EhUtils.getCategory(info.category).uppercase()
                Text(
                    text = categoryText,
                    modifier = Modifier
                        .clip(SquircleShape(6.dp))
                        .background(categoryColor)
                        .padding(vertical = 2.dp, horizontal = 6.dp),
                    color = EhUtils.getCategoryTextColor(categoryColor),
                    style = MiuixTheme.textStyles.footnote2,
                )
                if (showLastPageHint) {
                    val lastPage = remember { EhDB.getReadProgressFlow(info.gid) }.collectAsState(0).value
                    if (lastPage > 0) {
                        Text(
                            text = stringResource(R.string.history_last_read_page, lastPage + 1),
                            maxLines = 1,
                            softWrap = false,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                // 分类徽章与上传时间之间保持留白，不再插入简化标签，避免挤压右侧时间
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = info.posted.orEmpty(),
                    maxLines = 1,
                    softWrap = false,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                )
            }
        }
    }
}

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope)
fun GalleryInfoGridItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    info: GalleryInfo,
    modifier: Modifier = Modifier,
    onThumbClick: (() -> Unit)? = null,
    showLanguage: Boolean = true,
    showPages: Boolean = true,
    showProgress: Boolean = true,
    showFavoriteStatus: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = CrystalCard(
    modifier = modifier,
    onClick = onClick,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
) {
    Box(
        modifier = if (onThumbClick != null) {
            Modifier.clickable(onClick = onThumbClick)
        } else {
            Modifier
        },
    ) {
        with(listThumbGenerator) {
            SharedElementBox(key = "${info.gid}", shape = SquircleShape(12.dp)) {
                var ratio by remember(info) {
                    val ratio = if (info.thumbHeight != 0) {
                        (info.thumbWidth.toFloat() / info.thumbHeight).coerceIn(MIN_RATIO, MAX_RATIO)
                    } else {
                        DEFAULT_RATIO
                    }
                    mutableFloatStateOf(ratio)
                }
                AsyncImage(
                    model = requestOf(info),
                    contentDescription = null,
                    modifier = Modifier.aspectRatio(ratio),
                    onSuccess = {
                        ratio = (it.result.image.width.toFloat() / it.result.image.height).coerceIn(MIN_RATIO, MAX_RATIO)
                    },
                )
            }
        }
        val categoryColor = EhUtils.getCategoryColor(info.category)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .clip(SquircleShape(6.dp))
                .background(categoryColor)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shouldShowLanguage = showLanguage && info.simpleLanguage != null
            val textColor = EhUtils.getCategoryTextColor(categoryColor)
            if (showPages && info.pages > 0) {
                val readProgress = if (showProgress) {
                    remember { EhDB.getReadProgressFlow(info.gid) }.collectAsState(0).value
                } else {
                    0
                }
                Text(
                    text = if (readProgress > 0) "${readProgress + 1}/${info.pages}" else "${info.pages}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = textColor,
                )
                if (shouldShowLanguage) {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            if (shouldShowLanguage) {
                Text(
                    text = info.simpleLanguage.orEmpty(),
                    style = MiuixTheme.textStyles.footnote2,
                    color = textColor,
                )
            }
        }
        if (showFavoriteStatus) {
            val isFavorited by FavouriteStatusRouter.collectAsState(info) { it != NOT_FAVORITED }
            if (isFavorited) {
                Icon(
                    imageVector = MiuixIcons.FavoritesFill,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                    tint = EhUtils.favoriteIconColor,
                )
            }
        }
    }
}

/**
 * 画廊标题（带 AI 翻译）。
 *
 * - 自动模式（[AI_TRANSLATION_MODE_AUTO]）：进入列表即把标题轻量入队，真正的请求由
 *   [AiTranslationManager] 的全局队列在后台完成；卡片滑出屏幕不会取消请求，
 *   译写入库后由数据库 Flow 驱动本组件重组显示，因此快速滑动也不会漏翻；
 * - 手动模式：点击标题入队翻译；
 * - 已翻译后再次点击可在原文与译文之间切换显示；
 * - 翻译失败时：自动模式保持原标题静默，手动触发会 Toast 出具体原因。
 */
@Composable
fun GalleryTitleText(
    info: GalleryInfo,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val enabled by remember { Settings.aiTranslationEnabled.valueFlow() }
        .collectAsState(initial = Settings.aiTranslationEnabled.value)
    val triggerMode by remember { Settings.aiTranslationMode.valueFlow() }
        .collectAsState(initial = Settings.aiTranslationMode.value)
    val original = remember(info.gid) { EhUtils.getSuitableTitle(info) }
    val translation by remember(info.gid) {
        AiTranslationManager.translationFlow(info.gid)
    }.collectAsState(initial = null)
    val translated = translation
        ?.takeIf { it.originalTitle == original }
        ?.translatedTitle
        ?.takeIf { it.isNotBlank() && it != original }
    var showTranslated by remember(info.gid) { mutableStateOf(true) }
    var reportFailure by remember(info.gid) { mutableStateOf(false) }
    val context = LocalContext.current

    // 仅做轻量入队：请求不绑定卡片生命周期，滑出屏幕也不会被取消
    LaunchedEffect(info.gid, enabled, triggerMode, original) {
        if (enabled && triggerMode == AI_TRANSLATION_MODE_AUTO) {
            AiTranslationManager.enqueueTranslation(info.gid, original)
        }
    }

    // 只在用户手动触发时提示失败原因，自动模式保持静默，避免快速滑动时刷屏 Toast
    LaunchedEffect(info.gid) {
        AiTranslationManager.failures
            .filter { it.gid == info.gid }
            .collect { failure ->
                if (reportFailure) {
                    reportFailure = false
                    Toast.makeText(context, failure.message, Toast.LENGTH_LONG).show()
                }
            }
    }

    // 手动模式下点击标题即翻译（失败会 Toast）；自动模式不拦截点击，避免影响卡片本身的跳转
    val interceptClick = enabled && (translated != null || triggerMode != AI_TRANSLATION_MODE_AUTO)
    Text(
        text = if (showTranslated) translated ?: original else original,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = style,
        modifier = if (interceptClick) {
            modifier.clickable {
                if (translated != null) {
                    showTranslated = !showTranslated
                } else {
                    when (val result = AiTranslationManager.enqueueTranslation(info.gid, original)) {
                        is AiEnqueueResult.Rejected ->
                            Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                        AiEnqueueResult.AlreadyChinese ->
                            Toast.makeText(context, "标题已是中文，无需翻译", Toast.LENGTH_SHORT).show()
                        AiEnqueueResult.Queued, AiEnqueueResult.AlreadyQueued -> reportFailure = true
                    }
                }
            }
        } else {
            modifier
        },
    )
}

private const val MIN_RATIO = 0.5F
private const val MAX_RATIO = 1.5F
const val DEFAULT_RATIO = 0.67F

/** AI 翻译触发模式：0 = 列表自动翻译，1 = 手动点击翻译 */
const val AI_TRANSLATION_MODE_AUTO = 0
