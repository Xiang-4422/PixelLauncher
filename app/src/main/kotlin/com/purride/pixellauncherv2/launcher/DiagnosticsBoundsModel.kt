package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.layout.LauncherLayoutProfile

data class DiagnosticsBoundsSnapshot(
    val contentWidthPx: Int,
    val bodyHeightPx: Int,
    val visibleRowCount: Int,
    val geometryOk: Boolean,
) {
    val summary: String
        get() {
            val prefix = if (geometryOk) "OK" else "RISK"
            return "$prefix $visibleRowCount ROW"
        }
}

object DiagnosticsBoundsModel {

    /** 按当前字体的页面行高与 CHROME 状态栏高度生成边界快照。 */
    fun snapshot(
        screenProfile: LauncherLayoutProfile,
        fontSelection: LauncherFontSelection = PixelFontCatalog.defaultUiFontSelection,
    ): DiagnosticsBoundsSnapshot {
        val statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile, fontSelection)
        val contentWidth = (screenProfile.logicalWidth - LauncherSpacing.CONTENT_HORIZONTAL * 2)
            .coerceAtLeast(0)
        val bodyHeight = (screenProfile.logicalHeight - statusBarHeight - LauncherSpacing.CONTENT_VERTICAL * 2)
            .coerceAtLeast(0)
        val rowPitch = (
            PixelFontCatalog.metrics(fontSelection).cellHeight +
                LauncherSpacing.ROW_SPACING
            ).coerceAtLeast(1)
        val visibleRows = bodyHeight / rowPitch
        val geometryOk = contentWidth > 0 &&
            bodyHeight >= rowPitch &&
            statusBarHeight >= 0 &&
            statusBarHeight < screenProfile.logicalHeight

        return DiagnosticsBoundsSnapshot(
            contentWidthPx = contentWidth,
            bodyHeightPx = bodyHeight,
            visibleRowCount = visibleRows,
            geometryOk = geometryOk,
        )
    }
}
