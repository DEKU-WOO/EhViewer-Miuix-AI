package eu.kanade.tachiyomi.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.FadingMarqueeTitle
import com.ehviewer.core.ui.component.LiquidGlassCircleButtonIconSize
import com.ehviewer.core.ui.component.LiquidGlassCircleButtonSize
import com.ehviewer.core.ui.component.LiquidGlassNavHighlight
import com.ehviewer.core.ui.component.LiquidGlassSurface
import com.ehviewer.core.ui.component.LocalBackdrop
import com.ehviewer.core.ui.component.liquidGlassOverlayMaterial
import com.ehviewer.core.ui.util.AdaptiveBreakpoints
import com.ehviewer.core.ui.util.LocalWindowLayout
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SearchBarDefaults
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val animationSpec = tween<IntOffset>(200)
private val ReaderTopBarTopBreathing = SearchBarDefaults.InputFieldMinHeight - LiquidGlassCircleButtonSize

@Composable
context(navigator: DestinationsNavigator)
fun BoxScope.ReaderAppBars(
    visible: Boolean,
    title: String,
    isRtl: Boolean,
    showSeekBar: Boolean,
    currentPage: Int,
    totalPages: Int,
    onSliderValueChange: (Int) -> Unit,
    onClickSettings: () -> Unit,
) {
    val backdrop = LocalBackdrop.current
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal))
            .align(Alignment.TopCenter),
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = animationSpec),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = animationSpec),
    ) {
        val windowLayout = LocalWindowLayout.current
        val horizontalPadding = if (windowLayout.isWide) 32.dp else 16.dp
        val topPadding = if (windowLayout.isShortLandscape) 6.dp else ReaderTopBarTopBreathing
        val bottomPadding = if (windowLayout.isShortLandscape) 6.dp else 8.dp
        Row(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPadding,
                    bottom = bottomPadding,
                )
                .widthIn(max = AdaptiveBreakpoints.READER_BAR_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1) 左侧：与主页搜索栏 / 详情页顶栏完全一致的独立正圆形液态玻璃高光返回钮
            GlassIconButton(
                onClick = { navigator.popBackStack() },
                inheritTopBarMaterial = false,
                backdrop = backdrop,
                material = liquidGlassOverlayMaterial(),
                size = LiquidGlassCircleButtonSize,
                stroke = LiquidGlassNavHighlight,
            ) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = stringResource(id = R.string.desktop_a11y_back),
                    modifier = Modifier.size(LiquidGlassCircleButtonIconSize),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // 2) 右侧：清透单层全圆角液态玻璃标题胶囊（移除顶部页码，接入带左右柔和渐隐的跑马灯标题）
            LiquidGlassSurface(
                modifier = Modifier
                    .weight(1f)
                    .height(LiquidGlassCircleButtonSize),
                shape = CircleShape,
                elevation = 4.dp,
                refractionHeight = 14.dp,
                refractionAmount = 14.dp,
                highlight = LiquidGlassNavHighlight,
                containerColor = MiuixTheme.colorScheme.surfaceContainer,
                backdrop = backdrop,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    FadingMarqueeTitle(
                        text = title,
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.body2.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility.only(WindowInsetsSides.Horizontal))
            .align(Alignment.BottomCenter),
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = animationSpec),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = animationSpec),
    ) {
        val windowLayout = LocalWindowLayout.current
        // 横屏/宽屏用更宽松的左右内边距；矮视口收紧纵向间距，避免 chrome 吃掉近半屏高
        val horizontalPadding = if (windowLayout.isWide) 32.dp else 16.dp
        val verticalPadding = if (windowLayout.isShortLandscape) 6.dp else 12.dp
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                .widthIn(max = AdaptiveBreakpoints.READER_BAR_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (windowLayout.isShortLandscape) 4.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showSeekBar && totalPages > 1) {
                ChapterNavigator(
                    isRtl = isRtl,
                    currentPage = currentPage,
                    totalPages = totalPages,
                    onSliderValueChange = onSliderValueChange,
                )
            }
            BottomReaderBar(onClickSettings = onClickSettings)
        }
    }
}
