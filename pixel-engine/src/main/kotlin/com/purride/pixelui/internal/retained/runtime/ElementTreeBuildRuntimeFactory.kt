package com.purride.pixelui.internal

/**
 * retained element tree 构建运行时的默认工厂。
 */
internal object ElementTreeBuildRuntimeFactory {
    /**
     * 创建默认的 retained build runtime。
     */
    fun createDefault(
        /** Callback requesting a later host frame for out-of-pass changes. */
        onVisualUpdate: () -> Unit,
        /** Adapter for widgets outside the built-in retained inflater. */
        widgetAdapter: WidgetAdapter,
        /** 已由最近 ErrorBoundary 恢复的 build 错误通知。 */
        onRecoveredBuildError: (Throwable, String) -> Unit = { _, _ -> },
        /** Whether a fallback owner may provisionally focus a modal's first mounted control. */
        automaticallyFocusModalDescendants: Boolean = true,
    ): ElementTreeBuildRuntime {
        return RetainedBuildRuntime(
            onVisualUpdate = onVisualUpdate,
            elementChildUpdater = DefaultElementChildUpdater(
                elementInflater = DefaultElementInflater(
                    widgetAdapter = widgetAdapter,
                ),
            ),
            automaticallyFocusModalDescendants = automaticallyFocusModalDescendants,
            onRecoveredBuildError = onRecoveredBuildError,
        )
    }
}
