package com.purride.pixelui.internal

import com.purride.pixelui.BuildContext
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget

/** Owns one public State instance and its retained attachment lifecycle. */
internal class StateBinding(
    widget: StatefulWidget,
    context: BuildContext,
) {
    private val state: State<StatefulWidget> = createAttachedState(
        widget = widget,
        context = context,
    )
    /** Whether terminal disposal has already started for this one-shot binding. */
    private var disposed = false
    /** Whether the next build must deliver didChangeDependencies first. */
    private var dependenciesChanged = true

    /** Updates the immutable widget observed by the retained State. */
    fun update(newWidget: Widget): StatefulWidget {
        val oldWidget = state.widget
        @Suppress("UNCHECKED_CAST")
        state.widget = newWidget as StatefulWidget
        state.didUpdateWidget(oldWidget)
        return oldWidget
    }

    /** Builds the State after delivering any pending inherited-dependency notification. */
    fun build(context: BuildContext): Widget {
        state.context = context
        if (dependenciesChanged) {
            dependenciesChanged = false
            state.didChangeDependencies()
        }
        return state.build(context)
    }

    /** Records that inherited dependencies changed before the next State build. */
    fun markDependenciesChanged() {
        dependenciesChanged = true
    }

    /** Runs user disposal exactly once and always detaches State even when that callback fails. */
    fun dispose() {
        if (disposed) return
        disposed = true
        /** Failure collector guaranteeing mounted becomes false before an exception escapes. */
        val failures = TeardownFailureCollector()
        failures.capture { state.dispose() }
        failures.capture { state.detach() }
        failures.throwIfAny()
    }

    /** Creates, attaches, and initializes the State paired with the initial widget. */
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
