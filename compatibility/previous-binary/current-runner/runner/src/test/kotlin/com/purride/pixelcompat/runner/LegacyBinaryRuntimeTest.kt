package com.purride.pixelcompat.runner

import com.purride.pixelcore.GlyphProvider
import com.purride.pixelcore.GlyphStyle
import com.purride.pixelcore.PixelFontEngine
import com.purride.pixelcore.PixelFontFamily
import com.purride.pixelcore.PixelFontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

/** Runtime proof that an old consumer binary still runs after only the engine AAR is replaced. */
class LegacyBinaryRuntimeTest {
    /**
     * Invokes the additive code-point FontEngine path on a provider compiled before that method
     * existed, proving the inherited interface default prevents `AbstractMethodError`.
     */
    @Test
    fun oldCharOnlyGlyphProviderRunsOnCurrentCodePointEngine() {
        /** Old probe class loaded from the separately compiled consumer AAR. */
        val probeClass = Class.forName("com.purride.pixelcompat.legacy.LegacyGlyphProviderProbe")
        /** Frozen factory returning a provider class with only the Char method in its bytecode. */
        val createMethod = probeClass.getMethod("create")
        /** Frozen reset method preventing requests from another test invocation. */
        val resetMethod = probeClass.getMethod("reset")
        /** Frozen summary method exposing only primitive/String compatibility evidence. */
        val summaryMethod = probeClass.getMethod("requestSummary")
        resetMethod.invoke(null)
        /** Old implementation cast to the current interface at runtime. */
        val provider = createMethod.invoke(null) as GlyphProvider
        /** Current style with distinguishable narrow and wide fallback advances. */
        val style = GlyphStyle(
            cellHeight = 4,
            narrowAdvanceWidth = 3,
            wideAdvanceWidth = 6,
            oversampleFactor = 1,
            narrowMinimumSampleRatio = 1f,
            wideMinimumSampleRatio = 1f,
            narrowTextSizeRatio = 1f,
            wideTextSizeRatio = 1f,
            narrowFontWeight = PixelFontWeight.NORMAL,
            wideFontWeight = PixelFontWeight.NORMAL,
            narrowFontFamily = PixelFontFamily.MONOSPACE,
            wideFontFamily = PixelFontFamily.DEFAULT,
        )
        /** Current engine invoking `rasterizeGlyph(Int, ...)` on the old class. */
        val engine = PixelFontEngine(provider)
        /** One supplementary scalar represented by two UTF-16 source units. */
        val supplementaryText = String(Character.toChars(0x1F642))

        assertEquals(6, engine.measureText(supplementaryText, style))
        assertEquals("FFFD", summaryMethod.invoke(null) as String)
    }

    /** Invokes the old probe reflectively so this runner never recompiles its engine call sites. */
    @Test
    fun oldRenderSpiBinaryRunsOnCurrentEngine() {
        /** Old consumer class loaded from its precompiled AAR. */
        val probeClass = Class.forName("com.purride.pixelcompat.legacy.LegacyRenderSpiProbe")
        /** Static no-argument method whose bytecode was compiled against the frozen engine. */
        val probeMethod = probeClass.getMethod("run")
        /** Primitive/String-only behavior summary returned across the binary boundary. */
        val summary = probeMethod.invoke(null) as String

        assertEquals("create=1;update=2;first=true;second=true", summary)
    }
}
