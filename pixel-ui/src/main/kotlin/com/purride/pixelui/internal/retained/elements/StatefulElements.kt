package com.purride.pixelui.internal

import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.StatelessWidget
import com.purride.pixelui.Widget

/**
 * StatelessWidget 对应的 element。
 */
internal class StatelessElement(
    widget: StatelessWidget,
) : ComponentElement(widget) {
    /**
     * 调用 stateless widget 的 build。
     */
    override fun buildWidget(): Widget {
        return (widget as StatelessWidget).build(this)
    }
}

/**
 * StatefulWidget 对应的 element。
 */
internal class StatefulElement(
    widget: StatefulWidget,
) : ComponentElement(widget) {
    private val stateBinding = StateBinding(
        widget = widget,
        context = this,
    )

    /**
     * 更新 widget 时同步 state 的 didUpdateWidget 回调。
     */
    override fun update(newWidget: Widget) {
        super.update(newWidget)
        stateBinding.update(newWidget)
    }

    /**
     * 通过 state 构建当前组件树。
     */
    override fun buildWidget(): Widget {
        return stateBinding.build(this)
    }

    /**
     * 卸载时释放 state 绑定。
     */
    override fun onUnmount() {
        stateBinding.dispose()
    }

    /**
     * 让 state 可通过 context 主动触发重建。
     */
    override fun markCurrentElementNeedsBuild() {
        markNeedsBuild()
    }

    /**
     * 标记 inherited 依赖已变更。
     */
    fun markDependenciesChanged() {
        stateBinding.markDependenciesChanged()
        markNeedsBuild()
    }
}
