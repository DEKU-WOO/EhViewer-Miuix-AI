package com.ehviewer.core.ui.util

import androidx.compose.runtime.compositionLocalOf

/**
 * 标记当前页面是否为 Pager 中已落位激活的前台可见页。
 *
 * 当 `HorizontalPager(beyondViewportPageCount = 1)` 预组合左右相邻离屏页时，
 * 离屏页读取到的值为 `false`，用于门控 AI 自动翻译、VPS 历史增量同步、侧滑抽屉手势、返回拦截与底栏滚动联动。
 */
val LocalIsPageVisible = compositionLocalOf { true }

/**
 * 标记当前页面是否有任意像素处于屏幕视口内（包含正在跟手滑入或跨页平推滑入的过程）。
 *
 * 完全离屏预组合（距离 >= 1 页）时为 `false`，用于跳过离屏页顶栏实时模糊着色器开销，
 * 同时保证手指刚开始拖入相邻页时顶栏毛玻璃即刻在线、无落位后跳变。
 */
val LocalIsPageInViewport = compositionLocalOf { true }
