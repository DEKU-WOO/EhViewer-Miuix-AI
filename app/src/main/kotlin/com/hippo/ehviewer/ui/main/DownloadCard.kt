package com.hippo.ehviewer.ui.main

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ehviewer.core.database.model.DownloadInfo
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.CrystalCard
import com.ehviewer.core.ui.component.GalleryListCardRating
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.listThumbGenerator
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.util.FileUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val DownloadActionBadgeSize = 32.dp
private val DownloadActionIconSize = 16.dp
private val DownloadProgressBarHeight = 4.dp
private val DownloadFinishBadgeBg = Color(0xFF34C759).copy(alpha = 0.15f)
private val DownloadFinishIconLight = Color(0xFF28A745)
private val DownloadFinishIconDark = Color(0xFF34C759)

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope)
fun DownloadCard(
    onClick: () -> Unit,
    onThumbClick: () -> Unit,
    onLongClick: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    info: DownloadInfo,
    thumbHeight: Dp,
    selectMode: Boolean,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = CrystalCard(
    // 高度只锁定下限：缩略图保持设定大小，卡片整体随右侧信息列自然撑开，避免底部标签被裁切
    modifier = modifier.heightIn(min = thumbHeight),
    onClick = onClick,
    onLongClick = onLongClick,
    interactionSource = interactionSource,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        val thumb = remember {
            movableContentOf<DownloadInfo> {
                with(listThumbGenerator) {
                    EhAsyncCropThumb(
                        key = it,
                        modifier = Modifier.aspectRatio(DEFAULT_RATIO).fillMaxSize(),
                    )
                }
            }
        }
        if (selectMode) {
            Card(modifier = Modifier.height(thumbHeight)) {
                thumb(info)
            }
        } else {
            Card(modifier = Modifier.height(thumbHeight), onClick = onThumbClick) {
                thumb(info)
            }
        }
        val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
        val downloadState by DownloadManager.collectDownloadState(info.gid)
        val isFinish = downloadState == DownloadInfo.STATE_FINISH
        val isActive = downloadState == DownloadInfo.STATE_DOWNLOAD || downloadState == DownloadInfo.STATE_WAIT

        var total by remember(info.gid) { mutableIntStateOf(info.total) }
        var finished by remember(info.gid) { mutableIntStateOf(info.finished) }
        var speed by remember(info.gid) { mutableLongStateOf(info.speed) }
        if (!isFinish) {
            DownloadManager.updatedDownloadInfo(info) {
                total = this.total
                finished = this.finished
                speed = this.speed
            }
        }

        Column(modifier = Modifier.fillMaxHeight().padding(start = 8.dp, top = 4.dp, end = 6.dp, bottom = 4.dp)) {
            // 与在线画廊卡片保持同一紧凑标题规范（14.sp / 18.sp 行高 + 最多 2 行）
            GalleryTitleText(
                info = info,
                maxLines = 2,
                style = galleryListCardTitleStyle(),
            )
            // 标题区与下方元信息区保持清晰分层间距
            Spacer(modifier = Modifier.height(6.dp))
            Spacer(modifier = Modifier.weight(1f))
            val metaStyle = MiuixTheme.textStyles.footnote1.copy(fontSize = 11.5.sp, lineHeight = 14.sp)

            if (isFinish) {
                val pagesText = if (showProgress) {
                    val readProgress by remember(info.gid) { EhDB.getReadProgressFlow(info.gid) }.collectAsState(0)
                    if (readProgress > 0) "${readProgress + 1}/${info.pages}P" else "${info.pages}P"
                } else {
                    "${info.pages}P"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = info.uploader.orEmpty(),
                        modifier = Modifier.alignByBaseline().weight(1f).alpha(if (info.disowned) 0.5f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = metaStyle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = pagesText,
                        modifier = Modifier.alignByBaseline(),
                        maxLines = 1,
                        softWrap = false,
                        style = metaStyle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                // 下载中与暂停/未完成态共用同一套半幅进度条布局：
                // 左侧保留上传者名，右侧 54% 区域承载「速度/已暂停 + 进度页数」与半幅圆角进度条，右缘与已完成页数严格对齐
                val displayTotal = if (total > 0) total else info.pages.coerceAtLeast(0)
                val displayFinished = finished.coerceIn(0, displayTotal.coerceAtLeast(0))
                val progressFraction = if (displayTotal > 0) {
                    (displayFinished.toFloat() / displayTotal.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val trackColor = if (isActive) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                }
                val fillColor = if (isActive) {
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = info.uploader.orEmpty(),
                        modifier = Modifier
                            .weight(0.46f)
                            .padding(end = 8.dp)
                            .alpha(if (info.disowned) 0.5f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = metaStyle,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Column(
                        modifier = Modifier.weight(0.54f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = if (isActive) {
                                    FileUtils.humanReadableByteCount(speed.coerceAtLeast(0)) + "/s"
                                } else {
                                    "已暂停"
                                },
                                maxLines = 1,
                                softWrap = false,
                                style = metaStyle.copy(
                                    fontSize = if (isActive) 10.5.sp else 10.sp,
                                    lineHeight = 13.sp,
                                ),
                                color = if (isActive) {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.72f)
                                },
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "$displayFinished/${displayTotal}P",
                                maxLines = 1,
                                softWrap = false,
                                style = metaStyle,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(DownloadProgressBarHeight)
                                .clip(CircleShape)
                                .background(trackColor),
                        ) {
                            if (progressFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressFraction)
                                        .clip(CircleShape)
                                        .background(fillColor),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    GalleryListCardRating(rating = info.rating, modifier = Modifier.padding(vertical = 1.dp))
                    val categoryColor = EhUtils.getCategoryColor(info.category)
                    val categoryText = EhUtils.getCategory(info.category).uppercase()
                    Text(
                        text = categoryText,
                        modifier = Modifier
                            .clip(SquircleShape(6.dp))
                            .background(categoryColor)
                            .padding(vertical = 1.5.dp, horizontal = 6.dp),
                        color = EhUtils.getCategoryTextColor(categoryColor),
                        style = MiuixTheme.textStyles.footnote2.copy(lineHeight = 14.sp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // 右下角统一 32.dp 正圆形徽章/操作按钮：开始(▷) / 暂停(‖) / 已完成(✓)
                when {
                    isFinish -> {
                        Box(
                            modifier = Modifier
                                .size(DownloadActionBadgeSize)
                                .clip(CircleShape)
                                .background(DownloadFinishBadgeBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Ok,
                                contentDescription = stringResource(id = R.string.download_state_finish),
                                tint = if (isDark) DownloadFinishIconDark else DownloadFinishIconLight,
                                modifier = Modifier.size(DownloadActionIconSize),
                            )
                        }
                    }
                    isActive -> {
                        Box(
                            modifier = Modifier
                                .size(DownloadActionBadgeSize)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.14f))
                                .clickable(enabled = !selectMode, role = Role.Button, onClick = onStop),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Pause,
                                contentDescription = stringResource(id = R.string.download_pause),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(DownloadActionIconSize),
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .size(DownloadActionBadgeSize)
                                .clip(CircleShape)
                                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                                .clickable(enabled = !selectMode, role = Role.Button, onClick = onStart),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Play,
                                contentDescription = stringResource(id = R.string.download_start),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(DownloadActionIconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}
