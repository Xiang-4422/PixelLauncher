package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget

internal class StateBinding(
    widget: StatefulWidget,
    context: BuildContext,
) {
    private val state: State<StatefulWidget> = createAttachedState(
        widget = widget,
        context = context,
    )
    private var dependenciesChanged = true

    fun update(newWidget: Widget): StatefulWidget {
        val oldWidget = state.widget
        @Suppress("UNCHECKED_CAST")
        state.widget = newWidget as StatefulWidget
        state.didUpdateWidget(oldWidget)
        return oldWidget
    }

    fun build(context: BuildContext): Widget {
        state.context = context
        if (dependenciesChanged) {
            dependenciesChanged = false
            state.didChangeDependencies()
        }
        return state.build(context)
    }

    fun markDependenciesChanged() {
        dependenciesChanged = true
    }

    fun dispose() {
        state.dispose()
        state.detach()
    }

    private fun createAttachedState(
        widget: StatefulWidget,
        context: BuildContext,
    ): State<StatefulWidget> {
        @Suppress("UNCHECKED_CAST")
        val createdState = widget.createState() as State<StatefulWidget>
        createdState.widget = widget
        createdState.context = context
        createdState.attach()
        createdState.initState()
        return createdState
    }
}
