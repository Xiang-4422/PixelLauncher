package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.launcher.SnakeModel.Cell
import com.purride.pixellauncherv2.launcher.SnakeModel.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SnakeModelTest {

    private val random = Random(7)

    @Test
    fun initialSnakeIsCenteredHeadFirstAndFoodOnFreeCell() {
        val state = SnakeModel.initial(cols = 20, rows = 20, random = random)

        assertEquals(SnakeModel.INITIAL_LENGTH, state.body.size)
        assertEquals(Cell(10, 10), state.body.first())
        assertEquals(Direction.RIGHT, state.direction)
        assertFalse(state.isGameOver)
        assertFalse(state.food in state.body)
    }

    @Test
    fun stepMovesHeadAndDropsTail() {
        val state = SnakeModel.initial(20, 20, random).copy(food = Cell(0, 0))

        val next = SnakeModel.step(state, random)

        assertEquals(Cell(11, 10), next.body.first())
        assertEquals(SnakeModel.INITIAL_LENGTH, next.body.size)
        assertEquals(0, next.score)
    }

    @Test
    fun eatingGrowsScoresAndRespawnsFood() {
        val state = SnakeModel.initial(20, 20, random).copy(food = Cell(11, 10))

        val next = SnakeModel.step(state, random)

        assertEquals(SnakeModel.INITIAL_LENGTH + 1, next.body.size)
        assertEquals(1, next.score)
        assertNotEquals(Cell(11, 10), next.food)
        assertFalse(next.food in next.body)
    }

    @Test
    fun turnRejectsReversalIncludingDoubleInputWithinOneTick() {
        val state = SnakeModel.initial(20, 20, random)

        // 直接回头被拒
        assertEquals(null, SnakeModel.turn(state, Direction.LEFT).pendingDirection)
        // 合法转向记入 pending
        val up = SnakeModel.turn(state, Direction.UP)
        assertEquals(Direction.UP, up.pendingDirection)
        // 同一帧内二次输入以 pending 为基准：UP 后 DOWN 属于回头，仍被拒
        assertEquals(Direction.UP, SnakeModel.turn(up, Direction.DOWN).pendingDirection)
        // UP 后 LEFT 合法，覆盖 pending
        assertEquals(Direction.LEFT, SnakeModel.turn(up, Direction.LEFT).pendingDirection)
    }

    @Test
    fun hittingWallEndsGame() {
        var state = SnakeModel.initial(8, 8, random).copy(food = Cell(0, 0))
        repeat(8) { state = SnakeModel.step(state, random) }

        assertTrue(state.isGameOver)
        // 终局后状态冻结
        assertEquals(state, SnakeModel.step(state, random))
        assertEquals(state, SnakeModel.turn(state, Direction.UP))
    }

    @Test
    fun hittingOwnBodyEndsGameButChasingTailIsLegal() {
        // 蛇长 5 转圈：追尾（下一格是即将腾出的尾格）合法
        val chasing = SnakeModel.State(
            cols = 8, rows = 8,
            body = listOf(Cell(3, 3), Cell(3, 4), Cell(4, 4), Cell(4, 3)),
            direction = Direction.RIGHT,
            food = Cell(0, 0),
        )
        val next = SnakeModel.step(chasing, random)
        assertFalse("追尾应合法：尾格本帧腾出", next.isGameOver)

        // 撞到非尾巴的身体：死
        val crashing = SnakeModel.State(
            cols = 8, rows = 8,
            body = listOf(Cell(3, 3), Cell(3, 4), Cell(4, 4), Cell(4, 3), Cell(4, 2), Cell(3, 2)),
            direction = Direction.RIGHT,
            food = Cell(0, 0),
        )
        assertTrue(SnakeModel.step(crashing, random).isGameOver)
    }

    @Test
    fun tickIntervalSpeedsUpWithFloor() {
        assertEquals(SnakeModel.BASE_TICK_MS, SnakeModel.tickIntervalMs(0))
        assertTrue(SnakeModel.tickIntervalMs(10) < SnakeModel.BASE_TICK_MS)
        assertEquals(SnakeModel.MIN_TICK_MS, SnakeModel.tickIntervalMs(1_000))
    }

    /** 长局随机游走：任何时刻不变量都成立（不重叠、在界内、食物不在身上）。 */
    @Test
    fun randomWalkKeepsInvariants() {
        var state = SnakeModel.initial(12, 12, random)
        val directions = Direction.entries
        repeat(500) { i ->
            if (state.isGameOver) {
                state = SnakeModel.initial(12, 12, random)
            }
            if (i % 3 == 0) {
                state = SnakeModel.turn(state, directions[random.nextInt(directions.size)])
            }
            state = SnakeModel.step(state, random)
            if (!state.isGameOver) {
                assertEquals("蛇身不得重叠", state.body.size, state.body.toSet().size)
                assertTrue(state.body.all { cell -> cell.x in 0 until 12 && cell.y in 0 until 12 })
                assertFalse(state.food in state.body)
            }
        }
    }
}
