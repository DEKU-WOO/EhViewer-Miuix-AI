package eu.kanade.tachiyomi.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ehviewer.core.i18n.R
import com.ehviewer.core.ui.component.LiquidGlassSurface
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.ui.icons.filled.Crop
import com.ehviewer.core.ui.icons.filled.CropOff
import com.ehviewer.core.ui.theme.rememberMiuixThemeController
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.asMutableState
import com.hippo.ehviewer.collectAsState
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.PreferenceType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomReaderBar(
    onClickSettings: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
) = LiquidGlassSurface(
    modifier = modifier
        .fillMaxWidth()
        .height(56.dp),
    shape = CircleShape,
    elevation = 8.dp,
    containerColor = containerColor,
) {
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val readingMode by Settings.readingMode.collectAsState { ReadingModeType.fromPreference(it) }
        DropdownIconButton(
            label = stringResource(R.string.viewer),
            menuItems = ReadingModeType.entries,
            selectedItem = readingMode,
            isDark = isDark,
            onSelectedItemChange = {
                Settings.readingMode.value = it.prefValue
            },
        )
        val orientationMode by Settings.orientationMode.collectAsState { OrientationType.fromPreference(it) }
        DropdownIconButton(
            label = stringResource(R.string.pref_rotation_type),
            menuItems = OrientationType.entries,
            selectedItem = orientationMode,
            isDark = isDark,
            onSelectedItemChange = {
                Settings.orientationMode.value = it.prefValue
            },
        )
        var cropBorder by Settings.cropBorder.asMutableState()
        ActionButton(
            onClick = { cropBorder = !cropBorder },
            imageVector = if (cropBorder) EhIcons.Default.Crop else EhIcons.Default.CropOff,
            contentDescription = stringResource(R.string.pref_crop_borders),
        )
        ActionButton(
            onClick = onClickSettings,
            imageVector = MiuixIcons.Settings,
            contentDescription = stringResource(R.string.action_settings),
        )
    }
}

@Composable
private fun DropdownIconButton(
    label: String,
    menuItems: List<PreferenceType>,
    selectedItem: PreferenceType,
    isDark: Boolean,
    onSelectedItemChange: (PreferenceType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titles = menuItems.map { stringResource(it.stringRes) }
    val entry = remember(menuItems, titles, selectedItem, onSelectedItemChange) {
        DropdownEntry(
            items = menuItems.mapIndexed { index, item ->
                DropdownItem(
                    text = titles[index],
                    selected = item == selectedItem,
                    onClick = { onSelectedItemChange(item) },
                )
            },
        )
    }
    // 使用 MIUIX 原生不透明圆角下拉菜单，并锁定标准梦幻紫卡片色（浅色 #FFFFFF / 深色 #1E1624），带选中高亮与对勾
    MiuixTheme(controller = rememberMiuixThemeController(useDarkTheme = isDark, isAmoled = false)) {
        WindowIconDropdownMenu(
            entry = entry,
            modifier = modifier,
        ) {
            Icon(
                imageVector = selectedItem.icon,
                contentDescription = label,
            )
        }
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}
