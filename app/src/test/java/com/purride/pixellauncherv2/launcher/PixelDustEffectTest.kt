package com.purride.pixellauncherv2.launcher

import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.ManualFrameScheduler
import com.purride.pixellauncherv2.data.DeviceMotionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class PixelDustEffectTest {

    @Test
    fun shakeDetector_ignoresOrdinaryMotion() {
        val detector = PixelDustShakeDetector()

        repeat(10) { index ->
            val triggered = detector.record(
                motion(linearX = 2f, timestampNanos = index * 100_000_000L + 1L),
            )
            assertFalse(triggered)
        }
    }

    @Test
    fun shakeDetector_triggersOnStrongWindowWithoutCooldown() {
        val detector = PixelDustShakeDetector()

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
        val controller = PixelDustEffectController(
            vsync = PixelTickerProvider(scheduler),
            onFrame = { frameCount += 1 },
            random = Random(4),
        )
        val buffer = PixelBuffer(width = 4, height = 4).apply {
            setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        }

        assertTrue(controller.start(buffer, motion(linearX = 20f)))
        scheduler.advanceFrame(1_000_000_000L)
        scheduler.advanceFrame(1_100_000_000L)
        controller.clear()

        assertTrue(controller.start(buffer, motion(linearX = 20f)))
        val framesAfterRestart = frameCount
        scheduler.advanceFrame(2_000_000_000L)

        assertEquals(framesAfterRestart + 1, frameCount)
    }

    @Test
    fun controller_doesNotAutoRestoreWhenStill() {
        val scheduler = ManualFrameScheduler()
        val controller = PixelDustEffectController(
            vsync = PixelTickerProvider(scheduler),
            onFrame = {},
            random = Random(5),
        )
        val buffer = PixelBuffer(width = 4, height = 4).apply {
            setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        }

        assertTrue(controller.start(buffer, motion()))
        repeat(400) { index ->
            scheduler.advanceFrame(1_000_000_000L + index * 40_000_000L)
        }

        assertEquals(PixelDustEffectPhase.ACTIVE, controller.phase)
        assertTrue(controller.requestRestore())
        assertEquals(PixelDustEffectPhase.RESTORING, controller.phase)
    }

    @Test
    fun motionMapper_usesGravityOnlyForParticlePhysics() {
        val withLinearAcceleration = PixelDustMotionMapper.toScreenAcceleration(
            motion(gravityX = 1f, gravityY = 1f, linearX = 50f, linearY = -50f),
        )
        val withoutLinearAcceleration = PixelDustMotionMapper.toScreenAcceleration(
            motion(gravityX = 1f, gravityY = 1f),
        )

        assertEquals(withoutLinearAcceleration.x, withLinearAcceleration.x, 0.001f)
        assertEquals(withoutLinearAcceleration.y, withLinearAcceleration.y, 0.001f)
        assertTrue("x=${withLinearAcceleration.x}", withLinearAcceleration.x > 0f)
        assertTrue("y=${withLinearAcceleration.y}", withLinearAcceleration.y > 0f)
    }

    @Test
    fun particleField_usesOnlyVisiblePixels() {
        val buffer = PixelBuffer(width = 3, height = 2)
        buffer.setPixel(0, 0, PixelColor.fromRgb(255, 0, 0))
        buffer.setPixel(2, 1, PixelColor.fromRgb(0, 0, 255))

        val field = PixelDustParticleField.fromBuffer(
            buffer = buffer,
            snapshot = motion(),
            random = Random(1),
        )

        assertNotNull(field)
        requireNotNull(field)
        assertEquals(2, field.particleCount)
        assertEquals(0 to 0, field.originPosition(0))
        assertEquals(2 to 1, field.originPosition(1))
    }

    @Test
    fun particleField_skipsTransparentPixelsAndFlattensTranslucentColors() {
        val background = PixelColor.fromRgb(16, 16, 16)
        val buffer = PixelBuffer(width = 4, height = 1)
        buffer.setPixel(0, 0, PixelColor.fromArgb(0, 255, 255, 255))
        buffer.setPixel(1, 0, PixelColor.fromArgb(1, 255, 255, 255))
        buffer.setPixel(2, 0, PixelColor.fromArgb(80, 16, 16, 16))
        buffer.setPixel(3, 0, PixelColor.fromRgb(220, 40, 40))

        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(13),
                backgroundColor = background.argb,
            ),
        )

        assertEquals(2, field.particleCount)
        assertEquals(1 to 0, field.originPosition(0))
        assertEquals(3 to 0, field.originPosition(1))

        val rendered = PixelBuffer(width = 4, height = 1).also(field::drawTo)
        assertEquals(0xFF111111.toInt(), rendered.pixels[1])
        assertEquals(PixelColor.fromRgb(220, 40, 40).argb, rendered.pixels[3])
    }

    @Test
    fun particleField_rendersOriginalColorsWhileActiveAndRestoring() {
        val buffer = PixelBuffer(width = 2, height = 1)
        val sourceColor = PixelColor.fromRgb(3, 9, 27)
        buffer.setPixel(0, 0, sourceColor)
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(10),
            ),
        )

        val activeRender = PixelBuffer(width = 2, height = 1).also(field::drawTo)
        assertEquals(sourceColor.argb, activeRender.pixels[0])

        field.beginRestore()
        val restoreRender = PixelBuffer(width = 2, height = 1).also(field::drawTo)
        assertEquals(sourceColor.argb, restoreRender.pixels[0])
    }

    @Test
    fun particleField_pilesWithoutOverlappingCells() {
        val buffer = PixelBuffer(width = 4, height = 1)
        buffer.setPixel(1, 0, PixelColor.fromRgb(255, 255, 255))
        buffer.setPixel(2, 0, PixelColor.fromRgb(255, 255, 255))
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(6),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(160) {
            field.stepActive(1f / 30f, motion(gravityX = 20f))
        }

        val occupied = (0 until field.particleCount)
            .map { index ->
                val (x, y) = field.particlePosition(index)
                x.roundToInt() to y.roundToInt()
            }
            .toSet()
        val rendered = PixelBuffer(width = 4, height = 1).also(field::drawTo)

        assertEquals(2, occupied.size)
        assertEquals(2, rendered.pixels.count { color -> ((color ushr 24) and 0xFF) > 0 })
    }

    @Test
    fun particleField_movesDiagonallyWhenBothGravityAxesArePresent() {
        val buffer = PixelBuffer(width = 6, height = 6)
        buffer.setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(8),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(80) {
            field.stepActive(1f / 30f, motion(gravityX = 20f, gravityY = 20f))
        }

        val (x, y) = field.particlePosition(0)
        assertTrue("x=$x", x > 1f)
        assertTrue("y=$y", y > 1f)
    }

    @Test
    fun particleField_linearAccelerationDoesNotDriveActiveMotion() {
        val buffer = PixelBuffer(width = 5, height = 5)
        buffer.setPixel(2, 2, PixelColor.fromRgb(255, 255, 255))
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(linearX = 40f, linearY = -40f),
                random = Random(14),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(40) {
            field.stepActive(1f / 30f, motion(linearX = 40f, linearY = -40f))
        }

        assertEquals(2f, field.particlePosition(0).first, 0.001f)
        assertEquals(2f, field.particlePosition(0).second, 0.001f)
    }

    @Test
    fun particleField_initialScatterDoesNotUseLinearAccelerationDirection() {
        val buffer = PixelBuffer(width = 5, height = 5)
        buffer.setPixel(0, 0, PixelColor.fromRgb(255, 255, 255))
        val fieldWithoutLinearAcceleration = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(15),
            ),
        )
        val fieldWithLinearAcceleration = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(linearX = 80f, linearY = 80f),
                random = Random(15),
            ),
        )

        fieldWithoutLinearAcceleration.stepActive(1f / 30f, motion())
        fieldWithLinearAcceleration.stepActive(1f / 30f, motion())

        assertEquals(
            fieldWithoutLinearAcceleration.particlePosition(0).first,
            fieldWithLinearAcceleration.particlePosition(0).first,
            0.001f,
        )
        assertEquals(
            fieldWithoutLinearAcceleration.particlePosition(0).second,
            fieldWithLinearAcceleration.particlePosition(0).second,
            0.001f,
        )
    }

    @Test
    fun particleField_settlesSparseColumnWithoutInternalGaps() {
        val buffer = PixelBuffer(width = 1, height = 6)
        buffer.setPixel(0, 0, PixelColor.fromRgb(255, 255, 255))
        buffer.setPixel(0, 2, PixelColor.fromRgb(255, 255, 255))
        buffer.setPixel(0, 4, PixelColor.fromRgb(255, 255, 255))
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(9),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(80) {
            field.stepActive(1f / 30f, motion(gravityY = 20f))
        }

        val rows = (0 until field.particleCount)
            .map { index -> field.particlePosition(index).second.roundToInt() }
            .sorted()
        assertEquals(listOf(3, 4, 5), rows)
    }

    @Test
    fun particleField_compactsDensePileWithoutInternalGaps() {
        val buffer = PixelBuffer(width = 4, height = 4)
        for (y in 0..1) {
            for (x in 0..3) {
                buffer.setPixel(x, y, PixelColor.fromRgb(255, 255, 255))
            }
        }
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(11),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(80) {
            field.stepActive(1f / 30f, motion(gravityY = 20f))
        }

        val expected = (2..3)
            .flatMap { y -> (0..3).map { x -> x to y } }
            .toSet()
        assertEquals(expected, occupiedPositions(field))
    }

    @Test
    fun particleField_directionSwitchDoesNotTeleportParticles() {
        val buffer = PixelBuffer(width = 6, height = 6)
        for (x in 1..4) {
            buffer.setPixel(x, 2, PixelColor.fromRgb(255, 255, 255))
        }
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(7),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(120) {
            field.stepActive(1f / 30f, motion(gravityY = 20f))
        }
        val before = (0 until field.particleCount).map(field::particlePosition)

        field.stepActive(1f / 30f, motion(gravityY = -20f))

        for (index in 0 until field.particleCount) {
            val (beforeX, beforeY) = before[index]
            val (afterX, afterY) = field.particlePosition(index)
            assertTrue("index=$index before=$beforeX,$beforeY after=$afterX,$afterY", abs(afterX - beforeX) <= 5.001f)
            assertTrue("index=$index before=$beforeX,$beforeY after=$afterX,$afterY", abs(afterY - beforeY) <= 5.001f)
        }
    }

    @Test
    fun particleField_preservesEveryParticleAfterLongDiagonalTilt() {
        val buffer = PixelBuffer(width = 6, height = 6)
        for (y in 1..3) {
            for (x in 1..3) {
                buffer.setPixel(x, y, PixelColor.fromRgb(255, 255, 255))
            }
        }
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(),
                random = Random(12),
            ),
        )

        field.forceRestoreToOrigin()
        repeat(160) {
            field.stepActive(1f / 30f, motion(gravityX = 20f, gravityY = 20f))
        }

        assertEquals(field.particleCount, occupiedPositions(field).size)
        val rendered = PixelBuffer(width = 6, height = 6).also(field::drawTo)
        assertEquals(field.particleCount, rendered.pixels.count { color -> ((color ushr 24) and 0xFF) > 0 })
    }

    @Test
    fun particleField_staysInsideLogicalBounds() {
        val buffer = PixelBuffer(width = 4, height = 4)
        buffer.setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(linearX = 30f, linearY = -30f),
                random = Random(2),
            ),
        )

        repeat(80) {
            field.stepActive(1f / 30f, motion(gravityX = 9.8f, gravityY = -9.8f, linearX = 30f, linearY = -30f))
        }

        val (x, y) = field.particlePosition(0)
        assertTrue("x=$x", x in 0f..3f)
        assertTrue("y=$y", y in 0f..3f)
    }

    @Test
    fun particleField_restoreReturnsParticlesToOrigins() {
        val buffer = PixelBuffer(width = 4, height = 4)
        buffer.setPixel(1, 1, PixelColor.fromRgb(255, 255, 255))
        val field = requireNotNull(
            PixelDustParticleField.fromBuffer(
                buffer = buffer,
                snapshot = motion(linearX = 20f),
                random = Random(3),
            ),
        )

        field.stepActive(0.5f, motion(gravityX = 9f, linearX = 20f))
        field.beginRestore()
        field.applyRestore(1f)

        assertEquals(1f, field.particlePosition(0).first, 0.001f)
        assertEquals(1f, field.particlePosition(0).second, 0.001f)
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

    private fun occupiedPositions(field: PixelDustParticleField): Set<Pair<Int, Int>> {
        return (0 until field.particleCount)
            .map { index ->
                val (x, y) = field.particlePosition(index)
                x.roundToInt() to y.roundToInt()
            }
            .toSet()
    }
}
