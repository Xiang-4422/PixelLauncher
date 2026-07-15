package com.purride.pixelui.foundation

import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.ScreenProfile
import com.purride.pixelui.Builder
import com.purride.pixelui.Container
import com.purride.pixelui.HostCapabilities
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.MediaQuery
import com.purride.pixelui.MediaQueryData
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelWindowInsets
import com.purride.pixelui.TextDirection
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** Locks the additive Host-capability lookup without changing the legacy MediaQuery data ABI. */
class MediaQueryHostCapabilitiesCompatibilityTest {
    /** The original primary, synthetic-default, and three-argument constructors remain exact. */
    @Test
    fun mediaQueryDataRetainsOriginalConstructorAndCopyDescriptors() {
        /** Actual public constructor descriptors emitted for the legacy data class. */
        val constructorDescriptors = MediaQueryData::class.java.constructors
            .map(::constructorDescriptor)
            .toSet()
        assertEquals(EXPECTED_CONSTRUCTORS, constructorDescriptors)

        /** Actual copy descriptors, including Kotlin's static default bridge. */
        val copyDescriptors = MediaQueryData::class.java.declaredMethods
            .filter { method -> method.name == "copy" || method.name == "copy\$default" }
            .map(::methodDescriptor)
            .toSet()
        assertEquals(EXPECTED_COPY_METHODS, copyDescriptors)
    }

    /** MediaQuery exposes the explicit scope while retaining a deterministic scope-less fallback. */
    @Test
    fun mediaQueryCapabilityAccessorsDelegateToAdditiveScope() {
        /** Latest explicitly inherited snapshot observed by the probe. */
        var explicitSnapshot: HostCapabilitiesData? = HostCapabilitiesData.Default
        /** Latest effective snapshot observed by the probe. */
        var effectiveSnapshot: HostCapabilitiesData? = null
        /** Stable probe whose retained identity is unchanged between the two environment frames. */
        val probe = Builder(key = "capability-probe") { context ->
            explicitSnapshot = MediaQuery.maybeCapabilitiesOf(context)
            effectiveSnapshot = MediaQuery.capabilitiesOf(context)
            Container(width = 1, height = 1, fillColor = PixelColor.Transparent)
        }
        /** Retained test runtime used to rebuild only the inherited environment. */
        val tester = PixelTester()

        tester.pumpWidget(probe, logicalWidth = 1, logicalHeight = 1)
        assertNull(explicitSnapshot)
        assertSame(HostCapabilitiesData.Default, effectiveSnapshot)

        /** Non-default snapshot proving every accessor returns the explicitly inherited value. */
        val rtlSnapshot = HostCapabilitiesData(
            locales = listOf(PixelLocale("ar")),
            layoutDirection = TextDirection.RTL,
            textScaleFactor = 1.5f,
            highContrast = true,
            density = 2f,
            refreshRateHz = 120f,
        )
        tester.pumpWidget(
            HostCapabilities(
                data = rtlSnapshot,
                child = probe,
                key = "capability-scope",
            ),
            logicalWidth = 1,
            logicalHeight = 1,
        )
        assertSame(rtlSnapshot, explicitSnapshot)
        assertSame(rtlSnapshot, effectiveSnapshot)
        tester.dispose()
    }

    /** Builds a JVM constructor descriptor directly from Java reflection types. */
    private fun constructorDescriptor(constructor: java.lang.reflect.Constructor<*>): String {
        /** Encoded parameter sequence in declared JVM order. */
        val parameters = constructor.parameterTypes.joinToString(separator = "") { type ->
            type.jvmDescriptor()
        }
        return "($parameters)V"
    }

    /** Builds a named JVM method descriptor directly from Java reflection types. */
    private fun methodDescriptor(method: java.lang.reflect.Method): String {
        /** Encoded parameter sequence in declared JVM order. */
        val parameters = method.parameterTypes.joinToString(separator = "") { type ->
            type.jvmDescriptor()
        }
        return "${method.name}($parameters)${method.returnType.jvmDescriptor()}"
    }

    /** Encodes one Java reflection type using JVM field-descriptor syntax. */
    private fun Class<*>.jvmDescriptor(): String {
        if (isArray) return name.replace('.', '/')
        if (!isPrimitive) return "L${name.replace('.', '/')};"
        return when (this) {
            java.lang.Boolean.TYPE -> "Z"
            java.lang.Byte.TYPE -> "B"
            java.lang.Character.TYPE -> "C"
            java.lang.Short.TYPE -> "S"
            java.lang.Integer.TYPE -> "I"
            java.lang.Long.TYPE -> "J"
            java.lang.Float.TYPE -> "F"
            java.lang.Double.TYPE -> "D"
            java.lang.Void.TYPE -> "V"
            else -> error("Unsupported primitive descriptor for $name")
        }
    }

    /** Exact pre-M5-3 constructor descriptors protected from environment-model expansion. */
    private companion object {
        /** Public data-class constructors, including the Kotlin default-mask bridge. */
        val EXPECTED_CONSTRUCTORS: Set<String> = setOf(
            "(IILcom/purride/pixelcore/ScreenProfile;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;)V",
            "(IILcom/purride/pixelcore/ScreenProfile;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
            "(IILcom/purride/pixelcore/ScreenProfile;)V",
        )

        /** Data-class copy method and static Kotlin default bridge. */
        val EXPECTED_COPY_METHODS: Set<String> = setOf(
            "copy(IILcom/purride/pixelcore/ScreenProfile;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;)" +
                "Lcom/purride/pixelui/MediaQueryData;",
            "copy\$default(Lcom/purride/pixelui/MediaQueryData;" +
                "IILcom/purride/pixelcore/ScreenProfile;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "Lcom/purride/pixelui/PixelWindowInsets;" +
                "ILjava/lang/Object;)Lcom/purride/pixelui/MediaQueryData;",
        )

        /** Compile-time references keep legacy constructor parameter types covered by this test. */
        @Suppress("unused")
        val LEGACY_TYPE_SENTINEL: List<Class<*>> = listOf(
            ScreenProfile::class.java,
            PixelWindowInsets::class.java,
        )
    }
}
