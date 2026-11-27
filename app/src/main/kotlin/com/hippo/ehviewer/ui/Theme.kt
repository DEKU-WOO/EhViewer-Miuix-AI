package com.hippo.ehviewer.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import com.ehviewer.core.ui.util.ProvideVectorPainterCache
import com.ehviewer.core.ui.util.SHARED_THUMB_DURATION_MS
import com.ehviewer.core.ui.util.SharedThumbEasing
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import com.hippo.ehviewer.ui.destinations.GalleryDetailScreenDestination
import com.hippo.ehviewer.ui.theme.EhTheme
import com.hippo.ehviewer.ui.theme.resolveUseDarkTheme
import com.hippo.ehviewer.ui.tools.DialogState
import com.hippo.ehviewer.ui.tools.LocalGlobalDialogState
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

inline fun ComponentActivity.setMiuixContent(crossinline content: @Composable DialogState.() -> Unit) = setContent {
    // 应用内深色设置（theme_2：-1 跟随系统 / 1 浅色 / 2 深色）实时驱动主题，
    // Android 12+ 不经 AppCompatDelegate，必须在此显式解析
    val theme by Settings.theme.collectAsState()
    EhTheme(useDarkTheme = resolveUseDarkTheme(theme, isSystemInDarkTheme())) {
        ProvideVectorPainterCache {
            val dialogState = remember { DialogState() }
            CompositionLocalProvider(LocalGlobalDialogState provides dialogState) {
                Box(modifier = Modifier.fillMaxSize()) {
                    content(dialogState)
                }
            }
        }
    }
}

private typealias Ty = AnimatedContentTransitionScope<NavBackStackEntry>

private val DetailTransitionFloatSpec = tween<Float>(
    durationMillis = SHARED_THUMB_DURATION_MS,
    easing = SharedThumbEasing,
)

@Composable
fun rememberEhNavAnim(): NavHostAnimatedDestinationStyle {
    val slideDistance = rememberSlideDistance()
    return remember(slideDistance) {
        object : NavHostAnimatedDestinationStyle() {
            override val enterTransition: Ty.() -> EnterTransition = {
                if (targetState.destination.route == GalleryDetailScreenDestination.route) {
                    fadeIn(animationSpec = DetailTransitionFloatSpec) +
                        scaleIn(initialScale = 0.94f, animationSpec = DetailTransitionFloatSpec) +
                        slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = SHARED_THUMB_DURATION_MS,
                                easing = SharedThumbEasing,
                            ),
                        ) { (it * 0.06f).toInt() }
                } else {
                    materialSharedAxisXIn(true, slideDistance)
                }
            }

            override val exitTransition: Ty.() -> ExitTransition = {
                if (targetState.destination.route == GalleryDetailScreenDestination.route) {
                    fadeOut(animationSpec = DetailTransitionFloatSpec) +
                        scaleOut(targetScale = 0.96f, animationSpec = DetailTransitionFloatSpec)
                } else {
                    materialSharedAxisXOut(true, slideDistance)
                }
            }

            override val popEnterTransition: Ty.() -> EnterTransition = {
                if (initialState.destination.route == GalleryDetailScreenDestination.route) {
                    fadeIn(animationSpec = DetailTransitionFloatSpec) +
                        scaleIn(initialScale = 0.96f, animationSpec = DetailTransitionFloatSpec)
                } else {
                    materialSharedAxisXIn(false, slideDistance)
                }
            }

            override val popExitTransition: Ty.() -> ExitTransition = {
                if (initialState.destination.route == GalleryDetailScreenDestination.route) {
                    fadeOut(animationSpec = DetailTransitionFloatSpec) +
                        scaleOut(targetScale = 0.94f, animationSpec = DetailTransitionFloatSpec) +
                        slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = SHARED_THUMB_DURATION_MS,
                                easing = SharedThumbEasing,
                            ),
                        ) { (it * 0.06f).toInt() }
                } else {
                    materialSharedAxisXOut(false, slideDistance)
                }
            }
        }
    }
}
