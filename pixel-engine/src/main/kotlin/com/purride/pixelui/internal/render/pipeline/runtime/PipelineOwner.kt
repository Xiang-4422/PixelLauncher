package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBufferPool
import java.util.IdentityHashMap

/**
 * 新渲染管线的最小 owner。
 *
 * 负责 root 挂载、layout、paint 和命中测试调度，并把宿主提供的
 * [PixelBufferPool] 注入到每帧 PaintContext，供 scratch buffer 借/还使用。
 */
internal class PipelineOwner(
    root: RenderBox? = null,
    private val bufferPool: PixelBufferPool = PixelBufferPool(),
) {
    private var root: RenderBox? = null
    private var needsLayout = true
    private var needsPaint = true
    private var renderCount = 0
    private var layoutPassCount = 0
    private var paintPassCount = 0
    private var cacheHits = 0
    private var lastTargetDiagnostics = TargetDiagnostics()
    private var cachedResult: PixelRenderResult? = null
    private var cachedLogicalWidth: Int = -1
    private var cachedLogicalHeight: Int = -1

    /** Overlay portal presentations lifted during the most recent non-cached paint. */
    private var overlayLayers: List<RenderLiftedOverlayLayer> = emptyList()

    init {
        attachRoot(root)
    }

    fun attachRoot(root: RenderBox?) {
        if (this.root === root) return
        this.root?.detach()
        // Lifted layers belong to the detached tree and must never survive until the next render.
        overlayLayers = emptyList()
        this.root = root
        root?.attach(this)
        markNeedsLayout()
    }

    fun markNeedsLayout() {
        needsLayout = true
        needsPaint = true
    }

    fun markNeedsPaint() {
        needsPaint = true
    }

    private fun discardCachedResult() {
        cachedResult?.let { bufferPool.release(it.buffer) }
        cachedResult = null
    }

    fun render(
        logicalWidth: Int,
        logicalHeight: Int,
        framePhaseSink: PixelFramePhaseSink? = null,
    ): PixelRenderResult {
        val canReuseCache = !needsLayout && !needsPaint &&
            cachedResult != null &&
            cachedLogicalWidth == logicalWidth &&
            cachedLogicalHeight == logicalHeight
        if (canReuseCache) {
            cacheHits += 1
            framePhaseSink?.recordPipelineWork(
                dirtyRenderNodeCount = 0,
                paintedPixelCount = 0L,
                renderCacheHit = true,
            )
            return cachedResult!!
        }
        discardCachedResult()
        // A failed layout/paint must not leave hit-test references from the previous frame.
        overlayLayers = emptyList()
        val viewportChanged = cachedLogicalWidth != logicalWidth || cachedLogicalHeight != logicalHeight
        cachedLogicalWidth = logicalWidth
        cachedLogicalHeight = logicalHeight
        if (viewportChanged) needsLayout = true

        val root = root
        if (root == null) {
            overlayLayers = emptyList()
            framePhaseSink?.beginPaint()
            /** Empty frame still acquires and publishes a deterministic transparent buffer. */
            val emptyResult = try {
                PixelRenderSessionFactory.create(
                    width = logicalWidth,
                    height = logicalHeight,
                    bufferPool = bufferPool,
                ).toRenderResult()
            } finally {
                framePhaseSink?.endPaint()
            }
            cachedResult = emptyResult
            framePhaseSink?.recordPipelineWork(
                dirtyRenderNodeCount = 0,
                paintedPixelCount = 0L,
                renderCacheHit = false,
            )
            return emptyResult
        }
        /** Number of RenderObjects traversed by the current owner-wide dirty pass. */
        val dirtyRenderNodeCount = if (framePhaseSink != null && (needsLayout || needsPaint)) {
            root.subtreeNodeCount()
        } else {
            0
        }
        val constraints = RenderConstraints(maxWidth = logicalWidth, maxHeight = logicalHeight)
        if (needsLayout) {
            framePhaseSink?.beginLayout()
            try {
                root.layout(constraints)
            } finally {
                framePhaseSink?.endLayout()
            }
            layoutPassCount += 1
            needsLayout = false
        }
        framePhaseSink?.beginPaint()
        /** Completed buffer and target snapshot produced inside the exclusive engine-paint phase. */
        val result = try {
            /** Mutable render session whose allocations and buffer acquisition belong to paint. */
            val session = PixelRenderSessionFactory.create(
                width = logicalWidth,
                height = logicalHeight,
                bufferPool = bufferPool,
            )
            /** Paint context sharing the runtime-owned scratch buffer pool. */
            val rootPaintContext = PaintContext(buffer = session.buffer, bufferPool = bufferPool)
            overlayLayers = RenderOverlayLayerRegistry.collect(rootBuffer = session.buffer) { registeredLayers ->
                root.paint(context = rootPaintContext, offsetX = 0, offsetY = 0)
                var layerIndex = 0
                while (layerIndex < registeredLayers.size) {
                    /** Size before paint separates newly discovered nested layers from later siblings. */
                    val sizeBeforePaint = registeredLayers.size
                    paintLiftedLayer(
                        layer = registeredLayers[layerIndex],
                        context = rootPaintContext,
                        logicalWidth = logicalWidth,
                        logicalHeight = logicalHeight,
                    )
                    moveNewNestedLayersAfterCurrent(
                        layers = registeredLayers,
                        currentIndex = layerIndex,
                        sizeBeforePaint = sizeBeforePaint,
                    )
                    layerIndex += 1
                }
            }
            root.collectClickTargets(offsetX = 0, offsetY = 0, targets = session.clickTargets)
            root.collectPagerTargets(offsetX = 0, offsetY = 0, targets = session.pagerTargets)
            root.collectListTargets(offsetX = 0, offsetY = 0, targets = session.listTargets)
            root.collectScrollbarTargets(offsetX = 0, offsetY = 0, targets = session.scrollbarTargets)
            root.collectRefreshTargets(offsetX = 0, offsetY = 0, targets = session.refreshTargets)
            root.collectTextInputTargets(offsetX = 0, offsetY = 0, targets = session.textInputTargets)
            root.collectSliderTargets(offsetX = 0, offsetY = 0, targets = session.sliderTargets)
            root.collectSemantics(offsetX = 0, offsetY = 0, targets = session.semanticsTargets)
            overlayLayers.filter { layer -> layer.exportsTargets && layer.replaysTargets }.forEach { layer ->
                collectLiftedLayerTargets(layer = layer, session = session)
            }
            sortSessionTargetsByPlane(session)
            lastTargetDiagnostics = TargetDiagnostics(
                clickTargets = session.clickTargets.size,
                pagerTargets = session.pagerTargets.size,
                listTargets = session.listTargets.size,
                textInputTargets = session.textInputTargets.size,
            )
            session.toRenderResult()
        } finally {
            framePhaseSink?.endPaint()
        }
        renderCount += 1
        paintPassCount += 1
        needsPaint = false
        cachedResult = result
        /** Logical pixels touched by the owner-wide full-buffer paint pass. */
        val paintedPixelCount = logicalWidth.coerceAtLeast(0).toLong() *
            logicalHeight.coerceAtLeast(0).toLong()
        framePhaseSink?.recordPipelineWork(
            dirtyRenderNodeCount = dirtyRenderNodeCount,
            paintedPixelCount = paintedPixelCount,
            renderCacheHit = false,
        )
        return result
    }

    /** Releases the cached frame and detaches the complete render tree from this owner. */
    fun dispose() {
        discardCachedResult()
        attachRoot(null)
        cachedLogicalWidth = -1
        cachedLogicalHeight = -1
        lastTargetDiagnostics = TargetDiagnostics()
        overlayLayers = emptyList()
    }

    fun hitTest(x: Int, y: Int): HitTestResult {
        val result = HitTestResult()
        root?.hitTest(localX = x, localY = y, result = result)
        overlayLayers.filter { layer -> layer.exportsTargets && layer.replaysTargets }.forEach { layer ->
            layer.renderBox.hitTest(
                localX = x - layer.paintOffsetX,
                localY = y - layer.paintOffsetY,
                result = result,
            )
        }
        sortSourcesByPlane(result.hits)
        /** Modal scope markers participate in the hit list but never escape this method. */
        val modalFilter = highestActiveModalFilter(result.hits)
        if (modalFilter != null) {
            result.hits.removeAll { hit ->
                hit is RenderModalInteractionScope || !sourceAllowedByModal(hit, modalFilter)
            }
        } else {
            result.hits.removeAll { hit -> hit is RenderModalInteractionScope }
        }
        return result
    }

    /** Paints one lifted layer while retaining ancestor group opacity at the Host root. */
    private fun paintLiftedLayer(
        layer: RenderLiftedOverlayLayer,
        context: PaintContext,
        logicalWidth: Int,
        logicalHeight: Int,
    ) {
        try {
            if (layer.opacity <= 0f) return
            if (layer.opacity >= 1f) {
                layer.renderBox.paint(
                    context = context,
                    offsetX = layer.paintOffsetX,
                    offsetY = layer.paintOffsetY,
                )
                return
            }
            /** Full-root scratch required because a lifted presentation uses Host-global coordinates. */
            val scratch = bufferPool.acquire(
                width = logicalWidth.coerceAtLeast(1),
                height = logicalHeight.coerceAtLeast(1),
            )
            scratch.clear()
            try {
                layer.renderBox.paint(
                    context = context.derive(scratch = scratch, localOriginX = 0, localOriginY = 0),
                    offsetX = layer.paintOffsetX,
                    offsetY = layer.paintOffsetY,
                )
                blendScratchWithOpacity(
                    target = context.buffer,
                    scratch = scratch,
                    destX = 0,
                    destY = 0,
                    opacity = layer.opacity,
                )
            } finally {
                bufferPool.release(scratch)
            }
        } finally {
            (layer.renderBox as? RenderCapturedOverlayPlane)?.releaseCapture()
        }
    }

    /** Sorts every completed target family by the same global plane order used for painting. */
    private fun sortSessionTargetsByPlane(session: PixelRenderSession) {
        /** Identity rank table shared by every comparator invocation in this completed session. */
        val ranks = buildPlaneRanks()
        /** Stable comparator projecting each target source onto its greatest enclosing plane rank. */
        val sourceComparator = Comparator<RenderObject?> { left, right ->
            planeRank(left, ranks).compareTo(planeRank(right, ranks))
        }
        session.clickTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.pagerTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.listTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.scrollbarTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.refreshTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.textInputTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.sliderTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
        session.semanticsTargets.sortWith { left, right -> sourceComparator.compare(left.source, right.source) }
    }

    /** Sorts raw hit sources by the same plane order before applying modal ownership filtering. */
    private fun sortSourcesByPlane(sources: MutableList<RenderObject>) {
        /** Identity rank table shared by every raw hit comparison. */
        val ranks = buildPlaneRanks()
        sources.sortWith { left, right ->
            planeRank(left, ranks).compareTo(planeRank(right, ranks))
        }
    }

    /** Builds one identity rank table from the final depth-first plane list. */
    private fun buildPlaneRanks(): IdentityHashMap<RenderObject, Int> {
        /** Plane roots mapped to their exact bottom-to-top list indices. */
        val ranks = IdentityHashMap<RenderObject, Int>()
        overlayLayers.forEachIndexed { index, layer -> ranks[layer.targetRoot] = index }
        return ranks
    }

    /** Returns the greatest lifted plane rank found while walking [source]'s retained ancestry. */
    private fun planeRank(source: RenderObject?, ranks: IdentityHashMap<RenderObject, Int>): Int {
        /** Highest nested plane rank inherited by the source, or -1 for normal root content. */
        var rank = -1
        /** Current source ancestor inspected toward the retained render root. */
        var candidate = source
        while (candidate != null) {
            /** Plane rank owned by this ancestor when it is a lifted or captured subtree root. */
            val candidateRank = ranks[candidate]
            if (candidateRank != null && candidateRank > rank) rank = candidateRank
            candidate = candidate.parent
        }
        return rank
    }

    /** Collects every interaction and semantic channel at a lifted layer's preserved root origin. */
    private fun collectLiftedLayerTargets(
        layer: RenderLiftedOverlayLayer,
        session: PixelRenderSession,
    ) {
        /** Shared horizontal origin used by every target family in this deferred subtree. */
        val offsetX = layer.paintOffsetX
        /** Shared vertical origin used by every target family in this deferred subtree. */
        val offsetY = layer.paintOffsetY
        layer.renderBox.collectClickTargets(offsetX = offsetX, offsetY = offsetY, targets = session.clickTargets)
        layer.renderBox.collectPagerTargets(offsetX = offsetX, offsetY = offsetY, targets = session.pagerTargets)
        layer.renderBox.collectListTargets(offsetX = offsetX, offsetY = offsetY, targets = session.listTargets)
        layer.renderBox.collectScrollbarTargets(
            offsetX = offsetX,
            offsetY = offsetY,
            targets = session.scrollbarTargets,
        )
        layer.renderBox.collectRefreshTargets(offsetX = offsetX, offsetY = offsetY, targets = session.refreshTargets)
        layer.renderBox.collectTextInputTargets(
            offsetX = offsetX,
            offsetY = offsetY,
            targets = session.textInputTargets,
        )
        layer.renderBox.collectSliderTargets(offsetX = offsetX, offsetY = offsetY, targets = session.sliderTargets)
        layer.renderBox.collectSemantics(offsetX = offsetX, offsetY = offsetY, targets = session.semanticsTargets)
    }

    /**
     * Reorders layers discovered while painting the current layer directly after their parent.
     *
     * Registration appends for O(1) discovery. Moving only the appended suffix produces depth-first
     * tree paint order (`A, nested-A, later-B`) without recursive paint stack growth.
     */
    private fun moveNewNestedLayersAfterCurrent(
        layers: MutableList<RenderLiftedOverlayLayer>,
        currentIndex: Int,
        sizeBeforePaint: Int,
    ) {
        if (layers.size <= sizeBeforePaint || currentIndex + 1 >= sizeBeforePaint) return
        /** Nested suffix registered by the current layer's paint call. */
        val nestedLayers = layers.subList(sizeBeforePaint, layers.size).toList()
        repeat(nestedLayers.size) { layers.removeAt(layers.lastIndex) }
        layers.addAll(currentIndex + 1, nestedLayers)
    }

    fun collectDiagnostics(): List<RenderDiagnosticsNode> = root?.collectDiagnostics().orEmpty()

    fun collectPipelineDiagnostics(): PipelineDiagnostics {
        return PipelineDiagnostics(
            hasRoot = root != null,
            needsLayout = needsLayout,
            needsPaint = needsPaint,
            renderCount = renderCount,
            layoutPassCount = layoutPassCount,
            paintPassCount = paintPassCount,
            cacheHits = cacheHits,
            targetDiagnostics = lastTargetDiagnostics,
        )
    }
}

internal data class PipelineDiagnostics(
    val hasRoot: Boolean,
    val needsLayout: Boolean,
    val needsPaint: Boolean,
    val renderCount: Int,
    val layoutPassCount: Int,
    val paintPassCount: Int,
    val cacheHits: Int,
    val targetDiagnostics: TargetDiagnostics,
)

internal data class TargetDiagnostics(
    val clickTargets: Int = 0,
    val pagerTargets: Int = 0,
    val listTargets: Int = 0,
    val textInputTargets: Int = 0,
)
