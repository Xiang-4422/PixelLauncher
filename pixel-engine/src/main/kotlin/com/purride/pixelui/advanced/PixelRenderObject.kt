package com.purride.pixelui.advanced

/**
 * 定义 `PixelRenderObject` 在 `PixelRenderObject` 中承担的数据与行为边界。
 *
 * Stable root type for a consumer-defined retained render object.
 *
 * Engine ownership stays behind internal callbacks; public and protected signatures therefore never
 * expose the retained Element, PipelineOwner, or concrete pipeline implementation.
 */
public abstract class PixelRenderObject {
    /** Callback that invalidates the engine's layout phase while this object is attached. */
    private var layoutInvalidator: (() -> Unit)? = null

    /** Callback that invalidates the engine's paint phase while this object is attached. */
    private var paintInvalidator: (() -> Unit)? = null

    /** 执行 `PixelRenderObject` 的 `markNeedsLayout` 公开行为；具体参数、返回和副作用见下文。
 *
 * Marks this object and its attached pipeline as requiring another layout and paint pass.
 */
    protected fun markNeedsLayout() {
        layoutInvalidator?.invoke()
    }

    /** 执行 `PixelRenderObject` 的 `markNeedsPaint` 公开行为；具体参数、返回和副作用见下文。
 *
 * Marks this object and its attached pipeline as requiring another paint pass.
 */
    protected fun markNeedsPaint() {
        paintInvalidator?.invoke()
    }

    /** 执行 `PixelRenderObject` 的 `onAttach` 公开行为；具体参数、返回和副作用见下文。
 *
 * Lifecycle hook invoked after the engine attaches this object to a pipeline.
 */
    protected open fun onAttach(): Unit = Unit

    /** 执行 `PixelRenderObject` 的 `onDetach` 公开行为；具体参数、返回和副作用见下文。
 *
 * Lifecycle hook invoked before the engine releases this object from a pipeline.
 */
    protected open fun onDetach(): Unit = Unit

    /**
     * Connects engine invalidation callbacks without exposing the engine owner type in the SPI.
     */
    @JvmSynthetic
    internal fun attachToEngine(
        markLayoutDirty: () -> Unit,
        markPaintDirty: () -> Unit,
    ) {
        if (layoutInvalidator != null || paintInvalidator != null) {
            return
        }
        layoutInvalidator = markLayoutDirty
        paintInvalidator = markPaintDirty
        onAttach()
    }

    /** Disconnects engine invalidation callbacks and invokes the public lifecycle hook once. */
    @JvmSynthetic
    internal fun detachFromEngine() {
        if (layoutInvalidator == null && paintInvalidator == null) {
            return
        }
        onDetach()
        layoutInvalidator = null
        paintInvalidator = null
    }
}

/**
 * 定义 `PixelRenderBox` 在 `PixelRenderObject` 中承担的数据与行为边界。
 *
 * Stable box-model render object for consumer-defined layout, painting, and optional hit testing.
 */
public abstract class PixelRenderBox : PixelRenderObject() {
    /** 定义 `PixelRenderObject` 的 `size` 逻辑像素度量。
 *
 * Size selected by the latest [layout] call.
 */
    public var size: PixelRenderSize = PixelRenderSize.Zero
        protected set

    /** 执行 `PixelRenderObject` 的 `layout` 渲染或命中阶段。
 *
 * Selects [size] within the bounds supplied by [constraints].
 */
    public abstract fun layout(constraints: PixelRenderConstraints)

    /** 执行 `PixelRenderObject` 的 `paint` 渲染或命中阶段。
 *
 * Paints this object into [context] at the supplied absolute logical offset.
 */
    public abstract fun paint(
        context: PixelPaintContext,
        offsetX: Int,
        offsetY: Int,
    )

    /** 执行 `PixelRenderObject` 的 `hitTest` 渲染或命中阶段。
 *
 * Adds any objects hit at the supplied local coordinate to [result].
 */
    @PixelExperimentalApi
    public open fun hitTest(
        localX: Int,
        localY: Int,
        result: PixelHitTestResult,
    ): Unit = Unit
}

/** 定义 `PixelRenderObjectWithChild` 在 `PixelRenderObject` 中的可替换调用契约。
 *
 * Experimental protocol used by an engine adapter to replace a render object's only child.
 */
@PixelExperimentalApi
public interface PixelRenderObjectWithChild {
    /** 更新 `PixelRenderObject` 的 `setRenderObjectChild` 状态并保持派生数据一致。
 *
 * Replaces the current direct child, or clears it when [child] is null.
 */
    public fun setRenderObjectChild(child: PixelRenderObject?)
}

/** 定义 `PixelRenderObjectWithChildren` 在 `PixelRenderObject` 中的可替换调用契约。
 *
 * Experimental protocol used by an engine adapter to replace all direct render-object children.
 */
@PixelExperimentalApi
public interface PixelRenderObjectWithChildren {
    /** 更新 `PixelRenderObject` 的 `setRenderObjectChildren` 状态并保持派生数据一致。
 *
 * Replaces all direct children while preserving the supplied order.
 */
    public fun setRenderObjectChildren(children: List<PixelRenderObject>)
}

/**
 * 定义 `PixelSingleChildRenderObject` 在 `PixelRenderObject` 中承担的数据与行为边界。
 *
 * Experimental convenience base for a box with exactly one direct render-object child.
 */
@PixelExperimentalApi
public abstract class PixelSingleChildRenderObject : PixelRenderBox(), PixelRenderObjectWithChild {
    /** 公开 `PixelRenderObject` 的 `child` 配置或运行值。
 *
 * Direct child exposed to subclass layout, paint, and hit-test implementations.
 */
    protected var child: PixelRenderObject? = null
        private set

    /** 更新 `PixelRenderObject` 的 `setRenderObjectChild` 状态并保持派生数据一致。
 *
 * Replaces [child] and invalidates layout only when identity actually changes.
 */
    public final override fun setRenderObjectChild(child: PixelRenderObject?) {
        if (this.child === child) {
            return
        }
        this.child = child
        markNeedsLayout()
    }
}

/**
 * 定义 `PixelMultiChildRenderObject` 在 `PixelRenderObject` 中承担的数据与行为边界。
 *
 * Experimental convenience base for a box with an ordered list of direct render-object children.
 */
@PixelExperimentalApi
public abstract class PixelMultiChildRenderObject : PixelRenderBox(), PixelRenderObjectWithChildren {
    /** 公开 `PixelRenderObject` 的 `children` 配置或运行值。
 *
 * Ordered direct children exposed to subclass layout, paint, and hit-test implementations.
 */
    protected var children: List<PixelRenderObject> = emptyList()
        private set

    /** 更新 `PixelRenderObject` 的 `setRenderObjectChildren` 状态并保持派生数据一致。
 *
 * Replaces [children] and invalidates layout only when child identities or ordering change.
 */
    public final override fun setRenderObjectChildren(children: List<PixelRenderObject>) {
        if (hasSameChildren(this.children, children)) {
            return
        }
        this.children = children.toList()
        markNeedsLayout()
    }

    /** Returns true when two child lists contain the same object identities in the same order. */
    private fun hasSameChildren(
        current: List<PixelRenderObject>,
        next: List<PixelRenderObject>,
    ): Boolean {
        if (current.size != next.size) {
            return false
        }
        return current.indices.all { index -> current[index] === next[index] }
    }
}
