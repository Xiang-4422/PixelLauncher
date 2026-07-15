package com.purride.pixelui

/**
 * Host inspector 的一次只读快照。
 *
 * 该对象按需构造，用于调试面板、log dump 或崩溃诊断；不要在每帧生产热路径里采样。
 */
public data class PixelInspectorSnapshot(
    val frameStats: PixelHostFrameStats?,
    val allocationSample: PixelInspectorAllocationSample?,
    val targetCounts: PixelInspectorTargetCounts,
    val targetSnapshots: List<PixelInspectorTargetSnapshot>,
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
) {
    /** Full-frame diagnostics attached outside the frozen primary constructor for ABI stability. */
    private var capturedFrameDiagnostics: PixelHostFrameDiagnostics? = null

    /**
 * 公开 `PixelInspectorSnapshot` 的 `frameDiagnostics` 配置或运行值。
 *
     * Latest opt-in full-frame snapshot captured by the inspected Host, if available.
     *
     * This additive property is deliberately excluded from generated `copy`, `componentN`, and
     * equality semantics so the pre-1.0 primary-constructor ABI remains unchanged. Use
     * [withFrameDiagnostics] when a copied or synthetic Inspector snapshot must retain it.
     */
    public val frameDiagnostics: PixelHostFrameDiagnostics?
        get() = capturedFrameDiagnostics

    /** 执行 `PixelInspectorSnapshot` 的 `withFrameDiagnostics` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns a constructor-compatible copy carrying [frameDiagnostics] for synthetic tooling.
 */
    public fun withFrameDiagnostics(
        frameDiagnostics: PixelHostFrameDiagnostics?,
    ): PixelInspectorSnapshot {
        /** Copy preserves the frozen constructor value semantics before attaching additive data. */
        val snapshot = copy()
        snapshot.capturedFrameDiagnostics = frameDiagnostics
        return snapshot
    }

    /** Attaches Host-owned diagnostics without exposing a mutable public snapshot property. */
    internal fun attachFrameDiagnostics(frameDiagnostics: PixelHostFrameDiagnostics?) {
        capturedFrameDiagnostics = frameDiagnostics
    }
}

/**
 * Inspector 可展示的命中或语义目标种类。
 */
public enum class PixelInspectorTargetKind {
    CLICK,
    PAGER,
    LIST,
    SCROLLBAR,
    REFRESH,
    TEXT_INPUT,
    SLIDER,
    SEMANTICS,
}

/**
 * Inspector 面板展示用的 target 摘要。
 *
 * 这里只暴露命中区域和诊断字符串，不泄露内部 controller / state 引用。
 */
public data class PixelInspectorTargetSnapshot @JvmOverloads constructor(
    val kind: PixelInspectorTargetKind,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val detail: String = "",
    val elementPath: String? = null,
    val renderPath: String? = null,
) {
    /** 判断 `PixelInspectorSnapshot` 是否满足 `contains` 对应条件，不改变当前状态。 */
    public fun contains(x: Int, y: Int): Boolean {
        return width > 0 &&
            height > 0 &&
            x >= left &&
            y >= top &&
            x < left + width &&
            y < top + height
    }
}

/**
 * 按逻辑坐标查找最上层 inspector target。
 *
 * target 顺序与 snapshot 导出顺序一致；发生重叠时后导出的目标优先。
 */
public fun PixelInspectorSnapshot.targetAt(
    x: Int,
    y: Int,
    kinds: Set<PixelInspectorTargetKind> = PixelInspectorTargetKind.entries.toSet(),
): PixelInspectorTargetSnapshot? {
    return targetSnapshots.lastOrNull { target ->
        target.kind in kinds && target.contains(x, y)
    }
}

/**
 * Inspector 的一次 JVM heap 采样。
 *
 * 这不是严格的“本帧分配字节数”，而是按需读取当前 heap 使用量，用于调试面板
 * 快速观察内存趋势。采样会访问 [Runtime]，因此默认不在每次 [PixelHostView.inspect]
 * 调用中启用。
 */
public data class PixelInspectorAllocationSample(
    val usedHeapBytes: Long,
    val totalHeapBytes: Long,
    val maxHeapBytes: Long,
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
    /** 集中提供 `PixelInspectorSnapshot` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 提供 `PixelInspectorSnapshot` 的 `Empty` 稳定默认值或常量。 */
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
