package com.purride.pixelui.internal

import com.purride.pixelui.PixelFocusOwner
import com.purride.pixelui.PixelFocusOwnerScope
import com.purride.pixelui.Widget

/**
 * retained build runtime 的默认实现。
 *
 * 这层负责驱动 `BuildOwner` 更新根 Widget、执行 dirty build，并返回最新的
 * retained element tree 根节点。
 */
internal class RetainedBuildRuntime(
    /** Callback requesting a host frame for changes that happen outside the current build pass. */
    private val onVisualUpdate: () -> Unit,
    /** Reconciliation strategy used to inflate and update retained child Elements. */
    private val elementChildUpdater: ElementChildUpdater,
    /** Whether the fallback focus owner provisionally selects a modal's first mounted control. */
    automaticallyFocusModalDescendants: Boolean,
    /** 已被 ErrorBoundary 恢复的 build 错误通知。 */
    onRecoveredBuildError: (Throwable, String) -> Unit,
) : ElementTreeBuildRuntime {
    /** Runtime-local fallback owner used when the caller did not provide a Host focus boundary. */
    private val fallbackFocusOwner: PixelFocusOwner = PixelFocusOwner(
        automaticallyFocusModalDescendants = automaticallyFocusModalDescendants,
    )

    /** Stable identity that retains the fallback owner boundary across root widget updates. */
    private val fallbackFocusBoundaryKey: Any = Any()

    /** Whether the current root was wrapped in this runtime's transparent fallback boundary. */
    private var usesFallbackFocusBoundary: Boolean = false

    /** Build owner retaining the complete internal tree, including any fallback focus boundary. */
    private val buildOwner = BuildOwner(
        onVisualUpdate = onVisualUpdate,
        elementChildUpdater = elementChildUpdater,
        onRecoveredBuildError = onRecoveredBuildError,
    )

    /**
     * 执行一次 retained element tree 构建请求。
     */
    override fun resolveElementTree(request: ElementTreeBuildRequest): Element? {
        /** Caller root before runtime-local focus isolation is applied. */
        val requestedRoot = request.root
        usesFallbackFocusBoundary = requestedRoot !is PixelFocusOwnerScope
        /** Internal root that guarantees raw retained runtimes never share the legacy focus owner. */
        val retainedRoot = if (usesFallbackFocusBoundary) {
            PixelFocusOwnerScope(
                owner = fallbackFocusOwner,
                child = requestedRoot,
                key = fallbackFocusBoundaryKey,
            )
        } else {
            requestedRoot
        }
        buildOwner.renderPass(retainedRoot)
        return applicationRootElement()
    }

    /** Recovers the retained tree and returns the caller-visible root below the fallback boundary. */
    override fun recoverFromRenderError(error: Throwable): Element? {
        buildOwner.recoverFromRenderError(error) ?: return null
        return applicationRootElement()
    }

    /** Returns caller-visible diagnostics without exposing the transparent fallback boundary. */
    override fun collectDiagnostics(): BuildOwnerDiagnostics {
        /** Complete owner diagnostics before the runtime-only boundary is projected away. */
        val diagnostics = buildOwner.collectDiagnostics()
        if (!usesFallbackFocusBoundary || diagnostics.elementDiagnostics.isEmpty()) return diagnostics
        /** Path prefix contributed only by the runtime-owned inherited boundary. */
        val boundaryPathPrefix = "${diagnostics.elementDiagnostics.first().path}/"
        /** Application diagnostics restored to the same depth and path shape as the caller root. */
        val applicationDiagnostics = diagnostics.elementDiagnostics.drop(1).map { node ->
            node.copy(
                depth = (node.depth - 1).coerceAtLeast(0),
                path = node.path.removePrefix(boundaryPathPrefix),
            )
        }
        return diagnostics.copy(
            hasRoot = applicationDiagnostics.isNotEmpty(),
            elementDiagnostics = applicationDiagnostics,
        )
    }

    /** Returns caller widgets while hiding the runtime-owned inherited focus boundary. */
    override fun collectWidgets(): List<Widget> {
        /** Complete retained widget list in root-first order. */
        val widgets = buildOwner.collectWidgets()
        return if (usesFallbackFocusBoundary && widgets.firstOrNull() is PixelFocusOwnerScope) {
            widgets.drop(1)
        } else {
            widgets
        }
    }

    /** Returns the retained scheduler's cumulative rebuild counter without tree allocation. */
    override fun cumulativeRebuiltElementCount(): Long = buildOwner.cumulativeRebuiltElementCount()

    /**
     * 释放内部 `BuildOwner` 以及整棵 retained element tree。
     */
    override fun dispose() {
        /** Terminal collector guarantees focus state is cleared even when Element teardown fails. */
        val failures = TeardownFailureCollector()
        failures.capture { buildOwner.dispose() }
        failures.capture { fallbackFocusOwner.dispose() }
        usesFallbackFocusBoundary = false
        failures.throwIfAny()
    }

    /** Resolves the caller root below the runtime-owned focus boundary, when one was inserted. */
    private fun applicationRootElement(): Element? {
        /** Complete retained root currently owned by [buildOwner]. */
        val retainedRoot = buildOwner.rootElement ?: return null
        if (!usesFallbackFocusBoundary) return retainedRoot
        /** Sole child mounted below [PixelFocusOwnerScope]. */
        var applicationRoot: Element? = null
        retainedRoot.visitChildren { child ->
            if (applicationRoot == null) applicationRoot = child
        }
        return applicationRoot
    }
}
