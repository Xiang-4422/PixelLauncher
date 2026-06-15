package com.purride.pixelui

/**
 * Host inspector 的一次只读快照。
 *
 * 该对象按需构造，用于调试面板、log dump 或崩溃诊断；不要在每帧生产热路径里采样。
 */
public data class PixelInspectorSnapshot(
    val frameStats: PixelHostFrameStats?,
    val targetCounts: PixelInspectorTargetCounts,
    val elementTree: String,
    val renderTree: String,
    val semanticsTree: String,
    val hasPendingBuild: Boolean,
    val focusedTextInput: Boolean,
    val activePagerCount: Int,
    val activeListCount: Int,
    val activeSlider: Boolean,
    val activeScrollbar: Boolean,
    val activeRefresh: Boolean,
)

/**
 * 当前渲染结果导出的命中 / 语义目标数量。
 */
public data class PixelInspectorTargetCounts(
    val click: Int,
    val pager: Int,
    val list: Int,
    val scrollbar: Int,
    val refresh: Int,
    val textInput: Int,
    val slider: Int,
    val semantics: Int,
) {
    public companion object {
        public val Empty: PixelInspectorTargetCounts = PixelInspectorTargetCounts(
            click = 0,
            pager = 0,
            list = 0,
            scrollbar = 0,
            refresh = 0,
            textInput = 0,
            slider = 0,
            semantics = 0,
        )
    }
}
