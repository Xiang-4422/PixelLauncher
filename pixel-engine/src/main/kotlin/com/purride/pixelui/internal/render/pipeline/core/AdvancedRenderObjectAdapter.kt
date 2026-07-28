package com.purride.pixelui.internal

import com.purride.pixelui.advanced.PixelExperimentalApi
import com.purride.pixelui.advanced.PixelHitTestResult
import com.purride.pixelui.advanced.PixelMultiChildRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderObjectWidget
import com.purride.pixelui.advanced.PixelRenderObjectWithChild
import com.purride.pixelui.advanced.PixelRenderObjectWithChildren
import com.purride.pixelui.advanced.PixelRenderSize
import com.purride.pixelui.advanced.PixelSingleChildRenderObjectWidget
import java.util.IdentityHashMap

/** Internal marker that exposes the public SPI object owned by a pipeline adapter. */
internal interface AdvancedRenderObjectAdapter {
    /** Public render object receiving consumer-defined layout and paint calls. */
    val advancedRenderObject: PixelRenderObject
}

/**
 * Creates the internal retained-pipeline adapter appropriate for an advanced Widget's child model.
 */
internal object AdvancedRenderObjectAdapterFactory {
    /**
     * Wraps [advancedRenderObject] in an internal render object without leaking that implementation
     * type through any public advanced signature.
     */
    @OptIn(PixelExperimentalApi::class)
    fun create(
        widget: PixelRenderObjectWidget,
        advancedRenderObject: PixelRenderObject,
    ): RenderObject {
        /** Box implementation required by the current retained layout and paint pipeline. */
        val renderBox = advancedRenderObject as? PixelRenderBox
            ?: error(
                "${widget::class.qualifiedName} must create a PixelRenderBox; " +
                    "received ${advancedRenderObject::class.qualifiedName}.",
            )
        return when (widget) {
            is PixelSingleChildRenderObjectWidget -> {
                /** Child protocol used to synchronize the retained Widget child. */
                val childProtocol = renderBox as? PixelRenderObjectWithChild
                    ?: error(
                        "${widget::class.qualifiedName} must create a " +
                            "PixelRenderObjectWithChild.",
                    )
                AdvancedSingleChildRenderBoxAdapter(renderBox, childProtocol)
            }

            is PixelMultiChildRenderObjectWidget -> {
                /** Children protocol used to synchronize ordered retained Widget children. */
                val childrenProtocol = renderBox as? PixelRenderObjectWithChildren
                    ?: error(
                        "${widget::class.qualifiedName} must create a " +
                            "PixelRenderObjectWithChildren.",
                    )
                AdvancedMultiChildRenderBoxAdapter(renderBox, childrenProtocol)
            }

            else -> RetainedAdvancedLeafBox(renderBox)
        }
    }
}

/**
 * Shared bridge for public render callbacks, lifecycle, invalidation, and primitive conversion.
 *
 * @property renderBox Consumer-owned public box.
 * @property markLayoutDirty Callback into the internal pipeline's layout invalidation path.
 * @property markPaintDirty Callback into the internal pipeline's paint invalidation path.
 */
@OptIn(PixelExperimentalApi::class)
private class AdvancedRenderBoxBridge(
    private val renderBox: PixelRenderBox,
    private val markLayoutDirty: () -> Unit,
    private val markPaintDirty: () -> Unit,
) {
    /** Connects invalidation and public attach lifecycle after the adapter gains an owner. */
    fun attach() {
        renderBox.attachToEngine(markLayoutDirty, markPaintDirty)
    }

    /** Disconnects invalidation and public detach lifecycle before the adapter is discarded. */
    fun detach() {
        renderBox.detachFromEngine()
    }

    /** Runs consumer layout and converts the selected public size into the internal value type. */
    fun layout(constraints: RenderConstraints): RenderSize {
        renderBox.layout(constraints.toAdvancedConstraints())
        return renderBox.size.toInternalSize()
    }

    /** Runs consumer paint against a public context sharing the current destination and pool. */
    fun paint(
        context: PaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        /** Public facade associated with this exact internal context for the call duration. */
        val publicContext = PixelPaintContext(
            buffer = context.buffer,
            bufferPool = context.bufferPool,
        )
        AdvancedPaintContextRegistry.withContext(
            publicContext = publicContext,
            internalContext = context,
        ) {
            renderBox.paint(
                context = publicContext,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        }
    }

    /** Translates the public hit path back into concrete internal pipeline targets. */
    fun hitTest(
        adapter: RenderObject,
        localX: Int,
        localY: Int,
        result: HitTestResult,
    ) {
        /** Public result populated by the consumer-defined hit-test implementation. */
        val advancedResult = PixelHitTestResult()
        renderBox.hitTest(localX = localX, localY = localY, result = advancedResult)
        advancedResult.hits.forEach { target ->
            when {
                target === renderBox -> result.add(adapter)
                target is InternalRenderObjectFacade -> result.add(target.internalRenderObject)
            }
        }
    }
}

/** Internal adapter for a leaf or childless advanced render box. */
@OptIn(PixelExperimentalApi::class)
private class RetainedAdvancedLeafBox(
    private val renderBox: PixelRenderBox,
) : RenderBox(), AdvancedRenderObjectAdapter {
    /** Public object retained for Widget configuration updates. */
    override val advancedRenderObject: PixelRenderObject
        get() = renderBox

    /** Shared lifecycle and primitive conversion bridge. */
    private val bridge = AdvancedRenderBoxBridge(
        renderBox = renderBox,
        markLayoutDirty = { markNeedsLayout() },
        markPaintDirty = { markNeedsPaint() },
    )

    /** Connects the public object once this adapter is attached to a pipeline owner. */
    override fun onAttach() {
        bridge.attach()
    }

    /** Disconnects the public object once this adapter leaves its pipeline owner. */
    override fun onDetach() {
        bridge.detach()
    }

    /** Delegates layout and mirrors the resulting public size into the internal pipeline. */
    override fun layout(constraints: RenderConstraints) {
        size = bridge.layout(constraints)
    }

    /** Delegates painting to the consumer-owned public render box. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        bridge.paint(context, offsetX, offsetY)
    }

    /** Delegates hit testing and translates public targets back to the adapter. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        bridge.hitTest(this, localX, localY, result)
    }
}

/** Internal adapter for an advanced render box with exactly one retained child. */
@OptIn(PixelExperimentalApi::class)
private class AdvancedSingleChildRenderBoxAdapter(
    private val renderBox: PixelRenderBox,
    private val childProtocol: PixelRenderObjectWithChild,
) : SingleChildRenderObject(), AdvancedRenderObjectAdapter {
    /** Public object retained for Widget configuration updates. */
    override val advancedRenderObject: PixelRenderObject
        get() = renderBox

    /** Shared lifecycle and primitive conversion bridge. */
    private val bridge = AdvancedRenderBoxBridge(
        renderBox = renderBox,
        markLayoutDirty = { markNeedsLayout() },
        markPaintDirty = { markNeedsPaint() },
    )

    /** Connects the public object once this adapter is attached to a pipeline owner. */
    override fun onAttach() {
        bridge.attach()
    }

    /** Disconnects the public object once this adapter leaves its pipeline owner. */
    override fun onDetach() {
        bridge.detach()
    }

    /** Delegates layout and mirrors the resulting public size into the internal pipeline. */
    override fun layout(constraints: RenderConstraints) {
        size = bridge.layout(constraints)
    }

    /** Delegates painting to the consumer-owned public render box. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        bridge.paint(context, offsetX, offsetY)
    }

    /** Delegates hit testing and translates public targets back to internal targets. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        bridge.hitTest(this, localX, localY, result)
    }

    /** Synchronizes both the internal retained child and its public SPI facade. */
    override fun setRenderObjectChild(child: RenderObject?) {
        super.setRenderObjectChild(child)
        childProtocol.setRenderObjectChild(child?.toAdvancedFacade())
    }
}

/** Internal adapter for an advanced render box with ordered retained children. */
@OptIn(PixelExperimentalApi::class)
private class AdvancedMultiChildRenderBoxAdapter(
    private val renderBox: PixelRenderBox,
    private val childrenProtocol: PixelRenderObjectWithChildren,
) : MultiChildRenderObject(), AdvancedRenderObjectAdapter {
    /** Public object retained for Widget configuration updates. */
    override val advancedRenderObject: PixelRenderObject
        get() = renderBox

    /** Shared lifecycle and primitive conversion bridge. */
    private val bridge = AdvancedRenderBoxBridge(
        renderBox = renderBox,
        markLayoutDirty = { markNeedsLayout() },
        markPaintDirty = { markNeedsPaint() },
    )

    /** Connects the public object once this adapter is attached to a pipeline owner. */
    override fun onAttach() {
        bridge.attach()
    }

    /** Disconnects the public object once this adapter leaves its pipeline owner. */
    override fun onDetach() {
        bridge.detach()
    }

    /** Delegates layout and mirrors the resulting public size into the internal pipeline. */
    override fun layout(constraints: RenderConstraints) {
        size = bridge.layout(constraints)
    }

    /** Delegates painting to the consumer-owned public render box. */
    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        bridge.paint(context, offsetX, offsetY)
    }

    /** Delegates hit testing and translates public targets back to internal targets. */
    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        bridge.hitTest(this, localX, localY, result)
    }

    /** Synchronizes both internal retained children and ordered public SPI facades. */
    override fun setRenderObjectChildren(children: List<RenderObject>) {
        super.setRenderObjectChildren(children)
        childrenProtocol.setRenderObjectChildren(children.map(RenderObject::toAdvancedFacade))
    }
}

/** Internal capability implemented by public facades that wrap a concrete pipeline object. */
private interface InternalRenderObjectFacade {
    /** Concrete internal render object represented by this facade. */
    val internalRenderObject: RenderObject
}

/** Public-shaped facade for a non-box internal render object. */
private class AdvancedRenderObjectFacade(
    override val internalRenderObject: RenderObject,
) : PixelRenderObject(), InternalRenderObjectFacade

/** Public-shaped box facade that safely delegates to one internal retained child. */
@OptIn(PixelExperimentalApi::class)
private class AdvancedRenderBoxFacade(
    override val internalRenderObject: RenderBox,
) : PixelRenderBox(), InternalRenderObjectFacade {
    init {
        synchronizeSize()
    }

    /** Delegates child layout through public-to-internal constraint conversion. */
    override fun layout(constraints: PixelRenderConstraints) {
        internalRenderObject.layout(constraints.toInternalConstraints())
        synchronizeSize()
    }

    /** Delegates child painting while preserving the shared destination and buffer pool. */
    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        /** Original engine context when this facade is called from a retained bridge. */
        val engineContext = AdvancedPaintContextRegistry.resolve(context) ?: PaintContext(
            buffer = context.buffer,
            bufferPool = context.bufferPool,
        )
        internalRenderObject.paint(
            context = engineContext,
            offsetX = offsetX,
            offsetY = offsetY,
        )
    }

    /** Delegates child hit testing and returns public facades for all internal hits. */
    override fun hitTest(localX: Int, localY: Int, result: PixelHitTestResult) {
        /** Internal result populated by the retained child. */
        val internalResult = HitTestResult()
        internalRenderObject.hitTest(localX = localX, localY = localY, result = internalResult)
        internalResult.hits.forEach { target -> result.add(target.toAdvancedFacade()) }
    }

    /** Mirrors the internal child's latest layout size into this public facade. */
    private fun synchronizeSize() {
        size = internalRenderObject.size.toAdvancedSize()
    }
}

/**
 * Call-scoped association that preserves internal paint transforms through a stable public SPI.
 *
 * Keeping the association outside [PixelPaintContext] avoids adding internal engine types or
 * constructors to its published JVM ABI. Identity lookup also prevents equal buffer facades from
 * accidentally sharing metadata across nested advanced render calls.
 */
private object AdvancedPaintContextRegistry {
    /** Per-thread association stack supporting recursively painted advanced render objects. */
    private val contextStack: ThreadLocal<ArrayDeque<IdentityHashMap<PixelPaintContext, PaintContext>>> =
        ThreadLocal.withInitial(::ArrayDeque)

    /** Associates one public facade for [block] and removes all metadata even when paint throws. */
    fun <T> withContext(
        publicContext: PixelPaintContext,
        internalContext: PaintContext,
        block: () -> T,
    ): T {
        /** Fresh identity map isolates this bridge call from recursive advanced paint. */
        val frame = IdentityHashMap<PixelPaintContext, PaintContext>()
        frame[publicContext] = internalContext
        /** Non-null stack installed by [ThreadLocal.withInitial]. */
        val stack = requireNotNull(contextStack.get()) { "Advanced paint context registry failed to initialize" }
        stack.addLast(frame)
        try {
            return block()
        } finally {
            stack.removeLast()
            if (stack.isEmpty()) contextStack.remove()
        }
    }

    /** Resolves the closest recursive bridge frame that owns [publicContext]. */
    fun resolve(publicContext: PixelPaintContext): PaintContext? {
        /** Active frames ordered from outermost to innermost advanced paint call. */
        val stack = contextStack.get() ?: return null
        return stack.asReversed().firstNotNullOfOrNull { frame -> frame[publicContext] }
    }
}

/** Creates the narrowest public facade supported by this internal render object. */
private fun RenderObject.toAdvancedFacade(): PixelRenderObject {
    return when (this) {
        is RenderBox -> AdvancedRenderBoxFacade(this)
        else -> AdvancedRenderObjectFacade(this)
    }
}

/** Converts an internal layout constraint into its public stable SPI representation. */
private fun RenderConstraints.toAdvancedConstraints(): PixelRenderConstraints {
    return PixelRenderConstraints(
        minWidth = minWidth,
        maxWidth = maxWidth,
        minHeight = minHeight,
        maxHeight = maxHeight,
    )
}

/** 把公开 SPI 的布局约束转换为内部 pipeline 表示。 */
private fun PixelRenderConstraints.toInternalConstraints(): RenderConstraints {
    return RenderConstraints(
        minWidth = minWidth,
        maxWidth = maxWidth,
        minHeight = minHeight,
        maxHeight = maxHeight,
    )
}

/** Converts an internal size into its public stable SPI representation. */
private fun RenderSize.toAdvancedSize(): PixelRenderSize {
    return PixelRenderSize(width = width, height = height)
}

/** 把公开 SPI 的尺寸转换为内部 pipeline 表示。 */
private fun PixelRenderSize.toInternalSize(): RenderSize {
    return RenderSize(width = width, height = height)
}
