package com.purride.pixelui

import com.purride.pixelui.internal.PixelUiRuntime
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM contracts for locale fallback, formatting, precedence, and inherited providers. */
class PixelLocalizationsTest {
    /** Resolver order is exact, language, configured default, then terminal English. */
    @Test
    fun resolverUsesDeterministicExactLanguageDefaultAndEnglishFallbacks() {
        /** Exact regional/script bundle that must beat its language fallback. */
        val exactBundle = customBundle(localeTag = "zh-Hans-CN", marker = "EXACT")
        /** Language bundle serving unsupported Chinese script/region requests. */
        val languageBundle = customBundle(localeTag = "zh", marker = "LANGUAGE")
        /** Language-only French bundle reached through the configured regional default. */
        val defaultBundle = customBundle(localeTag = "fr", marker = "DEFAULT")
        /** Consumer exact delegate containing every non-built-in resolution branch. */
        val delegate = PixelLocalizationDelegate.fromBundles(
            exactBundle,
            languageBundle,
            defaultBundle,
        )
        /** Resolver whose default exercises default-locale language fallback. */
        val resolver = PixelLocalizationResolver(
            delegate = delegate,
            defaultLocale = PixelLocale("fr-CA"),
        )

        assertSame(exactBundle, resolver.resolveBundle(PixelLocale("zh-Hans-CN")))
        assertSame(languageBundle, resolver.resolveBundle(PixelLocale("zh-Hant-TW")))
        assertSame(defaultBundle, resolver.resolveBundle(PixelLocale("de-DE")))

        /** Resolver with no supported requested/default bundle must terminate at built-in English. */
        val englishFallbackResolver = PixelLocalizationResolver(
            defaultLocale = PixelLocale("es-MX"),
        )
        assertSame(
            PixelLocalizationBundle.English,
            englishFallbackResolver.resolveBundle(PixelLocale("de-DE")),
        )

        /** First preference language fallback must precede the second preference's exact match. */
        val firstLanguageBundle = customBundle(localeTag = "xx", marker = "FIRST-LANGUAGE")
        /** Exact second preference used to verify requested-list ordering. */
        val secondExactBundle = customBundle(localeTag = "fr-CA", marker = "SECOND-EXACT")
        /** Resolver containing both competing preference candidates. */
        val preferenceResolver = PixelLocalizationResolver(
            delegate = PixelLocalizationDelegate.fromBundles(
                firstLanguageBundle,
                secondExactBundle,
            ),
        )
        assertSame(
            firstLanguageBundle,
            preferenceResolver.resolveBundle(
                listOf(PixelLocale("xx-ZZ"), PixelLocale("fr-CA")),
            ),
        )
    }

    /** Built-in English and Chinese bundles expose complete labels and deterministic formatting. */
    @Test
    fun builtInBundlesProvideEnglishChineseAndNumericFormatting() {
        /** English terminal bundle used by legacy-compatible fallback paths. */
        val english = PixelLocalizationBundle.English
        /** Language-level Chinese bundle selected for `zh-*` requests. */
        val chinese = PixelLocalizationBundle.Chinese

        assertEquals("Button", english.labels.button)
        assertEquals("Navigation bar", english.navigationBar)
        assertEquals("按钮", chinese.labels.button)
        assertEquals("导航侧栏", chinese.navigationRail)
        assertEquals("-2147483648", english.formatInteger(Int.MIN_VALUE))
        assertEquals("9223372036854775807", chinese.formatInteger(Long.MAX_VALUE))
        assertEquals("0%", english.formatPercent(0f))
        assertEquals("13%", english.formatPercent(0.125f))
        assertEquals("100%", chinese.formatPercent(1f))
        assertSame(PixelLocalizationBundle.English, PixelLocalizationBundle.Default)
    }

    /** Consumers can install an exact custom bundle and replace both numeric formatter contracts. */
    @Test
    fun consumerCustomBundleOverridesLabelsAndFormatters() {
        /** Custom exact locale selected without exposing java.util.Locale in public API. */
        val locale = PixelLocale("ar-EG")
        /** Consumer bundle with observable application-owned formatting policies. */
        val custom = PixelLocalizationBundle(
            locale = locale,
            labels = PixelLocalizationBundle.English.labels.copy(button = "CUSTOM BUTTON"),
            navigationBar = "CUSTOM BAR",
            navigationRail = "CUSTOM RAIL",
            integerFormatter = PixelIntegerFormatter { value -> "N[$value]" },
            percentFormatter = PixelPercentFormatter { fraction -> "P[$fraction]" },
        )
        /** Exact resolver proving custom delegates take precedence over built-ins. */
        val resolver = PixelLocalizationResolver(
            delegate = PixelLocalizationDelegate.fromBundles(custom),
        )

        assertSame(custom, resolver.resolveBundle(locale))
        assertEquals("CUSTOM BUTTON", custom.labels.button)
        assertEquals("N[-42]", custom.formatInteger(-42))
        assertEquals("P[0.25]", custom.formatPercent(0.25f))
    }

    /** Component text precedence remains explicit, provider, theme, then English. */
    @Test
    fun textResolverAppliesFixedPrecedenceAndRejectsBlankLayers() {
        assertEquals(
            "EXPLICIT",
            PixelLocalizationResolver.resolveText(
                explicitText = "EXPLICIT",
                providerText = "PROVIDER",
                themeText = "THEME",
                englishFallback = "ENGLISH",
            ),
        )
        assertEquals(
            "PROVIDER",
            PixelLocalizationResolver.resolveText(null, "PROVIDER", "THEME", "ENGLISH"),
        )
        assertEquals(
            "THEME",
            PixelLocalizationResolver.resolveText(null, null, "THEME", "ENGLISH"),
        )
        assertEquals(
            "ENGLISH",
            PixelLocalizationResolver.resolveText(null, null, null, "ENGLISH"),
        )

        assertIllegalArgument {
            PixelLocalizationResolver.resolveText(" ", "PROVIDER", "THEME", "ENGLISH")
        }
        assertIllegalArgument {
            PixelLocalizationResolver.resolveText(null, "\t", "THEME", "ENGLISH")
        }
        assertIllegalArgument {
            PixelLocalizationResolver.resolveText(null, null, "\n", "ENGLISH")
        }
        assertIllegalArgument {
            PixelLocalizationResolver.resolveText(null, null, null, "")
        }
    }

    /** Bundle and delegate validation prevents blank output and ambiguous exact locale ownership. */
    @Test
    fun bundleAndDelegateRejectBlankInvalidAndMismatchedValues() {
        assertIllegalArgument {
            PixelLocalizationBundle(
                locale = PixelLocale.English,
                labels = PixelLabelTokens.Default,
                navigationBar = " ",
                navigationRail = "Navigation rail",
            )
        }
        assertIllegalArgument {
            PixelLocalizationBundle(
                locale = PixelLocale.English,
                labels = PixelLabelTokens.Default,
                navigationBar = "Navigation bar",
                navigationRail = "\t",
            )
        }

        /** Bundle whose invalid consumer integer formatter fails at its public output boundary. */
        val blankIntegerBundle = PixelLocalizationBundle(
            locale = PixelLocale("en-US"),
            labels = PixelLabelTokens.Default,
            navigationBar = "Navigation bar",
            navigationRail = "Navigation rail",
            integerFormatter = PixelIntegerFormatter { " " },
        )
        assertIllegalArgument { blankIntegerBundle.formatInteger(1) }

        /** Bundle whose invalid consumer percentage formatter fails at its public output boundary. */
        val blankPercentBundle = PixelLocalizationBundle(
            locale = PixelLocale("en-GB"),
            labels = PixelLabelTokens.Default,
            navigationBar = "Navigation bar",
            navigationRail = "Navigation rail",
            percentFormatter = PixelPercentFormatter { "\t" },
        )
        assertIllegalArgument { blankPercentBundle.formatPercent(0.5f) }
        assertIllegalArgument { blankPercentBundle.formatPercent(-0.01f) }
        assertIllegalArgument { blankPercentBundle.formatPercent(1.01f) }
        assertIllegalArgument { blankPercentBundle.formatPercent(Float.NaN) }

        /** Canonically duplicate locale registrations must not create ambiguous exact resolution. */
        val duplicateEnglish = customBundle(localeTag = "EN", marker = "DUPLICATE")
        assertIllegalArgument {
            PixelLocalizationDelegate.fromBundles(
                PixelLocalizationBundle.English,
                duplicateEnglish,
            )
        }

        /** Invalid delegate deliberately returning a French bundle for every exact request. */
        val mismatchedDelegate = PixelLocalizationDelegate {
            customBundle(localeTag = "fr", marker = "WRONG")
        }
        /** Resolver must reject delegate incoherence before considering later fallbacks. */
        val mismatchedResolver = PixelLocalizationResolver(delegate = mismatchedDelegate)
        assertIllegalArgument { mismatchedResolver.resolveBundle(PixelLocale("de-DE")) }
    }

    /** Localization remains absent until a provider is explicitly inserted into the widget tree. */
    @Test
    fun inheritedLocalizationIsExplicitAndNestedProviderWins() {
        assertNull(PixelLocalizations.maybeOf(EmptyBuildContext))
        assertNull(PixelLocalizations.maybeLocaleOf(EmptyBuildContext))
        assertThrows(IllegalStateException::class.java) {
            PixelLocalizations.of(EmptyBuildContext)
        }
        assertThrows(IllegalStateException::class.java) {
            PixelLocalizations.localeOf(EmptyBuildContext)
        }

        /** Off-screen runtime resolving nested inherited providers without Android dependencies. */
        val runtime = PixelUiRuntime()
        /** Observation proving Host locale injection alone does not install localization. */
        var hostOnlyBundle: PixelLocalizationBundle? = PixelLocalizationBundle.Default
        /** Bundle observed by a consumer between outer and inner provider boundaries. */
        var outerBundle: PixelLocalizationBundle? = null
        /** Active locale observed at the outer provider boundary. */
        var outerLocale: PixelLocale? = null
        /** Nearest bundle observed below the nested provider. */
        var innerBundle: PixelLocalizationBundle? = null
        /** Requested locale retained even though the inner bundle falls back to language `zh`. */
        var innerLocale: PixelLocale? = null
        try {
            runtime.render(
                root = HostCapabilities(
                    data = HostCapabilitiesData.Default.copy(
                        locales = listOf(PixelLocale("zh-Hans-CN")),
                    ),
                    child = Builder { hostContext ->
                        hostOnlyBundle = PixelLocalizations.maybeOf(hostContext)
                        SizedBox(width = 1, height = 1)
                    },
                ),
                logicalWidth = 2,
                logicalHeight = 2,
            )
            assertNull(hostOnlyBundle)

            runtime.render(
                root = PixelLocalizations(
                    locale = PixelLocale.English,
                    bundle = PixelLocalizationBundle.English,
                    child = Builder { outerContext ->
                        outerBundle = PixelLocalizations.of(outerContext)
                        outerLocale = PixelLocalizations.localeOf(outerContext)
                        PixelLocalizations(
                            locale = PixelLocale("zh-Hans-CN"),
                            resolver = PixelLocalizationResolver.Default,
                            child = Builder { innerContext ->
                                innerBundle = PixelLocalizations.of(innerContext)
                                innerLocale = PixelLocalizations.localeOf(innerContext)
                                SizedBox(width = 1, height = 1)
                            },
                        )
                    },
                ),
                logicalWidth = 2,
                logicalHeight = 2,
            )
        } finally {
            runtime.dispose()
        }

        assertSame(PixelLocalizationBundle.English, outerBundle)
        assertEquals(PixelLocale.English, outerLocale)
        assertSame(PixelLocalizationBundle.Chinese, innerBundle)
        assertEquals(PixelLocale("zh-Hans-CN"), innerLocale)
    }

    /** Host-aware provider rebuilds only for distinct preferred locale and retains consumer State. */
    @Test
    fun hostAwareProviderUsesPreferredLocaleWithDistinctRetainedUpdates() {
        /** Exact bundles returned by the counting consumer delegate. */
        val english = customBundle(localeTag = "en", marker = "COUNT-EN")
        val chinese = customBundle(localeTag = "zh", marker = "COUNT-ZH")
        /** Number of exact delegate loads, which also exposes provider rebuild count. */
        var delegateLoadCount = 0
        /** Counting exact delegate used instead of observing private provider internals. */
        val countingDelegate = PixelLocalizationDelegate { locale ->
            delegateLoadCount += 1
            when (locale) {
                english.locale -> english
                chinese.locale -> chinese
                else -> null
            }
        }
        /** Resolver installed by the explicit provider. */
        val resolver = PixelLocalizationResolver(delegate = countingDelegate)
        /** State allocation sink proving provider locale changes retain the exact dependent State. */
        val createdStates = mutableListOf<LocalizationConsumerState>()
        /** Exact dependent Widget reused through equal and changed Host capability snapshots. */
        val consumer = LocalizationConsumerWidget(createdStates, key = "localization-consumer")
        /** Exact provider Widget proving Host dependency dirtiness is value-distinct. */
        val provider = PixelLocalizationProvider(
            child = consumer,
            resolver = resolver,
            key = "localization-provider",
        )
        /** Retained runtime driving consecutive Host locale snapshots. */
        val runtime = PixelUiRuntime()
        try {
            runtime.render(
                root = hostLocalizationRoot(locale = PixelLocale("en-US"), child = provider),
                logicalWidth = 2,
                logicalHeight = 2,
            )
            /** Only dependent State allocated during the initial provider mount. */
            val retainedState = createdStates.single()
            /** Initial exact+language lookup count before equal Host reconciliation. */
            val initialLoadCount = delegateLoadCount

            assertEquals(1, retainedState.buildCount)
            assertSame(english, retainedState.latestBundle)

            runtime.render(
                root = hostLocalizationRoot(locale = PixelLocale("en-US"), child = provider),
                logicalWidth = 2,
                logicalHeight = 2,
            )

            assertEquals(initialLoadCount, delegateLoadCount)
            assertEquals(1, retainedState.buildCount)

            runtime.render(
                root = hostLocalizationRoot(locale = PixelLocale("zh-Hans-CN"), child = provider),
                logicalWidth = 2,
                logicalHeight = 2,
            )

            assertTrue(delegateLoadCount > initialLoadCount)
            assertEquals(2, retainedState.buildCount)
            assertSame(chinese, retainedState.latestBundle)
            assertEquals(PixelLocale("zh-Hans-CN"), retainedState.latestLocale)
            assertEquals(1, createdStates.size)
            assertSame(retainedState, createdStates.single())
        } finally {
            runtime.dispose()
        }
    }

    /** Host-aware provider checks every ordered preference while retaining the first active locale. */
    @Test
    fun hostAwareProviderResolvesCompletePreferenceList() {
        /** Supported second preference expected before configured default and English. */
        val supportedBundle = customBundle(localeTag = "fr-CA", marker = "SECOND-PREFERENCE")
        /** Resolver whose configured default must lose to the supported second Host preference. */
        val resolver = PixelLocalizationResolver(
            delegate = PixelLocalizationDelegate.fromBundles(supportedBundle),
            defaultLocale = PixelLocale.English,
        )
        /** Bundle observed below the explicit Host-aware provider. */
        var observedBundle: PixelLocalizationBundle? = null
        /** Active locale expected to preserve the first unsupported Host preference. */
        var observedLocale: PixelLocale? = null
        /** Provider consumer capturing both resolution and active-locale contracts. */
        val provider = PixelLocalizationProvider(
            resolver = resolver,
            child = Builder { context ->
                observedBundle = PixelLocalizations.of(context)
                observedLocale = PixelLocalizations.localeOf(context)
                SizedBox(width = 1, height = 1)
            },
        )
        /** Pure JVM runtime evaluating a Host list without platform locale classes. */
        val runtime = PixelUiRuntime()
        try {
            runtime.render(
                root = hostLocalizationRoot(
                    locales = listOf(PixelLocale("de-DE"), PixelLocale("fr-CA")),
                    child = provider,
                ),
                logicalWidth = 2,
                logicalHeight = 2,
            )
        } finally {
            runtime.dispose()
        }

        assertSame(supportedBundle, observedBundle)
        assertEquals(PixelLocale("de-DE"), observedLocale)
    }

    /** Explicit locale override wins over Host locale and nested provider remains nearest. */
    @Test
    fun providerLocaleOverrideIgnoresHostAndSupportsNesting() {
        /** Inner consumer observation below an explicit Chinese override. */
        var innerBundle: PixelLocalizationBundle? = null
        /** Outer consumer observation below an explicit English provider. */
        var outerBundle: PixelLocalizationBundle? = null
        /** Outer dependent build count expected to ignore Host locale changes. */
        var outerBuildCount = 0
        /** Inner dependent build count expected to ignore Host under an explicit locale override. */
        var innerBuildCount = 0
        /** Explicit inner provider independent of subsequent Host locale changes. */
        val innerProvider = PixelLocalizationProvider(
            child = Builder { context ->
                innerBuildCount += 1
                innerBundle = PixelLocalizations.of(context)
                SizedBox(width = 1, height = 1)
            },
            localeOverride = PixelLocale("zh-Hans-CN"),
        )
        /** Explicit outer provider wrapping both the outer observation and nested override. */
        val outerProvider = PixelLocalizationProvider(
            child = Builder { context ->
                outerBuildCount += 1
                outerBundle = PixelLocalizations.of(context)
                innerProvider
            },
            localeOverride = PixelLocale.English,
        )
        /** Retained runtime proving nearest-scope resolution for both provider levels. */
        val runtime = PixelUiRuntime()
        try {
            runtime.render(
                root = hostLocalizationRoot(locale = PixelLocale("en-US"), child = outerProvider),
                logicalWidth = 2,
                logicalHeight = 2,
            )

            runtime.render(
                root = hostLocalizationRoot(locale = PixelLocale("de-DE"), child = outerProvider),
                logicalWidth = 2,
                logicalHeight = 2,
            )
        } finally {
            runtime.dispose()
        }

        assertSame(PixelLocalizationBundle.English, outerBundle)
        assertSame(PixelLocalizationBundle.Chinese, innerBundle)
        assertEquals(1, outerBuildCount)
        assertEquals(1, innerBuildCount)
    }

    /** Creates a complete observable custom bundle for one canonical [localeTag]. */
    private fun customBundle(localeTag: String, marker: String): PixelLocalizationBundle {
        return PixelLocalizationBundle(
            locale = PixelLocale(localeTag),
            labels = PixelLocalizationBundle.English.labels.copy(button = marker),
            navigationBar = "$marker BAR",
            navigationRail = "$marker RAIL",
        )
    }

    /** Wraps [child] in a Host capability scope with one preferred [locale]. */
    private fun hostLocalizationRoot(locale: PixelLocale, child: Widget): Widget {
        return hostLocalizationRoot(locales = listOf(locale), child = child)
    }

    /** Wraps [child] in a Host capability scope with ordered preferred [locales]. */
    private fun hostLocalizationRoot(locales: List<PixelLocale>, child: Widget): Widget {
        return HostCapabilities(
            data = HostCapabilitiesData.Default.copy(locales = locales),
            child = child,
            key = "localization-host",
        )
    }

    /** Asserts argument validation without duplicating exception boilerplate. */
    private fun assertIllegalArgument(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java, block)
    }

    /** Stateful exact dependent used to observe provider rebuilds and retained identity. */
    private class LocalizationConsumerWidget(
        /** Shared allocation sink retained across declarative Host snapshots. */
        private val createdStates: MutableList<LocalizationConsumerState>,
        key: Any,
    ) : StatefulWidget(key = key) {
        /** Allocates and records the only State permitted for this stable retained identity. */
        override fun createState(): State<out StatefulWidget> {
            return LocalizationConsumerState().also(createdStates::add)
        }
    }

    /** Mutable dependent State recording nearest bundle, locale, and exact build count. */
    private class LocalizationConsumerState : State<LocalizationConsumerWidget>() {
        /** Number of retained builds completed by this exact State. */
        var buildCount: Int = 0
            private set

        /** Most recent nearest bundle observed through the inherited provider. */
        lateinit var latestBundle: PixelLocalizationBundle
            private set

        /** Most recent requested locale observed independently from fallback bundle locale. */
        lateinit var latestLocale: PixelLocale
            private set

        /** Subscribes to both inherited values and emits a minimal renderable leaf. */
        override fun build(context: BuildContext): Widget {
            buildCount += 1
            latestBundle = PixelLocalizations.of(context)
            latestLocale = PixelLocalizations.localeOf(context)
            return SizedBox(width = 1, height = 1)
        }
    }

    /** Context fixture proving localization is absent without an explicit provider. */
    private object EmptyBuildContext : BuildContext {
        /** Minimal Widget identity required by the BuildContext protocol. */
        override val widget: Widget = EmptyWidget

        /** No inherited dependency exists in the empty fixture. */
        override fun <T : InheritedWidget> dependOnInheritedWidgetOfExactType(type: KClass<T>): T? = null

        /** No inherited value exists in the empty fixture. */
        override fun <T : InheritedWidget> getInheritedWidgetOfExactType(type: KClass<T>): T? = null

        /** The empty fixture has no listenable dependencies. */
        override fun watch(listenable: Listenable?) = Unit
    }

    /** Minimal leaf used only as the empty BuildContext's Widget identity. */
    private object EmptyWidget : Widget {
        /** The fixture needs no retained key. */
        override val key: Any? = null
    }
}
