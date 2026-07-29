package com.purride.pixellauncherv2.launcher

import kotlin.random.Random

/**
 * 贪吃蛇的纯游戏逻辑：网格、移动、碰撞、进食、加速。
 *
 * 无 Android 依赖、无时间源——逻辑帧由控制器的 ticker 驱动，这里只做状态转移，
 * 全部规则可在 JVM 单测里穷举。
 */
object SnakeModel {

    /** 网格坐标（列 x、行 y，原点左上）。 */
    data class Cell(val x: Int, val y: Int)

    enum class Direction(val dx: Int, val dy: Int) {
        UP(0, -1),
        DOWN(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0),
        ;

        val opposite: Direction
            get() = when (this) {
                UP -> DOWN
                DOWN -> UP
                LEFT -> RIGHT
                RIGHT -> LEFT
            }
    }

    /**
     * 一局的完整状态。[body] 头在前尾在后；[pendingDirection] 是下一逻辑帧生效的
     * 转向——同一帧内连按多次只保留最后一次合法输入，避免快速连按穿过 180 度限制
     * （UP→LEFT→DOWN 两次输入在同一帧生效等于直接回头）。
     */
    data class State(
        val cols: Int,
        val rows: Int,
        val body: List<Cell>,
        val direction: Direction,
        val pendingDirection: Direction? = null,
        val food: Cell,
        val score: Int = 0,
        val isGameOver: Boolean = false,
    )

    /** 初始局面：蛇长 3、居中向右，食物落在空格。 */
    fun initial(cols: Int, rows: Int, random: Random): State {
        require(cols >= MIN_GRID && rows >= MIN_GRID) { "grid too small: ${cols}x$rows" }
        val headX = cols / 2
        val headY = rows / 2
        val body = List(INITIAL_LENGTH) { index -> Cell(headX - index, headY) }
        return State(
            cols = cols,
            rows = rows,
            body = body,
            direction = Direction.RIGHT,
            food = spawnFood(cols, rows, body, random),
        )
    }

    /**
     * 请求转向：与当前方向相反的输入忽略（蛇不能原地回头），
     * 其余记入 pending 等下一逻辑帧生效。
     */
    fun turn(state: State, direction: Direction): State {
        if (state.isGameOver) return state
        // 以"本帧实际将要行进的方向"为基准判定回头：pending 已存在时新输入相对 pending 判。
        val base = state.pendingDirection ?: state.direction
        if (direction == base || direction == base.opposite) return state
        return state.copy(pendingDirection = direction)
    }

    /** 推进一逻辑帧：前进 / 进食增长 / 撞墙或撞身即终局。 */
    fun step(state: State, random: Random): State {
        if (state.isGameOver) return state
        val direction = state.pendingDirection ?: state.direction
        val head = state.body.first()
        val next = Cell(head.x + direction.dx, head.y + direction.dy)
        val hitsWall = next.x !in 0 until state.cols || next.y !in 0 until state.rows
        // 尾格本帧会腾出（不吃食时），撞“即将离开的尾巴”不算死——经典规则。
        val bodyToCheck = if (next == state.food) state.body else state.body.dropLast(1)
        if (hitsWall || next in bodyToCheck) {
            return state.copy(isGameOver = true, direction = direction, pendingDirection = null)
        }
        val ate = next == state.food
        val newBody = buildList {
            add(next)
            addAll(if (ate) state.body else state.body.dropLast(1))
        }
        return state.copy(
            body = newBody,
            direction = direction,
            pendingDirection = null,
            food = if (ate) spawnFood(state.cols, state.rows, newBody, random) else state.food,
            score = if (ate) state.score + 1 else state.score,
        )
    }

    /** 逻辑帧间隔：随分数加速，有下限——无限加速最终必然不可玩。 */
    fun tickIntervalMs(score: Int): Long =
        (BASE_TICK_MS - score * SPEEDUP_PER_FOOD_MS).coerceAtLeast(MIN_TICK_MS)

    /** 在空格里随机落食物；满盘（通关）时返回蛇头位置占位，下一帧必然结束。 */
    private fun spawnFood(cols: Int, rows: Int, body: List<Cell>, random: Random): Cell {
        val occupied = body.toHashSet()
        val free = ArrayList<Cell>((cols * rows - occupied.size).coerceAtLeast(0))
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val cell = Cell(x, y)
                if (cell !in occupied) free.add(cell)
            }
        }
        if (free.isEmpty()) return body.first()
        return free[random.nextInt(free.size)]
    }

    const val INITIAL_LENGTH = 3
    const val MIN_GRID = 8
    const val BASE_TICK_MS = 160L
    const val SPEEDUP_PER_FOOD_MS = 3L
    const val MIN_TICK_MS = 70L
}
