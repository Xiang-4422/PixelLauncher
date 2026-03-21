package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleFluidEngineTest {

    private val engine = IdleFluidEngine(seed = 7)

    @Test
    fun targetLitCountUsesStrictCoverageMapping() {
        val width = 10
        val height = 10
        assertEquals(0, engine.targetLitCount(0, width, height))
        assertEquals(15, engine.targetLitCount(15, width, height))
        assertEquals(50, engine.targetLitCount(50, width, height))
        assertEquals(100, engine.targetLitCount(100, width, height))
    }

    @Test
    fun targetVisibleCellCountCapsAtHalfScreenWhenFullyCharged() {
        val width = 10
        val height = 10

        assertEquals(0, engine.targetVisibleCellCount(0, width, height))
        assertEquals(25, engine.targetVisibleCellCount(50, width, height))
        assertEquals(50, engine.targetVisibleCellCount(100, width, height))
    }

    @Test
    fun syncToBatteryProducesApproximateVisibleCoverage() {
        val state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 50,
            simulationWidth = 20,
            simulationHeight = 10,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )

        assertEquals(200, state.coverageField.size)
        val litCount = state.litMask.count { it }
        assertEquals(50, litCount)
    }

    @Test
    fun syncToBatteryHandlesTinyLogicalSizeWithoutCrashing() {
        val state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 100,
            simulationWidth = 1,
            simulationHeight = 1,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )

        assertEquals(1, state.width)
        assertEquals(1, state.height)
        assertEquals(1, state.coverageField.size)
        assertEquals(1, state.litMask.size)
    }

    @Test
    fun stepKeepsVisibleCoverageInReasonableRangeAcrossLongRun() {
        var state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 50,
            simulationWidth = 32,
            simulationHeight = 32,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )
        repeat(300) { frame ->
            state = engine.step(
                state = state,
                simulationWidth = 32,
                simulationHeight = 32,
                gravityX = 0f,
                gravityY = 9.81f,
                deltaSeconds = 0.016f,
                nowUptimeMs = 1_016L + (frame * 16L),
            )
            val litCount = state.litMask.count { it }
            assertEquals(engine.targetVisibleCellCount(50, 32, 32), litCount)
        }
    }

    @Test
    fun centroidMovesTowardGravityWithinHalfSecond() {
        val width = 40
        val height = 40
        var state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 50,
            simulationWidth = width,
            simulationHeight = height,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )
        val initialCentroidX = centroidX(state.litMask, width)

        repeat(30) { frame ->
            state = engine.step(
                state = state,
                simulationWidth = width,
                simulationHeight = height,
                gravityX = 9.81f,
                gravityY = 0f,
                deltaSeconds = 0.016f,
                nowUptimeMs = 1_016L + (frame * 16L),
            )
        }

        val movedCentroidX = centroidX(state.litMask, width)
        assertTrue(movedCentroidX > initialCentroidX)
    }

    @Test
    fun disturbanceDecaysWithoutLongTail() {
        var state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 50,
            simulationWidth = 24,
            simulationHeight = 24,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )
        state = engine.applyDisturbance(
            state = state,
            accelX = 4f,
            accelY = 0f,
            nowUptimeMs = 1_000L,
        )
        assertTrue(state.disturbanceX > 0f)
        assertEquals(0f, state.disturbanceY, 1e-4f)

        state = engine.step(
            state = state,
            simulationWidth = 24,
            simulationHeight = 24,
            gravityX = 0f,
            gravityY = 9.81f,
            deltaSeconds = 0.016f,
            nowUptimeMs = 1_000L + IdleFluidEngine.disturbanceDurationMs + 16L,
        )
        assertEquals(0L, state.disturbanceUntilUptimeMs)
        assertEquals(0f, state.disturbanceX, 1e-4f)
        assertEquals(0f, state.disturbanceY, 1e-4f)
    }

    @Test
    fun gravityScaleTracksHeightAndStaysClamped() {
        assertEquals(0.90f, engine.resolveGravityScaleForHeight(10), 1e-4f)
        assertEquals(1.00f, engine.resolveGravityScaleForHeight(64), 1e-4f)
        assertEquals(1.40f, engine.resolveGravityScaleForHeight(200), 1e-4f)
    }

    @Test
    fun dynamicDampingRespondsToDisturbanceAndAllowsSmallGravityContribution() {
        val noGravityNoDisturbance = engine.dynamicDampingForTesting(
            gravityMagnitude = 0f,
            disturbanceMagnitude = 0f,
        )
        val earthGravityNoDisturbance = engine.dynamicDampingForTesting(
            gravityMagnitude = 9.81f,
            disturbanceMagnitude = 0f,
        )
        val noGravityHighDisturbance = engine.dynamicDampingForTesting(
            gravityMagnitude = 0f,
            disturbanceMagnitude = 1.8f,
        )

        assertTrue(earthGravityNoDisturbance >= noGravityNoDisturbance)
        assertTrue(noGravityHighDisturbance > noGravityNoDisturbance)
    }

    @Test
    fun buildLitMaskKeepsTargetVisibleCellCountStable() {
        val nextMask = engine.buildLitMaskForTesting(
            coverageField = floatArrayOf(0.28f, 0.28f, 0.28f, 0.31f, 0.25f),
            previousMask = booleanArrayOf(false, true, true, false, true),
            previousCoverageField = floatArrayOf(0.26f, 0.30f, 0.27f, 0.29f, 0.27f),
            targetVisibleCellCount = 2,
        )

        assertEquals(2, nextMask.count { it })
        assertTrue(nextMask[1] || nextMask[2] || nextMask[3])
    }

    @Test
    fun centroidShiftsAtLeastTenPercentWidthWithinPointFiveToPointSevenSeconds() {
        val width = 40
        val height = 40
        val minExpectedShift = width * 0.10f
        var state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 50,
            simulationWidth = width,
            simulationHeight = height,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )
        val initialCentroidX = centroidX(state.litMask, width)

        repeat(38) { frame ->
            state = engine.step(
                state = state,
                simulationWidth = width,
                simulationHeight = height,
                gravityX = 6.94f,
                gravityY = 6.94f,
                deltaSeconds = 0.016f,
                nowUptimeMs = 1_016L + (frame * 16L),
            )
        }

        val movedCentroidX = centroidX(state.litMask, width)
        assertTrue((movedCentroidX - initialCentroidX) >= minExpectedShift)
    }

    @Test
    fun steadySizeSimulationReusesCoverageAndMaskBuffers() {
        var state = engine.syncToBattery(
            state = IdleFluidState(),
            batteryLevel = 65,
            simulationWidth = 30,
            simulationHeight = 30,
            gravityX = 0f,
            gravityY = 9.81f,
            nowUptimeMs = 1_000L,
        )
        val initialCoverageResizes = engine.coverageBufferResizeCountForTesting()
        val initialMaskResizes = engine.maskBufferResizeCountForTesting()

        repeat(60) { frame ->
            state = engine.step(
                state = state,
                simulationWidth = 30,
                simulationHeight = 30,
                gravityX = 0f,
                gravityY = 9.81f,
                deltaSeconds = 0.016f,
                nowUptimeMs = 1_016L + (frame * 16L),
            )
        }

        assertEquals(initialCoverageResizes, engine.coverageBufferResizeCountForTesting())
        assertEquals(initialMaskResizes, engine.maskBufferResizeCountForTesting())
    }

    private fun centroidX(mask: BooleanArray, width: Int): Float {
        if (mask.isEmpty() || width <= 0) {
            return 0f
        }
        var weighted = 0
        var total = 0
        mask.forEachIndexed { index, lit ->
            if (!lit) {
                return@forEachIndexed
            }
            weighted += (index % width)
            total += 1
        }
        return if (total <= 0) 0f else (weighted.toFloat() / total.toFloat())
    }
}
