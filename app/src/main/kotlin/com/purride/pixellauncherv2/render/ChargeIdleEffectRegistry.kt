package com.purride.pixellauncherv2.render

import com.purride.pixellauncherv2.render.charge.CascadeChargeEffectRenderer
import com.purride.pixellauncherv2.render.charge.DotMatrixChargeEffectRenderer
import com.purride.pixellauncherv2.render.charge.HorizonChargeEffectRenderer
import com.purride.pixellauncherv2.render.charge.StackChargeEffectRenderer
import com.purride.pixellauncherv2.render.charge.TankChargeEffectRenderer

object ChargeIdleEffectRegistry {
    fun rendererFor(effect: ChargeIdleEffect): ChargeIdleEffectRenderer {
        return when (effect) {
            ChargeIdleEffect.FLUID -> error("FLUID uses the existing Idle fluid engine and has no charge effect renderer.")
            ChargeIdleEffect.HORIZON -> HorizonChargeEffectRenderer
            ChargeIdleEffect.STACK -> StackChargeEffectRenderer
            ChargeIdleEffect.DOT_MATRIX -> DotMatrixChargeEffectRenderer
            ChargeIdleEffect.TANK -> TankChargeEffectRenderer
            ChargeIdleEffect.CASCADE -> CascadeChargeEffectRenderer
        }
    }
}
