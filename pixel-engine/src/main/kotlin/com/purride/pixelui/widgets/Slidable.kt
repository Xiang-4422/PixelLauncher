package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.MultiChildRenderObject
import com.purride.pixelui.internal.MultiChildRenderObjectWidget
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelRect
import com.purride.pixelui.internal.PixelRefreshTarget
import com.purride.pixelui.internal.PixelScrollbarTarget
import com.purride.pixelui.internal.PixelSemanticsTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSize
import kotlin.math.abs

/**
 * Slidable 被打开或 dismiss 的方向。
 */
public enum class SlidableDirection {
    /**
     * 向 start 侧打开，对应正向水平位移。
     */
    START,

    /**
     * 向 end 侧打开，对应负向水平位移。
     */
    END,
}

/**
 * action pane 跟随主内容滑动时的运动方式。
 */
public enum class SlidableMotion {
    /**
     * action pane 固定在内容后方。
     */
    BEHIND,

    /**
     * action pane 以抽屉效果半速跟随。
     */
    DRAWER,

    /**
     * action pane 和内容一起滚动进入。
     */
    SCROLL,
}

/**
 * Slidable 一侧的操作面板配置。
 *
 * [children] 会均分面板宽度；[extentRatio] 会钳位到 `0.1f..1.0f` 后换算为面板宽度。
 * 当 [dismissible] 为 true 且滑动距离达到 [dismissThreshold] 时，会触发外层 [Slidable]
 * 的 dismiss 回调。
 */
public data class SlidableActionPane(
    val children: List<Widget>,
    val extentRatio: Float = 0.35f,
    val motion: SlidableMotion = SlidableMotion.BEHIND,
    val dismissible: Boolean = false,
    val dismissThreshold: Float = 0.5f,
)

/**
 * 可水平滑出操作面板的像素行容器。
 *
 * 向右滑打开 [startActionPane]，向左滑打开 [endActionPane]。组件只管理当前滑动偏移和面板
 * 呈现，不会删除数据；需要删除或归档时在 [onDismissed] 中更新业务状态。
 */
public fun Slidable(
    child: Widget,
    startActionPane: SlidableActionPane? = null,
    endActionPane: SlidableActionPane? = null,
    onTap: (() -> Unit)? = null,
    onDismissed: ((SlidableDirection) -> Unit)? = null,
    key: Any? = null,
): Widget = SlidableWidget(
    child = child,
    startActionPane = startActionPane,
    endActionPane = endActionPane,
    onTap = onTap,
    onDismissed = onDismissed,
    key = key,
)

/**
 * Slidable action pane 内的单个像素按钮。
 */
public fun SlidableAction(
    label: String,
    backgroundColor: PixelColor,
    foregroundColor: PixelColor,
    onPressed: () -> Unit,
    key: Any? = null,
): Widget = GestureDetector(
    onTap = onPressed,
    child = Container(
        fillColor = backgroundColor,
        alignment = Alignment.CENTER,
        child = Text(
            label,
            style = TextStyle(color = foregroundColor),
            textAlign = TextAlign.CENTER,
            overflow = TextOverflow.ELLIPSIS,
            softWrap = false,
            maxLines = 1,
        ),
    ),
    key = key,
)

private class SlidableWidget(
    val child: Widget,
    val startActionPane: SlidableActionPane?,
    val endActionPane: SlidableActionPane?,
    val onTap: (() -> Unit)?,
    val onDismissed: ((SlidableDirection) -> Unit)?,
    key: Any?,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = SlidableState()
}

private class SlidableState : State<SlidableWidget>() {
    private var offsetPx = 0
    private var dragBaseOffsetPx = 0
    private var paneWidthPx = 0

    override fun didUpdateWidget(oldWidget: SlidableWidget) {
        if (widget.startActionPane == null && offsetPx > 0) offsetPx = 0
        if (widget.endActionPane == null && offsetPx < 0) offsetPx = 0
    }

    override fun build(context: BuildContext): Widget {
        paneWidthPx = estimatePaneWidth(MediaQuery.maybeOf(context)?.logicalWidth ?: 40)
        return GestureDetector(
            onTap = { widget.onTap?.invoke() },
            onSwipeStart = { dragBaseOffsetPx = offsetPx },
            onSwipeUpdate = { delta ->
                setState {
                    offsetPx = clampOffset(dragBaseOffsetPx + delta)
                }
            },
            onSwipeEnd = {
                setState { settleOffset() }
            },
            child = SlidableRenderWidget(
                startPane = widget.startActionPane?.toWidget(),
                endPane = widget.endActionPane?.toWidget(),
                child = widget.child,
                offsetPx = offsetPx,
                startExtentRatio = widget.startActionPane?.extentRatio ?: 0f,
                endExtentRatio = widget.endActionPane?.extentRatio ?: 0f,
                startMotion = widget.startActionPane?.motion ?: SlidableMotion.BEHIND,
                endMotion = widget.endActionPane?.motion ?: SlidableMotion.BEHIND,
            ),
            key = widget.key?.let { "$it:gesture" },
        )
    }

    private fun clampOffset(value: Int): Int {
        val startWidth = paneWidthPx.takeIf { widget.startActionPane != null } ?: 0
        val endWidth = paneWidthPx.takeIf { widget.endActionPane != null } ?: 0
        return value.coerceIn(-endWidth, startWidth)
    }

    private fun settleOffset() {
        val direction = when {
            offsetPx > 0 -> SlidableDirection.START
            offsetPx < 0 -> SlidableDirection.END
            else -> return
        }
        val pane = when (direction) {
            SlidableDirection.START -> widget.startActionPane
            SlidableDirection.END -> widget.endActionPane
        } ?: return
        val thresholdPx = (paneWidthPx * pane.dismissThreshold).toInt().coerceAtLeast(1)
        if (pane.dismissible && abs(offsetPx) >= thresholdPx) {
            widget.onDismissed?.invoke(direction)
            offsetPx = 0
            return
        }
        offsetPx = if (abs(offsetPx) >= thresholdPx) {
            if (direction == SlidableDirection.START) paneWidthPx else -paneWidthPx
        } else {
            0
        }
    }

    private fun SlidableActionPane.toWidget(): Widget {
        return Row(
            spacing = 0,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            children = children.map { action -> Expanded(child = action) },
        )
    }

    private fun estimatePaneWidth(width: Int): Int {
        val ratio = when {
            offsetPx > 0 -> widget.startActionPane?.extentRatio
            offsetPx < 0 -> widget.endActionPane?.extentRatio
            else -> widget.endActionPane?.extentRatio ?: widget.startActionPane?.extentRatio
        } ?: 0.35f
        return (width * ratio.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
    }
}

private class SlidableRenderWidget(
    private val startPane: Widget?,
    private val endPane: Widget?,
    private val child: Widget,
    private val offsetPx: Int,
    private val startExtentRatio: Float,
    private val endExtentRatio: Float,
    private val startMotion: SlidableMotion,
    private val endMotion: SlidableMotion,
) : MultiChildRenderObjectWidget(
    children = listOfNotNull(startPane, endPane, child),
) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderSlidable(
            hasStartPane = startPane != null,
            hasEndPane = endPane != null,
            offsetPx = offsetPx,
            startExtentRatio = startExtentRatio,
            endExtentRatio = endExtentRatio,
            startMotion = startMotion,
            endMotion = endMotion,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderSlidable).update(
            hasStartPane = startPane != null,
            hasEndPane = endPane != null,
            offsetPx = offsetPx,
            startExtentRatio = startExtentRatio,
            endExtentRatio = endExtentRatio,
            startMotion = startMotion,
            endMotion = endMotion,
        )
    }
}

private class RenderSlidable(
    private var hasStartPane: Boolean,
    private var hasEndPane: Boolean,
    private var offsetPx: Int,
    private var startExtentRatio: Float,
    private var endExtentRatio: Float,
    private var startMotion: SlidableMotion,
    private var endMotion: SlidableMotion,
) : MultiChildRenderObject() {
    private var paneWidthPx = 0

    fun update(
        hasStartPane: Boolean,
        hasEndPane: Boolean,
        offsetPx: Int,
        startExtentRatio: Float,
        endExtentRatio: Float,
        startMotion: SlidableMotion,
        endMotion: SlidableMotion,
    ) {
        if (
            this.hasStartPane == hasStartPane &&
            this.hasEndPane == hasEndPane &&
            this.offsetPx == offsetPx &&
            this.startExtentRatio == startExtentRatio &&
            this.endExtentRatio == endExtentRatio &&
            this.startMotion == startMotion &&
            this.endMotion == endMotion
        ) {
            return
        }
        this.hasStartPane = hasStartPane
        this.hasEndPane = hasEndPane
        this.offsetPx = offsetPx
        this.startExtentRatio = startExtentRatio
        this.endExtentRatio = endExtentRatio
        this.startMotion = startMotion
        this.endMotion = endMotion
        markNeedsLayout()
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        val child = childBox ?: run {
            size = RenderSize.Zero
            return
        }
        val width = constraints.maxWidth.coerceAtLeast(0)
        child.layout(
            RenderConstraints(
                minWidth = width,
                maxWidth = width,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            ),
        )
        size = RenderSize(
            width = constraints.constrainWidth(width),
            height = constraints.constrainHeight(child.size.height),
        )
        paneWidthPx = paneWidth()
        val paneConstraints = RenderConstraints(
            minWidth = paneWidthPx,
            maxWidth = paneWidthPx,
            minHeight = size.height,
            maxHeight = size.height,
        )
        startPaneBox?.layout(paneConstraints)
        endPaneBox?.layout(paneConstraints)
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val child = childBox ?: return
        val scratch = context.bufferPool.acquire(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        scratch.clear()
        try {
            val scratchContext = PaintContext(scratch, context.bufferPool)
            if (offsetPx > 0) {
                startPaneBox?.paint(scratchContext, startPaneX(), 0)
                scratch.fillRect(
                    offsetPx,
                    0,
                    size.width - offsetPx,
                    size.height,
                    PixelColor.Transparent,
                    com.purride.pixelcore.PixelBlendMode.Clear,
                )
            } else if (offsetPx < 0) {
                endPaneBox?.paint(scratchContext, endPaneX(), 0)
                scratch.fillRect(
                    0,
                    0,
                    size.width + offsetPx,
                    size.height,
                    PixelColor.Transparent,
                    com.purride.pixelcore.PixelBlendMode.Clear,
                )
            }
            child.paint(scratchContext, offsetPx, 0)
            context.buffer.blitRegion(scratch, 0, 0, size.width, size.height, offsetX, offsetY)
        } finally {
            context.bufferPool.release(scratch)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: com.purride.pixelui.internal.HitTestResult) {
        if (localX !in 0 until size.width || localY !in 0 until size.height) return
        if (offsetPx > 0) {
            startPaneBox?.hitTest(localX - startPaneX(), localY, result)
        } else if (offsetPx < 0) {
            endPaneBox?.hitTest(localX - endPaneX(), localY, result)
        }
        childBox?.hitTest(localX - offsetPx, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        collectPaneTargets(offsetX, offsetY, targets)
        childBox?.collectClickTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        childBox?.collectPagerTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        childBox?.collectListTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectScrollbarTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelScrollbarTarget>) {
        childBox?.collectScrollbarTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectRefreshTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelRefreshTarget>) {
        childBox?.collectRefreshTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        childBox?.collectTextInputTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        childBox?.collectSliderTargets(offsetX + offsetPx, offsetY, targets)
    }

    override fun collectSemantics(offsetX: Int, offsetY: Int, targets: MutableList<PixelSemanticsTarget>) {
        childBox?.collectSemantics(offsetX + offsetPx, offsetY, targets)
    }

    private fun collectPaneTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        if (offsetPx > 0) {
            startPaneBox?.collectClickTargets(offsetX + startPaneX(), offsetY, targets)
        } else if (offsetPx < 0) {
            endPaneBox?.collectClickTargets(offsetX + endPaneX(), offsetY, targets)
        }
    }

    private fun paneWidth(): Int {
        val ratio = when {
            offsetPx > 0 -> startExtentRatio
            offsetPx < 0 -> endExtentRatio
            hasEndPane -> endExtentRatio
            else -> startExtentRatio
        }
        return (size.width * ratio.coerceIn(0.1f, 1f)).toInt().coerceAtLeast(1)
    }

    private fun startPaneX(): Int = when (startMotion) {
        SlidableMotion.BEHIND -> 0
        SlidableMotion.DRAWER -> (offsetPx - paneWidthPx) / 2
        SlidableMotion.SCROLL -> offsetPx - paneWidthPx
    }

    private fun endPaneX(): Int = when (endMotion) {
        SlidableMotion.BEHIND -> size.width - paneWidthPx
        SlidableMotion.DRAWER -> size.width + (offsetPx + paneWidthPx) / 2
        SlidableMotion.SCROLL -> size.width + offsetPx
    }

    private val startPaneBox: RenderBox?
        get() = if (hasStartPane) children.getOrNull(0) as? RenderBox else null

    private val endPaneBox: RenderBox?
        get() {
            val index = when {
                hasStartPane && hasEndPane -> 1
                !hasStartPane && hasEndPane -> 0
                else -> return null
            }
            return children.getOrNull(index) as? RenderBox
        }

    private val childBox: RenderBox?
        get() = children.lastOrNull() as? RenderBox
}
