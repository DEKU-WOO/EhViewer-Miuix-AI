package com.ehviewer.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

/**
 * 梦幻紫氛围色板（方案 A）：
 * 深色采用护眼的柔和深紫黑底（#120D16）与暗紫灰卡片（#1E1624 / #271D2F），
 * 浅色采用对称的极淡薰衣草氛围白（#F7F4FA）与纯白一级卡片（#FFFFFF）。
 */
object DreamyPurplePalette {
    // 共享品牌主色（实心主按钮、筛选选中胶囊、下载半幅进度条、快速滚动条、多选选中圆勾）
    val Primary = Color(0xFFAF52DE)

    // 深/浅底高亮强调色（深底 #D277FF 提亮一档；浅底 #892EC0 加深保证高对比可读）
    val DarkHighlight = Color(0xFFD277FF)
    val LightHighlight = Color(0xFF892EC0)

    // 深色氛围表面梯度
    val DarkBackground = Color(0xFF120D16)
    val DarkSurfaceCard = Color(0xFF1E1624)
    val DarkSurfaceContainerHigh = Color(0xFF271D2F)
    val DarkSurfaceContainerHighest = Color(0xFF31253B)
    val DarkPrimaryContainer = Color(0xFF3A1F4D)
    val DarkOnSurface = Color(0xFFF3EDF7)
    val DarkOnSurfaceSecondary = Color(0xFFA89BB3)
    val DarkDivider = Color(0xFF352840)
    val DarkWindowDimming = Color(0x990D0811)

    // 液态玻璃深色微透明暗紫基底（替代原先冷灰/纯黑遮罩）
    val DarkGlassBase = Color(0xFF16101C)

    // AMOLED 纯黑模式下的同色系暗紫卡片梯度
    val AmoledSurfaceCard = Color(0xFF16101C)
    val AmoledSurfaceContainerHigh = Color(0xFF22182A)
    val AmoledSurfaceContainerHighest = Color(0xFF2C2036)
    val AmoledDivider = Color(0xFF2E2238)

    // 浅色薰衣草氛围表面梯度
    val LightBackground = Color(0xFFF7F4FA)
    val LightSurfaceCard = Color(0xFFFFFFFF)
    val LightSurfaceContainerHigh = Color(0xFFEFE5F8)
    val LightSurfaceContainerHighest = Color(0xFFE2D6EC)
    val LightPrimaryContainer = Color(0xFFEFE5F8)
    val LightOnSurface = Color(0xFF1D1524)
    val LightOnSurfaceSecondary = Color(0xFF685B73)
    val LightDivider = Color(0xFFE2D6EC)
    val LightWindowDimming = Color(0x661D1524)
}

/** 根据当前深/浅表面自动返回高对比度高亮色（深底 `#D277FF` / 浅底 `#892EC0`）。 */
@Composable
fun activeAccentColor(): Color = if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) {
    DreamyPurplePalette.DarkHighlight
} else {
    DreamyPurplePalette.LightHighlight
}

@Composable
fun rememberMiuixThemeController(useDarkTheme: Boolean, isAmoled: Boolean): ThemeController = remember(useDarkTheme, isAmoled) {
    val mode = if (useDarkTheme) ColorSchemeMode.Dark else ColorSchemeMode.Light
    val lightColors = miuixLightColorScheme(
        primary = DreamyPurplePalette.Primary,
        onPrimary = Color.White,
        primaryVariant = DreamyPurplePalette.Primary,
        onPrimaryVariant = Color.White,
        disabledPrimary = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
        disabledOnPrimary = Color.White.copy(alpha = 0.75f),
        disabledPrimaryButton = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
        disabledOnPrimaryButton = Color.White.copy(alpha = 0.78f),
        disabledPrimarySlider = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
        primaryContainer = DreamyPurplePalette.LightPrimaryContainer,
        onPrimaryContainer = DreamyPurplePalette.LightHighlight,
        secondary = DreamyPurplePalette.LightSurfaceContainerHigh,
        onSecondary = DreamyPurplePalette.LightOnSurface,
        secondaryVariant = DreamyPurplePalette.LightSurfaceContainerHigh,
        onSecondaryVariant = DreamyPurplePalette.LightOnSurface,
        disabledSecondary = DreamyPurplePalette.LightSurfaceContainerHigh.copy(alpha = 0.6f),
        disabledOnSecondary = DreamyPurplePalette.LightOnSurfaceSecondary.copy(alpha = 0.5f),
        disabledSecondaryVariant = DreamyPurplePalette.LightSurfaceContainerHigh.copy(alpha = 0.6f),
        disabledOnSecondaryVariant = DreamyPurplePalette.LightOnSurfaceSecondary.copy(alpha = 0.5f),
        secondaryContainer = DreamyPurplePalette.LightSurfaceContainerHigh,
        onSecondaryContainer = DreamyPurplePalette.LightOnSurface,
        secondaryContainerVariant = DreamyPurplePalette.LightSurfaceContainerHigh,
        onSecondaryContainerVariant = DreamyPurplePalette.LightOnSurfaceSecondary,
        tertiaryContainer = DreamyPurplePalette.LightSurfaceContainerHigh,
        onTertiaryContainer = DreamyPurplePalette.LightOnSurface,
        tertiaryContainerVariant = DreamyPurplePalette.LightSurfaceContainerHigh,
        background = DreamyPurplePalette.LightBackground,
        onBackground = DreamyPurplePalette.LightOnSurface,
        onBackgroundVariant = DreamyPurplePalette.LightOnSurfaceSecondary,
        // MIUIX Scaffold 与设置页背景均读取 surface，一级 Card 读取 surfaceContainer
        surface = DreamyPurplePalette.LightBackground,
        onSurface = DreamyPurplePalette.LightOnSurface,
        surfaceVariant = DreamyPurplePalette.LightSurfaceContainerHigh,
        surfaceContainer = DreamyPurplePalette.LightSurfaceCard,
        onSurfaceContainer = DreamyPurplePalette.LightOnSurface,
        onSurfaceContainerVariant = DreamyPurplePalette.LightOnSurfaceSecondary,
        surfaceContainerHigh = DreamyPurplePalette.LightSurfaceContainerHigh,
        onSurfaceContainerHigh = DreamyPurplePalette.LightOnSurfaceSecondary,
        surfaceContainerHighest = DreamyPurplePalette.LightSurfaceContainerHighest,
        onSurfaceContainerHighest = DreamyPurplePalette.LightOnSurface,
        onSurfaceVariantSummary = DreamyPurplePalette.LightOnSurfaceSecondary,
        onSurfaceVariantActions = DreamyPurplePalette.LightOnSurfaceSecondary,
        onSurfaceSecondary = DreamyPurplePalette.LightOnSurfaceSecondary,
        disabledOnSurface = DreamyPurplePalette.LightOnSurfaceSecondary.copy(alpha = 0.45f),
        outline = DreamyPurplePalette.LightDivider,
        dividerLine = DreamyPurplePalette.LightDivider,
        windowDimming = DreamyPurplePalette.LightWindowDimming,
        sliderKeyPoint = DreamyPurplePalette.Primary.copy(alpha = 0.30f),
        sliderKeyPointForeground = DreamyPurplePalette.Primary,
        sliderBackground = DreamyPurplePalette.LightSurfaceContainerHighest,
    )
    val darkColors = if (isAmoled) {
        miuixDarkColorScheme(
            primary = DreamyPurplePalette.Primary,
            onPrimary = Color.White,
            primaryVariant = DreamyPurplePalette.Primary,
            onPrimaryVariant = Color.White,
            disabledPrimary = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
            disabledOnPrimary = Color.White.copy(alpha = 0.72f),
            disabledPrimaryButton = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
            disabledOnPrimaryButton = Color.White.copy(alpha = 0.75f),
            disabledPrimarySlider = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
            primaryContainer = DreamyPurplePalette.DarkPrimaryContainer,
            onPrimaryContainer = DreamyPurplePalette.DarkHighlight,
            secondary = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            onSecondary = DreamyPurplePalette.DarkOnSurface,
            secondaryVariant = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            onSecondaryVariant = DreamyPurplePalette.DarkOnSurface,
            disabledSecondary = DreamyPurplePalette.AmoledSurfaceContainerHigh.copy(alpha = 0.6f),
            disabledOnSecondary = DreamyPurplePalette.DarkOnSurfaceSecondary.copy(alpha = 0.5f),
            disabledSecondaryVariant = DreamyPurplePalette.AmoledSurfaceContainerHigh.copy(alpha = 0.6f),
            disabledOnSecondaryVariant = DreamyPurplePalette.DarkOnSurfaceSecondary.copy(alpha = 0.5f),
            secondaryContainer = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            onSecondaryContainer = DreamyPurplePalette.DarkOnSurface,
            secondaryContainerVariant = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            onSecondaryContainerVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
            tertiaryContainer = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            onTertiaryContainer = DreamyPurplePalette.DarkOnSurface,
            tertiaryContainerVariant = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            background = Color.Black,
            onBackground = DreamyPurplePalette.DarkOnSurface,
            onBackgroundVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
            surface = Color.Black,
            onSurface = DreamyPurplePalette.DarkOnSurface,
            surfaceVariant = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            surfaceContainer = DreamyPurplePalette.AmoledSurfaceCard,
            onSurfaceContainer = DreamyPurplePalette.DarkOnSurface,
            onSurfaceContainerVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
            surfaceContainerHigh = DreamyPurplePalette.AmoledSurfaceContainerHigh,
            onSurfaceContainerHigh = DreamyPurplePalette.DarkOnSurfaceSecondary,
            surfaceContainerHighest = DreamyPurplePalette.AmoledSurfaceContainerHighest,
            onSurfaceContainerHighest = DreamyPurplePalette.DarkOnSurface,
            onSurfaceVariantSummary = DreamyPurplePalette.DarkOnSurfaceSecondary,
            onSurfaceVariantActions = DreamyPurplePalette.DarkOnSurfaceSecondary,
            onSurfaceSecondary = DreamyPurplePalette.DarkOnSurfaceSecondary,
            disabledOnSurface = DreamyPurplePalette.DarkOnSurfaceSecondary.copy(alpha = 0.45f),
            outline = DreamyPurplePalette.AmoledDivider,
            dividerLine = DreamyPurplePalette.AmoledDivider,
            windowDimming = DreamyPurplePalette.DarkWindowDimming,
            sliderKeyPoint = DreamyPurplePalette.Primary.copy(alpha = 0.30f),
            sliderKeyPointForeground = DreamyPurplePalette.DarkHighlight,
            sliderBackground = DreamyPurplePalette.AmoledSurfaceContainerHighest,
        )
    } else {
        miuixDarkColorScheme(
            primary = DreamyPurplePalette.Primary,
            onPrimary = Color.White,
            primaryVariant = DreamyPurplePalette.Primary,
            onPrimaryVariant = Color.White,
            disabledPrimary = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
            disabledOnPrimary = Color.White.copy(alpha = 0.72f),
            disabledPrimaryButton = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
            disabledOnPrimaryButton = Color.White.copy(alpha = 0.75f),
            disabledPrimarySlider = DreamyPurplePalette.Primary.copy(alpha = 0.35f),
            primaryContainer = DreamyPurplePalette.DarkPrimaryContainer,
            onPrimaryContainer = DreamyPurplePalette.DarkHighlight,
            secondary = DreamyPurplePalette.DarkSurfaceContainerHigh,
            onSecondary = DreamyPurplePalette.DarkOnSurface,
            secondaryVariant = DreamyPurplePalette.DarkSurfaceContainerHigh,
            onSecondaryVariant = DreamyPurplePalette.DarkOnSurface,
            disabledSecondary = DreamyPurplePalette.DarkSurfaceContainerHigh.copy(alpha = 0.6f),
            disabledOnSecondary = DreamyPurplePalette.DarkOnSurfaceSecondary.copy(alpha = 0.5f),
            disabledSecondaryVariant = DreamyPurplePalette.DarkSurfaceContainerHigh.copy(alpha = 0.6f),
            disabledOnSecondaryVariant = DreamyPurplePalette.DarkOnSurfaceSecondary.copy(alpha = 0.5f),
            secondaryContainer = DreamyPurplePalette.DarkSurfaceContainerHigh,
            onSecondaryContainer = DreamyPurplePalette.DarkOnSurface,
            secondaryContainerVariant = DreamyPurplePalette.DarkSurfaceContainerHigh,
            onSecondaryContainerVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
            tertiaryContainer = DreamyPurplePalette.DarkSurfaceContainerHigh,
            onTertiaryContainer = DreamyPurplePalette.DarkOnSurface,
            tertiaryContainerVariant = DreamyPurplePalette.DarkSurfaceContainerHigh,
            background = DreamyPurplePalette.DarkBackground,
            onBackground = DreamyPurplePalette.DarkOnSurface,
            onBackgroundVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
            surface = DreamyPurplePalette.DarkBackground,
            onSurface = DreamyPurplePalette.DarkOnSurface,
            surfaceVariant = DreamyPurplePalette.DarkSurfaceContainerHigh,
            surfaceContainer = DreamyPurplePalette.DarkSurfaceCard,
            onSurfaceContainer = DreamyPurplePalette.DarkOnSurface,
            onSurfaceContainerVariant = DreamyPurplePalette.DarkOnSurfaceSecondary,
            surfaceContainerHigh = DreamyPurplePalette.DarkSurfaceContainerHigh,
            onSurfaceContainerHigh = DreamyPurplePalette.DarkOnSurfaceSecondary,
            surfaceContainerHighest = DreamyPurplePalette.DarkSurfaceContainerHighest,
            onSurfaceContainerHighest = DreamyPurplePalette.DarkOnSurface,
            onSurfaceVariantSummary = DreamyPurplePalette.DarkOnSurfaceSecondary,
            onSurfaceVariantActions = DreamyPurplePalette.DarkOnSurfaceSecondary,
            onSurfaceSecondary = DreamyPurplePalette.DarkOnSurfaceSecondary,
            disabledOnSurface = DreamyPurplePalette.DarkOnSurfaceSecondary.copy(alpha = 0.45f),
            outline = DreamyPurplePalette.DarkDivider,
            dividerLine = DreamyPurplePalette.DarkDivider,
            windowDimming = DreamyPurplePalette.DarkWindowDimming,
            sliderKeyPoint = DreamyPurplePalette.Primary.copy(alpha = 0.30f),
            sliderKeyPointForeground = DreamyPurplePalette.DarkHighlight,
            sliderBackground = DreamyPurplePalette.DarkSurfaceContainerHighest,
        )
    }
    ThemeController(
        colorSchemeMode = mode,
        lightColors = lightColors,
        darkColors = darkColors,
    )
}
