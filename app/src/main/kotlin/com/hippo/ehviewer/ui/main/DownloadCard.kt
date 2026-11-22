package com.hippo.ehviewer.ui.main

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
        val stateFailed = stringResource(R.string.download_state_failed)
        val stateFailed2 = stringResource(R.string.download_state_failed_2, info.legacy)
        val downloadState by DownloadManager.collectDownloadState(info.gid)
        Column(modifier = Modifier.fillMaxHeight().padding(start = 8.dp, top = 2.dp, end = 4.dp, bottom = 4.dp)) {
            // 与在线画廊卡片保持同一标题规范（body1 + 最多 3 行），避免误用详情页大标题
            GalleryTitleText(
                info = info,
                maxLines = 3,
                style = MiuixTheme.textStyles.body1,
            )
            // 标题贴顶、作者/星级/分类/状态贴底，与在线卡片一致的上下两端对齐
            Spacer(modifier = Modifier.weight(1f))
            if (downloadState != DownloadInfo.STATE_DOWNLOAD) {
                val stateText = when (downloadState) {
                    DownloadInfo.STATE_NONE -> stringResource(R.string.download_state_none)
                    DownloadInfo.STATE_WAIT -> stringResource(R.string.download_state_wait)
                    DownloadInfo.STATE_FAILED -> if (info.legacy <= 0) stateFailed else stateFailed2
                    DownloadInfo.STATE_FINISH -> {
                        if (showProgress) {
                            val readProgress by remember(info.gid) { EhDB.getReadProgressFlow(info.gid) }.collectAsState(0)
                            if (readProgress > 0) "${readProgress + 1}/${info.pages}P" else "${info.pages}P"
                        } else {
                            "${info.pages}P"
                        }
                    }
                    else -> null // The item has been removed and this will be disposed soon
                }
                Row {
                    Text(
                        text = info.uploader.orEmpty(),
                        // 单独占满剩余宽度；不要再叠加 Spacer(weight(1f))，否则上传者名会被提前挤成省略号。
                        modifier = Modifier.alignByBaseline().weight(1f).alpha(if (info.disowned) 0.5f else 1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Text(
                        text = stateText.orEmpty(),
                        modifier = Modifier.alignByBaseline(),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                var total by remember { mutableIntStateOf(info.total) }
                var finished by remember { mutableIntStateOf(info.finished) }
                var speed by remember { mutableLongStateOf(info.speed) }
                DownloadManager.updatedDownloadInfo(info) {
                    total = this.total
                    finished = this.finished
                    speed = this.speed
                }
                if (total <= 0 || finished < 0) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Row {
                        Text(text = "$finished/${total}P", style = MiuixTheme.textStyles.footnote1)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(text = FileUtils.humanReadableByteCount(speed.coerceAtLeast(0)) + "/s", style = MiuixTheme.textStyles.footnote1)
                    }
                    LinearProgressIndicator(
                        progress = (finished.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    // Place the rating near the uploader text as there's more visual space
                    GalleryListCardRating(rating = info.rating, modifier = Modifier.padding(vertical = 1.dp))
                    val categoryColor = EhUtils.getCategoryColor(info.category)
                    val categoryText = EhUtils.getCategory(info.category).uppercase()
                    Text(
                        text = categoryText,
                        modifier = Modifier.clip(SquircleShape(4.dp)).background(categoryColor).padding(vertical = 2.dp, horizontal = 8.dp),
                        color = EhUtils.getCategoryTextColor(categoryColor),
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val running = downloadState == DownloadInfo.STATE_WAIT || downloadState == DownloadInfo.STATE_DOWNLOAD
                val icon = when (downloadState) {
                    DownloadInfo.STATE_WAIT, DownloadInfo.STATE_DOWNLOAD -> MiuixIcons.Pause
                    DownloadInfo.STATE_FINISH -> MiuixIcons.Ok
                    else -> MiuixIcons.Play
                }
                val actionDesc = when (downloadState) {
                    DownloadInfo.STATE_WAIT, DownloadInfo.STATE_DOWNLOAD -> stringResource(id = R.string.download_pause)
                    DownloadInfo.STATE_FINISH -> stringResource(id = R.string.download_state_finish)
                    else -> stringResource(id = R.string.download_start)
                }
                IconButton(
                    onClick = if (running) onStop else onStart,
                    modifier = Modifier.offset(4.dp),
                    enabled = !selectMode,
                ) {
                    Icon(imageVector = icon, contentDescription = actionDesc)
                }
            }
        }
    }
}
