package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.time.Duration.Companion.milliseconds

/** Locks inherited token behavior and the legacy JVM constructor surface. */
class PixelThemeCompatibilityTest {
    /** Complete tokens project legacy data and provide motion when no explicit motion theme exists. */
    @Test
    fun tokenConstructorProvidesTokensLegacyProjectionAndMotionFallback() {
        /** Motion value that identifies the complete theme fallback path. */
        val themedMotion = PixelMotionThemeData.Default.copy(
            feedback = PixelMotionSpec(
                duration = 37.milliseconds,
                role = PixelMotionRole.Feedback,
            ),
        )
        /** Complete token graph inherited by the probe. */
        val expectedTokens = PixelThemeTokens.Light.copy(motion = themedMotion)
        /** Mutable capture populated during retained build. */
        val capture = TokenThemeCapture()
        /** Retained test harness. */
        val tester = PixelTester()

        tester.pumpWidget(
            PixelTheme(
                tokens = expectedTokens,
                child = TokenThemeProbe(capture),
            ),
            logicalWidth = 2,
            logicalHeight = 2,
        )

        assertSame(expectedTokens, capture.tokens)
        assertEquals(expectedTokens.toLegacyThemeData(), capture.legacyData)
        assertSame(themedMotion, capture.motion)
        assertSame(themedMotion, capture.maybeMotion)
        tester.dispose()
    }

    /** An explicit PixelMotionTheme wins over motion stored in the enclosing complete theme. */
    @Test
    fun explicitMotionProviderOverridesCompleteThemeMotion() {
        /** Motion stored in the enclosing complete theme. */
        val themedMotion = PixelMotionThemeData.Default.copy(
            feedback = PixelMotionSpec(17.milliseconds, role = PixelMotionRole.Feedback),
        )
        /** Nearest explicit motion provider expected to win. */
        val explicitMotion = PixelMotionThemeData.Default.copy(
            feedback = PixelMotionSpec(91.milliseconds, role = PixelMotionRole.Feedback),
        )
        /** Mutable capture populated during retained build. */
        val capture = TokenThemeCapture()
        /** Retained test harness. */
        val tester = PixelTester()

        tester.pumpWidget(
            PixelTheme(
                tokens = PixelThemeTokens.Dark.copy(motion = themedMotion),
                child = PixelMotionTheme(
                    data = explicitMotion,
                    child = TokenThemeProbe(capture),
                ),
            ),
            logicalWidth = 2,
            logicalHeight = 2,
        )

        assertSame(explicitMotion, capture.motion)
        assertSame(explicitMotion, capture.maybeMotion)
        tester.dispose()
    }

    /** Missing providers retain safe Default values for both legacy and complete accessors. */
    @Test
    fun missingThemeUsesLegacyAndCompleteDefaults() {
        /** Mutable capture populated during retained build. */
        val capture = TokenThemeCapture()
        /** Retained test harness. */
        val tester = PixelTester()

        tester.pumpWidget(TokenThemeProbe(capture), logicalWidth = 2, logicalHeight = 2)

        assertSame(PixelThemeTokens.Default, capture.tokens)
        assertSame(PixelThemeData.Default, capture.legacyData)
        assertSame(PixelMotionThemeData.Default, capture.motion)
        assertNull(capture.maybeMotion)
        tester.dispose()
    }

    /** PixelTheme retains its exact old JVM constructor and adds a distinct token overload. */
    @Test
    fun pixelThemeRetainsLegacyJvmConstructorDescriptor() {
        /** Kotlin marker class used by the original default-argument constructor bridge. */
        val markerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        /** Original public constructor looked up by its exact erased JVM parameter types. */
        val legacyConstructor = PixelTheme::class.java.getConstructor(
            PixelThemeData::class.java,
            Widget::class.java,
            Any::class.java,
        )
        /** Original default-key constructor bridge retained for compiled Kotlin call sites. */
        val legacyDefaultConstructor = PixelTheme::class.java.getConstructor(
            PixelThemeData::class.java,
            Widget::class.java,
            Any::class.java,
            Int::class.javaPrimitiveType!!,
            markerClass,
        )
        /** New constructor whose first parameter type makes the overload binary-distinct. */
        val tokenConstructor = PixelTheme::class.java.getConstructor(
            PixelThemeTokens::class.java,
            Widget::class.java,
            Any::class.java,
        )

        assertTrue(Modifier.isPublic(legacyConstructor.modifiers))
        assertTrue(Modifier.isPublic(legacyDefaultConstructor.modifiers))
        assertTrue(Modifier.isPublic(tokenConstructor.modifiers))
        assertNotNull(PixelTheme::class.java.getDeclaredMethod("getData"))
    }

    /** PixelThemeData retains its five-value constructor, component methods, copy, and no-arg ABI. */
    @Test
    fun pixelThemeDataRetainsLegacyDataClassJvmSurface() {
        /** Kotlin marker class used by the original default-argument constructor bridge. */
        val markerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        /** Exact original five-parameter constructor. */
        val primaryConstructor = PixelThemeData::class.java.getConstructor(
            PixelThemeColors::class.java,
            PixelTextStyle::class.java,
            PixelButtonStyle::class.java,
            PixelTextButtonStyle::class.java,
            PixelTextFieldStyle::class.java,
        )
        /** Original all-default no-argument constructor. */
        val noArgConstructor = PixelThemeData::class.java.getConstructor()
        /** Original all-default bit-mask constructor used by compiled Kotlin call sites. */
        val defaultConstructor = PixelThemeData::class.java.getConstructor(
            PixelThemeColors::class.java,
            PixelTextStyle::class.java,
            PixelButtonStyle::class.java,
            PixelTextButtonStyle::class.java,
            PixelTextFieldStyle::class.java,
            Int::class.javaPrimitiveType!!,
            markerClass,
        )
        /** Exact original data-class copy method. */
        val copyMethod = PixelThemeData::class.java.getDeclaredMethod(
            "copy",
            PixelThemeColors::class.java,
            PixelTextStyle::class.java,
            PixelButtonStyle::class.java,
            PixelTextButtonStyle::class.java,
            PixelTextFieldStyle::class.java,
        )

        assertTrue(Modifier.isPublic(primaryConstructor.modifiers))
        assertTrue(Modifier.isPublic(noArgConstructor.modifiers))
        assertTrue(Modifier.isPublic(defaultConstructor.modifiers))
        assertTrue(Modifier.isPublic(copyMethod.modifiers))
        (1..5).forEach { componentIndex ->
            assertNotNull(PixelThemeData::class.java.getDeclaredMethod("component$componentIndex"))
        }
    }

    /** PixelThemeColors retains thirteen inline-color components, copy, and constructor bridges. */
    @Test
    fun pixelThemeColorsRetainsLegacyInlineValueJvmSurface() {
        /** Public constructor parameter signatures emitted around the private inline-value primary. */
        val publicSignatures = PixelThemeColors::class.java.constructors.map { constructor ->
            constructor.parameterTypes.map(Class<*>::getName)
        }
        /** Thirteen unboxed PixelColor values followed by the synthetic marker. */
        val primaryBridge = List(13) { "int" } + "kotlin.jvm.internal.DefaultConstructorMarker"
        /** Thirteen colors, one default mask, and the synthetic marker. */
        val defaultBridge = List(14) { "int" } + "kotlin.jvm.internal.DefaultConstructorMarker"
        /** Mangled data-class copy method retaining thirteen unboxed colors. */
        val copyMethod = PixelThemeColors::class.java.declaredMethods.single { method ->
            method.name.startsWith("copy-") && !method.name.contains("\$default")
        }
        /** Mangled component methods emitted for every legacy palette property. */
        val componentMethods = PixelThemeColors::class.java.declaredMethods.filter { method ->
            method.name.startsWith("component") && !method.name.contains("\$default")
        }

        assertTrue(primaryBridge in publicSignatures)
        assertTrue(defaultBridge in publicSignatures)
        assertEquals(13, copyMethod.parameterCount)
        assertEquals(13, componentMethods.size)
    }

    /** PixelLabelTokens retains its original 29-label data-class JVM surface. */
    @Test
    fun pixelLabelTokensRetainsLegacyDataClassJvmSurface() {
        /** Kotlin marker class used by the original default-argument constructor bridge. */
        val markerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        /** Exact ordered parameter types of the original 29-label primary constructor and copy. */
        val labelParameterTypes = List(29) { String::class.java }
        /** Original public primary constructor containing exactly the frozen label set. */
        val primaryConstructor = PixelLabelTokens::class.java.getConstructor(
            *labelParameterTypes.toTypedArray(),
        )
        /** Original all-default no-argument constructor. */
        val noArgConstructor = PixelLabelTokens::class.java.getConstructor()
        /** Original one-mask default-argument constructor used by compiled Kotlin call sites. */
        val defaultConstructor = PixelLabelTokens::class.java.getConstructor(
            *(labelParameterTypes + Int::class.javaPrimitiveType!! + markerClass).toTypedArray(),
        )
        /** Exact original data-class copy method. */
        val copyMethod = PixelLabelTokens::class.java.getDeclaredMethod(
            "copy",
            *labelParameterTypes.toTypedArray(),
        )
        /** Static default-copy bridge used by precompiled Kotlin named/default calls. */
        val defaultCopyMethod = PixelLabelTokens::class.java.getDeclaredMethod(
            "copy\$default",
            *(
                listOf(PixelLabelTokens::class.java) +
                    labelParameterTypes +
                    listOf(Int::class.javaPrimitiveType!!, Any::class.java)
                ).toTypedArray(),
        )

        assertTrue(Modifier.isPublic(primaryConstructor.modifiers))
        assertTrue(Modifier.isPublic(noArgConstructor.modifiers))
        assertTrue(Modifier.isPublic(defaultConstructor.modifiers))
        assertTrue(Modifier.isPublic(copyMethod.modifiers))
        assertTrue(Modifier.isPublic(defaultCopyMethod.modifiers))
        assertTrue(Modifier.isStatic(defaultCopyMethod.modifiers))
        (1..29).forEach { componentIndex ->
            assertNotNull(PixelLabelTokens::class.java.getDeclaredMethod("component$componentIndex"))
        }
    }
}

/** Mutable assertion sink populated by [TokenThemeProbe]. */
private class TokenThemeCapture {
    /** Complete tokens resolved by the probe. */
    var tokens: PixelThemeTokens? = null

    /** Legacy data resolved by the probe. */
    var legacyData: PixelThemeData? = null

    /** Effective motion tokens resolved by the probe. */
    var motion: PixelMotionThemeData? = null

    /** Nullable effective motion tokens resolved by the probe. */
    var maybeMotion: PixelMotionThemeData? = null
}

/** Stateless retained probe that reads all theme compatibility accessors. */
private class TokenThemeProbe(
    /** Sink receiving values read during build. */
    private val capture: TokenThemeCapture,
) : StatelessWidget() {
    /** Captures inherited theme values and returns a fixed paintable leaf. */
    override fun build(context: BuildContext): Widget {
        capture.tokens = PixelTheme.tokensOf(context)
        capture.legacyData = PixelTheme.of(context)
        capture.motion = PixelMotionTheme.of(context)
        capture.maybeMotion = PixelMotionTheme.maybeOf(context)
        return Container(width = 1, height = 1, fillColor = PixelColor.White, borderColor = null)
    }
}
