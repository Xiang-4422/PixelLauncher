package com.purride.pixelui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.host.PixelAccessibilityNodeSnapshot
import com.purride.pixelui.internal.host.PixelAccessibilityTreeSnapshot
import com.purride.pixelui.internal.host.PixelHostAccessibilityNodeProvider
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** API-device acceptance for retained input and Android accessibility across provider replacement. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class PixelLocalizationAccessibilityRetentionInstrumentedTest {
    /**
     * English, Chinese, and consumer bundles update only spoken text while keyed Android nodes and
     * the complete caller-owned TextField edit state remain retained.
     */
    @Test
    fun providerReplacementRetainsTextFieldAndNavigationAndroidIdentityAndState() {
        /** Public-widget fixture retained for every provider phase in the same attached Host. */
        val fixture = LocalizationRetentionFixture()
        /** Original controller reference proving the input owner is never replaced. */
        val originalController = fixture.textController
        /** Original state reference proving localization never substitutes caller edit state. */
        val originalState = fixture.textState

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Real Activity-attached Host used for rendering and platform accessibility. */
                val host = activity.hostView
                host.capabilitiesOverride =
                    host.hostCapabilities.copy(motionSettings = PixelMotionSettings(animatorDurationScale = 0f))
                host.setContent(fixture::build)
                drawLocalizationSynchronously(host)

                /** Public Android provider surface returned by the production Host. */
                val platformProvider: AccessibilityNodeProvider = host.accessibilityNodeProvider
                /** Existing test-only address book used solely to obtain Android virtual IDs. */
                val addressProvider = platformProvider as PixelHostAccessibilityNodeProvider
                /** Initial English virtual tree after the first physical Host frame. */
                val initialTree = addressProvider.snapshotForTesting()
                /** Default-labelled TextField addressed before any public edit action. */
                val initialField = initialTree.requireLocalizationNode(
                    label = ENGLISH_TEXT_FIELD_LABEL,
                    role = PixelSemanticRole.TEXT_FIELD,
                )

                /** Android click requests real TextField/IME focus through production semantics. */
                assertTrue(
                    platformProvider.performAction(
                        initialField.virtualViewId,
                        AccessibilityNodeInfo.ACTION_CLICK,
                        null,
                    ),
                )
                drawLocalizationSynchronously(host)
                assertTrue(fixture.textState.isFocused)

                /** Public Android arguments replacing the complete editable value. */
                val setTextArguments = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        RETAINED_TEXT,
                    )
                }
                assertTrue(
                    platformProvider.performAction(
                        initialField.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SET_TEXT,
                        setTextArguments,
                    ),
                )
                /** Public Android arguments selecting a non-collapsed interior text range. */
                val setSelectionArguments = Bundle().apply {
                    putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                        RETAINED_SELECTION_START,
                    )
                    putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                        RETAINED_SELECTION_END,
                    )
                }
                assertTrue(
                    platformProvider.performAction(
                        initialField.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SET_SELECTION,
                        setSelectionArguments,
                    ),
                )
                /**
                 * Android accessibility exposes no composition-range action, so the public
                 * controller establishes this IME-owned range before provider replacement.
                 */
                fixture.textController.updateComposition(
                    state = fixture.textState,
                    compositionStart = RETAINED_COMPOSITION_START,
                    compositionEnd = RETAINED_COMPOSITION_END,
                )
                drawLocalizationSynchronously(host)

                /** English tree after focus, edit, selection, and composition are fully applied. */
                val englishTree = addressProvider.snapshotForTesting()
                /** Focused English TextField node used as the retained platform baseline. */
                val englishField = englishTree.requireLocalizationNode(
                    label = ENGLISH_TEXT_FIELD_LABEL,
                    role = PixelSemanticRole.TEXT_FIELD,
                )
                /** Loading NavigationBar collection exposing the provider status channel. */
                val englishBar = englishTree.requireLocalizationNode(
                    label = ENGLISH_NAVIGATION_BAR_LABEL,
                    role = PixelSemanticRole.GENERIC,
                )
                /** Stable selected destination whose identity is independent from locale text. */
                val englishHome = englishTree.requireLocalizationNode(
                    label = HOME_LABEL,
                    role = PixelSemanticRole.TAB,
                )
                /** Android accessibility focus placed on the selected destination, not input focus. */
                assertTrue(
                    platformProvider.performAction(
                        englishHome.virtualViewId,
                        AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,
                        null,
                    ),
                )
                drawLocalizationSynchronously(host)

                /** Fresh English tree after Android accessibility focus is committed. */
                val focusedEnglishTree = addressProvider.snapshotForTesting()
                /** Final English TextField address used for all cross-provider comparisons. */
                val focusedEnglishField = focusedEnglishTree.requireLocalizationNode(
                    label = ENGLISH_TEXT_FIELD_LABEL,
                    role = PixelSemanticRole.TEXT_FIELD,
                )
                /** Final English NavigationBar address carrying Loading state text. */
                val focusedEnglishBar = focusedEnglishTree.requireLocalizationNode(
                    label = ENGLISH_NAVIGATION_BAR_LABEL,
                    role = PixelSemanticRole.GENERIC,
                )
                /** Final English selected destination address carrying accessibility focus. */
                val focusedEnglishHome = focusedEnglishTree.requireLocalizationNode(
                    label = HOME_LABEL,
                    role = PixelSemanticRole.TAB,
                )
                /** Real Android EditText node after all retained state has been established. */
                val englishFieldInfo = platformProvider.requireLocalizationInfo(focusedEnglishField)
                /** Real Android NavigationBar collection node before localization replacement. */
                val englishBarInfo = platformProvider.requireLocalizationInfo(focusedEnglishBar)
                /** Real Android selected Tab node before localization replacement. */
                val englishHomeInfo = platformProvider.requireLocalizationInfo(focusedEnglishHome)
                /** TextField virtual ID, class, and physical bounds baseline. */
                val englishFieldIdentity = platformProvider.captureLocalizationIdentity(
                    focusedEnglishField,
                )
                /** Navigation collection virtual ID, class, and physical bounds baseline. */
                val englishBarIdentity = platformProvider.captureLocalizationIdentity(
                    focusedEnglishBar,
                )
                /** Stable keyed destination virtual ID, class, and physical bounds baseline. */
                val englishHomeIdentity = platformProvider.captureLocalizationIdentity(
                    focusedEnglishHome,
                )

                assertEquals("android.widget.EditText", englishFieldInfo.className.toString())
                assertEquals(ENGLISH_TEXT_FIELD_LABEL, englishFieldInfo.contentDescription.toString())
                assertEquals(RETAINED_TEXT, englishFieldInfo.text.toString())
                assertEquals(RETAINED_SELECTION_START, englishFieldInfo.textSelectionStart)
                assertEquals(RETAINED_SELECTION_END, englishFieldInfo.textSelectionEnd)
                assertTrue(englishFieldInfo.isFocused)
                assertLocalizationStateDescription(
                    info = englishBarInfo,
                    expected = ENGLISH_LOADING_LABEL,
                )
                assertEquals("android.widget.Button", englishHomeInfo.className.toString())
                assertTrue(englishHomeInfo.isSelected)
                assertTrue(englishHomeInfo.isAccessibilityFocused)

                fixture.bundle = PixelLocalizationBundle.Chinese
                host.invalidate()
                drawLocalizationSynchronously(host)

                /** Chinese virtual tree produced by replacing only the inherited provider values. */
                val chineseTree = addressProvider.snapshotForTesting()
                /** Same TextField located by its updated built-in Chinese default name. */
                val chineseField = chineseTree.requireLocalizationNode(
                    label = CHINESE_TEXT_FIELD_LABEL,
                    role = PixelSemanticRole.TEXT_FIELD,
                )
                /** Same NavigationBar located by its updated built-in Chinese collection name. */
                val chineseBar = chineseTree.requireLocalizationNode(
                    label = CHINESE_NAVIGATION_BAR_LABEL,
                    role = PixelSemanticRole.GENERIC,
                )
                /** Explicit business destination label remains deliberately untranslated. */
                val chineseHome = chineseTree.requireLocalizationNode(
                    label = HOME_LABEL,
                    role = PixelSemanticRole.TAB,
                )
                /** Real Android EditText after the English-to-Chinese provider update. */
                val chineseFieldInfo = platformProvider.requireLocalizationInfo(chineseField)
                /** Real Android navigation collection exposing the Chinese Loading status. */
                val chineseBarInfo = platformProvider.requireLocalizationInfo(chineseBar)
                /** Real Android selected destination after the inherited update. */
                val chineseHomeInfo = platformProvider.requireLocalizationInfo(chineseHome)

                assertEquals(
                    englishFieldIdentity,
                    platformProvider.captureLocalizationIdentity(chineseField),
                )
                assertEquals(
                    englishBarIdentity,
                    platformProvider.captureLocalizationIdentity(chineseBar),
                )
                assertEquals(
                    englishHomeIdentity,
                    platformProvider.captureLocalizationIdentity(chineseHome),
                )
                assertEquals(CHINESE_TEXT_FIELD_LABEL, chineseFieldInfo.contentDescription.toString())
                assertEquals(RETAINED_TEXT, chineseFieldInfo.text.toString())
                assertEquals(RETAINED_SELECTION_START, chineseFieldInfo.textSelectionStart)
                assertEquals(RETAINED_SELECTION_END, chineseFieldInfo.textSelectionEnd)
                assertTrue(chineseFieldInfo.isFocused)
                assertLocalizationStateDescription(
                    info = chineseBarInfo,
                    expected = CHINESE_LOADING_LABEL,
                )
                assertTrue(chineseHomeInfo.isSelected)
                assertTrue(chineseHomeInfo.isAccessibilityFocused)
                assertRetainedTextState(fixture = fixture, expectedState = originalState)
                assertSame(originalController, fixture.textController)

                fixture.bundle = CUSTOM_LOCALIZATION_BUNDLE
                host.invalidate()
                drawLocalizationSynchronously(host)

                /** Consumer-bundle virtual tree proving the behavior is not built-in-only. */
                val customTree = addressProvider.snapshotForTesting()
                /** Same TextField addressed through the consumer's default component name. */
                val customField = customTree.requireLocalizationNode(
                    label = CUSTOM_TEXT_FIELD_LABEL,
                    role = PixelSemanticRole.TEXT_FIELD,
                )
                /** Same collection addressed through the consumer navigation name. */
                val customBar = customTree.requireLocalizationNode(
                    label = CUSTOM_NAVIGATION_BAR_LABEL,
                    role = PixelSemanticRole.GENERIC,
                )
                /** Same explicit selected destination after the consumer-bundle update. */
                val customHome = customTree.requireLocalizationNode(
                    label = HOME_LABEL,
                    role = PixelSemanticRole.TAB,
                )
                /** Real Android EditText after switching to a consumer bundle. */
                val customFieldInfo = platformProvider.requireLocalizationInfo(customField)
                /** Real Android navigation collection exposing consumer Loading text. */
                val customBarInfo = platformProvider.requireLocalizationInfo(customBar)
                /** Real Android selected and accessibility-focused destination after two switches. */
                val customHomeInfo = platformProvider.requireLocalizationInfo(customHome)

                assertEquals(
                    englishFieldIdentity,
                    platformProvider.captureLocalizationIdentity(customField),
                )
                assertEquals(
                    englishBarIdentity,
                    platformProvider.captureLocalizationIdentity(customBar),
                )
                assertEquals(
                    englishHomeIdentity,
                    platformProvider.captureLocalizationIdentity(customHome),
                )
                assertEquals(CUSTOM_TEXT_FIELD_LABEL, customFieldInfo.contentDescription.toString())
                assertEquals(RETAINED_TEXT, customFieldInfo.text.toString())
                assertEquals(RETAINED_SELECTION_START, customFieldInfo.textSelectionStart)
                assertEquals(RETAINED_SELECTION_END, customFieldInfo.textSelectionEnd)
                assertTrue(customFieldInfo.isFocused)
                assertLocalizationStateDescription(
                    info = customBarInfo,
                    expected = CUSTOM_LOADING_LABEL,
                )
                assertTrue(customHomeInfo.isSelected)
                assertTrue(customHomeInfo.isAccessibilityFocused)
                assertRetainedTextState(fixture = fixture, expectedState = originalState)
                assertSame(originalController, fixture.textController)

                /** Post-switch action proves the retained Android node still controls the same state. */
                val postSwitchSelectionArguments = Bundle().apply {
                    putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,
                        POST_SWITCH_SELECTION,
                    )
                    putInt(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,
                        POST_SWITCH_SELECTION,
                    )
                }
                assertTrue(
                    platformProvider.performAction(
                        customField.virtualViewId,
                        AccessibilityNodeInfo.ACTION_SET_SELECTION,
                        postSwitchSelectionArguments,
                    ),
                )
                assertEquals(POST_SWITCH_SELECTION, fixture.textState.selectionStart)
                assertEquals(POST_SWITCH_SELECTION, fixture.textState.selectionEnd)
                assertEquals(RETAINED_COMPOSITION_START, fixture.textState.compositionStart)
                assertEquals(RETAINED_COMPOSITION_END, fixture.textState.compositionEnd)
            }
        }
    }

    /** Verifies every publicly observable edit field that must survive provider replacement. */
    private fun assertRetainedTextState(
        fixture: LocalizationRetentionFixture,
        expectedState: PixelTextFieldState,
    ) {
        assertSame(expectedState, fixture.textState)
        assertEquals(RETAINED_TEXT, fixture.textState.text)
        assertEquals(RETAINED_SELECTION_START, fixture.textState.selectionStart)
        assertEquals(RETAINED_SELECTION_END, fixture.textState.selectionEnd)
        assertEquals(RETAINED_COMPOSITION_START, fixture.textState.compositionStart)
        assertEquals(RETAINED_COMPOSITION_END, fixture.textState.compositionEnd)
        assertTrue(fixture.textState.isFocused)
    }

    /** Verifies the API 30 property or the API 24–29 spoken-text compatibility projection. */
    private fun assertLocalizationStateDescription(
        info: AccessibilityNodeInfo,
        expected: String,
    ) {
        /** Platform-visible state channel selected by the production provider's SDK branch. */
        val actual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.stateDescription
        } else {
            info.text
        }
        assertEquals(expected, actual.toString())
    }

    /** Mutable public-widget declaration retained across all localization provider phases. */
    private class LocalizationRetentionFixture {
        /** Caller-owned controller kept identical across every declarative rebuild. */
        val textController: PixelTextFieldController = PixelTextFieldController()

        /** Caller-owned State whose complete edit and focus values must remain intact. */
        val textState: PixelTextFieldState = textController.create(initialText = INITIAL_TEXT)

        /** Current immutable provider bundle read by the next retained Host build. */
        var bundle: PixelLocalizationBundle = PixelLocalizationBundle.English

        /** Caller-controlled selected business destination, deliberately stable across locale text. */
        private var selectedDestinationId: String = HOME_ID

        /** Explicit destination definitions whose labels are never translated by a provider. */
        private val destinations: List<PixelNavigationDestination> = listOf(
            PixelNavigationDestination(id = HOME_ID, label = HOME_LABEL, icon = localizationIcon()),
            PixelNavigationDestination(
                id = SETTINGS_ID,
                label = SETTINGS_LABEL,
                icon = localizationIcon(),
            ),
        )

        /** Builds only public widgets under stable provider, TextField, and NavigationBar keys. */
        fun build(): Widget {
            return PixelLocalizations(
                locale = bundle.locale,
                bundle = bundle,
                child = Column(
                    children = listOf(
                        SizedBox(
                            width = TEXT_FIELD_WIDTH,
                            height = TEXT_FIELD_HEIGHT,
                            child = TextField(
                                state = textState,
                                controller = textController,
                                placeholder = "",
                                key = TEXT_FIELD_KEY,
                            ),
                        ),
                        SizedBox(
                            width = NAVIGATION_WIDTH,
                            height = NAVIGATION_HEIGHT,
                            child = NavigationBar(
                                destinations = destinations,
                                selectedId = selectedDestinationId,
                                onSelected = { destinationId ->
                                    selectedDestinationId = destinationId
                                },
                                states = PixelControlStateSet.of(PixelControlState.Loading),
                                key = NAVIGATION_KEY,
                            ),
                        ),
                    ),
                    spacing = FIXTURE_SPACING,
                    key = FIXTURE_COLUMN_KEY,
                ),
                key = LOCALIZATION_PROVIDER_KEY,
            )
        }
    }

    /** Stable dimensions, semantic text, keys, and the consumer localization bundle. */
    private companion object {
        /** Initial value replaced through Android ACTION_SET_TEXT. */
        const val INITIAL_TEXT: String = "seed"

        /** Editable value that must survive both provider replacements. */
        const val RETAINED_TEXT: String = "pixel engine"

        /** Start of the selection created through Android ACTION_SET_SELECTION. */
        const val RETAINED_SELECTION_START: Int = 2

        /** Exclusive end of the retained Android selection. */
        const val RETAINED_SELECTION_END: Int = 7

        /** Start of the composition range established through the public controller API. */
        const val RETAINED_COMPOSITION_START: Int = 1

        /** Exclusive end of the retained public-controller composition range. */
        const val RETAINED_COMPOSITION_END: Int = 5

        /** Collapsed selection used to prove actions still target the retained State afterward. */
        const val POST_SWITCH_SELECTION: Int = 4

        /** Built-in English default TextField accessible name. */
        const val ENGLISH_TEXT_FIELD_LABEL: String = "Text field"

        /** Built-in Chinese default TextField accessible name. */
        const val CHINESE_TEXT_FIELD_LABEL: String = "文本框"

        /** Consumer default TextField accessible name. */
        const val CUSTOM_TEXT_FIELD_LABEL: String = "CUSTOM TEXT FIELD"

        /** Built-in English default NavigationBar accessible name. */
        const val ENGLISH_NAVIGATION_BAR_LABEL: String = "Navigation bar"

        /** Built-in Chinese default NavigationBar accessible name. */
        const val CHINESE_NAVIGATION_BAR_LABEL: String = "导航栏"

        /** Consumer default NavigationBar accessible name. */
        const val CUSTOM_NAVIGATION_BAR_LABEL: String = "CUSTOM NAVIGATION BAR"

        /** Built-in English Loading status mapped to Android stateDescription. */
        const val ENGLISH_LOADING_LABEL: String = "LOADING"

        /** Built-in Chinese Loading status mapped to Android stateDescription. */
        const val CHINESE_LOADING_LABEL: String = "加载中"

        /** Consumer Loading status mapped to Android stateDescription. */
        const val CUSTOM_LOADING_LABEL: String = "CUSTOM LOADING"

        /** Stable business identity for the selected destination. */
        const val HOME_ID: String = "home"

        /** Explicit visible and spoken name for the selected destination. */
        const val HOME_LABEL: String = "Home"

        /** Stable business identity for the unselected destination. */
        const val SETTINGS_ID: String = "settings"

        /** Explicit visible and spoken name for the unselected destination. */
        const val SETTINGS_LABEL: String = "Settings"

        /** Retained public TextField declaration key. */
        const val TEXT_FIELD_KEY: String = "api37-localized-text-field"

        /** Retained public NavigationBar declaration key. */
        const val NAVIGATION_KEY: String = "api37-localized-navigation"

        /** Retained public localization boundary key. */
        const val LOCALIZATION_PROVIDER_KEY: String = "api37-localization-provider"

        /** Retained fixture layout key. */
        const val FIXTURE_COLUMN_KEY: String = "api37-localization-column"

        /** Exact logical TextField width kept constant through every phase. */
        const val TEXT_FIELD_WIDTH: Int = 120

        /** Exact logical TextField height kept constant through every phase. */
        const val TEXT_FIELD_HEIGHT: Int = 24

        /** Exact logical NavigationBar width kept constant through every phase. */
        const val NAVIGATION_WIDTH: Int = 160

        /** Exact logical NavigationBar height kept constant through every phase. */
        const val NAVIGATION_HEIGHT: Int = 48

        /** Stable logical gap between the TextField and NavigationBar. */
        const val FIXTURE_SPACING: Int = 4

        /** Complete consumer bundle used after the built-in Chinese phase. */
        val CUSTOM_LOCALIZATION_BUNDLE: PixelLocalizationBundle = PixelLocalizationBundle(
            locale = PixelLocale("fr"),
            labels = PixelLabelTokens.Default.copy(
                textField = CUSTOM_TEXT_FIELD_LABEL,
                loading = CUSTOM_LOADING_LABEL,
            ),
            navigationBar = CUSTOM_NAVIGATION_BAR_LABEL,
            navigationRail = "CUSTOM NAVIGATION RAIL",
        )
    }
}

/** Draws one real Host frame synchronously so provider state is immediately queryable. */
private fun drawLocalizationSynchronously(host: PixelHostView) {
    /** Temporary physical bitmap supplying the Android Canvas required by View.draw. */
    val bitmap = Bitmap.createBitmap(
        host.width.coerceAtLeast(1),
        host.height.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888,
    )
    host.draw(Canvas(bitmap))
    bitmap.recycle()
}

/** Finds exactly one current virtual node by public semantic label and role. */
private fun PixelAccessibilityTreeSnapshot.requireLocalizationNode(
    label: String,
    role: PixelSemanticRole,
): PixelAccessibilityNodeSnapshot {
    return nodes.single { snapshot -> snapshot.node.label == label && snapshot.node.role == role }
}

/** Creates the real Android node corresponding to one virtual address. */
private fun AccessibilityNodeProvider.requireLocalizationInfo(
    snapshot: PixelAccessibilityNodeSnapshot,
): AccessibilityNodeInfo {
    return requireNotNull(createAccessibilityNodeInfo(snapshot.virtualViewId)) {
        "Missing AccessibilityNodeInfo for virtual id ${snapshot.virtualViewId}."
    }
}

/** Captures the exact virtual ID, Android class, and physical screen bounds of one node. */
private fun AccessibilityNodeProvider.captureLocalizationIdentity(
    snapshot: PixelAccessibilityNodeSnapshot,
): RetainedAndroidNodeIdentity {
    /** Fresh platform node built by the real Host AccessibilityNodeProvider. */
    val info = requireLocalizationInfo(snapshot)
    /** Defensive physical screen rectangle populated by Android's public node API. */
    val bounds = Rect()
    info.getBoundsInScreen(bounds)
    return RetainedAndroidNodeIdentity(
        virtualViewId = snapshot.virtualViewId,
        className = info.className.toString(),
        boundsInScreen = bounds,
    )
}

/** Android identity and geometry that localization text must never replace. */
private data class RetainedAndroidNodeIdentity(
    /** Host-scoped virtual descendant ID allocated for the retained semantic owner. */
    val virtualViewId: Int,
    /** Android framework class mapped from the unchanged public semantic role. */
    val className: String,
    /** Exact physical screen bounds exported by the attached production Host. */
    val boundsInScreen: Rect,
)

/** Creates one opaque icon used only by the real public NavigationBar fixture. */
private fun localizationIcon(): PixelIconData {
    /** Single opaque alpha-mask pixel whose tint cannot affect semantic identity. */
    val pixels = intArrayOf(PixelColor.White.argb)
    return PixelIconData(PixelBitmap(width = 1, height = 1, pixels = pixels))
}
