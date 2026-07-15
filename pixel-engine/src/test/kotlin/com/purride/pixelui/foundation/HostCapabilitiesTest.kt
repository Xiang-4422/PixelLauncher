package com.purride.pixelui

import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the platform-neutral host capability value and inherited-provider contracts. */
class HostCapabilitiesTest {
    /** Canonical BCP-47 casing produces value-equal locale identifiers. */
    @Test
    fun localeCanonicalizesBcp47TagAndUsesEnglishDefault() {
        /** Locale built from deliberately non-canonical casing. */
        val canonicalized = PixelLocale("ZH-hans-cn")

        assertEquals("zh-Hans-CN", canonicalized.languageTag)
        assertEquals(PixelLocale("zh-Hans-CN"), canonicalized)
        assertEquals(PixelLocale("zh-Hans-CN").hashCode(), canonicalized.hashCode())
        assertSame(PixelLocale.English, PixelLocale.Default)
        assertEquals("en", PixelLocale.Default.languageTag)
        assertEquals("zh-Hans-CN", canonicalized.toString())
    }

    /** Blank, malformed, and underscore-separated locale tags fail instead of truncating. */
    @Test
    fun localeRejectsInvalidBcp47Tags() {
        assertIllegalArgument { PixelLocale("") }
        assertIllegalArgument { PixelLocale("   ") }
        assertIllegalArgument { PixelLocale("en-") }
        assertIllegalArgument { PixelLocale("en_US") }
    }

    /** Logical feature geometry supports line folds while rejecting invalid edge ranges. */
    @Test
    fun logicalRectAndDisplayFeatureRemainPlatformNeutralValues() {
        /** Zero-width rectangle modelling a vertical fold line. */
        val foldBounds = PixelLogicalRect(left = 320f, top = 0f, right = 320f, bottom = 800f)
        /** Logical fold detached from every Android or AndroidX geometry type. */
        val fold = PixelDisplayFeature(
            bounds = foldBounds,
            type = PixelDisplayFeatureType.FOLD,
            state = PixelDisplayFeatureState.HALF_OPENED,
        )

        assertEquals(0f, foldBounds.width)
        assertEquals(800f, foldBounds.height)
        assertEquals(PixelDisplayFeatureType.FOLD, fold.type)
        assertEquals(PixelDisplayFeatureState.HALF_OPENED, fold.state)
        assertIllegalArgument {
            PixelLogicalRect(left = 2f, top = 0f, right = 1f, bottom = 1f)
        }
        assertIllegalArgument {
            PixelLogicalRect(left = 0f, top = 2f, right = 1f, bottom = 1f)
        }
        assertIllegalArgument {
            PixelLogicalRect(left = Float.NaN, top = 0f, right = 1f, bottom = 1f)
        }
        assertIllegalArgument {
            PixelLogicalRect(left = 0f, top = 0f, right = Float.POSITIVE_INFINITY, bottom = 1f)
        }
        assertIllegalArgument {
            PixelLogicalRect(
                left = -Float.MAX_VALUE,
                top = 0f,
                right = Float.MAX_VALUE,
                bottom = 1f,
            )
        }
        assertIllegalArgument {
            PixelLogicalRect(
                left = 0f,
                top = -Float.MAX_VALUE,
                right = 1f,
                bottom = Float.MAX_VALUE,
            )
        }
    }

    /** Capability lists own their inputs and reject mutation through Java-compatible casts. */
    @Test
    fun capabilityListsAreDefensiveUnmodifiableSnapshots() {
        /** Mutable source used to prove constructor ownership. */
        val sourceLocales = mutableListOf(PixelLocale("en-US"))
        /** Mutable feature source used to prove constructor ownership. */
        val sourceFeatures = mutableListOf(
            PixelDisplayFeature(
                bounds = PixelLogicalRect(0f, 0f, 10f, 4f),
                type = PixelDisplayFeatureType.CUTOUT,
            ),
        )
        /** Snapshot that must not observe later source mutations. */
        val data = HostCapabilitiesData(
            locales = sourceLocales,
            displayFeatures = sourceFeatures,
        )

        sourceLocales += PixelLocale("fr")
        sourceFeatures.clear()

        assertEquals(listOf(PixelLocale("en-US")), data.locales)
        assertEquals(1, data.displayFeatures.size)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (data.locales as MutableList<PixelLocale>).add(PixelLocale("de"))
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (data.displayFeatures as MutableList<PixelDisplayFeature>).clear()
        }
    }

    /** Every numeric capability rejects NaN, infinity, zero, or negative values as applicable. */
    @Test
    fun capabilityValidatesLocaleAndNumericRanges() {
        assertIllegalArgument { HostCapabilitiesData(locales = emptyList()) }
        assertIllegalArgument {
            HostCapabilitiesData(locales = listOf(PixelLocale("en"), PixelLocale("EN")))
        }
        assertIllegalArgument { HostCapabilitiesData(textScaleFactor = 0f) }
        assertIllegalArgument { HostCapabilitiesData(textScaleFactor = Float.NaN) }
        assertIllegalArgument { HostCapabilitiesData(textScaleFactor = Float.POSITIVE_INFINITY) }
        assertIllegalArgument { HostCapabilitiesData(density = 0f) }
        assertIllegalArgument { HostCapabilitiesData(density = -1f) }
        assertIllegalArgument { HostCapabilitiesData(density = Float.NaN) }
        assertIllegalArgument { HostCapabilitiesData(density = Float.POSITIVE_INFINITY) }
        assertIllegalArgument { HostCapabilitiesData(refreshRateHz = 0f) }
        assertIllegalArgument { HostCapabilitiesData(refreshRateHz = -60f) }
        assertIllegalArgument { HostCapabilitiesData(refreshRateHz = Float.NaN) }
        assertIllegalArgument { HostCapabilitiesData(refreshRateHz = Float.POSITIVE_INFINITY) }
    }

    /** Manual copy preserves value equality and defensively owns replacement list inputs. */
    @Test
    fun capabilityCopyAndEqualityCoverEveryValue() {
        /** Feature included in equality and hash calculations. */
        val hinge = PixelDisplayFeature(
            bounds = PixelLogicalRect(400f, 0f, 420f, 900f),
            type = PixelDisplayFeatureType.HINGE,
            state = PixelDisplayFeatureState.FLAT,
        )
        /** Fully populated baseline snapshot. */
        val original = HostCapabilitiesData(
            locales = listOf(PixelLocale("ar-EG"), PixelLocale("en")),
            layoutDirection = TextDirection.RTL,
            textScaleFactor = 1.25f,
            highContrast = true,
            motionSettings = PixelMotionSettings(animatorDurationScale = 0.5f, reduceMotion = true),
            density = 2.75f,
            refreshRateHz = 120f,
            displayFeatures = listOf(hinge),
        )
        /** Independent snapshot produced by the manual copy contract. */
        val equalCopy = original.copy()
        /** Mutable replacement proving that copy also takes list ownership. */
        val replacementLocales = mutableListOf(PixelLocale("ja-JP"))
        /** Copy with exactly one changed capability. */
        val changed = original.copy(locales = replacementLocales)

        replacementLocales += PixelLocale("en")

        assertEquals(original, equalCopy)
        assertEquals(original.hashCode(), equalCopy.hashCode())
        assertEquals(listOf(PixelLocale("ja-JP")), changed.locales)
        assertFalse(original == changed)
        assertTrue(original.toString().contains("refreshRateHz=120.0"))
        assertEquals(HostCapabilitiesData(), HostCapabilitiesData.Default)
        assertNull(HostCapabilitiesData.Default.refreshRateHz)
    }

    /** Axis-specific dp resolvers use the documented inclusive breakpoints. */
    @Test
    fun windowSizeClassesResolveWidthAndHeightBoundaries() {
        assertEquals(PixelWindowSizeClass.COMPACT, PixelWindowSizeClass.forWidthDp(599.99f))
        assertEquals(PixelWindowSizeClass.MEDIUM, PixelWindowSizeClass.forWidthDp(600f))
        assertEquals(PixelWindowSizeClass.MEDIUM, PixelWindowSizeClass.forWidthDp(839.99f))
        assertEquals(PixelWindowSizeClass.EXPANDED, PixelWindowSizeClass.forWidthDp(840f))
        assertEquals(PixelWindowSizeClass.COMPACT, PixelWindowSizeClass.forHeightDp(479.99f))
        assertEquals(PixelWindowSizeClass.MEDIUM, PixelWindowSizeClass.forHeightDp(480f))
        assertEquals(PixelWindowSizeClass.MEDIUM, PixelWindowSizeClass.forHeightDp(899.99f))
        assertEquals(PixelWindowSizeClass.EXPANDED, PixelWindowSizeClass.forHeightDp(900f))
        assertIllegalArgument { PixelWindowSizeClass.forWidthDp(-1f) }
        assertIllegalArgument { PixelWindowSizeClass.forWidthDp(Float.NaN) }
        assertIllegalArgument { PixelWindowSizeClass.forHeightDp(Float.POSITIVE_INFINITY) }
    }

    /** Capability value and provider signatures remain independent from Android and WindowManager. */
    @Test
    fun publicCapabilityApiDoesNotExposePlatformTypes() {
        /** Public capability classes whose declared Java surface is required to stay portable. */
        val capabilityTypes = listOf(
            PixelLocale::class.java,
            PixelLogicalRect::class.java,
            PixelDisplayFeatureType::class.java,
            PixelDisplayFeatureState::class.java,
            PixelDisplayFeature::class.java,
            PixelWindowSizeClass::class.java,
            HostCapabilitiesData::class.java,
            HostCapabilities::class.java,
        )
        /** Java-visible declarations include generic arguments that a raw type-only check misses. */
        val publicSignatures = capabilityTypes.flatMap { type ->
            type.declaredConstructors
                .filter { constructor -> Modifier.isPublic(constructor.modifiers) }
                .map { constructor -> constructor.toGenericString() } +
                type.declaredMethods
                    .filter { method -> Modifier.isPublic(method.modifiers) }
                    .map { method -> method.toGenericString() } +
                type.declaredFields
                    .filter { field -> Modifier.isPublic(field.modifiers) }
                    .map { field -> field.toGenericString() }
        }
        /** Forbidden package fragments cover Android framework and AndroidX Window abstractions. */
        val forbiddenFragments = listOf("android.", "androidx.window.")

        publicSignatures.forEach { signature ->
            forbiddenFragments.forEach { fragment ->
                assertFalse("Platform type leaked through $signature", signature.contains(fragment))
            }
        }
    }

    /** Equal snapshots suppress inherited notification while each individual field change notifies. */
    @Test
    fun inheritedProviderUsesDistinctSnapshotUpdatesAndAccessors() {
        /** Stable leaf used as the inherited provider child. */
        val child = TestLeafWidget
        /** Fully known baseline to make every single-field mutation explicit. */
        val baseline = HostCapabilitiesData(
            locales = listOf(PixelLocale("en")),
            refreshRateHz = 60f,
        )
        /** Provider representing the prior retained-tree configuration. */
        val oldProvider = HostCapabilities(data = baseline, child = child)
        /** Every entry changes exactly one observable capability. */
        val changedSnapshots = listOf(
            baseline.copy(locales = listOf(PixelLocale("fr"))),
            baseline.copy(layoutDirection = TextDirection.RTL),
            baseline.copy(textScaleFactor = 1.5f),
            baseline.copy(highContrast = true),
            baseline.copy(motionSettings = PixelMotionSettings(reduceMotion = true)),
            baseline.copy(density = 2f),
            baseline.copy(refreshRateHz = 120f),
            baseline.copy(
                displayFeatures = listOf(
                    PixelDisplayFeature(PixelLogicalRect(10f, 0f, 12f, 100f)),
                ),
            ),
        )

        assertFalse(
            HostCapabilities(data = baseline.copy(), child = child)
                .updateShouldNotify(oldProvider),
        )
        changedSnapshots.forEach { changedSnapshot ->
            assertTrue(
                "Expected changed capability to notify: $changedSnapshot",
                HostCapabilities(data = changedSnapshot, child = child)
                    .updateShouldNotify(oldProvider),
            )
        }

        /** Context with the explicit provider available through dependency lookup. */
        val explicitContext = RecordingBuildContext(oldProvider)
        /** Context proving the scope-less `of` fallback. */
        val emptyContext = RecordingBuildContext(null)

        assertSame(baseline, HostCapabilities.maybeOf(explicitContext))
        assertSame(baseline, HostCapabilities.of(explicitContext))
        assertEquals(2, explicitContext.dependencyReadCount)
        assertNull(HostCapabilities.maybeOf(emptyContext))
        assertSame(HostCapabilitiesData.Default, HostCapabilities.of(emptyContext))
    }

    /** Asserts that [block] fails through the public argument-validation contract. */
    private fun assertIllegalArgument(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    /** Minimal leaf used only to satisfy inherited provider and context widget contracts. */
    private object TestLeafWidget : Widget {
        /** The fixture has no retained identity requirement. */
        override val key: Any? = null
    }

    /** Build context fixture that records dependency reads for [HostCapabilities]. */
    private class RecordingBuildContext(
        /** Explicit inherited provider returned to matching dependency requests. */
        private val inheritedCapabilities: HostCapabilities?,
    ) : BuildContext {
        /** Context fixture widget identity. */
        override val widget: Widget = TestLeafWidget

        /** Number of dependency-registering inherited lookups performed by accessors. */
        var dependencyReadCount: Int = 0
            private set

        /** Returns the configured provider only for the exact capability type. */
        override fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T? {
            dependencyReadCount += 1
            @Suppress("UNCHECKED_CAST")
            return if (type == HostCapabilities::class) inheritedCapabilities as T? else null
        }

        /** Mirrors the same fixture lookup without recording a dependency. */
        override fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return if (type == HostCapabilities::class) inheritedCapabilities as T? else null
        }

        /** The fixture has no listenable dependencies. */
        override fun watch(listenable: Listenable?) = Unit
    }
}
