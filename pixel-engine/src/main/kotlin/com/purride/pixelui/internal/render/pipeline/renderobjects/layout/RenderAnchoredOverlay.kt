package com.purride.pixelui.internal

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.PixelPopoverAlignment
import com.purride.pixelui.PixelPopoverPlacement
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.TextDirection
import com.purride.pixelui.animation.IntOffset
import java.util.IdentityHashMap

/** One root-lifted presentation or deferred sibling plus the policies it must continue to obey. */
internal data class RenderLiftedOverlayLayer(
    /** Retained presentation or sibling subtree painted in Host-root coordinates. */
    val renderBox: RenderBox,
    /** Original retained subtree whose descendant targets inherit this plane's z-order. */
    val targetRoot: RenderBox,
    /** Whether PipelineOwner must replay target and hit collection from [renderBox]. */
    val replaysTargets: Boolean,
    /** Host-global horizontal origin used when replaying a deferred in-flow subtree. */
    val paintOffsetX: Int,
    /** Host-global vertical origin used when replaying a deferred in-flow subtree. */
    val paintOffsetY: Int,
    /** Product of every ancestor group opacity bypassed by root lifting. */
    val opacity: Float,
    /** Whether bypassed ancestors still permit hit testing, targets, and semantics. */
    val exportsTargets: Boolean,
)

/** Result of moving one higher Stack sibling out of its original paint position. */
internal enum class RenderDeferredSiblingMode {
    /** No registry frame was active, so the Stack must paint the sibling normally. */
    Rejected,

    /** Root-coordinate subtree retained for later paint and target traversal. */
    DeferredSubtree,

    /** Scratch-coordinate pixels captured now; original targets remain in ancestor traversal. */
    CapturedRaster,
}

/** Internal contract allowing PipelineOwner to release a one-frame captured raster deterministically. */
internal interface RenderCapturedOverlayPlane {
    /** Returns the pooled capture buffer after its final paint or an aborted render. */
    fun releaseCapture()
}

/** One transparent scratch plane mapped back through its original global scale and clip extent. */
private class RenderCapturedOverlayRaster(
    /** Pooled transparent raster containing only the deferred sibling's pixels. */
    private var raster: PixelBuffer?,
    /** Pool that must receive [raster] exactly once. */
    private val bufferPool: com.purride.pixelcore.PixelBufferPool,
    /** Host-global destination origin inherited from the captured PaintContext. */
    private val destinationLeft: Int,
    /** Host-global destination origin inherited from the captured PaintContext. */
    private val destinationTop: Int,
    /** Host-global width after applying the captured rational x scale. */
    private val destinationWidth: Int,
    /** Host-global height after applying the captured rational y scale. */
    private val destinationHeight: Int,
) : RenderBox(), RenderCapturedOverlayPlane {
    /** Captured planes are already measured and never participate in retained layout. */
    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(
            width = destinationWidth.coerceAtLeast(0),
            height = destinationHeight.coerceAtLeast(0),
        )
    }

    /** Replays the captured pixels with nearest-neighbor mapping into Host-root coordinates. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        /** Capture retained until this single replay finishes, including exceptional paths. */
        val source = raster ?: return
        try {
            if (destinationWidth <= 0 || destinationHeight <= 0) return
            for (destinationY in 0 until destinationHeight) {
                /** Source row selected by the captured y-scale without Int multiplication overflow. */
                val sourceY = (
                    destinationY.toLong() * source.height.toLong() / destinationHeight.toLong()
                    ).toInt()
                for (destinationX in 0 until destinationWidth) {
                    /** Source column selected by the captured x-scale without Int overflow. */
                    val sourceX = (
                        destinationX.toLong() * source.width.toLong() / destinationWidth.toLong()
                        ).toInt()
                    /** Transparent pixels remain no-ops under the normal SrcOver write contract. */
                    val color = PixelColor(source.pixels[sourceY * source.width + sourceX])
                    context.buffer.setPixel(
                        x = destinationLeft + destinationX,
                        y = destinationTop + destinationY,
                        color = color,
                    )
                }
            }
        } finally {
            releaseCapture()
        }
    }

    /** Returns the pooled raster exactly once after replay or owner-level failure cleanup. */
    override fun releaseCapture() {
        /** Buffer atomically removed from this retained one-frame plane before pool release. */
        val captured = raster ?: return
        raster = null
        bufferPool.release(captured)
    }
}

/** Mutable collection frame with identity de-duplication for multiply painted retained portals. */
private data class RenderOverlayRegistryFrame(
    /** Root frame buffer; only direct root-coordinate siblings may be safely deferred. */
    val rootBuffer: PixelBuffer,
    /** Layers registered in deterministic tree paint order. */
    val layers: MutableList<RenderLiftedOverlayLayer>,
    /** Render subtree identities already registered in this frame. */
    val seenLayers: IdentityHashMap<RenderBox, Boolean>,
)

/** Per-thread registry interleaving portal presentations with later root-coordinate siblings. */
internal object RenderOverlayLayerRegistry {
    /** Nested registry stack supporting isolated runtimes rendered recursively on one thread. */
    private val registryStack: ThreadLocal<ArrayDeque<RenderOverlayRegistryFrame>> =
        ThreadLocal.withInitial(::ArrayDeque)

    /** Collects every root plane registered while [block] paints base and nested overlay trees. */
    fun collect(
        rootBuffer: PixelBuffer,
        block: (MutableList<RenderLiftedOverlayLayer>) -> Unit,
    ): List<RenderLiftedOverlayLayer> {
        /** Mutable layer order shared with PipelineOwner while nested layers are discovered. */
        val layers = mutableListOf<RenderLiftedOverlayLayer>()
        /** Registry frame that rejects duplicate presentation render identities. */
        val frame = RenderOverlayRegistryFrame(
            rootBuffer = rootBuffer,
            layers = layers,
            seenLayers = IdentityHashMap(),
        )
        val stack = currentStack()
        stack.addLast(frame)
        /** Whether [block] completed and PipelineOwner therefore owns normal plane cleanup. */
        var completed = false
        try {
            block(layers)
            completed = true
        } finally {
            if (!completed) {
                layers.forEach { layer ->
                    (layer.renderBox as? RenderCapturedOverlayPlane)?.releaseCapture()
                }
            }
            stack.removeLast()
            if (stack.isEmpty()) registryStack.remove()
        }
        return layers.toList()
    }

    /** Registers one retained presentation and snapshots bypassed ancestor paint/target policy. */
    fun register(layer: RenderBox, portal: RenderAnchoredOverlayPortal) {
        val stack = currentStack()
        /** Innermost active runtime frame receiving this portal presentation. */
        val frame = stack.lastOrNull()
        if (frame != null && frame.seenLayers.put(layer, true) == null) {
            frame.layers += resolveLiftedLayer(
                layer = layer,
                targetRoot = layer,
                replaysTargets = true,
                firstBypassedAncestor = portal.parent,
                paintOffsetX = 0,
                paintOffsetY = 0,
            )
        }
        if (stack.isEmpty()) registryStack.remove()
    }

    /** Returns the current layer-count checkpoint for any active paint coordinate system. */
    fun layerCheckpoint(): Int? {
        /** Innermost runtime frame receiving planes discovered by the current Stack. */
        val frame = currentFrame() ?: return null
        return frame.layers.size
    }

    /** Reports whether a portal or deferred plane was registered after [checkpoint]. */
    fun hasLayersAfter(checkpoint: Int): Boolean {
        /** Active frame whose append-only list defines current global paint order. */
        val frame = currentFrame() ?: return false
        return frame.layers.size > checkpoint
    }

    /**
     * Defers one higher Stack sibling into the root plane list.
     *
     * Root-coordinate siblings retain their render subtree. Scratch-coordinate siblings paint once
     * into a transparent raster carrying the exact clip extent and global scale of [context].
     */
    fun registerDeferredSibling(
        layer: RenderBox,
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ): RenderDeferredSiblingMode {
        /** Active frame receiving the sibling in the same order as its original Stack traversal. */
        val frame = currentFrame() ?: return RenderDeferredSiblingMode.Rejected
        if (frame.seenLayers.put(layer, true) != null) {
            return RenderDeferredSiblingMode.DeferredSubtree
        }
        if (context.buffer === frame.rootBuffer) {
            frame.layers += resolveLiftedLayer(
                layer = layer,
                targetRoot = layer,
                replaysTargets = true,
                firstBypassedAncestor = layer.parent,
                paintOffsetX = context.globalX(offsetX),
                paintOffsetY = context.globalY(offsetY),
            )
            return RenderDeferredSiblingMode.DeferredSubtree
        }
        /** Plane index before nested portals from this sibling append their own presentations. */
        val insertionIndex = frame.layers.size
        /** Transparent sibling-only raster retaining the current scratch clipping dimensions. */
        val capture = context.bufferPool.acquire(
            width = context.buffer.width.coerceAtLeast(1),
            height = context.buffer.height.coerceAtLeast(1),
        )
        capture.clear()
        try {
            layer.paint(context = context.redirect(capture), offsetX = offsetX, offsetY = offsetY)
        } catch (failure: Throwable) {
            context.bufferPool.release(capture)
            throw failure
        }
        /** Raster renderer mapped through the exact global transform represented by [context]. */
        val capturedPlane = RenderCapturedOverlayRaster(
            raster = capture,
            bufferPool = context.bufferPool,
            destinationLeft = context.globalOriginX,
            destinationTop = context.globalOriginY,
            destinationWidth = context.globalWidth(context.buffer.width),
            destinationHeight = context.globalHeight(context.buffer.height),
        )
        frame.layers.add(
            insertionIndex,
            resolveLiftedLayer(
                layer = capturedPlane,
                targetRoot = layer,
                replaysTargets = false,
                firstBypassedAncestor = layer.parent,
                paintOffsetX = 0,
                paintOffsetY = 0,
            ),
        )
        return RenderDeferredSiblingMode.CapturedRaster
    }

    /** Returns the non-null stack installed by [ThreadLocal.withInitial]. */
    private fun currentStack(): ArrayDeque<RenderOverlayRegistryFrame> {
        return requireNotNull(registryStack.get()) { "Overlay layer registry failed to initialize" }
    }

    /** Returns the innermost frame and removes an accidentally initialized empty thread stack. */
    private fun currentFrame(): RenderOverlayRegistryFrame? {
        /** Stack initialized lazily for callers that can also paint outside PipelineOwner. */
        val stack = currentStack()
        /** Innermost frame, absent when a render object is painted directly in a unit test. */
        val frame = stack.lastOrNull()
        if (frame == null) registryStack.remove()
        return frame
    }

    /** Computes inherited opacity and interaction gates that root lifting must not bypass. */
    private fun resolveLiftedLayer(
        layer: RenderBox,
        targetRoot: RenderBox,
        replaysTargets: Boolean,
        firstBypassedAncestor: RenderObject?,
        paintOffsetX: Int,
        paintOffsetY: Int,
    ): RenderLiftedOverlayLayer {
        /** Effective group alpha accumulated while walking from portal to render root. */
        var opacity = 1f
        /** Logical target export allowed by every bypassed ancestor. */
        var exportsTargets = true
        /** Current bypassed ancestor inspected on the path toward the render root. */
        var ancestor: RenderObject? = firstBypassedAncestor
        while (ancestor != null) {
            when (ancestor) {
                is RenderOpacity -> {
                    opacity = (opacity * ancestor.effectiveOpacity).coerceIn(0f, 1f)
                    if (ancestor.effectiveOpacity <= 0f) exportsTargets = false
                }
                is RenderVisualOnly -> {
                    if (ancestor.suppressesLiftedOverlayTargets) exportsTargets = false
                }
            }
            ancestor = ancestor.parent
        }
        return RenderLiftedOverlayLayer(
            renderBox = layer,
            targetRoot = targetRoot,
            replaysTargets = replaysTargets,
            paintOffsetX = paintOffsetX,
            paintOffsetY = paintOffsetY,
            opacity = opacity,
            exportsTargets = exportsTargets,
        )
    }
}

/**
 * 定义 `RenderAnchoredOverlayPortal` 在 `RenderAnchoredOverlay` 中承担的数据与行为边界。
 *
 * Two-branch portal whose normal layout size equals its anchor while its presentation uses the
 * complete Host viewport and global coordinate system.
 */
public class RenderAnchoredOverlayPortal(
    /** Stable link updated immediately before the anchor paints. */
    private var link: PixelAnchoredOverlayLink,
    /** Configured Host logical width. */
    private var viewportWidth: Int,
    /** Configured Host logical height. */
    private var viewportHeight: Int,
) : MultiChildRenderObject() {
    /** Global portal origin from the most recent paint, used by later hit testing. */
    private var paintedOriginX: Int = 0

    /** Global portal origin from the most recent paint, used by later hit testing. */
    private var paintedOriginY: Int = 0

    /** 更新 `RenderAnchoredOverlay` 的 `updatePortal` 状态并保持派生数据一致。
 *
 * Reconfigures the link or viewport and invalidates layout only when geometry changed.
 */
    public fun updatePortal(link: PixelAnchoredOverlayLink, viewportWidth: Int, viewportHeight: Int) {
        val geometryChanged = this.viewportWidth != viewportWidth || this.viewportHeight != viewportHeight
        val linkChanged = this.link !== link
        if (!geometryChanged && !linkChanged) return
        if (linkChanged) this.link.clearAnchorBounds()
        this.link = link
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        markNeedsLayout()
        markNeedsPaint()
    }

    /** Lays out the anchor normally and gives the optional presentation a tight Host viewport. */
    override fun layout(constraints: RenderConstraints) {
        val anchor = anchorChild
        anchor?.layout(constraints)
        val anchorSize = anchor?.size ?: RenderSize.Zero
        size = RenderSize(
            width = constraints.constrainWidth(anchorSize.width),
            height = constraints.constrainHeight(anchorSize.height),
        )
        presentationChild?.layout(
            RenderConstraints(
                minWidth = effectiveViewportWidth(constraints),
                maxWidth = effectiveViewportWidth(constraints),
                minHeight = effectiveViewportHeight(constraints),
                maxHeight = effectiveViewportHeight(constraints),
            ),
        )
    }

    /** Records the exact global anchor box, then paints the presentation at Host origin. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        paintedOriginX = context.globalX(offsetX)
        paintedOriginY = context.globalY(offsetY)
        val anchor = anchorChild
        link.updateAnchorBounds(
            left = paintedOriginX,
            top = paintedOriginY,
            width = context.globalWidth(anchor?.size?.width ?: 0),
            height = context.globalHeight(anchor?.size?.height ?: 0),
        )
        anchor?.paint(context, offsetX, offsetY)
        presentationChild?.let { presentation ->
            RenderOverlayLayerRegistry.register(layer = presentation, portal = this)
        }
    }

    /** Routes the global point to the in-flow anchor and the full-viewport presentation. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        anchorChild?.hitTest(localX, localY, result)
    }

    /** Exports anchor and presentation click targets in their distinct coordinate systems. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        anchorChild?.collectClickTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation pager targets in their distinct coordinate systems. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        anchorChild?.collectPagerTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation list targets in their distinct coordinate systems. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        anchorChild?.collectListTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation scrollbar targets in their distinct coordinate systems. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        anchorChild?.collectScrollbarTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation refresh targets in their distinct coordinate systems. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        anchorChild?.collectRefreshTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation text-input targets in their distinct coordinate systems. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        anchorChild?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation slider targets in their distinct coordinate systems. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        anchorChild?.collectSliderTargets(offsetX, offsetY, targets)
    }

    /** Exports anchor and presentation semantics in their distinct coordinate systems. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        anchorChild?.collectSemantics(offsetX, offsetY, targets)
    }

    /** Clears geometry when this retained portal detaches from its runtime. */
    override fun onDetach() {
        link.clearAnchorBounds()
    }

    /** Anchor branch that alone determines normal parent layout. */
    private val anchorChild: RenderBox?
        get() = children.getOrNull(0) as? RenderBox

    /** Optional independent overlay branch painted after the anchor. */
    private val presentationChild: RenderBox?
        get() = children.getOrNull(1) as? RenderBox

    /** Uses the configured Host width, falling back to the current parent maximum in tests. */
    private fun effectiveViewportWidth(constraints: RenderConstraints): Int {
        return viewportWidth.takeIf { value -> value > 0 } ?: constraints.maxWidth
    }

    /** Uses the configured Host height, falling back to the current parent maximum in tests. */
    private fun effectiveViewportHeight(constraints: RenderConstraints): Int {
        return viewportHeight.takeIf { value -> value > 0 } ?: constraints.maxHeight
    }
}

/**
 * 定义 `RenderAnchoredOverlayFollower` 在 `RenderAnchoredOverlay` 中承担的数据与行为边界。
 *
 * Full-viewport follower that measures content inside safe bounds and recomputes placement from
 * the anchor's actual global box before every paint.
 */
public class RenderAnchoredOverlayFollower(
    child: RenderBox? = null,
    /** Stable geometry source published by the paired portal. */
    private var link: PixelAnchoredOverlayLink,
    /** Preferred vertical placement policy. */
    private var placement: PixelPopoverPlacement,
    /** Horizontal anchor alignment policy. */
    private var alignment: PixelPopoverAlignment,
    /** Ambient logical direction used to resolve horizontal Start and End. */
    private var textDirection: TextDirection,
    /** Anchor-origin adjustment preserved by the public Popover API. */
    private var contentOffset: IntOffset,
    /** Combined safe-area and IME exclusion insets. */
    private var safeInsets: PixelWindowInsets,
    /** Distance retained from each safe viewport edge. */
    private var viewportMargin: Int,
    /** Current Host logical width. */
    private var viewportWidth: Int,
    /** Current Host logical height. */
    private var viewportHeight: Int,
) : SingleChildRenderObject() {
    /** Child horizontal position relative to this full-viewport follower. */
    private var childOffsetX: Int = 0

    /** Child vertical position relative to this full-viewport follower. */
    private var childOffsetY: Int = 0

    init {
        setRenderObjectChild(child)
    }

    /** 更新 `RenderAnchoredOverlay` 的 `updateFollower` 状态并保持派生数据一致。
 *
 * Updates all placement inputs while preserving the retained popup render subtree.
 */
    @Suppress("LongParameterList")
    public fun updateFollower(
        link: PixelAnchoredOverlayLink,
        placement: PixelPopoverPlacement,
        alignment: PixelPopoverAlignment,
        textDirection: TextDirection,
        contentOffset: IntOffset,
        safeInsets: PixelWindowInsets,
        viewportMargin: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        if (
            this.link === link &&
            this.placement == placement &&
            this.alignment == alignment &&
            this.textDirection == textDirection &&
            this.contentOffset == contentOffset &&
            this.safeInsets == safeInsets &&
            this.viewportMargin == viewportMargin &&
            this.viewportWidth == viewportWidth &&
            this.viewportHeight == viewportHeight
        ) {
            return
        }
        this.link = link
        this.placement = placement
        this.alignment = alignment
        this.textDirection = textDirection
        this.contentOffset = contentOffset
        this.safeInsets = safeInsets
        this.viewportMargin = viewportMargin
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        markNeedsLayout()
        markNeedsPaint()
    }

    /** Measures popup content loosely inside the safe Host viewport. */
    override fun layout(constraints: RenderConstraints) {
        /** Non-negative Host width selected from inherited geometry or current constraints. */
        val width = viewportWidth.takeIf { value -> value > 0 } ?: constraints.maxWidth
        /** Non-negative Host height selected from inherited geometry or current constraints. */
        val height = viewportHeight.takeIf { value -> value > 0 } ?: constraints.maxHeight
        size = RenderSize(width = width.coerceAtLeast(0), height = height.coerceAtLeast(0))
        /** Saturated width remaining after non-negative insets and the two viewport margins. */
        val safeWidth = safeAvailableExtent(
            total = size.width,
            leadingInset = safeInsets.left,
            trailingInset = safeInsets.right,
            margin = viewportMargin,
        )
        /** Saturated height remaining after non-negative insets and the two viewport margins. */
        val safeHeight = safeAvailableExtent(
            total = size.height,
            leadingInset = safeInsets.top,
            trailingInset = safeInsets.bottom,
            margin = viewportMargin,
        )
        renderChild?.layout(RenderConstraints(maxWidth = safeWidth, maxHeight = safeHeight))
    }

    /** Resolves collision policy from current anchor geometry and paints at the global result. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        resolveChildOffset()
        renderChild?.paint(context, offsetX + childOffsetX, offsetY + childOffsetY)
    }

    /** Tests popup content at its last resolved collision-safe position. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        renderChild?.hitTest(localX - childOffsetX, localY - childOffsetY, result)
    }

    /** Exports click targets at the resolved popup position. */
    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        renderChild?.collectClickTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports pager targets at the resolved popup position. */
    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        renderChild?.collectPagerTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports list targets at the resolved popup position. */
    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        renderChild?.collectListTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports scrollbar targets at the resolved popup position. */
    override fun collectScrollbarTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelScrollbarTarget>,
    ) {
        renderChild?.collectScrollbarTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports refresh targets at the resolved popup position. */
    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        renderChild?.collectRefreshTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports text-input targets at the resolved popup position. */
    override fun collectTextInputTargets(
        offsetX: Int,
        offsetY: Int,
        targets: MutableList<PixelTextInputTarget>,
    ) {
        renderChild?.collectTextInputTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports slider targets at the resolved popup position. */
    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        renderChild?.collectSliderTargets(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Exports semantics at the resolved popup position. */
    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        renderChild?.collectSemantics(offsetX + childOffsetX, offsetY + childOffsetY, targets)
    }

    /** Computes horizontal clamping and vertical flip from actual measured anchor/content bounds. */
    private fun resolveChildOffset() {
        /** Measured popup extent already constrained by [layout] to the safe viewport. */
        val childSize = renderChild?.size ?: RenderSize.Zero
        /** Host width promoted before arithmetic so public Int extremes cannot wrap. */
        val viewportWidth = size.width.toLong()
        /** Host height promoted before arithmetic so public Int extremes cannot wrap. */
        val viewportHeight = size.height.toLong()
        /** Non-negative viewport margin promoted before addition and subtraction. */
        val margin = viewportMargin.coerceAtLeast(0).toLong()
        /** Inclusive safe left edge after saturating a potentially extreme public inset. */
        val safeLeft = (safeInsets.left.coerceAtLeast(0).toLong() + margin)
            .coerceIn(0L, viewportWidth)
        /** Inclusive safe top edge after saturating a potentially extreme public inset. */
        val safeTop = (safeInsets.top.coerceAtLeast(0).toLong() + margin)
            .coerceIn(0L, viewportHeight)
        /** Exclusive safe right edge, never below [safeLeft]. */
        val safeRight = (viewportWidth - safeInsets.right.coerceAtLeast(0).toLong() - margin)
            .coerceIn(safeLeft, viewportWidth)
        /** Exclusive safe bottom edge, never below [safeTop]. */
        val safeBottom = (viewportHeight - safeInsets.bottom.coerceAtLeast(0).toLong() - margin)
            .coerceIn(safeTop, viewportHeight)
        /** Global anchor left promoted before adding the public content offset. */
        val anchorLeft = link.anchorLeft.toLong()
        /** Global anchor top promoted before adding the public content offset. */
        val anchorTop = link.anchorTop.toLong()
        /** Non-negative measured anchor width used by alignment and vertical-space decisions. */
        val anchorWidth = link.anchorWidth.coerceAtLeast(0).toLong()
        /** Non-negative measured anchor height used by flip and gap decisions. */
        val anchorHeight = link.anchorHeight.coerceAtLeast(0).toLong()
        /** Non-negative measured popup width promoted for overflow-safe clamping. */
        val childWidth = childSize.width.coerceAtLeast(0).toLong()
        /** Non-negative measured popup height promoted for overflow-safe clamping. */
        val childHeight = childSize.height.coerceAtLeast(0).toLong()
        /** Public horizontal offset promoted before any anchor-relative addition. */
        val contentOffsetX = contentOffset.x.toLong()
        /** Public vertical offset promoted before any anchor-relative addition. */
        val contentOffsetY = contentOffset.y.toLong()
        /** Desired horizontal origin before collision clamping. */
        val desiredX = when (alignment.resolve(textDirection)) {
            PixelPopoverAlignment.Start -> anchorLeft + contentOffsetX
            PixelPopoverAlignment.Center -> anchorLeft + (anchorWidth - childWidth) / 2L + contentOffsetX
            PixelPopoverAlignment.End -> anchorLeft + anchorWidth - childWidth + contentOffsetX
        }
        /** Greatest safe popup origin; collapsed safe rects resolve deterministically to one edge. */
        val maximumX = (safeRight - childWidth).coerceAtLeast(safeLeft)
        childOffsetX = desiredX.coerceIn(safeLeft, maximumX).toInt()

        /** Anchor-relative origin below the anchor before collision handling. */
        val belowY = anchorTop + contentOffsetY
        /** Legacy offset portion interpreted as the visual gap beyond the anchor edge. */
        val gapFromAnchorEdge = (contentOffsetY - anchorHeight).coerceAtLeast(0L)
        /** Mirrored origin above the anchor using the same non-negative visual gap. */
        val aboveY = anchorTop - gapFromAnchorEdge - childHeight
        /** Whether the complete popup fits below without crossing either safe edge. */
        val fitsBelow = belowY >= safeTop && belowY + childHeight <= safeBottom
        /** Whether the complete popup fits above without crossing either safe edge. */
        val fitsAbove = aboveY >= safeTop && aboveY + childHeight <= safeBottom
        /** Placement candidate after applying explicit policy or deterministic auto-flip. */
        val preferredY = when (placement) {
            PixelPopoverPlacement.Below -> belowY
            PixelPopoverPlacement.Above -> aboveY
            PixelPopoverPlacement.Auto -> when {
                fitsBelow -> belowY
                fitsAbove -> aboveY
                safeBottom - (anchorTop + anchorHeight) >= anchorTop - safeTop -> belowY
                else -> aboveY
            }
        }
        /** Greatest safe vertical popup origin after its constrained height is removed. */
        val maximumY = (safeBottom - childHeight).coerceAtLeast(safeTop)
        childOffsetY = preferredY.coerceIn(safeTop, maximumY).toInt()
    }

    /** Current popup render subtree. */
    private val renderChild: RenderBox?
        get() = child as? RenderBox
}

/** Returns a non-negative Int extent after overflow-safe inset and margin subtraction. */
private fun safeAvailableExtent(
    total: Int,
    leadingInset: Int,
    trailingInset: Int,
    margin: Int,
): Int {
    /** Non-negative available axis length promoted for saturated arithmetic. */
    val safeTotal = total.coerceAtLeast(0).toLong()
    /** Non-negative leading exclusion promoted before summation. */
    val safeLeadingInset = leadingInset.coerceAtLeast(0).toLong()
    /** Non-negative trailing exclusion promoted before summation. */
    val safeTrailingInset = trailingInset.coerceAtLeast(0).toLong()
    /** Non-negative public margin promoted before doubling. */
    val safeMargin = margin.coerceAtLeast(0).toLong()
    /** Remaining extent clamped to the representable layout domain. */
    val available = safeTotal - safeLeadingInset - safeTrailingInset - safeMargin * 2L
    return available.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

/** Resolves logical Start and End against [textDirection] while leaving Center unchanged. */
private fun PixelPopoverAlignment.resolve(textDirection: TextDirection): PixelPopoverAlignment {
    if (textDirection == TextDirection.LTR || this == PixelPopoverAlignment.Center) return this
    return when (this) {
        PixelPopoverAlignment.Start -> PixelPopoverAlignment.End
        PixelPopoverAlignment.Center -> PixelPopoverAlignment.Center
        PixelPopoverAlignment.End -> PixelPopoverAlignment.Start
    }
}
