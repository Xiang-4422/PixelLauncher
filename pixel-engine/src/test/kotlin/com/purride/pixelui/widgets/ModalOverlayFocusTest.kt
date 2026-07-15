package com.purride.pixelui.widgets

import com.purride.pixelui.Column
import com.purride.pixelui.Dialog
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.Menu
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelMotionScope
import com.purride.pixelui.PixelMotionSettings
import com.purride.pixelui.PixelOverlayController
import com.purride.pixelui.PixelOverlayHost
import com.purride.pixelui.Popover
import com.purride.pixelui.Text
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies modal focus entry, trapping, dismissal, and opener restoration for overlay controls. */
class ModalOverlayFocusTest {
    /** Controller Dialog restores focus on logical Back dismissal while its visual exit is retained. */
    @Test
    fun controllerDialogBackRestoresBeforeRetainedExitCompletes() {
        /** Overlay controller whose Dialog presentation owns one retained exit animation. */
        val overlay = PixelOverlayController()
        /** Stable application opener captured before the Dialog becomes modal. */
        val opener = FocusNode("dialog-opener")
        /** Stable first Dialog descendant that receives modal focus. */
        val dialogAction = FocusNode("dialog-action")
        /** Runtime-local tester and virtual ticker used to retain the outgoing Dialog visual. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelMotionScope(
                    vsync = tester.vsync,
                    settings = PixelMotionSettings.Default,
                    child = PixelOverlayHost(
                        controller = overlay,
                        child = Focus(
                            node = opener,
                            autofocus = true,
                            child = OutlinedButton(text = "OPEN DIALOG", onPressed = { }),
                        ),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )
            assertTrue(opener.isFocused)

            overlay.showDialog(
                title = Text("SETTINGS"),
                content = Focus(
                    node = dialogAction,
                    child = OutlinedButton(text = "SAVE", onPressed = { }),
                ),
            )
            tester.pumpFrame(0)
            tester.pumpFrame(0)
            tester.pumpFrame(50)
            assertTrue(dialogAction.isFocused)
            assertFalse(opener.isFocused)

            assertTrue(tester.pressKey(PixelKey.BACK))

            assertTrue(opener.isFocused)
            assertFalse(dialogAction.isFocused)
            assertFalse(dialogAction.requestFocus())
            assertFalse(tester.dumpSemanticsTree().contains("SETTINGS"))
            assertTrue(tester.vsync.liveTickerCount > 0)
            tester.pumpAndSettle()
            assertTrue(opener.isFocused)
            assertFalse(dialogAction.requestFocus())
        } finally {
            tester.dispose()
        }
    }

    /** Popover moves focus inside, rejects background requests, and restores on logical close. */
    @Test
    fun popoverTrapsFocusAndEscapeRestoresOpener() {
        /** Controlled logical expansion consumed by Popover and its dismiss callback. */
        val expanded = ValueNotifier(false)
        /** Stable opener focus retained while the modal presentation is mounted. */
        val opener = FocusNode("popover-opener")
        /** Stable focus node expected to become the first modal descendant. */
        val popupAction = FocusNode("popover-action")
        /** Later sibling used to prove programmatic background focus rejection. */
        val background = FocusNode("popover-background")
        /** Runtime-local tester whose focus owner must never use global state. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = ValueListenableBuilder(expanded) { _, isExpanded ->
                    Column(
                        children = listOf(
                            Popover(
                                anchor = Focus(
                                    node = opener,
                                    autofocus = true,
                                    child = OutlinedButton(text = "OPEN", onPressed = { expanded.value = true }),
                                ),
                                content = Focus(
                                    node = popupAction,
                                    child = OutlinedButton(text = "POPUP ACTION", onPressed = { }),
                                ),
                                expanded = isExpanded,
                                onDismiss = { expanded.value = false },
                                modal = true,
                                key = "popover",
                            ),
                            Focus(
                                node = background,
                                child = OutlinedButton(text = "BACKGROUND", onPressed = { }),
                            ),
                        ),
                    )
                },
                logicalWidth = 96,
                logicalHeight = 48,
            )
            assertTrue(opener.isFocused)

            expanded.value = true
            tester.pumpFrame(0)

            assertTrue(popupAction.isFocused)
            assertFalse(opener.isFocused)
            assertFalse(background.requestFocus())
            assertTrue(tester.pressKey(PixelKey.TAB))
            assertTrue(popupAction.isFocused)

            assertTrue(tester.pressKey(PixelKey.ESCAPE))

            assertFalse(expanded.value)
            assertTrue(opener.isFocused)
            assertFalse(popupAction.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** Standalone Menu receives initial focus and restores the opener after Escape removes it. */
    @Test
    fun standaloneMenuEscapeRestoresOpener() {
        /** Controlled insertion state for the standalone modal Menu. */
        val menuVisible = ValueNotifier(false)
        /** Stable opener focus captured when the Menu becomes active. */
        val opener = FocusNode("menu-opener")
        /** Background sibling that must be rejected while the Menu is active. */
        val background = FocusNode("menu-background")
        /** Runtime-local tester used to dispatch Escape through the modal owner stack. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = ValueListenableBuilder(menuVisible) { _, visible ->
                    Column(
                        children = buildList {
                            add(
                                Focus(
                                    node = opener,
                                    autofocus = true,
                                    child = OutlinedButton(text = "OPEN MENU", onPressed = { menuVisible.value = true }),
                                ),
                            )
                            if (visible) {
                                add(
                                    Menu(
                                        items = listOf(
                                            PixelMenuItem(label = "COPY", onSelected = { menuVisible.value = false }),
                                            PixelMenuItem(label = "DELETE", onSelected = { menuVisible.value = false }),
                                        ),
                                        onDismissRequest = { menuVisible.value = false },
                                        key = "menu",
                                    ),
                                )
                            }
                            add(
                                Focus(
                                    node = background,
                                    child = OutlinedButton(text = "BACKGROUND", onPressed = { }),
                                    key = "menu-background-focus",
                                ),
                            )
                        },
                    )
                },
                logicalWidth = 96,
                logicalHeight = 64,
            )
            assertTrue(opener.isFocused)

            menuVisible.value = true
            tester.pumpFrame(0)

            assertFalse(opener.isFocused)
            assertFalse(background.requestFocus())
            assertTrue(tester.dumpSemanticsTree().contains("MENU_ITEM label=\"COPY\" enabled=true focused=true"))
            assertTrue(tester.pressKey(PixelKey.ESCAPE))

            assertFalse(menuVisible.value)
            assertTrue(opener.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** A Menu inside Dialog owns a distinct top modal and restores the Dialog's prior focus. */
    @Test
    fun nestedMenuInsideDialogRestoresDialogFocus() {
        /** Controlled insertion state for the nested Menu presentation. */
        val menuVisible = ValueNotifier(false)
        /** Stable Dialog action that becomes the nested Menu opener. */
        val dialogAction = FocusNode("dialog-menu-opener")
        /** Runtime-local tester used to dispatch Escape through both modal layers. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                ValueListenableBuilder(menuVisible) { _, visible ->
                    Dialog(
                        content = Column(
                            children = buildList {
                                add(
                                    Focus(
                                        node = dialogAction,
                                        autofocus = true,
                                        child = OutlinedButton("DIALOG ACTION", onPressed = { }),
                                    ),
                                )
                                if (visible) {
                                    add(
                                        Menu(
                                            items = listOf(
                                                PixelMenuItem(label = "NESTED ITEM", onSelected = { }),
                                            ),
                                            onDismissRequest = { menuVisible.value = false },
                                            key = "dialog-nested-menu",
                                        ),
                                    )
                                }
                            },
                        ),
                        modal = true,
                    )
                },
                logicalWidth = 96,
                logicalHeight = 48,
            )
            assertTrue(dialogAction.isFocused)

            menuVisible.value = true
            tester.pumpFrame(0)

            assertFalse(dialogAction.isFocused)
            assertFalse(dialogAction.requestFocus())
            assertTrue(
                tester.semanticsNodesByLabel("NESTED ITEM").single().focused,
            )
            assertTrue(tester.pressKey(PixelKey.ESCAPE))

            assertFalse(menuVisible.value)
            assertTrue(dialogAction.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** A later modal autofocus descendant replaces the provisional first enabled focus. */
    @Test
    fun modalAutofocusOverridesFirstEnabledDescendant() {
        /** First enabled node mounted before the explicit autofocus candidate. */
        val first = FocusNode("modal-first")
        /** Explicit autofocus candidate that must win after the complete modal build. */
        val preferred = FocusNode("modal-preferred")
        /** Runtime-local tester used to observe the final initial focus. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Dialog(
                    content = Column(
                        children = listOf(
                            Focus(node = first, child = OutlinedButton("FIRST", onPressed = { })),
                            Focus(
                                node = preferred,
                                autofocus = true,
                                child = OutlinedButton("PREFERRED", onPressed = { }),
                            ),
                        ),
                    ),
                    modal = true,
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )

            assertFalse(first.isFocused)
            assertTrue(preferred.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** A non-dismissible modal consumes Back before a focused child shortcut can leak it. */
    @Test
    fun nondismissibleModalConsumesBackBeforeChildHandler() {
        /** Number of child Back handlers that must remain zero under modal priority. */
        var childBackCount = 0
        /** Runtime-local tester used to dispatch the normalized gamepad/Android Back action. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Dialog(
                    content = Focus(
                        autofocus = true,
                        onKeyEvent = { event ->
                            if (event.key == PixelKey.BACK) {
                                childBackCount += 1
                                true
                            } else {
                                false
                            }
                        },
                        child = OutlinedButton("STAY", onPressed = { }),
                    ),
                    modal = true,
                    onDismissRequest = null,
                ),
                logicalWidth = 72,
                logicalHeight = 32,
            )

            assertTrue(tester.pressKey(PixelKey.BACK))
            assertTrue(tester.semanticsNodesByLabel("Dialog").isNotEmpty())
            assertTrue(childBackCount == 0)
        } finally {
            tester.dispose()
        }
    }

    /** Removing a lower Dialog rewires the upper opener chain back to application content. */
    @Test
    fun dismissingLowerDialogKeepsUpperFocusAndRestoresBackground() {
        /** Controller that permits removal by stable handle instead of only top dismissal. */
        val overlay = PixelOverlayController()
        /** Background opener that must survive both retained exits. */
        val background = FocusNode("nested-background")
        /** Focus node owned by the lower Dialog. */
        val lowerAction = FocusNode("nested-lower")
        /** Focus node owned by the upper Dialog. */
        val upperAction = FocusNode("nested-upper")
        /** Runtime-local tester whose motion clock retains both outgoing presentations. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelMotionScope(
                    vsync = tester.vsync,
                    settings = PixelMotionSettings.Default,
                    child = PixelOverlayHost(
                        controller = overlay,
                        child = Focus(
                            node = background,
                            autofocus = true,
                            child = OutlinedButton("BACKGROUND", onPressed = { }),
                        ),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )
            val lowerHandle = overlay.showDialog(
                content = Focus(
                    node = lowerAction,
                    child = OutlinedButton("LOWER", onPressed = { }),
                ),
            )
            tester.pumpFrame(0)
            val upperHandle = overlay.showDialog(
                content = Focus(
                    node = upperAction,
                    child = OutlinedButton("UPPER", onPressed = { }),
                ),
            )
            tester.pumpFrame(0)
            assertTrue(upperAction.isFocused)

            assertTrue(lowerHandle.dismiss())
            tester.pumpFrame(0)
            assertTrue(upperAction.isFocused)
            assertFalse(lowerAction.requestFocus())

            assertTrue(upperHandle.dismiss())
            tester.pumpFrame(0)
            assertTrue(background.isFocused)
            assertFalse(lowerAction.requestFocus())
            assertFalse(upperAction.requestFocus())
            tester.pumpAndSettle()
            assertTrue(background.isFocused)
        } finally {
            tester.dispose()
        }
    }

    /** Clearing three retained Dialogs preserves the transitive opener and blocks every exit node. */
    @Test
    fun clearingDialogStackRestoresOriginalOpener() {
        /** Controller whose clear operation removes every logical entry in one rebuild. */
        val overlay = PixelOverlayController()
        /** Original application focus restored after the complete modal stack closes. */
        val background = FocusNode("clear-background")
        /** Stable outgoing nodes used to verify retained scopes remain focus-blocked. */
        val dialogNodes = List(3) { index -> FocusNode("clear-dialog-$index") }
        /** Runtime-local tester whose motion clock retains all three exit visuals. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = PixelMotionScope(
                    vsync = tester.vsync,
                    settings = PixelMotionSettings.Default,
                    child = PixelOverlayHost(
                        controller = overlay,
                        child = Focus(
                            node = background,
                            autofocus = true,
                            child = OutlinedButton("BACKGROUND", onPressed = { }),
                        ),
                    ),
                ),
                logicalWidth = 96,
                logicalHeight = 48,
            )
            dialogNodes.forEachIndexed { index, node ->
                overlay.showDialog(
                    content = Focus(
                        node = node,
                        child = OutlinedButton("DIALOG $index", onPressed = { }),
                    ),
                )
                tester.pumpFrame(0)
            }
            assertTrue(dialogNodes.last().isFocused)

            overlay.clear()
            tester.pumpFrame(0)

            assertTrue(background.isFocused)
            dialogNodes.forEach { node -> assertFalse(node.requestFocus()) }
            tester.pumpAndSettle()
            assertTrue(background.isFocused)
        } finally {
            tester.dispose()
        }
    }
}
