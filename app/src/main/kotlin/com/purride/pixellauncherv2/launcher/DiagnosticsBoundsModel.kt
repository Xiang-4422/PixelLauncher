package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.render.ScreenProfile

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

    fun snapshot(screenProfile: ScreenProfile): DiagnosticsBoundsSnapshot {
        val statusBarHeight = LauncherHeaderLayout.statusBarHeight(screenProfile)
        val contentWidth = (screenProfile.logicalWidth - LauncherSpacing.CONTENT_HORIZONTAL * 2)
            .coerceAtLeast(0)
        val bodyHeight = (screenProfile.logicalHeight - statusBarHeight - LauncherSpacing.CONTENT_VERTICAL * 2)
            .coerceAtLeast(0)
        val rowPitch = (
            PixelFontCatalog.metrics(PixelFontCatalog.defaultUiFontSize).cellHeight +
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
