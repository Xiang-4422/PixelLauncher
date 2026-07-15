package com.purride.pixelui.internal.retained

import com.purride.pixelui.Dialog
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelFocusIndicatorTokens
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.internal.ElementTreeBuildRuntime
import com.purride.pixelui.internal.ElementTreeBuildRuntimeFactory
import com.purride.pixelui.internal.UnsupportedWidgetAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies that low-level retained runtimes never fall back to process-wide focus state. */
class RetainedBuildRuntimeFocusIsolationTest {
    /** Theme-first class initialization must retain the standard button focus specification. */
    @Test
    fun defaultThemeInitializationRetainsButtonFocusIndicator() {
        /** Focus token reached through the complete default theme before direct companion access. */
        val indicator = PixelThemeTokens.Default.components.button.focusIndicator

        assertEquals(PixelFocusIndicatorTokens(), indicator)
    }

    /** Simultaneous raw runtimes may autofocus independently and dispose without cross-talk. */
    @Test
    fun rawBuildRuntimesOwnIndependentAutofocusState() {
        /** Explicit node mounted in the first raw retained runtime. */
        val firstNode = FocusNode(debugLabel = "raw-first")
        /** Explicit node mounted in the second raw retained runtime. */
        val secondNode = FocusNode(debugLabel = "raw-second")
        /** First raw build runtime kept alive while its sibling mounts. */
        val firstRuntime = createRuntime()
        /** Second raw build runtime proving autofocus is not process-global. */
        val secondRuntime = createRuntime()
        /** Whether the first runtime has already crossed its terminal boundary. */
        var firstDisposed = false
        try {
            firstRuntime.resolveElementTree(autofocusTree(firstNode, label = "FIRST"))
            secondRuntime.resolveElementTree(autofocusTree(secondNode, label = "SECOND"))

            assertTrue(firstNode.isFocused)
            assertTrue(secondNode.isFocused)

            firstRuntime.dispose()
            firstDisposed = true

            assertFalse(firstNode.isFocused)
            assertTrue(secondNode.isFocused)
        } finally {
            if (!firstDisposed) firstRuntime.dispose()
            secondRuntime.dispose()
        }
    }

    /** A pre-focused raw runtime cannot change repeated modal diagnostics in sibling runtimes. */
    @Test
    fun repeatedModalBuildsIgnoreEarlierRawRuntimeFocus() {
        /** Node that remains focused in an unrelated raw runtime throughout both modal builds. */
        val backgroundNode = FocusNode(debugLabel = "background-owner")
        /** Runtime deliberately mounted first to reproduce order-sensitive global contamination. */
        val backgroundRuntime = createRuntime()
        try {
            backgroundRuntime.resolveElementTree(autofocusTree(backgroundNode, label = "BACKGROUND"))
            assertTrue(backgroundNode.isFocused)

            /** First independently built modal Element shape. */
            val firstDiagnostics = buildModalDiagnostics()
            assertTrue(backgroundNode.isFocused)
            /** Repeated independently built modal Element shape. */
            val secondDiagnostics = buildModalDiagnostics()

            assertTrue(backgroundNode.isFocused)
            assertFalse(firstDiagnostics.any { node -> node.contains("PixelControlFocusIndicatorWidget") })
            assertEquals(firstDiagnostics, secondDiagnostics)
        } finally {
            backgroundRuntime.dispose()
        }
    }

    /**
     * Creates one isolated raw build runtime with the production inflater.
     *
     * @param automaticallyFocusModalDescendants Whether raw modal attachment may select a control.
     */
    private fun createRuntime(
        automaticallyFocusModalDescendants: Boolean = true,
    ): ElementTreeBuildRuntime {
        return ElementTreeBuildRuntimeFactory.createDefault(
            onVisualUpdate = { },
            widgetAdapter = UnsupportedWidgetAdapter,
            automaticallyFocusModalDescendants = automaticallyFocusModalDescendants,
        )
    }

    /** Builds one explicitly autofocusable leaf for cross-runtime ownership assertions. */
    private fun autofocusTree(node: FocusNode, label: String): Widget {
        return Focus(
            node = node,
            autofocus = true,
            child = Text(label),
        )
    }

    /** Builds and serializes one modal tree without retaining the runtime beyond this call. */
    private fun buildModalDiagnostics(): List<String> {
        /** Short-lived raw runtime whose focus owner must be independent of every earlier build. */
        val runtime = createRuntime(automaticallyFocusModalDescendants = false)
        return try {
            runtime.resolveElementTree(
                Dialog(
                    title = Text("CONFIRM"),
                    content = Text("DELETE ITEM"),
                    actions = listOf(
                        OutlinedButton("CANCEL", onPressed = { }),
                        OutlinedButton("OK", onPressed = { }),
                    ),
                ),
            )
            runtime.collectDiagnostics().elementDiagnostics.map { node ->
                "${node.depth}:${node.widgetName}:${node.childCount}"
            }
        } finally {
            runtime.dispose()
        }
    }
}
