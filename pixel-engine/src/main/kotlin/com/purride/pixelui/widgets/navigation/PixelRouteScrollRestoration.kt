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
 * 定义 `PixelRouteScrollRestoration` 在 `PixelRouteScrollRestoration` 中承担的数据与行为边界。
 *
 * Automatically preserves a List/Grid/CustomScrollView position inside one route entry.
 *
 * The saved value lives in the nearest [PixelNavigator]. A route that stays in the
 * navigator stack regains its scroll position when it becomes visible again. Popped or
 * replaced routes release their buckets after the transition settles.
 */
public class PixelRouteScrollRestoration(
    /** 提供 `PixelRouteScrollRestoration` 用于识别或兼容校验的 `restorationId` 值。 */
    public val restorationId: String,
    /** 保存 `PixelRouteScrollRestoration` 当前的 `state` 状态维度。 */
    public val state: PixelListState,
    /** 提供 `PixelRouteScrollRestoration` 执行 `controller` 职责时使用的协作者。 */
    public val controller: PixelListController,
    /** 提供 `PixelRouteScrollRestoration` 当前管理的 `child` 内容。 */
    public val child: Widget,
    /** 记录 `PixelRouteScrollRestoration` 的 `policy` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val policy: PixelListRestorationPolicy = PixelListRestorationPolicy.AbsoluteOffset,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    init {
        require(restorationId.isNotBlank()) { "restorationId must not be blank" }
    }

    override fun createState(): State<out StatefulWidget> = PixelRouteScrollRestorationState()
}

private class PixelRouteScrollRestorationState : State<PixelRouteScrollRestoration>() {
    /** Entry-local state bucket currently associated with this restoration widget. */
    private var activeBucket: PixelRouteStateBucket? = null

    /** Restoration identifier already applied to the current state instance. */
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
