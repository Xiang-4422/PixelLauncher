package com.purride.pixellockscreen.ui

import java.util.Arrays
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** 图案绘制区域在 Pixel Engine 逻辑网格中的固定几何。 */
internal data class PatternCredentialLayout(
    /** 完整场景逻辑宽度。 */
    val logicalWidth: Int,
    /** 完整场景逻辑高度。 */
    val logicalHeight: Int,
    /** 图案画布左边界。 */
    val patternLeft: Int,
    /** 图案画布上边界。 */
    val patternTop: Int,
    /** 主提示左边界。 */
    val promptLeft: Int,
    /** 主提示上边界。 */
    val promptTop: Int,
    /** 主提示宽度。 */
    val promptWidth: Int,
    /** 主提示高度。 */
    val promptHeight: Int,
    /** 反馈文字左边界。 */
    val feedbackLeft: Int,
    /** 反馈文字上边界。 */
    val feedbackTop: Int,
    /** 反馈文字宽度。 */
    val feedbackWidth: Int,
    /** 反馈文字高度。 */
    val feedbackHeight: Int,
    /** 紧急按钮左边界。 */
    val emergencyLeft: Int,
    /** 紧急按钮上边界。 */
    val emergencyTop: Int,
    /** 紧急按钮宽度。 */
    val emergencyWidth: Int,
    /** 紧急按钮高度。 */
    val emergencyHeight: Int,
) {
    /** 返回指定格子的全局逻辑横坐标。 */
    fun centerX(cellId: Int): Int = patternLeft + GRID_MARGIN + (cellId % GRID_SIDE) * GRID_STEP

    /** 返回指定格子的全局逻辑纵坐标。 */
    fun centerY(cellId: Int): Int = patternTop + GRID_MARGIN + (cellId / GRID_SIDE) * GRID_STEP

    /** 判断逻辑坐标是否位于紧急入口内。 */
    fun containsEmergency(logicalX: Int, logicalY: Int): Boolean =
        logicalX in emergencyLeft until emergencyLeft + emergencyWidth &&
            logicalY in emergencyTop until emergencyTop + emergencyHeight

    internal companion object {
        /** 九宫格单边数量。 */
        const val GRID_SIDE: Int = 3

        /** 图案画布边缘到第一排圆心的距离。 */
        const val GRID_MARGIN: Int = 13

        /** 相邻圆心的逻辑像素间距。 */
        const val GRID_STEP: Int = 26

        /** 图案画布逻辑边长。 */
        const val PATTERN_SIZE: Int = 78

        /** 圆心命中的逻辑半径。 */
        const val HIT_RADIUS: Int = 10
    }
}

/** 返回当前方向下不会裁切的图案认证布局。 */
internal fun patternCredentialLayout(isLandscape: Boolean): PatternCredentialLayout =
    if (isLandscape) {
        PatternCredentialLayout(
            logicalWidth = LOCKSCREEN_LANDSCAPE_WIDTH,
            logicalHeight = LOCKSCREEN_LANDSCAPE_HEIGHT,
            patternLeft = 76,
            patternTop = 9,
            promptLeft = 1,
            promptTop = 18,
            promptWidth = 74,
            promptHeight = 24,
            feedbackLeft = 1,
            feedbackTop = 52,
            feedbackWidth = 74,
            feedbackHeight = 24,
            emergencyLeft = 158,
            emergencyTop = 32,
            emergencyWidth = 32,
            emergencyHeight = 32,
        )
    } else {
        PatternCredentialLayout(
            logicalWidth = LOCKSCREEN_PORTRAIT_WIDTH,
            logicalHeight = LOCKSCREEN_PORTRAIT_HEIGHT,
            patternLeft = 9,
            patternTop = 57,
            promptLeft = 4,
            promptTop = 26,
            promptWidth = 88,
            promptHeight = 20,
            feedbackLeft = 4,
            feedbackTop = 143,
            feedbackWidth = 88,
            feedbackHeight = 24,
            emergencyLeft = 13,
            emergencyTop = 172,
            emergencyWidth = 70,
            emergencyHeight = 15,
        )
    }

/** 只允许渲染器按索引读取当前手势路径的内部接口。 */
internal interface PatternVisualPath {
    /** 当前路径长度。 */
    val size: Int

    /** 返回指定位置的格子编号。 */
    fun cellAt(index: Int): Int
}

/**
 * 平台无关的九宫格二维拖动跟踪器。
 *
 * 跟踪器只保存当前按下序列，并在完成或取消的 `finally` 中覆写全部路径槽位。
 */
internal class PatternGestureTracker(
    /** 当前方向的逻辑布局。 */
    private var layout: PatternCredentialLayout,
    /** 首枚格子命中回调。 */
    private val onStarted: () -> Unit,
    /** 新格子命中回调。 */
    private val onCellAdded: (Int) -> Unit,
    /** 有效路径抬起回调。 */
    private val onCompleted: (Int) -> Unit,
    /** 已开始路径取消回调。 */
    private val onCancelled: () -> Unit,
    /** 路径像素需要重绘的回调。 */
    private val onVisualChanged: () -> Unit,
) : PatternVisualPath {
    /** 按经过顺序保存的固定九格数组。 */
    private val cells: IntArray = IntArray(CELL_COUNT) { EMPTY_CELL }

    /** 当前有效格子数量。 */
    private var currentSize: Int = 0

    /** 是否处于一次尚未抬起的指针序列。 */
    private var tracking: Boolean = false

    /** 上一次逻辑横坐标，用于补采快速移动经过的格子。 */
    private var previousX: Int = 0

    /** 上一次逻辑纵坐标，用于补采快速移动经过的格子。 */
    private var previousY: Int = 0

    /** 当前路径长度。 */
    override val size: Int
        get() = currentSize

    /** 方向变化时更新几何并清除尚未完成的旧坐标手势。 */
    fun updateLayout(nextLayout: PatternCredentialLayout) {
        if (layout == nextLayout) {
            return
        }
        cancel()
        layout = nextLayout
    }

    /** 开始新的指针序列；落点不在格子内时仍允许后续移动进入。 */
    fun start(logicalX: Int, logicalY: Int) {
        if (tracking) {
            cancel()
        }
        clearPath()
        tracking = true
        previousX = logicalX
        previousY = logicalY
        addHitAt(logicalX, logicalY)
    }

    /** 沿当前移动线段逐逻辑像素采样，避免快速拖动漏掉中间格子。 */
    fun update(logicalX: Int, logicalY: Int) {
        if (!tracking) {
            return
        }
        /** 当前线段横向距离。 */
        val deltaX = logicalX - previousX
        /** 当前线段纵向距离。 */
        val deltaY = logicalY - previousY
        /** 保证每个逻辑像素至少采样一次的步数。 */
        val steps = max(abs(deltaX), abs(deltaY)).coerceAtLeast(1)
        for (step in 1..steps) {
            /** 当前插值比例。 */
            val progress = step.toFloat() / steps.toFloat()
            /** 当前采样横坐标。 */
            val sampleX = (previousX + deltaX * progress).roundToInt()
            /** 当前采样纵坐标。 */
            val sampleY = (previousY + deltaY * progress).roundToInt()
            addHitAt(sampleX, sampleY)
        }
        previousX = logicalX
        previousY = logicalY
    }

    /** 完成当前路径，并保证外部回调异常时仍立即清零。 */
    fun end() {
        if (!tracking) {
            return
        }
        tracking = false
        try {
            if (currentSize > 0) {
                onCompleted(currentSize)
            }
        } finally {
            clearPath()
            onVisualChanged()
        }
    }

    /** 取消当前路径，并保证外部回调异常时仍立即清零。 */
    fun cancel() {
        if (!tracking) {
            clearPath()
            return
        }
        tracking = false
        try {
            if (currentSize > 0) {
                onCancelled()
            }
        } finally {
            clearPath()
            onVisualChanged()
        }
    }

    /** 返回指定路径位置的格子编号。 */
    override fun cellAt(index: Int): Int {
        if (index !in 0 until currentSize) {
            throw IndexOutOfBoundsException("pattern_visual_cell_index")
        }
        return cells[index]
    }

    /** 检测当前坐标命中的最近格子，并按 Android 规则补齐跨格中点。 */
    private fun addHitAt(logicalX: Int, logicalY: Int) {
        /** 当前坐标命中的格子。 */
        val hitCell = hitCellAt(logicalX, logicalY) ?: return
        if (contains(hitCell)) {
            return
        }
        if (currentSize == 0) {
            onStarted()
        } else {
            /** 当前路径最后一格。 */
            val previousCell = cells[currentSize - 1]
            /** Android LockPatternView 跨两格时自动补齐的中间格。 */
            val gapCell = gapCellBetween(previousCell, hitCell)
            if (gapCell != null && !contains(gapCell)) {
                appendCell(gapCell)
            }
        }
        appendCell(hitCell)
    }

    /** 返回坐标命中的格子，命中区使用圆形而不是矩形。 */
    private fun hitCellAt(logicalX: Int, logicalY: Int): Int? {
        repeat(CELL_COUNT) { cellId ->
            /** 相对当前圆心的横向距离。 */
            val deltaX = logicalX - layout.centerX(cellId)
            /** 相对当前圆心的纵向距离。 */
            val deltaY = logicalY - layout.centerY(cellId)
            if (deltaX * deltaX + deltaY * deltaY <= HIT_RADIUS_SQUARED) {
                return cellId
            }
        }
        return null
    }

    /** 按 Android 图案锁规则计算水平、垂直或对角跨两格的中点。 */
    private fun gapCellBetween(previousCell: Int, nextCell: Int): Int? {
        /** 前一格行号。 */
        val previousRow = previousCell / GRID_SIDE
        /** 前一格列号。 */
        val previousColumn = previousCell % GRID_SIDE
        /** 新格行号。 */
        val nextRow = nextCell / GRID_SIDE
        /** 新格列号。 */
        val nextColumn = nextCell % GRID_SIDE
        /** 行差。 */
        val rowDelta = nextRow - previousRow
        /** 列差。 */
        val columnDelta = nextColumn - previousColumn
        if (abs(rowDelta) != 2 && abs(columnDelta) != 2) {
            return null
        }
        if (abs(rowDelta) == 2 && abs(columnDelta) == 1) {
            return null
        }
        if (abs(columnDelta) == 2 && abs(rowDelta) == 1) {
            return null
        }
        /** 跨两格路径的整数中点行号。 */
        val middleRow = previousRow + rowDelta / 2
        /** 跨两格路径的整数中点列号。 */
        val middleColumn = previousColumn + columnDelta / 2
        return middleRow * GRID_SIDE + middleColumn
    }

    /** 追加唯一格子并依次通知安全会话和渲染器。 */
    private fun appendCell(cellId: Int) {
        cells[currentSize] = cellId
        currentSize += 1
        onCellAdded(cellId)
        onVisualChanged()
    }

    /** 判断当前路径是否已经经过指定格子。 */
    private fun contains(cellId: Int): Boolean {
        repeat(currentSize) { index ->
            if (cells[index] == cellId) {
                return true
            }
        }
        return false
    }

    /** 覆写全部路径槽位。 */
    private fun clearPath() {
        Arrays.fill(cells, EMPTY_CELL)
        currentSize = 0
    }

    private companion object {
        /** 九宫格总格子数。 */
        const val CELL_COUNT: Int = 9

        /** 清零后的非格子哨兵。 */
        const val EMPTY_CELL: Int = -1

        /** 命中半径平方，避免每个移动采样执行开方。 */
        const val HIT_RADIUS_SQUARED: Int =
            PatternCredentialLayout.HIT_RADIUS * PatternCredentialLayout.HIT_RADIUS

        /** 九宫格单边数量。 */
        const val GRID_SIDE: Int = PatternCredentialLayout.GRID_SIDE
    }
}
