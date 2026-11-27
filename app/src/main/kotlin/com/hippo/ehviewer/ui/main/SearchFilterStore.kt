package com.hippo.ehviewer.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ehviewer.core.preferences.edit
import com.hippo.ehviewer.Settings

/**
 * 全局筛选条件共享状态与持久化仓库。
 *
 * - 全局筛选弹窗（列表页顶栏按钮触发）与搜索展开页筛选区共享同一份内存状态；
 * - 启动时直接从已同步初始化的 [Settings]（DataStore）读取，彻底消除冷启动空读竞态与 `@Transient` 丢失；
 * - 修改筛选胶囊仅更新内存状态（不主动触发列表网络刷新），在关闭筛选弹窗、退出搜索展开页或退后台时原子落盘。
 */
object SearchFilterStore {
    var category by mutableIntStateOf(Settings.searchFilterCategory.value)
    var language by mutableIntStateOf(Settings.languageFilter.value)
    var advancedOption by mutableStateOf(
        AdvancedSearchOption(
            advanceSearch = Settings.searchFilterAdvanceBits.value,
            minRating = Settings.searchFilterMinRating.value,
            fromPage = Settings.searchFilterFromPage.value,
            toPage = Settings.searchFilterToPage.value,
        ),
    )

    fun persist() {
        val snapCategory = category
        val snapLanguage = language
        val snapOption = advancedOption
        Settings.edit { pref ->
            pref[Settings.searchFilterCategory] = snapCategory
            pref[Settings.languageFilter] = snapLanguage
            pref[Settings.searchFilterAdvanceBits] = snapOption.advanceSearch
            pref[Settings.searchFilterMinRating] = snapOption.minRating
            pref[Settings.searchFilterFromPage] = snapOption.fromPage
            pref[Settings.searchFilterToPage] = snapOption.toPage
        }
    }
}
