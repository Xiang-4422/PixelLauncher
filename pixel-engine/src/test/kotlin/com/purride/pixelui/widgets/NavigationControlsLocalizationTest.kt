package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.NavigationRail
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelLabelTokens
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.PixelLocalizationBundle
import com.purride.pixelui.PixelLocalizations
import com.purride.pixelui.PixelMultiStackNavigatorController
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import kotlin.jvm.functions.Function1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Localization precedence, retained state, and JVM compatibility for NavigationBar/Rail. */
class NavigationControlsLocalizationTest {
    /** All four public entry points consume Chinese provider text without translating destinations. */
    @Test
    fun chineseProviderLocalizesAllFourEntryPointsAndOverridesThemeStateLabels() {
        /** Explicit destinations remain business content and must never be provider-translated. */
        val destinations = destinations()
        /** Loading and Error together expose both localized state channels on groups and items. */
        val states = PixelControlStateSet.of(PixelControlState.Loading, PixelControlState.Error)
        /** Theme sentinels prove an installed provider has higher state-label precedence. */
        val themeTokens = PixelThemeTokens.Default.copy(
            labels = PixelLabelTokens.Default.copy(
                loading = "THEME LOADING",
                error = "THEME ERROR",
            ),
        )
        /** Independent controller owners exercise both controller-bound overloads. */
        val barController = PixelMultiStackNavigatorController(initialStackId = "home")
        /** Rail controller remains unattached so localization is isolated from Navigator rendering. */
        val railController = PixelMultiStackNavigatorController(initialStackId = "home")
        /** Matrix includes direct and controller variants for both layout families. */
        val cases = listOf(
            NavigationLocalizationCase(
                name = "direct bar",
                expectedContainerLabel = "导航栏",
                build = {
                    NavigationBar(
                        destinations = destinations,
                        selectedId = "home",
                        onSelected = {},
                        states = states,
                    )
                },
            ),
            NavigationLocalizationCase(
                name = "controller bar",
                expectedContainerLabel = "导航栏",
                build = {
                    NavigationBar(
                        destinations = destinations,
                        controller = barController,
                        states = states,
                    )
                },
            ),
            NavigationLocalizationCase(
                name = "direct rail",
                expectedContainerLabel = "导航侧栏",
                build = {
                    NavigationRail(
                        destinations = destinations,
                        selectedId = "home",
                        onSelected = {},
                        states = states,
                    )
                },
            ),
            NavigationLocalizationCase(
                name = "controller rail",
                expectedContainerLabel = "导航侧栏",
                build = {
                    NavigationRail(
                        destinations = destinations,
                        controller = railController,
                        states = states,
                    )
                },
            ),
        )
        /** Reused runtime mounts each real public entry point under the same provider contract. */
        val tester = PixelTester()
        try {
            cases.forEach { case ->
                tester.pumpWidget(
                    widget = PixelTheme(
                        tokens = themeTokens,
                        child = PixelLocalizations(
                            locale = PixelLocale("zh-CN"),
                            bundle = PixelLocalizationBundle.Chinese,
                            child = case.build(),
                        ),
                    ),
                    logicalWidth = 120,
                    logicalHeight = 72,
                )

                /** Localized collection node proves family-specific provider text resolution. */
                val collection = tester.semanticsNodesByLabel(case.expectedContainerLabel).single()
                assertEquals(case.name, "加载中", collection.value)
                assertEquals(case.name, "错误", collection.error)
                /** Destination labels remain mandatory explicit caller content. */
                val home = tester.semanticsNodesByLabel("Home").single()
                assertTrue(case.name, home.selected)
                assertEquals(case.name, "加载中", home.value)
                assertEquals(case.name, "错误", home.error)
                assertEquals(1, tester.semanticsNodesByLabel("Settings").size)
                assertTrue(tester.semanticsNodesByLabel("主页").isEmpty())
                assertFalse(collection.value == "THEME LOADING")
                assertFalse(collection.error == "THEME ERROR")
            }
        } finally {
            tester.dispose()
        }
    }

    /** Explicit, provider, theme, and English layers retain the documented legacy-sentinel order. */
    @Test
    fun containerAndStateLabelsFollowGrandfatheredPrecedence() {
        /** Theme-only state sentinels verify behavior when no localization provider is mounted. */
        val themeLabels = PixelLabelTokens.Default.copy(
            loading = "THEME LOADING",
            error = "THEME ERROR",
        )
        /** Shared state set exposes both fallback channels in a single render. */
        val states = PixelControlStateSet.of(PixelControlState.Loading, PixelControlState.Error)
        /** Runtime reused across no-provider, explicit override, and sentinel branches. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelTheme(
                    tokens = PixelThemeTokens.Default.copy(labels = themeLabels),
                    child = NavigationBar(
                        destinations = destinations(),
                        selectedId = "home",
                        onSelected = {},
                        states = states,
                    ),
                ),
                logicalWidth = 120,
                logicalHeight = 48,
            )
            /** No provider preserves the old English container and current theme state labels. */
            val noProvider = tester.semanticsNodesByLabel("Navigation bar").single()
            assertEquals("THEME LOADING", noProvider.value)
            assertEquals("THEME ERROR", noProvider.error)
            assertEquals("THEME LOADING", tester.semanticsNodesByLabel("Home").single().value)

            /** Controller-bound Rail proves a distinguishable custom explicit name always wins. */
            val controller = PixelMultiStackNavigatorController(initialStackId = "home")
            tester.pumpWidget(
                widget = PixelLocalizations(
                    locale = PixelLocale("zh"),
                    bundle = PixelLocalizationBundle.Chinese,
                    child = NavigationRail(
                        destinations = destinations(),
                        controller = controller,
                        states = states,
                        semanticLabel = "Primary destinations",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 72,
            )
            /** Explicit collection label wins while state labels still come from the provider. */
            val explicit = tester.semanticsNodesByLabel("Primary destinations").single()
            assertEquals("加载中", explicit.value)
            assertEquals("错误", explicit.error)
            assertTrue(tester.semanticsNodesByLabel("导航侧栏").isEmpty())

            tester.pumpWidget(
                widget = PixelLocalizations(
                    locale = PixelLocale("zh"),
                    bundle = PixelLocalizationBundle.Chinese,
                    child = NavigationRail(
                        destinations = destinations(),
                        selectedId = "home",
                        onSelected = {},
                        semanticLabel = "Navigation rail",
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 72,
            )
            /** Old overload cannot distinguish this exact English value from an omitted default. */
            assertEquals(1, tester.semanticsNodesByLabel("导航侧栏").size)
            assertTrue(tester.semanticsNodesByLabel("Navigation rail").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Provider replacement updates text without losing selected semantic identity or group focus. */
    @Test
    fun providerUpdatePreservesSelectionFocusAndStableDestinationIdentity() {
        /** Mutable bundle simulates a retained app switching its installed provider locale. */
        var bundle = PixelLocalizationBundle.English
        /** Stable selected id proves localization never owns business selection state. */
        val selectedId = "home"
        /** Loading keeps the single group focus while exposing a localized status value. */
        val states = PixelControlStateSet.of(PixelControlState.Loading)
        /** Builds a fresh declaration with stable provider, group, and destination keys. */
        fun buildTree(): Widget = PixelLocalizations(
            locale = bundle.locale,
            bundle = bundle,
            child = NavigationBar(
                destinations = destinations(),
                selectedId = selectedId,
                onSelected = {},
                states = states,
                key = "localized-bar",
            ),
            key = "localization-provider",
        )
        /** Retained runtime owns the focus node and semantic id across provider updates. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 48)
            assertTrue(tester.pressKey(PixelKey.TAB))
            /** Original selected destination identity captured before the provider update. */
            val englishHome = tester.semanticsNodesByLabel("Home").single()
            assertTrue(englishHome.selected)
            assertTrue(englishHome.focused)
            assertEquals("LOADING", englishHome.value)
            assertEquals(1, tester.semanticsNodesByLabel("Navigation bar").size)

            bundle = PixelLocalizationBundle.Chinese
            tester.pumpWidget(buildTree(), logicalWidth = 120, logicalHeight = 48)

            /** Same destination node must now expose provider state text without remounting. */
            val chineseHome = tester.semanticsNodesByLabel("Home").single()
            assertEquals(englishHome.id, chineseHome.id)
            assertTrue(chineseHome.selected)
            assertTrue(chineseHome.focused)
            assertEquals("加载中", chineseHome.value)
            assertEquals(1, tester.semanticsNodesByLabel("导航栏").size)
            assertTrue(tester.semanticsNodesByLabel("Navigation bar").isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Migration keeps all four public JVM methods and their Kotlin default bridges unchanged. */
    @Test
    fun localizationMigrationPreservesNavigationJvmDescriptorsAndDefaultBridges() {
        /** Generated Kotlin file owner containing all navigation top-level methods. */
        val owner = Class.forName("com.purride.pixelui.NavigationControlsKt")
        /** Primitive references avoid nullable platform descriptors in the expected signatures. */
        val intType = requireNotNull(Int::class.javaPrimitiveType)
        /** Boolean primitive shared by enabled, animation, and reselection parameters. */
        val booleanType = requireNotNull(Boolean::class.javaPrimitiveType)
        /** Controlled overload descriptor shared by Bar and Rail. */
        val directParameters = listOf(
            List::class.java,
            String::class.java,
            Function1::class.java,
            intType,
            booleanType,
            String::class.java,
            Any::class.java,
        )
        /** Controller overload descriptor shared by Bar and Rail. */
        val controllerParameters = listOf(
            List::class.java,
            PixelMultiStackNavigatorController::class.java,
            booleanType,
            booleanType,
            intType,
            booleanType,
            String::class.java,
            Any::class.java,
        )
        /** Exact methods include each original entry point and compiler-generated default bridge. */
        val expectedMethods = listOf(
            ExpectedJvmMethod("NavigationBar", directParameters),
            ExpectedJvmMethod("NavigationBar\$default", directParameters + listOf(intType, Any::class.java)),
            ExpectedJvmMethod("NavigationBarWithController", controllerParameters),
            ExpectedJvmMethod(
                "NavigationBarWithController\$default",
                controllerParameters + listOf(intType, Any::class.java),
            ),
            ExpectedJvmMethod("NavigationRail", directParameters),
            ExpectedJvmMethod("NavigationRail\$default", directParameters + listOf(intType, Any::class.java)),
            ExpectedJvmMethod("NavigationRailWithController", controllerParameters),
            ExpectedJvmMethod(
                "NavigationRailWithController\$default",
                controllerParameters + listOf(intType, Any::class.java),
            ),
        )

        expectedMethods.forEach { expected ->
            /** Exact reflective match rejects both renamed methods and descriptor drift. */
            val method = owner.declaredMethods.singleOrNull { candidate ->
                candidate.name == expected.name &&
                    candidate.parameterTypes.toList() == expected.parameterTypes
            }
            assertNotNull("Missing JVM method ${expected.name}${expected.parameterTypes}", method)
            assertSame(Widget::class.java, method?.returnType)
        }
    }

    /** Creates two enabled destinations with mandatory explicit visible and spoken labels. */
    private fun destinations(): List<PixelNavigationDestination> {
        /** Shared immutable alpha mask keeps localization tests independent from icon rendering. */
        val icon = icon()
        return listOf(
            PixelNavigationDestination(id = "home", label = "Home", icon = icon),
            PixelNavigationDestination(id = "settings", label = "Settings", icon = icon),
        )
    }

    /** Creates one opaque 3×3 icon whose pixels are deterministically theme-tinted. */
    private fun icon(): PixelIconData {
        /** Opaque mask pixels avoid introducing another semantic node or platform resource. */
        val pixels = IntArray(9) { PixelColor.White.argb }
        return PixelIconData(PixelBitmap(width = 3, height = 3, pixels = pixels))
    }
}

/** One real navigation entry-point factory and its localized container expectation. */
private data class NavigationLocalizationCase(
    /** Human-readable entry-point name included in state-label assertion failures. */
    val name: String,
    /** Provider-resolved collection name expected from this Bar or Rail family. */
    val expectedContainerLabel: String,
    /** Deferred public component construction under the active test provider. */
    val build: () -> Widget,
)

/** Exact generated JVM method name and ordered erased parameter types. */
private data class ExpectedJvmMethod(
    /** Generated static method name, including `$default` when applicable. */
    val name: String,
    /** Ordered erased JVM parameter classes forming the existing descriptor. */
    val parameterTypes: List<Class<*>>,
)
