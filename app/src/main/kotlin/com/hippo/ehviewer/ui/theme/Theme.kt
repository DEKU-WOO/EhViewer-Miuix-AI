package com.hippo.ehviewer.ui.theme

import androidx.compose.foundation.scrollbar.LocalScrollbarStyle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ehviewer.core.ui.component.scrollbarStyle
import com.ehviewer.core.ui.theme.DreamyPurplePalette
import com.ehviewer.core.ui.theme.rememberMiuixThemeController
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.collectAsState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.TextStyles

val TextStyles.titleLarge get() = title3
val TextStyles.bodyMedium get() = body2

private fun buildDreamyPurpleDarkM3Colors(amoled: Boolean): ColorScheme = darkColorScheme(
    primary = DreamyPurplePalette.Primary,
    onPrimary = Color.White,
    primaryContainer = DreamyPurplePalette.DarkPrimaryContainer,
    onPrimaryContainer = DreamyPurplePalette.DarkHighlight,
    secondary = DreamyPurplePalette.DarkHighlight,
    onSecondary = Color(0xFF271033),
    secondaryContainer = if (amoled) DreamyPurplePalette.AmoledSurfaceContainerHigh else DreamyPurplePalette.DarkSurfaceContainerHigh,
    onSecondaryContainer = DreamyPurplePalette.DarkOnSurface,
    tertiary = DreamyPurplePalette.DarkHighlight,
    onTertiary = Color(0xFF271033),
    tertiaryContainer = if (amoled) DreamyPurplePalette.AmoledSurfaceContainerHigh else DreamyPurplePalette.DarkSurfaceContainerHigh,
    onTertiaryContainer = DreamyPurplePalette.DarkHighlight,
    background = if (amoled) Color.Black else DreamyPurplePalette.DarkBackground,
    onBackground = DreamyPurplePalette.DarkOnSurface,
    surface = if (amoled) DreamyPurplePalette.AmoledSurfaceCard else DreamyPurplePalette.DarkSurfaceCard,
    onSurface = DreamyPurplePalette.DarkOnSurface,
    surfaceVariant = if (amoled) DreamyPurplePalette.AmoledSurfaceContainerHigh else DreamyPurplePalette.DarkSurfaceContainerHigh,
    onSurfaceVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
    surfaceContainerLowest = if (amoled) Color.Black else DreamyPurplePalette.DarkBackground,
    surfaceContainerLow = if (amoled) DreamyPurplePalette.AmoledSurfaceCard else Color(0xFF18111D),
    surfaceContainer = if (amoled) DreamyPurplePalette.AmoledSurfaceCard else DreamyPurplePalette.DarkSurfaceCard,
    surfaceContainerHigh = if (amoled) DreamyPurplePalette.AmoledSurfaceContainerHigh else DreamyPurplePalette.DarkSurfaceContainerHigh,
    surfaceContainerHighest = if (amoled) DreamyPurplePalette.AmoledSurfaceContainerHighest else DreamyPurplePalette.DarkSurfaceContainerHighest,
    outline = DreamyPurplePalette.DarkOnSurfaceSecondary,
    outlineVariant = if (amoled) DreamyPurplePalette.AmoledDivider else DreamyPurplePalette.DarkDivider,
)

private val DreamyPurpleLightM3Colors: ColorScheme = lightColorScheme(
    primary = DreamyPurplePalette.Primary,
    onPrimary = Color.White,
    primaryContainer = DreamyPurplePalette.LightPrimaryContainer,
    onPrimaryContainer = DreamyPurplePalette.LightHighlight,
    secondary = DreamyPurplePalette.LightHighlight,
    onSecondary = Color.White,
    secondaryContainer = DreamyPurplePalette.LightSurfaceContainerHigh,
    onSecondaryContainer = DreamyPurplePalette.LightOnSurface,
    tertiary = DreamyPurplePalette.LightHighlight,
    onTertiary = Color.White,
    tertiaryContainer = DreamyPurplePalette.LightSurfaceContainerHigh,
    onTertiaryContainer = DreamyPurplePalette.LightHighlight,
    background = DreamyPurplePalette.LightBackground,
    onBackground = DreamyPurplePalette.LightOnSurface,
    surface = DreamyPurplePalette.LightSurfaceCard,
    onSurface = DreamyPurplePalette.LightOnSurface,
    surfaceVariant = DreamyPurplePalette.LightSurfaceContainerHigh,
    onSurfaceVariant = DreamyPurplePalette.LightOnSurfaceSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = DreamyPurplePalette.LightBackground,
    surfaceContainer = DreamyPurplePalette.LightSurfaceCard,
    surfaceContainerHigh = DreamyPurplePalette.LightSurfaceContainerHigh,
    surfaceContainerHighest = DreamyPurplePalette.LightSurfaceContainerHighest,
    outline = DreamyPurplePalette.LightOnSurfaceSecondary,
    outlineVariant = DreamyPurplePalette.LightDivider,
)

// Settings.theme 存的是 AppCompatDelegate night mode 值（arrays.xml night_mode_values = [-1, 1, 2]）
@PublishedApi
internal fun resolveUseDarkTheme(theme: Int, isSystemDarkTheme: Boolean) = when (theme) {
    1 -> false
    2 -> true
    else -> isSystemDarkTheme
}

@Composable
fun EhTheme(useDarkTheme: Boolean, content: @Composable () -> Unit) {
    val amoled by Settings.blackDarkTheme.collectAsState()
    val colors = remember(useDarkTheme, amoled) {
        if (useDarkTheme) {
            buildDreamyPurpleDarkM3Colors(amoled)
        } else {
            DreamyPurpleLightM3Colors
        }
    }

    val miuixController = rememberMiuixThemeController(useDarkTheme, amoled)

    MiuixTheme(controller = miuixController) {
        MaterialTheme(colorScheme = colors, motionScheme = CustomMotionScheme) {
            // 移动端快速滚动条统一使用主主题色 #AF52DE
            val scrollbarStyle = scrollbarStyle(
                thumbColor = MiuixTheme.colorScheme.primary,
                hoverThumbColor = MiuixTheme.colorScheme.primary,
            )
            CompositionLocalProvider(
                LocalContentColor provides MaterialTheme.colorScheme.onBackground,
                LocalScrollbarStyle provides scrollbarStyle,
                content = content,
            )
        }
    }
}

@Composable
fun Color.scrim() = copy(alpha = if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) 0.5f else 0.9f)

// https://issuetracker.google.com/363892346
object CustomMotionScheme : MotionScheme by MotionScheme.expressive() {
    override fun <T> defaultSpatialSpec() = defaultEffectsSpec<T>()
}
