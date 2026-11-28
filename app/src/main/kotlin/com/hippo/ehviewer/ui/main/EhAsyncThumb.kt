package com.hippo.ehviewer.ui.main

import android.view.Choreographer
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.ehviewer.core.model.GalleryInfo
import com.ehviewer.core.ui.component.SquircleShape
import com.ehviewer.core.ui.util.SETNodeGenerator
import com.ehviewer.core.ui.util.SharedElementBox
import com.ehviewer.core.ui.util.TransitionsVisibilityScope
import com.ehviewer.core.ui.util.thenIf
import com.hippo.ehviewer.ktbuilder.imageRequest
import com.hippo.ehviewer.ui.tools.shouldCrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 轻量级共享脉冲时钟：仅当屏幕内存在加载态缩略图时按引用计数挂载单个 Choreographer 回调，
 * 所有可见骨架屏共用同一相位并在 graphicsLayer 绘制阶段读取，零协程、零逐帧重组。
 */
private object SharedThumbPulseClock {
    private const val PULSE_DURATION_NS = 900_000_000L
    private var activeConsumers = 0
    private var startTimeNs = 0L
    val alpha = mutableFloatStateOf(0.35f)

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (activeConsumers <= 0) return
            if (startTimeNs == 0L) startTimeNs = frameTimeNanos
            val elapsed = (frameTimeNanos - startTimeNs).coerceAtLeast(0L)
            val cycle = (elapsed / PULSE_DURATION_NS)
            val fraction = (elapsed % PULSE_DURATION_NS).toFloat() / PULSE_DURATION_NS.toFloat()
            val pingPong = if (cycle % 2L == 0L) fraction else 1f - fraction
            alpha.floatValue = 0.35f + 0.40f * FastOutSlowInEasing.transform(pingPong)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun acquire() {
        if (activeConsumers++ == 0) {
            startTimeNs = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun release() {
        activeConsumers = (activeConsumers - 1).coerceAtLeast(0)
        if (activeConsumers == 0) {
            Choreographer.getInstance().removeFrameCallback(frameCallback)
            startTimeNs = 0L
        }
    }
}

@Composable
private fun rememberSharedThumbPulseAlpha(): State<Float> {
    DisposableEffect(Unit) {
        SharedThumbPulseClock.acquire()
        onDispose { SharedThumbPulseClock.release() }
    }
    return SharedThumbPulseClock.alpha
}

@Composable
@NonRestartableComposable
fun requestOf(model: GalleryInfo, constrainSize: Boolean = true) = with(LocalContext.current) {
    remember(model, constrainSize) { imageRequest(model, constrainSize) }
}

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope, _: SETNodeGenerator)
fun EhAsyncCropThumb(
    key: GalleryInfo,
    modifier: Modifier = Modifier,
) = SharedElementBox(key = "${key.gid}", shape = SquircleShape(12.dp)) {
    var contentScale by remember(key) { mutableStateOf(ContentScale.Fit) }
    val request = requestOf(key, constrainSize = true)
    Image(
        // https://github.com/coil-kt/coil/issues/2959
        painter = rememberAsyncImagePainter(
            model = request,
            onSuccess = {
                if (it.result.image.shouldCrop) {
                    contentScale = ContentScale.Crop
                }
            },
        ),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope, _: SETNodeGenerator)
fun EhThumbCard(
    key: GalleryInfo,
    modifier: Modifier = Modifier,
    constrainSize: Boolean = true,
) = Card(modifier = modifier) {
    SharedElementBox(key = "${key.gid}", shape = SquircleShape(12.dp)) {
        var contentScale by remember(key) { mutableStateOf(ContentScale.Fit) }
        val request = requestOf(key, constrainSize = constrainSize)
        val painter = rememberAsyncImagePainter(
            model = request,
            onSuccess = {
                if (it.result.image.shouldCrop) {
                    contentScale = ContentScale.Crop
                }
            },
        )
        val state by painter.state.collectAsState()
        Box(modifier = Modifier.fillMaxSize()) {
            if (state is AsyncImagePainter.State.Loading) {
                val pulseAlpha = rememberSharedThumbPulseAlpha()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { this.alpha = pulseAlpha.value }
                        .background(MiuixTheme.colorScheme.surfaceContainer),
                )
            }
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.thenIf(state !is AsyncImagePainter.State.Success) {
                    // Keep applying this when state is `Loading` to avoid cutting off the ripple
                    clickable { if (state is AsyncImagePainter.State.Error) painter.restart() }
                }.fillMaxSize(),
                contentScale = contentScale,
            )
        }
    }
}
