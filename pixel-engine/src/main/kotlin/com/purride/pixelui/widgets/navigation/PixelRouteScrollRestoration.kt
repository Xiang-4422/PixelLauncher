package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.BuildContext
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Widget
import com.purride.pixelui.dependOnInheritedWidgetOfExactType
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListRestorationPolicy
import com.purride.pixelui.state.PixelListState

/**
 * Automatically preserves a List/Grid/CustomScrollView position inside one route entry.
 *
 * The saved value lives in the nearest [PixelNavigator]. A route that stays in the
 * navigator stack regains its scroll position when it becomes visible again. Popped or
 * replaced routes release their buckets after the transition settles.
 */
public class PixelRouteScrollRestoration(
    public val restorationId: String,
    public val state: PixelListState,
    public val controller: PixelListController,
    public val child: Widget,
    public val policy: PixelListRestorationPolicy = PixelListRestorationPolicy.AbsoluteOffset,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    init {
        require(restorationId.isNotBlank()) { "restorationId must not be blank" }
    }

    override fun createState(): State<out StatefulWidget> = PixelRouteScrollRestorationState()
}

private class PixelRouteScrollRestorationState : State<PixelRouteScrollRestoration>() {
    private var activeBucket: PixelRouteRestorationBucket? = null
    private var restoredId: String? = null

    override fun didUpdateWidget(oldWidget: PixelRouteScrollRestoration) {
        if (
            oldWidget.restorationId != widget.restorationId ||
            oldWidget.state !== widget.state ||
            oldWidget.controller !== widget.controller
        ) {
            activeBucket?.writeScrollState(
                oldWidget.restorationId,
                oldWidget.controller.saveState(oldWidget.state),
            )
            restoredId = null
        }
    }

    override fun build(context: BuildContext): Widget {
        val scope = context.dependOnInheritedWidgetOfExactType<PixelRouteStorageScope>()
            ?: error("PixelRouteScrollRestoration requires a PixelNavigator ancestor")
        if (activeBucket !== scope.bucket) {
            activeBucket = scope.bucket
            restoredId = null
        }
        if (restoredId != widget.restorationId) {
            scope.bucket.readScrollState(widget.restorationId)?.let { savedState ->
                widget.controller.scheduleRestoreState(
                    state = widget.state,
                    savedState = savedState,
                    policy = widget.policy,
                )
            }
            restoredId = widget.restorationId
        }
        return widget.child
    }

    override fun dispose() {
        activeBucket?.writeScrollState(
            widget.restorationId,
            widget.controller.saveState(widget.state),
        )
    }
}
