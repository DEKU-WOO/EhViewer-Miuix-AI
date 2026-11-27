package com.ehviewer.core.ui.util

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.fetchAndIncrement

val SharedThumbEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
const val SHARED_THUMB_DURATION_MS = 400

private val DefaultSharedThumbBoundsTransform = BoundsTransform { _, _ ->
    tween(durationMillis = SHARED_THUMB_DURATION_MS, easing = SharedThumbEasing)
}

private val DefaultSharedThumbEnter: EnterTransition = fadeIn(
    animationSpec = tween(durationMillis = SHARED_THUMB_DURATION_MS, easing = SharedThumbEasing),
)

private val DefaultSharedThumbExit: ExitTransition = fadeOut(
    animationSpec = tween(durationMillis = SHARED_THUMB_DURATION_MS, easing = SharedThumbEasing),
)

val NoopTransitionsVisibilityScope = TransitionsVisibilityScope(emptySet())

@JvmInline
@Stable
value class TransitionsVisibilityScope(val scopes: Set<AnimatedVisibilityScope>)

@Composable
context(visScope: TransitionsVisibilityScope)
inline fun <T> togetherWith(scope: AnimatedVisibilityScope, block: @Composable TransitionsVisibilityScope.() -> T) =
    block(remember(visScope.scopes, scope) { TransitionsVisibilityScope(visScope.scopes + scope) })

@Composable
context(shareScope: SharedTransitionScope, visScope: TransitionsVisibilityScope, generator: SETNodeGenerator)
fun Modifier.sharedBounds(
    key: String,
    enter: EnterTransition = DefaultSharedThumbEnter,
    exit: ExitTransition = DefaultSharedThumbExit,
    boundsTransform: BoundsTransform = DefaultSharedThumbBoundsTransform,
): Modifier {
    // 门控离屏预组合 Tab，杜绝 HorizontalPager 相邻离屏页同 gid 卡片抢占 SETNode 或注册重复 sharedBounds key
    if (!LocalIsPageVisible.current) return this
    val node = remember(key) { generator.summon(key) }
    DisposableEffect(key, node) {
        onDispose {
            generator.dispose(node)
        }
    }
    return visScope.scopes.fold(this) { modifier, scope ->
        with(shareScope) {
            modifier.sharedBounds(
                sharedContentState = rememberSharedContentState("${node.syntheticKey} + ${scope.transition.label}"),
                animatedVisibilityScope = scope,
                enter = enter,
                exit = exit,
                boundsTransform = boundsTransform,
            )
        }
    }
}

@Composable
context(_: SharedTransitionScope, _: TransitionsVisibilityScope, _: SETNodeGenerator)
inline fun SharedElementBox(key: String, shape: Shape, crossinline content: @Composable BoxScope.() -> Unit) {
    val modifier = Modifier.sharedBounds(key = key).clip(shape)
    CompositionLocalProvider { Box(modifier = modifier, content = content) }
}

data class SETNode(
    val contentKey: String,
    val uniqueID: String,
)

val SETNode.syntheticKey: String
    get() = contentKey + uniqueID

val listThumbGenerator = SETNodeGenerator()
val detailThumbGenerator = SETNodeGenerator()

fun initSETConnection() {
    listThumbGenerator connectTo detailThumbGenerator
}

private val atomicIncId = AtomicInt(0)

class SETNodeGenerator {
    private val tracker = hashMapOf<String, SETNode>()
    private val opposites = mutableListOf<SETNodeGenerator>()

    fun summon(contentKey: String): SETNode {
        val node = opposites.firstNotNullOfOrNull { it.tracker[contentKey] }
            ?: tracker[contentKey]
            ?: SETNode(contentKey = contentKey, uniqueID = "${atomicIncId.fetchAndIncrement()}")
        tracker[contentKey] = node
        return node
    }

    fun dispose(node: SETNode) = tracker.remove(node.contentKey, node)

    infix fun connectTo(other: SETNodeGenerator) {
        opposites.add(other)
        other.opposites.add(this)
    }
}
