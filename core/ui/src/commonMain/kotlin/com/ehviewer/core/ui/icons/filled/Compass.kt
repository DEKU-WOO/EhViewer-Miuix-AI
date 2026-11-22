package com.ehviewer.core.ui.icons.filled

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import com.ehviewer.core.ui.icons.EhIcons
import com.ehviewer.core.util.unsafeLazy

/** 指南针图标：用于设置页「导航项」入口。 */
val EhIcons.Filled.Compass by unsafeLazy {
    materialIcon(name = "Filled.Compass") {
        materialPath {
            moveTo(12.0f, 10.9f)
            curveToRelative(-0.61f, 0.0f, -1.1f, 0.49f, -1.1f, 1.1f)
            reflectiveCurveToRelative(0.49f, 1.1f, 1.1f, 1.1f)
            reflectiveCurveToRelative(1.1f, -0.49f, 1.1f, -1.1f)
            reflectiveCurveToRelative(-0.49f, -1.1f, -1.1f, -1.1f)
            close()
            moveTo(12.0f, 2.0f)
            curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
            reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
            reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
            reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
            close()
            moveTo(14.19f, 14.19f)
            lineTo(6.0f, 18.0f)
            lineToRelative(3.81f, -8.19f)
            lineTo(18.0f, 6.0f)
            lineToRelative(-3.81f, 8.19f)
            close()
        }
    }
}
