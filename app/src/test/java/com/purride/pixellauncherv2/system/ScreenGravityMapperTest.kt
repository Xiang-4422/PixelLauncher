package com.purride.pixellauncherv2.system

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenGravityMapperTest {

    @Test
    fun mapToScreen_usesLauncherGravityDirectionInPortrait() {
        val mapped = ScreenGravityMapper.mapToScreen(
            rawGravityX = 2f,
            rawGravityY = 3f,
            rawGravityZ = 0f,
            rotation = Surface.ROTATION_0,
        )

        assertEquals(-2f, mapped.first, 0.001f)
        assertEquals(3f, mapped.second, 0.001f)
    }
}
