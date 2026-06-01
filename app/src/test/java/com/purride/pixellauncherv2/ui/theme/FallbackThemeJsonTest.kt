package com.purride.pixellauncherv2.ui.theme

import com.purride.pixellauncherv2.launcher.PixelTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the theme-map invariant that crashed startup when AUTO was added.
 *
 * LauncherThemes builds its fallback theme map from [FallbackThemeJson.byTheme],
 * so every real (non-AUTO) [PixelTheme] must have a fallback entry, and the AUTO
 * sentinel must NOT be a key — it is resolved to DAY/NIGHT before any map lookup.
 * (Parsing itself needs org.json and is exercised on-device, not here.)
 */
class FallbackThemeJsonTest {

    @Test
    fun byTheme_coversEveryRealThemeAndExcludesAuto() {
        PixelTheme.entries
            .filter { it != PixelTheme.AUTO }
            .forEach { theme ->
                assertTrue(
                    "FallbackThemeJson is missing a fallback for $theme",
                    FallbackThemeJson.byTheme.containsKey(theme),
                )
            }
        assertFalse(
            "AUTO must not be a theme-map key (it is a sentinel resolved before lookup)",
            FallbackThemeJson.byTheme.containsKey(PixelTheme.AUTO),
        )
    }
}
