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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
import com.hippo.ehviewer.client.AiTranslationManager
import com.hippo.ehviewer.client.AiTranslationResult
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.util.FavouriteStatusRouter
import kotlinx.coroutines.launch
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
                maxLines = 2,
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
                info.simpleLanguage?.let {
                    Text(text = it, style = MiuixTheme.textStyles.footnote1)
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
                val displayTags = remember(info.simpleTags) {
                    info.simpleTags?.filterNot { it.startsWith("language:") }?.take(2)
                }
                if (!displayTags.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    displayTags.forEach { rawTag ->
                        val tagText = rawTag.substringAfter(':')
                        Text(
                            text = tagText,
                            modifier = Modifier
                                .clip(SquircleShape(4.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = info.posted.orEmpty(),
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
 * - 自动模式（[AI_TRANSLATION_MODE_AUTO]）：进入列表即自动翻译并展示译文；
 * - 手动模式：点击标题触发翻译；
 * - 已翻译后再次点击可在原文与译文之间切换显示。
 * - 翻译失败时：自动模式保持原标题静默（避免列表弹出一串提示），手动点击（含自动模式下补点）会 Toast 出具体原因。
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
    var translated by remember(info.gid) { mutableStateOf<String?>(null) }
    var showTranslated by remember(info.gid) { mutableStateOf(false) }
    var translating by remember(info.gid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    suspend fun translate(reportError: Boolean) {
        if (!enabled || translated != null || translating) return
        translating = true
        when (val result = AiTranslationManager.getOrTranslateTitle(info.gid, original)) {
            is AiTranslationResult.Success -> {
                if (result.title.isNotBlank() && result.title != original) {
                    translated = result.title
                    showTranslated = true
                }
            }
            is AiTranslationResult.Failure -> {
                if (reportError) Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
        }
        translating = false
    }

    LaunchedEffect(info.gid, enabled, triggerMode) {
        if (enabled && triggerMode == AI_TRANSLATION_MODE_AUTO) translate(reportError = false)
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
                    scope.launch { translate(reportError = true) }
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
