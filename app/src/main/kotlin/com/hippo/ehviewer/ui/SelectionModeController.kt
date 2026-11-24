package com.hippo.ehviewer.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局多选状态标记：任一列表页进入长按多选时置为 true，
 * 主界面据此刻暂停左右滑动切页，退出多选后自动恢复。
 */
object SelectionModeController {
    var active by mutableStateOf(false)
        private set

    fun setActive(value: Boolean) {
        active = value
    }
}
