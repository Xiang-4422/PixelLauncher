package com.purride.pixelui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.purride.pixelcore.PixelGridGeometryResolver
import com.purride.pixelcore.PixelViewportPolicy
import com.purride.pixelcore.ScreenProfile
import kotlin.math.ceil

/** View 与 Window 的 attachment 状态；它与 owner lifecycle 正交变化。 */
public enum class PixelHostAttachmentState {
    /** View 当前不属于任何 Window。 */
    Detached,

    /** View 当前已经 attach 到一个 Window。 */
    Attached,
}

/**
 * Host 接收的 owner lifecycle 状态。
 *
 * [Unmanaged] 保留没有 `LifecycleOwner` 的兼容行为：View attach 后立即可交互。其余状态
 * 来自绑定 owner 或显式 `start/resume/pause/stop` 调用；[Destroyed] 是不可逆终态。
 */
public enum class PixelHostLifecycleState {
    /** 没有 owner 或显式 lifecycle，使用 attach 即活跃的兼容策略。 */
    Unmanaged,

    /** owner 已创建但尚未 start。 */
    Initialized,

    /** owner 已 start，但尚未 resume。 */
    Started,

    /** Host 可以渲染动态内容和接收输入。 */
    Resumed,

    /** owner 已 pause，逻辑状态保留但动态工作暂停。 */
    Paused,

    /** owner 已 stop，逻辑状态保留但动态工作暂停。 */
    Stopped,

    /** Host 已终结；后续 lifecycle 调用均为幂等空操作。 */
    Destroyed,
}

/** 当前 Host 的 `LifecycleOwner` 来源。 */
public enum class PixelHostLifecycleOwnerBinding {
    /** 当前没有绑定 owner，生命周期由显式 API 或兼容策略管理。 */
    None,

    /** owner 自动来自 `ViewTreeLifecycleOwner`。 */
    ViewTree,

    /** owner 由消费者显式绑定，优先级高于 ViewTree owner。 */
    Explicit,
}

/**
 * Host 生命周期的只读诊断快照。
 *
 * @property attachmentState 当前 Window attachment 轴状态。
 * @property lifecycleState 当前 owner lifecycle 轴状态。
 * @property ownerBinding 当前 owner 来源。
 * @property isInteractive 两个状态轴合并后是否允许动态渲染与输入。
 * @property transitionSequence 最近一次有效状态变化的单调序号。
 * @property ignoredTransitionCount 重复、乱序或终态后事件被安全忽略的累计数量。
 * @property attachCount 有效 attach 次数。
 * @property detachCount 有效 detach 次数。
 * @property startCount 有效 start 次数。
 * @property resumeCount 有效 resume 次数。
 * @property pauseCount 有效 pause 次数。
 * @property stopCount 有效 stop 次数。
 * @property destroyCount 有效 destroy 次数，最大为一。
 */
public data class PixelHostLifecycleDiagnostics(
    public val attachmentState: PixelHostAttachmentState,
    public val lifecycleState: PixelHostLifecycleState,
    public val ownerBinding: PixelHostLifecycleOwnerBinding,
    public val isInteractive: Boolean,
    public val transitionSequence: Long,
    public val ignoredTransitionCount: Long,
    public val attachCount: Long,
    public val detachCount: Long,
    public val startCount: Long,
    public val resumeCount: Long,
    public val pauseCount: Long,
    public val stopCount: Long,
    public val destroyCount: Long,
)

/**
 * 协调 Android attachment、`LifecycleOwner` 和非 owner 显式生命周期。
 *
 * attachment 与 owner lifecycle 分开保存；只有 attach 且 unmanaged/resumed 时才可交互。
 * 所有方法都允许重复或乱序调用，并把被忽略事件记录到 diagnostics。
 */
internal class PixelHostLifecycleCoordinator(
    /** 有效状态变化后通知 Host 更新 back、输入和渲染 gating。 */
    private val onDiagnosticsChanged: (PixelHostLifecycleDiagnostics) -> Unit = {},
) {
    /** 当前 Window attachment 状态。 */
    private var attachmentState: PixelHostAttachmentState = PixelHostAttachmentState.Detached

    /** 当前 owner lifecycle 状态。 */
    private var lifecycleState: PixelHostLifecycleState = PixelHostLifecycleState.Unmanaged

    /** 当前 owner 绑定来源。 */
    private var ownerBinding: PixelHostLifecycleOwnerBinding = PixelHostLifecycleOwnerBinding.None

    /** 当前被观察的 owner；destroy 时必须清空。 */
    private var lifecycleOwner: LifecycleOwner? = null

    /** 有效状态变化的单调序号。 */
    private var transitionSequence: Long = 0L

    /** 被安全忽略的重复、乱序或终态事件数量。 */
    private var ignoredTransitionCount: Long = 0L

    /** 有效 attach 次数。 */
    private var attachCount: Long = 0L

    /** 有效 detach 次数。 */
    private var detachCount: Long = 0L

    /** 有效 start 次数。 */
    private var startCount: Long = 0L

    /** 有效 resume 次数。 */
    private var resumeCount: Long = 0L

    /** 有效 pause 次数。 */
    private var pauseCount: Long = 0L

    /** 有效 stop 次数。 */
    private var stopCount: Long = 0L

    /** 有效 destroy 次数；终态保护保证最大为一。 */
    private var destroyCount: Long = 0L

    /** 把 Android owner 事件映射到同一显式状态机。 */
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> initialize()
            Lifecycle.Event.ON_START -> start()
            Lifecycle.Event.ON_RESUME -> resume()
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            Lifecycle.Event.ON_ANY -> ignoreTransition()
        }
    }

    /** 当前两个状态轴合并后是否允许动态渲染和输入。 */
    val isInteractive: Boolean
        get() = attachmentState == PixelHostAttachmentState.Attached &&
            (lifecycleState == PixelHostLifecycleState.Unmanaged ||
                lifecycleState == PixelHostLifecycleState.Resumed)

    /** 当前是否已进入不可逆终态。 */
    val isDestroyed: Boolean
        get() = lifecycleState == PixelHostLifecycleState.Destroyed

    /** 当前是否由消费者显式绑定 owner。 */
    val hasExplicitLifecycleOwner: Boolean
        get() = ownerBinding == PixelHostLifecycleOwnerBinding.Explicit

    /** 返回不持有 owner 实例的只读诊断快照。 */
    fun diagnostics(): PixelHostLifecycleDiagnostics {
        return PixelHostLifecycleDiagnostics(
            attachmentState = attachmentState,
            lifecycleState = lifecycleState,
            ownerBinding = ownerBinding,
            isInteractive = isInteractive,
            transitionSequence = transitionSequence,
            ignoredTransitionCount = ignoredTransitionCount,
            attachCount = attachCount,
            detachCount = detachCount,
            startCount = startCount,
            resumeCount = resumeCount,
            pauseCount = pauseCount,
            stopCount = stopCount,
            destroyCount = destroyCount,
        )
    }

    /** 记录一次真实 View attach；终态或重复 attach 会安全忽略。 */
    fun attach() {
        if (isDestroyed || attachmentState == PixelHostAttachmentState.Attached) {
            ignoreTransition()
            return
        }
        attachmentState = PixelHostAttachmentState.Attached
        attachCount += 1L
        emitChange()
    }

    /** 记录一次真实 View detach，但不销毁 retained tree 或 owner 绑定。 */
    fun detach() {
        if (attachmentState == PixelHostAttachmentState.Detached) {
            ignoreTransition()
            return
        }
        attachmentState = PixelHostAttachmentState.Detached
        detachCount += 1L
        emitChange()
    }

    /** 显式绑定一个 owner；该绑定不会被后续 ViewTree 自动发现覆盖。 */
    fun bindExplicitLifecycleOwner(owner: LifecycleOwner) {
        bindLifecycleOwner(owner, PixelHostLifecycleOwnerBinding.Explicit)
    }

    /**
     * 同步 attach 点发现的 ViewTree owner；显式 owner 始终优先。
     *
     * `owner == null` 时只解除旧的 ViewTree 绑定，不影响显式绑定。
     */
    fun updateViewTreeLifecycleOwner(owner: LifecycleOwner?) {
        if (hasExplicitLifecycleOwner || isDestroyed) return
        if (owner == null) {
            if (ownerBinding == PixelHostLifecycleOwnerBinding.ViewTree) {
                unbindLifecycleOwner()
            }
            return
        }
        bindLifecycleOwner(owner, PixelHostLifecycleOwnerBinding.ViewTree)
    }

    /** 解除当前 owner，并回到 attach 即活跃的 unmanaged 兼容模式。 */
    fun unbindLifecycleOwner() {
        val owner = lifecycleOwner
        if (owner == null && ownerBinding == PixelHostLifecycleOwnerBinding.None) {
            ignoreTransition()
            return
        }
        owner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        ownerBinding = PixelHostLifecycleOwnerBinding.None
        if (!isDestroyed) lifecycleState = PixelHostLifecycleState.Unmanaged
        emitChange()
    }

    /** 显式进入 started；resume 后迟到的 start 不会把活跃 Host 降级。 */
    fun start() {
        if (isDestroyed || lifecycleState == PixelHostLifecycleState.Started ||
            lifecycleState == PixelHostLifecycleState.Resumed
        ) {
            ignoreTransition()
            return
        }
        lifecycleState = PixelHostLifecycleState.Started
        startCount += 1L
        emitChange()
    }

    /** 显式进入 resumed；允许从任意非终态直接收敛到可运行状态。 */
    fun resume() {
        if (isDestroyed || lifecycleState == PixelHostLifecycleState.Resumed) {
            ignoreTransition()
            return
        }
        lifecycleState = PixelHostLifecycleState.Resumed
        resumeCount += 1L
        emitChange()
    }

    /** 显式 pause；Stopped 后的迟到 pause 不会把状态倒退。 */
    fun pause() {
        if (isDestroyed || lifecycleState == PixelHostLifecycleState.Paused ||
            lifecycleState == PixelHostLifecycleState.Stopped
        ) {
            ignoreTransition()
            return
        }
        lifecycleState = PixelHostLifecycleState.Paused
        pauseCount += 1L
        emitChange()
    }

    /** 显式 stop；允许从任意非终态直接收敛到 stopped。 */
    fun stop() {
        if (isDestroyed || lifecycleState == PixelHostLifecycleState.Stopped) {
            ignoreTransition()
            return
        }
        lifecycleState = PixelHostLifecycleState.Stopped
        stopCount += 1L
        emitChange()
    }

    /** 进入不可逆 destroy，并在通知 Host 前彻底移除 lifecycle observer。 */
    fun destroy() {
        if (isDestroyed) {
            ignoreTransition()
            return
        }
        lifecycleState = PixelHostLifecycleState.Destroyed
        destroyCount += 1L
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = null
        ownerBinding = PixelHostLifecycleOwnerBinding.None
        emitChange()
    }

    /** 把 Android 物理 inset 转换为逻辑像素 inset。 */
    fun platformInsetsToLogical(
        /** Physical inset measured from the Host's left edge. */
        leftPx: Int,
        /** Physical inset measured from the Host's top edge. */
        topPx: Int,
        /** Physical inset measured from the Host's right edge. */
        rightPx: Int,
        /** Physical inset measured from the Host's bottom edge. */
        bottomPx: Int,
        /** Current physical Host width. */
        viewWidth: Int,
        /** Current physical Host height. */
        viewHeight: Int,
        /** Current logical grid profile. */
        screenProfile: ScreenProfile,
        /**
         * Explicit viewport policy, or `null` to retain the historical inset conversion that
         * predates alignment-aware viewport policies.
         */
        viewportPolicy: PixelViewportPolicy? = null,
        /**
         * Whether physical viewport cropping contributes to the logical inset. Stable system-bar
         * padding includes it; transient IME obscuration excludes crop that exists without IME.
         */
        includeViewportCrop: Boolean = true,
        /** Whether the Host paints logical pixel gaps. */
        pixelGapEnabled: Boolean,
        /** Current logical pixel gap ratio. */
        pixelGapRatio: Float,
    ): PixelWindowInsets {
        /** Paint/touch geometry shared by the inset conversion. */
        val geometry = if (viewportPolicy == null) {
            PixelGridGeometryResolver.resolve(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                profile = screenProfile,
                pixelGapEnabled = pixelGapEnabled,
                pixelGapRatio = pixelGapRatio,
            )
        } else {
            PixelGridGeometryResolver.resolve(
                viewWidth = viewWidth,
                viewHeight = viewHeight,
                profile = screenProfile,
                viewportPolicy = viewportPolicy,
                pixelGapEnabled = pixelGapEnabled,
                pixelGapRatio = pixelGapRatio,
            )
        } ?: return PixelWindowInsets.Zero
        /** Positive physical scale used to convert obscured edge spans. */
        val cellSize = geometry.cellSize.coerceAtLeast(1f)
        if (viewportPolicy == null) {
            return PixelWindowInsets(
                left = leftPx.toLogicalInset(cellSize),
                top = topPx.toLogicalInset(cellSize),
                right = rightPx.toLogicalInset(cellSize),
                bottom = bottomPx.toLogicalInset(cellSize),
            )
        }
        /** Physical left content boundary used as the inset projection baseline. */
        val contentLeft = if (includeViewportCrop) geometry.originX else maxOf(geometry.originX, 0f)
        /** Physical top content boundary used as the inset projection baseline. */
        val contentTop = if (includeViewportCrop) geometry.originY else maxOf(geometry.originY, 0f)
        /** Physical right content boundary used as the inset projection baseline. */
        val contentRight = if (includeViewportCrop) {
            geometry.originX + geometry.contentWidth
        } else {
            minOf(geometry.originX + geometry.contentWidth, viewWidth.toFloat())
        }
        /** Physical bottom content boundary used as the inset projection baseline. */
        val contentBottom = if (includeViewportCrop) {
            geometry.originY + geometry.contentHeight
        } else {
            minOf(geometry.originY + geometry.contentHeight, viewHeight.toFloat())
        }
        /** Physical x at which unobscured content begins. */
        val visibleLeft = leftPx.toFloat()
        /** Physical y at which unobscured content begins. */
        val visibleTop = topPx.toFloat()
        /** Physical x after the final unobscured pixel. */
        val visibleRight = (viewWidth - rightPx).toFloat()
        /** Physical y after the final unobscured pixel. */
        val visibleBottom = (viewHeight - bottomPx).toFloat()
        return PixelWindowInsets(
            left = (visibleLeft - contentLeft).toLogicalInset(cellSize)
                .coerceIn(0, screenProfile.logicalWidth),
            top = (visibleTop - contentTop).toLogicalInset(cellSize)
                .coerceIn(0, screenProfile.logicalHeight),
            right = (contentRight - visibleRight).toLogicalInset(cellSize)
                .coerceIn(0, screenProfile.logicalWidth),
            bottom = (contentBottom - visibleBottom).toLogicalInset(cellSize)
                .coerceIn(0, screenProfile.logicalHeight),
        )
    }

    /** 直接构造已经是逻辑像素单位的 inset。 */
    fun manualInsets(left: Int, top: Int, right: Int, bottom: Int): PixelWindowInsets {
        return PixelWindowInsets(left = left, top = top, right = right, bottom = bottom)
    }

    /** 绑定或切换 owner，并让 observer 同步 owner 的当前状态。 */
    private fun bindLifecycleOwner(
        owner: LifecycleOwner,
        binding: PixelHostLifecycleOwnerBinding,
    ) {
        if (isDestroyed) {
            ignoreTransition()
            return
        }
        if (lifecycleOwner === owner && ownerBinding == binding) {
            ignoreTransition()
            return
        }
        if (lifecycleOwner === owner) {
            ownerBinding = binding
            emitChange()
            return
        }
        lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
        lifecycleOwner = owner
        ownerBinding = binding
        lifecycleState = PixelHostLifecycleState.Initialized
        emitChange()
        if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
            destroy()
            return
        }
        owner.lifecycle.addObserver(lifecycleObserver)
    }

    /** owner `ON_CREATE` 的幂等映射。 */
    private fun initialize() {
        if (isDestroyed || lifecycleState != PixelHostLifecycleState.Unmanaged) {
            ignoreTransition()
            return
        }
        lifecycleState = PixelHostLifecycleState.Initialized
        emitChange()
    }

    /** 记录一次被安全忽略的事件。 */
    private fun ignoreTransition() {
        ignoredTransitionCount += 1L
    }

    /** 提交有效变化并向 Host 发送不含 owner 强引用的 diagnostics。 */
    private fun emitChange() {
        transitionSequence += 1L
        onDiagnosticsChanged(diagnostics())
    }

    /** 向上取整地把一个正物理 inset 转成逻辑像素。 */
    private fun Int.toLogicalInset(cellSize: Float): Int {
        if (this <= 0) return 0
        return ceil(this / cellSize).toInt()
    }

    /** Rounds one positive physical overlap up to a complete obscured logical cell. */
    private fun Float.toLogicalInset(cellSize: Float): Int {
        if (this <= 0f) return 0
        return ceil(this / cellSize).toInt()
    }
}
