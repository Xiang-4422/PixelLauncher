package com.purride.pixelui

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.purride.pixelcore.PixelColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayDeque
import kotlin.math.abs

/** Real-host device acceptance for theme pixels, geometry, focus, and Android virtual actions. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 24)
class ThemeStateInstrumentedTest {
    /** Five supported themes paint state colors and export production accessibility bounds. */
    @Test
    fun themesDrawRepresentativeStatesAndExposeExactScaledBounds() {
        /** Instrumentation owning the real Activity window and accessibility connection. */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** Public Android connection that materializes PixelHostView's virtual provider nodes. */
        val automation = instrumentation.uiAutomation

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            THEME_CASES.forEach { themeCase ->
                /** Caller-owned focus identity proving each theme's additive focus layer. */
                val focusNode = FocusNode(debugLabel = "${themeCase.name}-focus")
                /** Physical Host draw captured from the actual attached View. */
                lateinit var frame: HostDrawFrame
                scenario.onActivity { activity ->
                    /** Production PixelHostView created and attached by the test Activity. */
                    val host = activity.hostView
                    host.capabilitiesOverride =
                    host.hostCapabilities.copy(motionSettings = PixelMotionSettings(animatorDurationScale = 0f))
                    host.setContent {
                        themedStateFixture(themeCase, focusNode)
                    }
                    drawSynchronously(host)
                    focusNode.requestFocus()
                    host.invalidate()
                    frame = drawSynchronously(host)
                    assertTrue(focusNode.isFocused)
                }
                instrumentation.waitForIdleSync()

                /** Exact component-state colors expected in the physical Android bitmap. */
                val expectedColors = listOf(
                    themeCase.tokens.colors.danger,
                    themeCase.tokens.colors.primary,
                    themeCase.tokens.colors.warning,
                    themeCase.tokens.colors.focus,
                )
                expectedColors.forEach { color ->
                    assertTrue(
                        "${themeCase.name} Host draw missed exact state pixel $color",
                        frame.contains(color),
                    )
                }

                /** Fresh real accessibility window after the themed Host frame was drawn. */
                val root = automation.requireNodeEventually(themeCase.buttonLabel).root
                /** Android virtual Button exported by the production provider. */
                val button = root.requireDescendantWithDescription(themeCase.buttonLabel)
                /** Android virtual Checkbox exported by the production provider. */
                val checkbox = root.requireDescendantWithDescription(themeCase.checkboxLabel)
                /** Android virtual ProgressBar exported by the production provider. */
                val progress = root.requireDescendantWithDescription(themeCase.progressLabel)
                /** Screen-space bounds for the three public components. */
                val buttonBounds = button.screenBounds()
                val checkboxBounds = checkbox.screenBounds()
                val progressBounds = progress.screenBounds()

                assertEquals("android.widget.Button", button.className.toString())
                assertEquals("android.widget.CheckBox", checkbox.className.toString())
                assertEquals("android.widget.ProgressBar", progress.className.toString())
                assertInsideHost(buttonBounds, frame.hostBoundsInScreen)
                assertInsideHost(checkboxBounds, frame.hostBoundsInScreen)
                assertInsideHost(progressBounds, frame.hostBoundsInScreen)
                assertTrue(buttonBounds.width() > 0 && buttonBounds.height() > 0)
                assertTrue(progressBounds.width() > progressBounds.height())

                /** Physical cell size inferred only from the public 32px ProgressBar bounds. */
                val cellSize = progressBounds.width() / PROGRESS_WIDTH.toFloat()
                assertNear(
                    expected = BUTTON_WIDTH * cellSize,
                    actual = buttonBounds.width().toFloat(),
                    tolerance = PHYSICAL_ROUNDING_TOLERANCE_PX,
                    message = "${themeCase.name} OutlinedButton width",
                )
                assertNear(
                    expected = BUTTON_HEIGHT * cellSize,
                    actual = buttonBounds.height().toFloat(),
                    tolerance = PHYSICAL_ROUNDING_TOLERANCE_PX,
                    message = "${themeCase.name} OutlinedButton height",
                )
                /** Checkbox logical extent resolved through the public theme token graph. */
                val checkboxExtent = themeCase.tokens.components.checkbox
                    .resolveMinimumWidth(themeCase.tokens.sizes)
                assertNear(
                    expected = checkboxExtent * cellSize,
                    actual = checkboxBounds.width().toFloat(),
                    tolerance = PHYSICAL_ROUNDING_TOLERANCE_PX,
                    message = "${themeCase.name} Checkbox width",
                )
                assertNear(
                    expected = checkboxExtent * cellSize,
                    actual = checkboxBounds.height().toFloat(),
                    tolerance = PHYSICAL_ROUNDING_TOLERANCE_PX,
                    message = "${themeCase.name} Checkbox height",
                )
                assertNear(
                    expected = PROGRESS_HEIGHT * cellSize,
                    actual = progressBounds.height().toFloat(),
                    tolerance = PHYSICAL_ROUNDING_TOLERANCE_PX,
                    message = "${themeCase.name} ProgressBar height",
                )
            }
        }
    }

    /** Loading preserves input focus but removes Android click; Disabled clears the same focus. */
    @Test
    fun loadingAndDisabledUpdateRealAndroidVirtualCapability() {
        /** Controlled public-widget fixture retained through both capability transitions. */
        val fixture = CapabilityFixture()
        /** Instrumentation owning the real accessibility connection. */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** Public Android accessibility client routed through the Host's virtual provider. */
        val automation = instrumentation.uiAutomation

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Production Host receiving the controlled public widget tree. */
                val host = activity.hostView
                host.capabilitiesOverride =
                    host.hostCapabilities.copy(motionSettings = PixelMotionSettings(animatorDurationScale = 0f))
                host.setContent(fixture::build)
                drawSynchronously(host)
                fixture.focusNode.requestFocus()
                host.invalidate()
                drawSynchronously(host)
                assertTrue(fixture.focusNode.isFocused)
            }
            instrumentation.waitForIdleSync()

            /** Normal Android virtual node proving click is initially executable. */
            val normal = automation.requireNodeEventually(CAPABILITY_LABEL) { node ->
                node.isFocused &&
                    node.isEnabled &&
                    AccessibilityNodeInfo.ACTION_CLICK in node.actionIds()
            }.node
            assertTrue(normal.isFocused)
            assertTrue(normal.isEnabled)
            assertTrue(AccessibilityNodeInfo.ACTION_CLICK in normal.actionIds())
            assertTrue(normal.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(1, fixture.activationCount)

            scenario.onActivity { activity ->
                fixture.activationCount = 0
                fixture.states = PixelControlStateSet.of(PixelControlState.Loading)
                activity.hostView.invalidate()
                drawSynchronously(activity.hostView)
                assertTrue("Loading must retain the public FocusNode", fixture.focusNode.isFocused)
            }
            instrumentation.waitForIdleSync()

            /** Fresh Loading node from Android's real virtual accessibility hierarchy. */
            val loading = automation.requireNodeEventually(CAPABILITY_LABEL) { node ->
                node.isFocused &&
                    !node.isEnabled &&
                    AccessibilityNodeInfo.ACTION_CLICK !in node.actionIds()
            }.node
            assertTrue(loading.isFocused)
            assertFalse(loading.isEnabled)
            assertFalse(AccessibilityNodeInfo.ACTION_CLICK in loading.actionIds())
            assertFalse(loading.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(0, fixture.activationCount)

            scenario.onActivity { activity ->
                fixture.states = PixelControlStateSet.of(PixelControlState.Disabled)
                activity.hostView.invalidate()
                drawSynchronously(activity.hostView)
                assertFalse("Disabled must clear the public FocusNode", fixture.focusNode.isFocused)
            }
            instrumentation.waitForIdleSync()

            /** Fresh Disabled node proving both platform focus and click capability were cleared. */
            val disabled = automation.requireNodeEventually(CAPABILITY_LABEL) { node ->
                !node.isFocused &&
                    !node.isEnabled &&
                    AccessibilityNodeInfo.ACTION_CLICK !in node.actionIds()
            }.node
            assertFalse(disabled.isFocused)
            assertFalse(disabled.isEnabled)
            assertFalse(AccessibilityNodeInfo.ACTION_CLICK in disabled.actionIds())
            assertFalse(disabled.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(0, fixture.activationCount)
        }
    }

    /** A controlled Checkbox keeps selection, focus, and actions across a complete theme switch. */
    @Test
    fun controlledCheckboxStateAndFocusSurviveCompleteThemeSwitch() {
        /** Controlled public-widget fixture retained while its complete theme graph changes. */
        val fixture = ControlledThemeSwitchFixture()
        /** Instrumentation owning the real Activity window and accessibility connection. */
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        /** Public Android client used for every focus, click, and semantic assertion. */
        val automation = instrumentation.uiAutomation

        ActivityScenario.launch(PixelHostLifecycleTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                /** Production Host receiving the controlled public widget tree. */
                val host = activity.hostView
                host.capabilitiesOverride =
                    host.hostCapabilities.copy(motionSettings = PixelMotionSettings(animatorDurationScale = 0f))
                host.setContent(fixture::build)
                drawSynchronously(host)
            }
            instrumentation.waitForIdleSync()

            /** Initial unchecked Checkbox materialized by Android's real virtual provider. */
            val initialCheckbox = automation.requireNodeEventually(CONTROLLED_CHECKBOX_LABEL) { node ->
                !node.isChecked &&
                    AccessibilityNodeInfo.ACTION_FOCUS in node.actionIds() &&
                    AccessibilityNodeInfo.ACTION_CLICK in node.actionIds()
            }.node
            assertEquals("android.widget.CheckBox", initialCheckbox.className.toString())
            assertTrue(initialCheckbox.isCheckable)
            assertFalse(initialCheckbox.isChecked)
            assertTrue(initialCheckbox.performAction(AccessibilityNodeInfo.ACTION_FOCUS))
            instrumentation.waitForIdleSync()

            /** Fresh focused Checkbox used to execute the first controlled toggle. */
            val focusedCheckbox = automation.requireNodeEventually(CONTROLLED_CHECKBOX_LABEL) { node ->
                node.isFocused && !node.isChecked
            }.node
            assertTrue(AccessibilityNodeInfo.ACTION_CLICK in focusedCheckbox.actionIds())
            assertTrue(focusedCheckbox.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertTrue("Checkbox callback must update caller-owned state", fixture.checkboxChecked)

            /** Physical Light-theme frame after the Checkbox entered its selected state. */
            lateinit var lightCheckedFrame: HostDrawFrame
            scenario.onActivity { activity ->
                activity.hostView.invalidate()
                lightCheckedFrame = drawSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()

            /** Fresh selected node proving the controlled update retained Android input focus. */
            val lightChecked = automation.requireNodeEventually(CONTROLLED_CHECKBOX_LABEL) { node ->
                node.isChecked && node.isFocused
            }.node
            assertTrue(AccessibilityNodeInfo.ACTION_CLICK in lightChecked.actionIds())
            assertTrue(lightCheckedFrame.contains(PixelThemeTokens.Light.colors.primary))

            /** Public Button whose semantic label identifies the currently active complete theme. */
            val lightThemeButton = automation.requireNodeEventually(ACTIVE_LIGHT_THEME_LABEL) { node ->
                AccessibilityNodeInfo.ACTION_CLICK in node.actionIds()
            }.node
            assertEquals("android.widget.Button", lightThemeButton.className.toString())
            assertTrue(lightThemeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertEquals(1, fixture.themeSwitchCount)
            assertEquals(PixelThemeTokens.HighContrastDark, fixture.activeTokens)

            /** Physical HighContrastDark frame rebuilt from the same retained controlled state. */
            lateinit var highContrastCheckedFrame: HostDrawFrame
            scenario.onActivity { activity ->
                activity.hostView.invalidate()
                highContrastCheckedFrame = drawSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()

            /** Updated active-theme semantic node proving the complete theme graph was replaced. */
            val highContrastTheme = automation.requireNodeEventually(
                ACTIVE_HIGH_CONTRAST_DARK_THEME_LABEL,
            ) { node -> AccessibilityNodeInfo.ACTION_CLICK in node.actionIds() }
            assertTrue(
                highContrastTheme.root
                    .findDescendantsWithDescription(ACTIVE_LIGHT_THEME_LABEL)
                    .isEmpty(),
            )
            assertTrue(highContrastCheckedFrame.contains(PixelThemeTokens.HighContrastDark.colors.primary))

            /** Selected Checkbox after theme replacement; stable key retains focus and capability. */
            val highContrastChecked = automation.requireNodeEventually(CONTROLLED_CHECKBOX_LABEL) { node ->
                node.isChecked &&
                    node.isFocused &&
                    AccessibilityNodeInfo.ACTION_CLICK in node.actionIds()
            }.node
            assertTrue(highContrastChecked.isEnabled)
            assertTrue(highContrastChecked.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            assertFalse("Post-switch action must still update controlled state", fixture.checkboxChecked)

            scenario.onActivity { activity ->
                activity.hostView.invalidate()
                drawSynchronously(activity.hostView)
            }
            instrumentation.waitForIdleSync()

            /** Final unchecked node proving the post-switch action and focus remain live. */
            val finalCheckbox = automation.requireNodeEventually(CONTROLLED_CHECKBOX_LABEL) { node ->
                !node.isChecked && node.isFocused
            }.node
            assertTrue(AccessibilityNodeInfo.ACTION_CLICK in finalCheckbox.actionIds())
            assertEquals(1, fixture.themeSwitchCount)
        }
    }

    /** Builds the three representative public components for one theme acceptance frame. */
    private fun themedStateFixture(themeCase: ThemeCase, focusNode: FocusNode): Widget {
        return PixelTheme(
            tokens = themeCase.tokens,
            child = Column(
                children = listOf(
                    SizedBox(
                        width = BUTTON_WIDTH,
                        height = BUTTON_HEIGHT,
                        child = Focus(
                            node = focusNode,
                            child = OutlinedButton(
                                text = themeCase.buttonLabel,
                                onPressed = {},
                                states = PixelControlStateSet.of(PixelControlState.Error),
                                key = "${themeCase.name}-button",
                            ),
                        ),
                    ),
                    Checkbox(
                        checked = true,
                        onChanged = {},
                        states = PixelControlStateSet.of(PixelControlState.Selected),
                        semanticLabel = themeCase.checkboxLabel,
                        key = "${themeCase.name}-checkbox",
                    ),
                    ProgressBar(
                        progress = 0.5f,
                        states = PixelControlStateSet.of(PixelControlState.Loading),
                        width = PROGRESS_WIDTH,
                        height = PROGRESS_HEIGHT,
                        key = "${themeCase.name}-progress",
                    ),
                ),
                spacing = themeCase.tokens.spacing.small,
            ),
        )
    }

    /** Draws the actual attached Host and copies its physical pixels before recycling the bitmap. */
    private fun drawSynchronously(host: PixelHostView): HostDrawFrame {
        /** Physical bitmap backing the real View.draw call. */
        val bitmap = Bitmap.createBitmap(
            host.width.coerceAtLeast(1),
            host.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        host.draw(Canvas(bitmap))
        /** Exact Android-space pixels copied before the temporary bitmap is recycled. */
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        /** Host location used to validate virtual node screen bounds without internal geometry. */
        val hostLocation = IntArray(2)
        host.getLocationOnScreen(hostLocation)
        /** Immutable physical Host rectangle corresponding to the captured frame. */
        val hostBounds = Rect(
            hostLocation[0],
            hostLocation[1],
            hostLocation[0] + host.width,
            hostLocation[1] + host.height,
        )
        bitmap.recycle()
        return HostDrawFrame(pixels = pixels, hostBoundsInScreen = hostBounds)
    }

    /**
     * Requires one exact public accessibility description whose latest platform state satisfies
     * [condition], then returns it with the fresh window root that owns it.
     */
    private fun UiAutomation.requireNodeEventually(
        description: String,
        condition: (AccessibilityNodeInfo) -> Boolean = { true },
    ): PlatformNodeResult {
        /** Monotonic deadline preventing a missing provider update from hanging instrumentation. */
        val deadline = SystemClock.uptimeMillis() + ACCESSIBILITY_TIMEOUT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            /** Fresh active Android window after any asynchronous accessibility content event. */
            val root = rootInActiveWindow
            if (root != null) {
                /** Exact public virtual descendants matching the requested spoken label. */
                val matches = root.findDescendantsWithDescription(description)
                if (matches.size == 1 && condition(matches.single())) {
                    return PlatformNodeResult(root = root, node = matches.single())
                }
                check(matches.size <= 1) { "Duplicate Android accessibility nodes for $description" }
            }
            SystemClock.sleep(ACCESSIBILITY_POLL_MS)
        }
        throw NoSuchElementException("Android accessibility node $description did not appear")
    }

    /** Returns one exact described descendant from an already materialized Android window root. */
    private fun AccessibilityNodeInfo.requireDescendantWithDescription(
        description: String,
    ): AccessibilityNodeInfo {
        /** Exact descendants produced by the real virtual-node provider. */
        val matches = findDescendantsWithDescription(description)
        check(matches.size == 1) {
            "Expected one Android accessibility node for $description, found ${matches.size}"
        }
        return matches.single()
    }

    /** Breadth-first traversal over Android's public accessibility hierarchy. */
    private fun AccessibilityNodeInfo.findDescendantsWithDescription(
        description: String,
    ): List<AccessibilityNodeInfo> {
        /** Pending Android nodes in stable hierarchy order. */
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        /** Matching virtual nodes returned to the caller. */
        val matches = mutableListOf<AccessibilityNodeInfo>()
        pending.add(this)
        while (pending.isNotEmpty()) {
            /** Current materialized Android node under inspection. */
            val current = pending.removeFirst()
            if (current.contentDescription?.toString() == description) matches += current
            repeat(current.childCount) { childIndex ->
                /** Public child materialized by Android's accessibility connection. */
                val child = current.getChild(childIndex)
                if (child != null) pending.add(child)
            }
        }
        return matches
    }

    /** Returns a defensive screen-space bounds snapshot for one Android virtual node. */
    private fun AccessibilityNodeInfo.screenBounds(): Rect {
        /** Mutable Android rectangle populated by the platform node. */
        val bounds = Rect()
        getBoundsInScreen(bounds)
        return bounds
    }

    /** Returns the public Android action ids currently advertised by this node. */
    private fun AccessibilityNodeInfo.actionIds(): Set<Int> {
        return actionList.mapTo(linkedSetOf()) { action -> action.id }
    }

    /** Verifies a virtual screen rectangle stays fully inside the attached Host. */
    private fun assertInsideHost(node: Rect, host: Rect) {
        assertTrue("Node has empty bounds: $node", node.width() > 0 && node.height() > 0)
        assertTrue("Node $node starts outside Host $host", node.left >= host.left && node.top >= host.top)
        assertTrue("Node $node ends outside Host $host", node.right <= host.right && node.bottom <= host.bottom)
    }

    /** Compares physical dimensions while allowing only edge-rounding error. */
    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float,
        message: String,
    ) {
        assertTrue("$message expected=$expected actual=$actual", abs(expected - actual) <= tolerance)
    }

    /** Controlled public Checkbox and complete theme graph used for retained-switch acceptance. */
    private class ControlledThemeSwitchFixture {
        /** Complete public token graph consumed by the next retained Host build. */
        var activeTokens: PixelThemeTokens = PixelThemeTokens.Light
            private set

        /** Caller-owned Checkbox selection that must survive replacing [activeTokens]. */
        var checkboxChecked: Boolean = false
            private set

        /** Number of complete theme replacements requested through the public Button action. */
        var themeSwitchCount: Int = 0
            private set

        /** Builds stable-key public controls from the latest caller-owned theme and selection. */
        fun build(): Widget {
            /** Semantic label reflecting the complete theme graph used for this exact build. */
            val activeThemeLabel = if (activeTokens == PixelThemeTokens.HighContrastDark) {
                ACTIVE_HIGH_CONTRAST_DARK_THEME_LABEL
            } else {
                ACTIVE_LIGHT_THEME_LABEL
            }
            return PixelTheme(
                tokens = activeTokens,
                child = Column(
                    children = listOf(
                        OutlinedButton(
                            text = activeThemeLabel,
                            onPressed = {
                                activeTokens = PixelThemeTokens.HighContrastDark
                                themeSwitchCount += 1
                            },
                            key = "instrumented-controlled-theme-switch",
                        ),
                        Checkbox(
                            checked = checkboxChecked,
                            onChanged = { nextChecked -> checkboxChecked = nextChecked },
                            states = PixelControlStateSet.Normal,
                            semanticLabel = CONTROLLED_CHECKBOX_LABEL,
                            key = "instrumented-controlled-theme-checkbox",
                        ),
                    ),
                    spacing = activeTokens.spacing.small,
                ),
            )
        }
    }

    /** Controlled public Button fixture used for real Android capability transitions. */
    private class CapabilityFixture {
        /** Caller-owned focus identity retained across Normal, Loading, and Disabled. */
        val focusNode: FocusNode = FocusNode(debugLabel = "instrumented-capability")

        /** Current public component states consumed on the next Host draw. */
        var states: PixelControlStateSet = PixelControlStateSet.Normal

        /** Executed public Button callback count. */
        var activationCount: Int = 0

        /** Builds the latest controlled public widget tree without internal render primitives. */
        fun build(): Widget {
            return PixelTheme(
                tokens = PixelThemeTokens.Dark,
                child = Focus(
                    node = focusNode,
                    child = OutlinedButton(
                        text = CAPABILITY_LABEL,
                        onPressed = { activationCount += 1 },
                        states = states,
                        key = "instrumented-capability-button",
                    ),
                ),
            )
        }
    }

    /** One supported theme and the exact public labels used to locate its Android nodes. */
    private data class ThemeCase(
        /** Stable diagnostic theme name. */
        val name: String,
        /** Compact unique prefix that does not distort the component under test. */
        val semanticPrefix: String,
        /** Complete public theme token graph. */
        val tokens: PixelThemeTokens,
    ) {
        /** Unique Button semantic label rendered as its visible text. */
        val buttonLabel: String = "$semanticPrefix BUTTON"

        /** Unique explicit Checkbox semantic label. */
        val checkboxLabel: String = "$semanticPrefix CHECKBOX"

        /** Theme-localized ProgressBar semantic label. */
        val progressLabel: String = tokens.labels.progress
    }

    /** Immutable pixels and Host screen rectangle captured from one real View.draw call. */
    private data class HostDrawFrame(
        /** Physical Android ARGB pixels in row-major order. */
        val pixels: IntArray,
        /** Attached Host bounds in Android screen coordinates. */
        val hostBoundsInScreen: Rect,
    ) {
        /** Reports whether the physical Host output contains one exact opaque theme color. */
        fun contains(color: PixelColor): Boolean = pixels.any { argb -> argb == color.argb }
    }

    /** Fresh Android window root paired with its one exact virtual descendant. */
    private data class PlatformNodeResult(
        /** Materialized active Android accessibility window root. */
        val root: AccessibilityNodeInfo,
        /** Exact described virtual node inside [root]. */
        val node: AccessibilityNodeInfo,
    )

    /** Shared deterministic dimensions, polling limits, labels, and supported theme fixtures. */
    private companion object {
        /** Public ProgressBar width used to infer the physical Host cell size. */
        const val PROGRESS_WIDTH: Int = 32

        /** Public ProgressBar height checked against the inferred physical cell size. */
        const val PROGRESS_HEIGHT: Int = 7

        /** Explicit public OutlinedButton width checked through Android screen bounds. */
        const val BUTTON_WIDTH: Int = 40

        /** Explicit public OutlinedButton height checked through Android screen bounds. */
        const val BUTTON_HEIGHT: Int = 16

        /** Maximum physical discrepancy caused by independently rounded semantic edges. */
        const val PHYSICAL_ROUNDING_TOLERANCE_PX: Float = 2f

        /** Maximum wait for Android to publish a freshly drawn virtual-node tree. */
        const val ACCESSIBILITY_TIMEOUT_MS: Long = 3_000L

        /** Poll interval for Android accessibility tree publication. */
        const val ACCESSIBILITY_POLL_MS: Long = 50L

        /** Stable accessible label for the Loading and Disabled capability fixture. */
        const val CAPABILITY_LABEL: String = "THEME CAPABILITY BUTTON"

        /** Stable semantic label for the controlled Checkbox retained across theme replacement. */
        const val CONTROLLED_CHECKBOX_LABEL: String = "THEME RETAINED CHECKBOX"

        /** Active-theme semantic label before the complete token graph replacement. */
        const val ACTIVE_LIGHT_THEME_LABEL: String = "ACTIVE THEME LIGHT"

        /** Active-theme semantic label after switching to the high-contrast token graph. */
        const val ACTIVE_HIGH_CONTRAST_DARK_THEME_LABEL: String =
            "ACTIVE THEME HIGH CONTRAST DARK"

        /** Consumer theme with unique colors and expanded Checkbox geometry. */
        val CUSTOM_THEME: PixelThemeTokens = PixelThemeTokens.Dark.copy(
            colors = PixelColorScheme.Dark.copy(
                danger = PixelColor.fromRgb(197, 19, 47),
                onDanger = PixelColor.fromRgb(255, 227, 229),
                primary = PixelColor.fromRgb(17, 101, 233),
                onPrimary = PixelColor.fromRgb(249, 235, 211),
                warning = PixelColor.fromRgb(239, 151, 7),
                onWarning = PixelColor.fromRgb(37, 21, 3),
                outline = PixelColor.fromRgb(229, 31, 97),
                focus = PixelColor.fromRgb(7, 223, 251),
            ),
            sizes = PixelSizeTokens.Default.copy(selectionControlExtent = 13),
            spacing = PixelSpacingTokens.Default.copy(small = 3),
            borders = PixelBorderTokens.Default.copy(thin = 2, focus = 3),
            labels = PixelLabelTokens.Default.copy(progress = "CUSTOM THEME PROGRESS"),
        )

        /** Four built-in themes and one consumer theme required by M5-1 device acceptance. */
        val THEME_CASES: List<ThemeCase> = listOf(
            ThemeCase(name = "DARK", semanticPrefix = "D", tokens = PixelThemeTokens.Dark),
            ThemeCase(name = "LIGHT", semanticPrefix = "L", tokens = PixelThemeTokens.Light),
            ThemeCase(
                name = "HIGH CONTRAST DARK",
                semanticPrefix = "HCD",
                tokens = PixelThemeTokens.HighContrastDark,
            ),
            ThemeCase(
                name = "HIGH CONTRAST LIGHT",
                semanticPrefix = "HCL",
                tokens = PixelThemeTokens.HighContrastLight,
            ),
            ThemeCase(name = "CUSTOM", semanticPrefix = "C", tokens = CUSTOM_THEME),
        )
    }
}
