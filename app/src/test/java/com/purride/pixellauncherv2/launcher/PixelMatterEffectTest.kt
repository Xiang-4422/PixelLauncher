package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

class PixelMatterEffectTest {

    @Test
    fun shakeDetector_ignoresOrdinaryMotion() {
        val detector = PixelMatterShakeDetector()

        repeat(10) { index ->
            val triggered = detector.record(
                motion(linearX = 2f, timestampNanos = index * 100_000_000L + 1L),
            )
            assertFalse(triggered)
        }
    }

    @Test
    fun shakeDetector_triggersOnStrongWindowWithoutCooldown() {
        val detector = PixelMatterShakeDetector()

        assertFalse(detector.record(motion(linearX = 8.8f, timestampNanos = 100_000_000L)))
        assertFalse(detector.record(motion(linearX = 13.8f, timestampNanos = 200_000_000L)))
        assertTrue(detector.record(motion(linearX = 9.0f, timestampNanos = 300_000_000L)))

        assertFalse(detector.record(motion(linearX = 8.8f, timestampNanos = 500_000_000L)))
        assertFalse(detector.record(motion(linearX = 13.8f, timestampNanos = 600_000_000L)))
        assertTrue(detector.record(motion(linearX = 9.0f, timestampNanos = 700_000_000L)))
    }

    @Test
    fun controller_restartedEffectTicksImmediately() {
        val scheduler = ManualFrameScheduler()
        var frameCount = 0
        val controller = PixelMatterController(
            vsync = PixelTickerProvider(scheduler),
            onFrame = { frameCount += 1 },
            random = Random(4),
        )
        val buffer = PixelBuffer(width = 4, height = 4).apply {
            setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        }

        assertTrue(controller.start(PixelMatterEffectMode.SAND, buffer, motion(linearX = 20f)))
        scheduler.advanceFrame(1_000_000_000L)
        scheduler.advanceFrame(1_100_000_000L)
        controller.clear()

        assertTrue(controller.start(PixelMatterEffectMode.SAND, buffer, motion(linearX = 20f)))
        val framesAfterRestart = frameCount
        scheduler.advanceFrame(2_000_000_000L)

        assertEquals(framesAfterRestart + 1, frameCount)
    }

    @Test
    fun controller_activeShakeAppliesImpulseWithoutRebuildingSimulation() {
        val scheduler = ManualFrameScheduler()
        val controller = PixelMatterController(
            vsync = PixelTickerProvider(scheduler),
            onFrame = {},
            random = Random(16),
        )
        val buffer = PixelBuffer(width = 6, height = 3).apply {
            setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        }

        assertTrue(controller.start(PixelMatterEffectMode.WATER, buffer, motion()))
        val simulationBeforeShake = requireNotNull(controller.simulation)

        assertTrue(controller.applyShakeImpulse(motion(linearX = 80f)))
        scheduler.advanceFrame(1_000_000_000L)

        assertSame(simulationBeforeShake, controller.simulation)
        assertEquals(PixelMatterEffectPhase.ACTIVE, controller.phase)
    }

    @Test
    fun controller_doesNotAutoRestoreWhenStill() {
        val scheduler = ManualFrameScheduler()
        val controller = PixelMatterController(
            vsync = PixelTickerProvider(scheduler),
            onFrame = {},
            random = Random(5),
        )
        val buffer = PixelBuffer(width = 4, height = 4).apply {
            setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        }

        assertTrue(controller.start(PixelMatterEffectMode.SMOKE, buffer, motion()))
        repeat(400) { index ->
            scheduler.advanceFrame(1_000_000_000L + index * 40_000_000L)
        }

        assertEquals(PixelMatterEffectPhase.ACTIVE, controller.phase)
        assertTrue(controller.requestRestore())
        assertEquals(PixelMatterEffectPhase.RESTORING, controller.phase)
    }

    @Test
    fun capture_usesOnlyVisiblePixelsAndFlattensTranslucentColors() {
        val background = PixelColor.fromRgb(16, 16, 16)
        val buffer = PixelBuffer(width = 4, height = 1)
        buffer.setPixel(0, 0, PixelColor.fromArgb(0, 255, 255, 255))
        buffer.setPixel(1, 0, PixelColor.fromArgb(1, 255, 255, 255))
        buffer.setPixel(2, 0, PixelColor.fromArgb(80, 16, 16, 16))
        buffer.setPixel(3, 0, PixelColor.fromRgb(220, 40, 40))

        val seed = requireNotNull(
            PixelMatterCapture.capture(
                buffer = buffer,
                backgroundColor = background.argb,
            ),
        )

        assertEquals(2, seed.particleCount)
        assertEquals(1, seed.originX[0])
        assertEquals(0, seed.originY[0])
        assertEquals(3, seed.originX[1])
        assertEquals(0xFF111111.toInt(), seed.colors[0])
        assertEquals(PixelColor.fromRgb(220, 40, 40).argb, seed.colors[1])
    }

    @Test
    fun capture_skipsConfiguredBackgroundPaletteColors() {
        val offPixel = PixelColor.fromRgb(30, 41, 59)
        val panel = PixelColor.fromRgb(17, 24, 39)
        val panelSubtle = PixelColor.fromRgb(22, 32, 51)
        val bezel = PixelColor.fromRgb(11, 16, 32)
        val text = PixelColor.fromRgb(248, 250, 252)
        val buffer = PixelBuffer(width = 5, height = 1)
        buffer.setPixel(0, 0, offPixel)
        buffer.setPixel(1, 0, panel)
        buffer.setPixel(2, 0, panelSubtle)
        buffer.setPixel(3, 0, bezel)
        buffer.setPixel(4, 0, text)

        val seed = requireNotNull(
            PixelMatterCapture.capture(
                buffer = buffer,
                backgroundColor = offPixel.argb,
                ignoredBackgroundColors = intArrayOf(
                    offPixel.argb,
                    panel.argb,
                    panelSubtle.argb,
                    bezel.argb,
                ),
            ),
        )

        assertEquals(1, seed.particleCount)
        assertEquals(4, seed.originX[0])
        assertEquals(text.argb, seed.colors[0])
    }

    @Test
    fun motionMapper_usesGravityOnlyForParticlePhysics() {
        val withLinearAcceleration = PixelMatterMotionMapper.toScreenAcceleration(
            motion(gravityX = 1f, gravityY = 1f, linearX = 50f, linearY = -50f),
        )
        val withoutLinearAcceleration = PixelMatterMotionMapper.toScreenAcceleration(
            motion(gravityX = 1f, gravityY = 1f),
        )

        assertEquals(withoutLinearAcceleration.x, withLinearAcceleration.x, 0.001f)
        assertEquals(withoutLinearAcceleration.y, withLinearAcceleration.y, 0.001f)
        assertTrue("x=${withLinearAcceleration.x}", withLinearAcceleration.x > 0f)
        assertTrue("y=${withLinearAcceleration.y}", withLinearAcceleration.y > 0f)
    }

    @Test
    fun handInputMapper_mapsFrontCameraLandmarksToLogicalPixelsAndDetectsPinch() {
        val xs = FloatArray(21) { 0.5f }
        val ys = FloatArray(21) { 0.5f }
        xs[4] = 0.30f
        ys[4] = 0.50f
        xs[8] = 0.25f
        ys[8] = 0.50f

        val snapshot = requireNotNull(
            PixelMatterHandInputMapper.fromNormalizedLandmarks(
                normalizedX = xs,
                normalizedY = ys,
                timestampMs = 100L,
                logicalWidth = 11,
                logicalHeight = 21,
                confidence = 0.92f,
                mirrorX = true,
            ),
        )

        assertTrue(snapshot.isPinching)
        assertEquals(7.5f, snapshot.indexTip.x, 0.001f)
        assertEquals(10f, snapshot.indexTip.y, 0.001f)
        assertEquals(7.25f, snapshot.pinchCenter.x, 0.001f)
    }

    @Test
    fun handInputMapper_smoothsFreshMotionAndRejectsStaleConfidence() {
        val firstXs = FloatArray(21) { 0.45f }
        val firstYs = FloatArray(21) { 0.50f }
        firstXs[8] = 0.45f
        firstYs[8] = 0.50f
        firstXs[4] = 0.70f
        firstYs[4] = 0.50f
        val first = requireNotNull(
            PixelMatterHandInputMapper.fromNormalizedLandmarks(
                normalizedX = firstXs,
                normalizedY = firstYs,
                timestampMs = 100L,
                logicalWidth = 101,
                logicalHeight = 101,
                confidence = 0.9f,
                mirrorX = false,
            ),
        )
        val movedXs = firstXs.copyOf()
        movedXs[8] = 0.85f
        for (palmIndex in intArrayOf(0, 5, 9, 13, 17)) {
            movedXs[palmIndex] = 0.55f
        }
        val moved = requireNotNull(
            PixelMatterHandInputMapper.fromNormalizedLandmarks(
                normalizedX = movedXs,
                normalizedY = firstYs,
                timestampMs = 150L,
                logicalWidth = 101,
                logicalHeight = 101,
                confidence = 0.9f,
                previous = first,
                mirrorX = false,
            ),
        )

        assertTrue("x=${moved.indexTip.x}", moved.indexTip.x in 45.1f..84.9f)
        assertTrue(moved.palmVelocity.x > 0f)
        assertFalse(first.isFresh(nowMs = 500L))
        assertEquals(
            null,
            PixelMatterHandInputMapper.fromNormalizedLandmarks(
                normalizedX = movedXs,
                normalizedY = firstYs,
                timestampMs = 200L,
                logicalWidth = 101,
                logicalHeight = 101,
                confidence = 0.1f,
            ),
        )
    }

    @Test
    fun handForce_repelsNormallyAndAttractsWhenPinching() {
        val openHand = handSnapshot(
            indexX = 5f,
            indexY = 5f,
            pinchX = 5f,
            pinchY = 5f,
            isPinching = false,
        )
        val pinchingHand = openHand.copy(isPinching = true)

        val repel = PixelMatterHandForces.forceAt(
            x = 7f,
            y = 5f,
            input = openHand,
            radius = 5f,
            repelStrength = 10f,
            attractStrength = 10f,
            windStrength = 0f,
        )
        val attract = PixelMatterHandForces.forceAt(
            x = 7f,
            y = 5f,
            input = pinchingHand,
            radius = 5f,
            repelStrength = 10f,
            attractStrength = 10f,
            windStrength = 0f,
        )

        assertTrue(repel.x > 0f)
        assertTrue(attract.x < 0f)
    }

    @Test
    fun handInputMapper_classifiesCommonGestures() {
        val cases = listOf(
            PixelMatterHandGesture.OPEN_PALM to syntheticHandLandmarks(
                thumb = true,
                index = true,
                middle = true,
                ring = true,
                pinky = true,
            ),
            PixelMatterHandGesture.CLOSED_FIST to syntheticHandLandmarks(),
            PixelMatterHandGesture.POINTING_UP to syntheticHandLandmarks(index = true),
            PixelMatterHandGesture.VICTORY to syntheticHandLandmarks(index = true, middle = true),
            PixelMatterHandGesture.THUMB_UP to syntheticHandLandmarks(thumb = true, thumbDown = false),
            PixelMatterHandGesture.THUMB_DOWN to syntheticHandLandmarks(thumb = true, thumbDown = true),
            PixelMatterHandGesture.I_LOVE_YOU to syntheticHandLandmarks(thumb = true, index = true, pinky = true),
            PixelMatterHandGesture.PINCH to syntheticHandLandmarks(thumb = true, index = true, pinch = true),
        )

        for ((expected, landmarks) in cases) {
            val snapshot = requireNotNull(
                PixelMatterHandInputMapper.fromNormalizedLandmarks(
                    normalizedX = landmarks.first,
                    normalizedY = landmarks.second,
                    timestampMs = 1L,
                    logicalWidth = 100,
                    logicalHeight = 100,
                    confidence = 1f,
                    mirrorX = false,
                ),
            )

            assertEquals(expected, snapshot.gesture)
        }
    }

    @Test
    fun sandSimulation_movesDiagonallyAndPreservesParticles() {
        val simulation = simulationFor(PixelMatterEffectMode.SAND, denseBlockBuffer(width = 7, height = 7))

        simulation.forceRestoreToOrigin()
        repeat(80) {
            simulation.step(1f / 30f, motion(gravityX = 20f, gravityY = 20f))
        }

        assertEquals(simulation.particleCount, occupiedPositions(simulation).size)
        val movedDiagonally = (0 until simulation.particleCount).any { index ->
            val (originX, originY) = simulation.originPosition(index)
            val (x, y) = simulation.particlePosition(index)
            x.roundToInt() > originX && y.roundToInt() > originY
        }
        assertTrue(movedDiagonally)
    }

    @Test
    fun sandSimulation_runtimeShakeFluidizesDenseBlockInsteadOfRigidTranslation() {
        val simulation = simulationFor(PixelMatterEffectMode.SAND, denseBlockBuffer(width = 10, height = 7))

        simulation.forceRestoreToOrigin()
        simulation.applyImpulse(motion(linearX = 80f, linearY = 45f), Random(23))
        repeat(28) {
            simulation.step(1f / 30f, motion(gravityX = 18f, gravityY = 14f))
        }

        val offsets = particleOffsets(simulation)
        assertTrue("offsets=$offsets", offsets.size >= 5)
        assertEquals(simulation.particleCount, occupiedPositions(simulation).size)
    }

    @Test
    fun waterSimulation_flowsHorizontallyAndPreservesParticles() {
        val buffer = PixelBuffer(width = 8, height = 1).apply {
            setPixel(1, 0, PixelColor.fromRgb(0, 140, 255))
            setPixel(2, 0, PixelColor.fromRgb(0, 140, 255))
            setPixel(3, 0, PixelColor.fromRgb(0, 140, 255))
        }
        val simulation = simulationFor(PixelMatterEffectMode.WATER, buffer)

        simulation.forceRestoreToOrigin()
        repeat(80) {
            simulation.step(1f / 30f, motion(gravityX = 20f))
        }

        val columns = (0 until simulation.particleCount)
            .map { index -> simulation.particlePosition(index).first.roundToInt() }
            .sorted()
        assertEquals(listOf(5, 6, 7), columns)
        assertEquals(simulation.particleCount, occupiedPositions(simulation).size)
    }

    @Test
    fun waterSimulation_respondsToHandRepulsion() {
        val buffer = PixelBuffer(width = 8, height = 5).apply {
            setPixel(3, 2, PixelColor.fromRgb(0, 140, 255))
        }
        val simulation = simulationFor(PixelMatterEffectMode.WATER, buffer)

        simulation.forceRestoreToOrigin()
        repeat(40) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(indexX = 1.5f, indexY = 2f, pinchX = 1.5f, pinchY = 2f),
            )
        }

        val (x, _) = simulation.particlePosition(0)
        assertTrue("x=$x", x.roundToInt() > 3)
        assertEquals(simulation.particleCount, occupiedPositions(simulation).size)
    }

    @Test
    fun smokeSimulation_diffusesWithoutGravityControlAndRendersTranslucently() {
        val buffer = PixelBuffer(width = 5, height = 5).apply {
            setPixel(2, 3, PixelColor.fromRgb(220, 220, 220))
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer, snapshot = motion(gravityY = 20f))

        simulation.forceRestoreToOrigin()
        repeat(20) {
            simulation.step(1f / 30f, motion(gravityY = 20f, linearX = 80f, linearY = -80f))
        }

        val (x, y) = simulation.particlePosition(0)
        assertTrue("x=$x y=$y", x != 2f || y != 3f)
        val rendered = PixelBuffer(width = 5, height = 5).also(simulation::drawTo)
        val renderedColor = rendered.pixels.first { color -> ((color ushr 24) and 0xFF) > 0 }
        assertTrue(((renderedColor ushr 24) and 0xFF) < 255)
    }

    @Test
    fun smokeSimulation_respondsToHandWind() {
        val buffer = PixelBuffer(width = 9, height = 5).apply {
            setPixel(4, 2, PixelColor.fromRgb(220, 220, 220))
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer)

        simulation.forceRestoreToOrigin()
        repeat(20) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(
                    indexX = 4f,
                    indexY = 2f,
                    pinchX = 4f,
                    pinchY = 2f,
                    palmVelocityX = 80f,
                ),
            )
        }

        val (x, _) = simulation.particlePosition(0)
        assertTrue("x=$x", x > 4f)
    }

    @Test
    fun smokeSimulation_pinchingGathersWholeCloud() {
        val buffer = PixelBuffer(width = 15, height = 9).apply {
            setPixel(1, 1, PixelColor.fromRgb(220, 220, 220))
            setPixel(1, 7, PixelColor.fromRgb(220, 220, 220))
            setPixel(13, 1, PixelColor.fromRgb(220, 220, 220))
            setPixel(13, 7, PixelColor.fromRgb(220, 220, 220))
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer)
        simulation.forceRestoreToOrigin()
        val targetX = 7f
        val targetY = 4f
        val before = averageDistanceTo(simulation, targetX, targetY)

        repeat(16) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(
                    indexX = targetX,
                    indexY = targetY,
                    pinchX = targetX,
                    pinchY = targetY,
                    isPinching = true,
                ),
            )
        }

        val after = averageDistanceTo(simulation, targetX, targetY)
        assertTrue("before=$before after=$after", after < before * 0.72f)
    }

    @Test
    fun smokeSimulation_pinchingDragMovesWholeCloud() {
        val buffer = PixelBuffer(width = 15, height = 7).apply {
            for (y in 2..4) {
                for (x in 6..8) {
                    setPixel(x, y, PixelColor.fromRgb(220, 220, 220))
                }
            }
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer)
        simulation.forceRestoreToOrigin()
        val beforeX = averageX(simulation)

        repeat(12) { frame ->
            val handX = 7f + frame * 0.22f
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(
                    indexX = handX,
                    indexY = 3f,
                    pinchX = handX,
                    pinchY = 3f,
                    isPinching = true,
                    palmVelocityX = 90f,
                ),
            )
        }

        val afterX = averageX(simulation)
        assertTrue("before=$beforeX after=$afterX", afterX > beforeX + 0.85f)
    }

    @Test
    fun smokeSimulation_openHandDispersesWholeCloud() {
        val buffer = PixelBuffer(width = 15, height = 9).apply {
            for (y in 3..5) {
                for (x in 6..8) {
                    setPixel(x, y, PixelColor.fromRgb(220, 220, 220))
                }
            }
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer)
        simulation.forceRestoreToOrigin()
        val centerX = averageX(simulation)
        val centerY = averageY(simulation)
        val before = averageDistanceTo(simulation, centerX, centerY)

        repeat(12) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(indexX = centerX, indexY = centerY, pinchX = centerX, pinchY = centerY),
            )
        }

        val after = averageDistanceTo(simulation, centerX, centerY)
        assertTrue("before=$before after=$after", after > before + 0.55f)
    }

    @Test
    fun smokeSimulation_fistCrushesCloud() {
        val buffer = PixelBuffer(width = 15, height = 9).apply {
            setPixel(2, 2, PixelColor.fromRgb(220, 220, 220))
            setPixel(2, 6, PixelColor.fromRgb(220, 220, 220))
            setPixel(12, 2, PixelColor.fromRgb(220, 220, 220))
            setPixel(12, 6, PixelColor.fromRgb(220, 220, 220))
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer)
        simulation.forceRestoreToOrigin()
        val targetX = 7f
        val targetY = 4f
        val before = averageDistanceTo(simulation, targetX, targetY)

        repeat(10) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(
                    indexX = targetX,
                    indexY = targetY,
                    pinchX = targetX,
                    pinchY = targetY,
                    gesture = PixelMatterHandGesture.CLOSED_FIST,
                ),
            )
        }

        val after = averageDistanceTo(simulation, targetX, targetY)
        assertTrue("before=$before after=$after", after < before * 0.70f)
    }

    @Test
    fun smokeSimulation_thumbGesturesDriveVerticalFlow() {
        val up = simulationFor(PixelMatterEffectMode.SMOKE, smokeClusterBuffer())
        val down = simulationFor(PixelMatterEffectMode.SMOKE, smokeClusterBuffer())
        up.forceRestoreToOrigin()
        down.forceRestoreToOrigin()
        val startY = averageY(up)

        repeat(8) {
            up.step(
                1f / 30f,
                motion(),
                handSnapshot(indexX = 7f, indexY = 4f, pinchX = 7f, pinchY = 4f, gesture = PixelMatterHandGesture.THUMB_UP),
            )
            down.step(
                1f / 30f,
                motion(),
                handSnapshot(indexX = 7f, indexY = 4f, pinchX = 7f, pinchY = 4f, gesture = PixelMatterHandGesture.THUMB_DOWN),
            )
        }

        assertTrue("start=$startY up=${averageY(up)}", averageY(up) < startY - 0.35f)
        assertTrue("start=$startY down=${averageY(down)}", averageY(down) > startY + 0.35f)
    }

    @Test
    fun smokeSimulation_loveGestureBurstsAndSwirlsCloud() {
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, smokeClusterBuffer())
        simulation.forceRestoreToOrigin()
        val centerX = averageX(simulation)
        val centerY = averageY(simulation)
        val before = averageDistanceTo(simulation, centerX, centerY)

        repeat(10) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(
                    indexX = centerX,
                    indexY = centerY,
                    pinchX = centerX,
                    pinchY = centerY,
                    gesture = PixelMatterHandGesture.I_LOVE_YOU,
                    rotationRadians = 0.8f,
                ),
            )
        }

        val after = averageDistanceTo(simulation, centerX, centerY)
        assertTrue("before=$before after=$after", after > before + 0.70f)
    }

    @Test
    fun smokeSimulation_victorySplitsTowardTwoFingerTargets() {
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, smokeClusterBuffer())
        simulation.forceRestoreToOrigin()
        val before = averageDistanceToNearest(simulation, firstX = 4f, firstY = 2f, secondX = 10f, secondY = 6f)

        repeat(12) {
            simulation.step(
                1f / 30f,
                motion(),
                handSnapshot(
                    indexX = 4f,
                    indexY = 2f,
                    middleX = 10f,
                    middleY = 6f,
                    pinchX = 7f,
                    pinchY = 4f,
                    gesture = PixelMatterHandGesture.VICTORY,
                ),
            )
        }

        val after = averageDistanceToNearest(simulation, firstX = 4f, firstY = 2f, secondX = 10f, secondY = 6f)
        assertTrue("before=$before after=$after", after < before * 0.82f)
    }

    @Test
    fun smokeSimulation_spreadsAlongBoundaryInsteadOfCollapsingToLine() {
        val buffer = PixelBuffer(width = 9, height = 7).apply {
            for (y in 4..5) {
                for (x in 3..5) {
                    setPixel(x, y, PixelColor.fromRgb(220, 220, 220))
                }
            }
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer, snapshot = motion(gravityY = 20f))

        simulation.forceRestoreToOrigin()
        repeat(100) {
            simulation.step(1f / 30f, motion(gravityY = 20f))
        }
        repeat(16) {
            simulation.step(1f / 30f, motion(gravityY = -20f))
        }

        val rows = (0 until simulation.particleCount)
            .map { index -> simulation.particlePosition(index).second.roundToInt() }
            .toSet()
        val columns = (0 until simulation.particleCount)
            .map { index -> simulation.particlePosition(index).first.roundToInt() }
            .toSet()

        assertTrue("rows=$rows", rows.size >= 2)
        assertTrue("columns=$columns", columns.size >= 3)
    }

    @Test
    fun smokeSimulation_separatesDenseSmokeInsteadOfStackingParticles() {
        val buffer = PixelBuffer(width = 9, height = 9).apply {
            for (y in 3..5) {
                for (x in 3..5) {
                    setPixel(x, y, PixelColor.fromRgb(220, 220, 220))
                }
            }
        }
        val simulation = simulationFor(PixelMatterEffectMode.SMOKE, buffer)

        simulation.forceRestoreToOrigin()
        simulation.applyImpulse(motion(linearX = 80f, linearY = 80f), Random(31))
        repeat(36) {
            simulation.step(1f / 30f, motion(gravityX = -20f, gravityY = -20f, linearX = -80f, linearY = 80f))
        }

        val occupied = occupiedPositions(simulation)

        assertTrue("occupied=$occupied count=${simulation.particleCount}", occupied.size >= simulation.particleCount / 2)
    }

    @Test
    fun simulation_restoreReturnsParticlesToOriginsForAllModes() {
        for (mode in PixelMatterEffectMode.entries) {
            val simulation = simulationFor(mode, singlePixelBuffer())
            simulation.step(0.5f, motion(gravityX = 20f, linearX = 20f))
            simulation.beginRestore()
            simulation.applyRestore(1f)

            assertEquals(1f, simulation.particlePosition(0).first, 0.001f)
            assertEquals(1f, simulation.particlePosition(0).second, 0.001f)
        }
    }

    private fun simulationFor(
        mode: PixelMatterEffectMode,
        buffer: PixelBuffer,
        snapshot: DeviceMotionSnapshot = motion(),
    ): PixelMatterSimulation {
        val seed = PixelMatterCapture.capture(buffer)
        assertNotNull(seed)
        return PixelMatterSimulationFactory.create(
            mode = mode,
            seed = requireNotNull(seed),
            snapshot = snapshot,
            random = Random(12),
        )
    }

    private fun singlePixelBuffer(): PixelBuffer {
        return PixelBuffer(width = 4, height = 4).apply {
            setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        }
    }

    private fun denseBlockBuffer(width: Int, height: Int): PixelBuffer {
        return PixelBuffer(width = width, height = height).apply {
            for (y in 2..4) {
                for (x in 2..5) {
                    setPixel(x, y, PixelColor.fromRgb(80, 160, 255))
                }
            }
        }
    }

    private fun smokeClusterBuffer(): PixelBuffer {
        return PixelBuffer(width = 15, height = 9).apply {
            for (y in 3..5) {
                for (x in 6..8) {
                    setPixel(x, y, PixelColor.fromRgb(220, 220, 220))
                }
            }
        }
    }

    private fun motion(
        gravityX: Float = 0f,
        gravityY: Float = 0f,
        linearX: Float = 0f,
        linearY: Float = 0f,
        timestampNanos: Long = 1L,
    ): DeviceMotionSnapshot = DeviceMotionSnapshot(
        gravityX = gravityX,
        gravityY = gravityY,
        gravityZ = DeviceMotionSnapshot.staticGravityMagnitude,
        linearAccelX = linearX,
        linearAccelY = linearY,
        linearAccelZ = 0f,
        timestampNanos = timestampNanos,
    )

    private fun syntheticHandLandmarks(
        thumb: Boolean = false,
        index: Boolean = false,
        middle: Boolean = false,
        ring: Boolean = false,
        pinky: Boolean = false,
        thumbDown: Boolean = false,
        pinch: Boolean = false,
    ): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(21) { 0.5f }
        val ys = FloatArray(21) { 0.62f }
        setLandmark(xs, ys, 0, 0.50f, 0.78f)
        setLandmark(xs, ys, 5, 0.40f, 0.58f)
        setLandmark(xs, ys, 9, 0.50f, 0.54f)
        setLandmark(xs, ys, 13, 0.60f, 0.58f)
        setLandmark(xs, ys, 17, 0.70f, 0.62f)
        setFinger(xs, ys, pip = 6, tip = 8, x = 0.40f, extended = index)
        setFinger(xs, ys, pip = 10, tip = 12, x = 0.50f, extended = middle)
        setFinger(xs, ys, pip = 14, tip = 16, x = 0.60f, extended = ring)
        setFinger(xs, ys, pip = 18, tip = 20, x = 0.70f, extended = pinky)
        if (thumb) {
            val thumbTipY = if (thumbDown) 0.86f else 0.42f
            val thumbIpY = if (thumbDown) 0.72f else 0.56f
            setLandmark(xs, ys, 3, 0.34f, thumbIpY)
            setLandmark(xs, ys, 4, 0.24f, thumbTipY)
        } else {
            setLandmark(xs, ys, 3, 0.42f, 0.64f)
            setLandmark(xs, ys, 4, 0.48f, 0.66f)
        }
        if (pinch) {
            setLandmark(xs, ys, 4, 0.41f, 0.30f)
            setLandmark(xs, ys, 8, 0.43f, 0.31f)
        }
        return xs to ys
    }

    private fun setFinger(
        xs: FloatArray,
        ys: FloatArray,
        pip: Int,
        tip: Int,
        x: Float,
        extended: Boolean,
    ) {
        setLandmark(xs, ys, pip, x, if (extended) 0.42f else 0.50f)
        setLandmark(xs, ys, tip, x, if (extended) 0.24f else 0.62f)
    }

    private fun setLandmark(
        xs: FloatArray,
        ys: FloatArray,
        index: Int,
        x: Float,
        y: Float,
    ) {
        xs[index] = x
        ys[index] = y
    }

    private fun occupiedPositions(simulation: PixelMatterSimulation): Set<Pair<Int, Int>> {
        return (0 until simulation.particleCount)
            .map { index ->
                val (x, y) = simulation.particlePosition(index)
                x.roundToInt() to y.roundToInt()
            }
            .toSet()
    }

    private fun particleOffsets(simulation: PixelMatterSimulation): Set<Pair<Int, Int>> {
        return (0 until simulation.particleCount)
            .map { index ->
                val (originX, originY) = simulation.originPosition(index)
                val (x, y) = simulation.particlePosition(index)
                x.roundToInt() - originX to y.roundToInt() - originY
            }
            .toSet()
    }

    private fun averageX(simulation: PixelMatterSimulation): Float {
        var sum = 0f
        for (index in 0 until simulation.particleCount) {
            sum += simulation.particlePosition(index).first
        }
        return sum / simulation.particleCount.toFloat()
    }

    private fun averageY(simulation: PixelMatterSimulation): Float {
        var sum = 0f
        for (index in 0 until simulation.particleCount) {
            sum += simulation.particlePosition(index).second
        }
        return sum / simulation.particleCount.toFloat()
    }

    private fun averageDistanceTo(
        simulation: PixelMatterSimulation,
        targetX: Float,
        targetY: Float,
    ): Float {
        var sum = 0f
        for (index in 0 until simulation.particleCount) {
            val (x, y) = simulation.particlePosition(index)
            val dx = x - targetX
            val dy = y - targetY
            sum += kotlin.math.sqrt(dx * dx + dy * dy)
        }
        return sum / simulation.particleCount.toFloat()
    }

    private fun averageDistanceToNearest(
        simulation: PixelMatterSimulation,
        firstX: Float,
        firstY: Float,
        secondX: Float,
        secondY: Float,
    ): Float {
        var sum = 0f
        for (index in 0 until simulation.particleCount) {
            val (x, y) = simulation.particlePosition(index)
            val firstDx = x - firstX
            val firstDy = y - firstY
            val secondDx = x - secondX
            val secondDy = y - secondY
            val firstDistance = kotlin.math.sqrt(firstDx * firstDx + firstDy * firstDy)
            val secondDistance = kotlin.math.sqrt(secondDx * secondDx + secondDy * secondDy)
            sum += minOf(firstDistance, secondDistance)
        }
        return sum / simulation.particleCount.toFloat()
    }

    private fun handSnapshot(
        indexX: Float,
        indexY: Float,
        pinchX: Float,
        pinchY: Float,
        middleX: Float = indexX,
        middleY: Float = indexY,
        isPinching: Boolean = false,
        palmVelocityX: Float = 0f,
        palmVelocityY: Float = 0f,
        gesture: PixelMatterHandGesture = if (isPinching) PixelMatterHandGesture.PINCH else PixelMatterHandGesture.OPEN_PALM,
        rotationRadians: Float = 0f,
    ): PixelMatterHandSnapshot = PixelMatterHandSnapshot(
        timestampMs = 1L,
        indexTip = PixelMatterHandPoint(indexX, indexY),
        thumbTip = PixelMatterHandPoint(pinchX, pinchY),
        middleTip = PixelMatterHandPoint(middleX, middleY),
        ringTip = PixelMatterHandPoint(indexX, indexY),
        pinkyTip = PixelMatterHandPoint(indexX, indexY),
        palmCenter = PixelMatterHandPoint(indexX, indexY),
        pinchCenter = PixelMatterHandPoint(pinchX, pinchY),
        isPinching = isPinching,
        palmVelocity = PixelMatterHandPoint(palmVelocityX, palmVelocityY),
        confidence = 1f,
        gesture = gesture,
        rotationRadians = rotationRadians,
    )
}
