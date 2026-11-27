package com.hippo.ehviewer.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf

/**
 * 全局多选与搜索展开状态标记（按页面 ownerKey 隔离登记）：
 * 任一可见页进入长按多选或展开搜索框时置为 true，主界面 `HorizontalPager` 据此锁死左右滑动切页，
 * 避免离屏预组合页销毁时误清当前可见页的状态。
 */
object SelectionModeController {
    private val activeOwners = mutableStateMapOf<String, Boolean>()

    val active: Boolean by derivedStateOf { activeOwners.values.any { it } }

    var legacyActive: Boolean
        get() = active
        set(value) {
            if (value) activeOwners["__default__"] = true else activeOwners.remove("__default__")
        }

    fun update(ownerKey: String, isActive: Boolean) {
        if (isActive) {
            activeOwners[ownerKey] = true
        } else {
            activeOwners.remove(ownerKey)
        }
    }

    fun clearOwner(ownerKey: String) {
        activeOwners.remove(ownerKey)
    }

    fun clearAll() {
        activeOwners.clear()
    }
}

object SearchExpandController {
    private val expandedOwners = mutableStateMapOf<String, Boolean>()

    val expanded: Boolean by derivedStateOf { expandedOwners.values.any { it } }

    fun update(ownerKey: String, isExpanded: Boolean) {
        if (isExpanded) {
            expandedOwners[ownerKey] = true
        } else {
            expandedOwners.remove(ownerKey)
        }
    }

    fun clearOwner(ownerKey: String) {
        expandedOwners.remove(ownerKey)
    }
}
