package com.purride.pixellauncherv2.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargeIdleEffectRendererTest {

    private val rendererEffects = ChargeIdleEffect.entries.filter { it != ChargeIdleEffect.FLUID }

    @Test
    fun allChargeEffectRenderersProduceVisibleMaskWhenCharging() {
        rendererEffects.forEach { effect ->
            val frame = ChargeIdleEffectRegistry.rendererFor(effect).render(
                width = 48,
                height = 64,
                batteryLevel = 50,
                isCharging = true,
                gravityX = 0f,
                gravityY = 1f,
                nowUptimeMs = 1_000L,
                sequence = 1L,
            )

            assertEquals(48, frame?.width)
            assertEquals(64, frame?.height)
            assertTrue(frame!!.mask.any { it.toInt() != 0 })
        }
    }

    @Test
    fun chargeEffectRenderersReturnNullWhenNotCharging() {
        rendererEffects.forEach { effect ->
            val frame = ChargeIdleEffectRegistry.rendererFor(effect).render(
                width = 48,
                height = 64,
                batteryLevel = 80,
                isCharging = false,
                gravityX = 0f,
                gravityY = 1f,
                nowUptimeMs = 1_000L,
                sequence = 1L,
            )

            assertEquals(null, frame)
        }
    }

    @Test
    fun horizonEffectShowsHalfScreenAtFullCharge() {
        val frame = ChargeIdleEffectRegistry.rendererFor(ChargeIdleEffect.HORIZON).render(
            width = 20,
            height = 10,
            batteryLevel = 100,
            isCharging = true,
            gravityX = 0f,
            gravityY = 1f,
            nowUptimeMs = 1_000L,
            sequence = 1L,
        )!!

        assertEquals(100, frame.mask.count { it.toInt() != 0 })
    }
}
