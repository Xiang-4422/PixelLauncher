package com.purride.pixelui.widgets

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.FocusNode
import com.purride.pixelui.Form
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldDecoration
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.PixelColorScheme
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsAction
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.TextField
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelSemanticsActionArguments
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 带装饰 TextField 的布局、语义、状态与 JVM 表面生产契约。 */
class FormFieldDecorationTest {
    /** Visible decoration is paint-only and folds into one actionable TextField semantic node. */
    @Test
    fun decorationMergesVisibleTextIntoSingleFieldNode() {
        /** Controlled field owner used to prove required decoration does not install validation. */
        val controller = PixelTextFieldController()
        /** Non-empty controlled value exported independently from the decorated label. */
        val state = controller.create(initialText = "Ada")
        /** Off-screen runtime exposing paint, input-target, and semantic snapshots. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = TextField(
                    state = state,
                    controller = controller,
                    decoration = FormFieldDecoration(
                        label = "Name",
                        helper = "Use your legal name",
                        required = true,
                        counter = "3/20",
                    ),
                    semanticHint = "Profile identity",
                    key = "decorated-name",
                ),
                logicalWidth = 32,
                logicalHeight = 32,
            )

            assertTrue(tester.exists(find.byText("Name *")))
            assertTrue(tester.exists(find.byText("Use your legal name")))
            assertTrue(tester.exists(find.byText("3/20")))
            /** The only exported node owns every currently visible decoration string. */
            val node = tester.semanticsNodes().single()
            assertEquals(PixelSemanticRole.TEXT_FIELD, node.role)
            assertEquals("Name *", node.label)
            assertEquals("Ada", node.value)
            assertEquals("Profile identity. Use your legal name. 3/20", node.hint)
            assertEquals(null, node.error)
            /** Input target geometry proves the semantic boundary excludes outer decoration. */
            val target = requireNotNull(tester.renderResult).textInputTargets.single()
            assertEquals(target.bounds.left, node.left)
            assertEquals(target.bounds.top, node.top)
            assertEquals(target.bounds.width, node.width)
            assertEquals(target.bounds.height, node.height)
            assertTrue(
                tester.performSemanticsAction(
                    id = node.id,
                    action = PixelSemanticsAction.SET_TEXT,
                    arguments = PixelSemanticsActionArguments(text = "Grace"),
                ),
            )
            assertEquals("Grace", state.text)
        } finally {
            tester.dispose()
        }
    }

    /** Error replaces helper visually and semantically while counter survives narrow constraints. */
    @Test
    fun errorOverridesHelperAndCounterSurvivesAtNarrowWidth() {
        /** Controlled field owner for the active validation state. */
        val controller = PixelTextFieldController()
        /** Empty value that keeps placeholder behavior independent from decoration strings. */
        val state = controller.create()
        /** Off-screen narrow runtime exercising ellipsis and fixed input semantics geometry. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = TextField(
                    state = state,
                    controller = controller,
                    decoration = FormFieldDecoration(
                        label = "Very long account label",
                        helper = "This helper must be hidden",
                        error = "Account identifier is invalid",
                        counter = "999/999",
                    ),
                    placeholder = "Account",
                    semanticHint = "Enter an account",
                    key = "narrow-account",
                ),
                logicalWidth = 12,
                logicalHeight = 32,
            )

            assertFalse(tester.exists(find.byText("This helper must be hidden")))
            assertTrue(tester.exists(find.byText("Account identifier is invalid")))
            assertTrue(tester.exists(find.byText("999/999")))
            /** Unique field node retains error as error and counter as non-duplicated hint. */
            val node = tester.semanticsNodes().single()
            assertEquals("Very long account label", node.label)
            assertEquals("Enter an account. 999/999", node.hint)
            assertEquals("Account identifier is invalid", node.error)
            /** Narrow layout must keep the real input target inside its logical viewport. */
            val target = requireNotNull(tester.renderResult).textInputTargets.single()
            assertTrue(target.bounds.left >= 0)
            assertTrue(target.bounds.width <= 12)
            assertTrue(target.bounds.left + target.bounds.width <= 12)
        } finally {
            tester.dispose()
        }
    }

    /** Generic FormField validation can drive decoration errors without changing its public API. */
    @Test
    fun genericFormValidationOverridesStaticHelperAndRestoresIt() {
        /** Existing generic form owner whose descriptor must remain untouched. */
        val form = FormController()
        /** Existing generic field state carrying validation output into decoration. */
        val formState = FormFieldState("")
        /** Controlled text owner deliberately separate from generic form value state. */
        val textController = PixelTextFieldController()
        /** Controlled editable state mapped into [formState] by the public callback. */
        val textState = textController.create()
        /** Off-screen runtime rebuilding decoration when generic validation state changes. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Form(
                    controller = form,
                    child = FormField(
                        state = formState,
                        validator = { value -> if (value.isBlank()) "Name is required" else null },
                        key = "generic-name-field",
                    ) { _, field ->
                        TextField(
                            state = textState,
                            controller = textController,
                            decoration = FormFieldDecoration(
                                label = "Name",
                                helper = "Public profile name",
                                error = field.errorText,
                                counter = "0/20",
                            ),
                            onChanged = field::setValue,
                            key = "generic-name-input",
                        )
                    },
                ),
                logicalWidth = 36,
                logicalHeight = 32,
            )
            /** Stable semantic identity before validation changes only decoration content. */
            val initialNode = tester.semanticsNodes().single()
            assertTrue(tester.exists(find.byText("Public profile name")))

            assertFalse(form.validate())
            tester.pumpFrame(0)
            /** Error frame retains the same field node and caller counter. */
            val errorNode = tester.semanticsNodes().single()
            assertEquals(initialNode.id, errorNode.id)
            assertEquals("Name is required", errorNode.error)
            assertFalse(tester.exists(find.byText("Public profile name")))
            assertTrue(tester.exists(find.byText("0/20")))

            formState.setValue("Ada")
            assertTrue(form.validate())
            tester.pumpFrame(0)
            /** Successful revalidation restores helper without remounting the input. */
            val validNode = tester.semanticsNodes().single()
            assertEquals(initialNode.id, validNode.id)
            assertEquals(null, validNode.error)
            assertTrue(tester.exists(find.byText("Public profile name")))
        } finally {
            tester.dispose()
        }
    }

    /** Decoration updates retain input identity, focus, selection, composition, and surface extent. */
    @Test
    fun decorationUpdateRetainsInputStateAndSemanticIdentity() {
        /** Controlled field owner retained across decoration-only rebuilds. */
        val controller = PixelTextFieldController()
        /** Text, selection, and IME composition state that must survive the rebuild. */
        val state = controller.create(initialText = "COMPOSE", selectionStart = 1, selectionEnd = 4)
        /** Explicit focus owner proving decoration never replaces the automatic field binding. */
        val focusNode = FocusNode(debugLabel = "stable-decoration-field")
        /** Mutable presentation-only input driving the retained subtree update. */
        val decoration = ValueNotifier(
            FormFieldDecoration(
                helper = "Initial helper",
                counter = "7/20",
            ),
        )
        /** Off-screen runtime preserving the same retained tree between notifier frames. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = ValueListenableBuilder(
                    listenable = decoration,
                    key = "decoration-listener",
                ) { _, value ->
                    TextField(
                        state = state,
                        controller = controller,
                        decoration = value,
                        placeholder = "Account",
                        focusNode = focusNode,
                        key = "stable-decoration-field",
                    )
                },
                logicalWidth = 40,
                logicalHeight = 36,
            )
            focusNode.requestFocus()
            controller.updateComposition(state = state, compositionStart = 1, compositionEnd = 4)
            tester.pumpFrame(0)
            /** Original semantic identity owned by the retained input boundary. */
            val originalNode = tester.semanticsNodes().single()
            /** Original surface target used to freeze width and height, not decoration offsets. */
            val originalTarget = requireNotNull(tester.renderResult).textInputTargets.single()

            decoration.value = FormFieldDecoration(
                label = "Account",
                error = "Invalid account",
                required = true,
                counter = "7/20",
            )
            tester.pumpFrame(0)

            /** Updated node must reuse the same RenderSemantics identity. */
            val updatedNode = tester.semanticsNodes().single()
            /** Updated target must still address the same controlled input state and owner. */
            val updatedTarget = requireNotNull(tester.renderResult).textInputTargets.single()
            assertEquals(originalNode.id, updatedNode.id)
            assertEquals("Account *", updatedNode.label)
            assertEquals("Invalid account", updatedNode.error)
            assertSame(state, updatedTarget.state)
            assertSame(controller, updatedTarget.controller)
            assertEquals(originalTarget.bounds.width, updatedTarget.bounds.width)
            assertEquals(originalTarget.bounds.height, updatedTarget.bounds.height)
            assertTrue(focusNode.isFocused)
            assertTrue(state.isFocused)
            assertEquals(1, state.selectionStart)
            assertEquals(4, state.selectionEnd)
            assertEquals(1, state.compositionStart)
            assertEquals(4, state.compositionEnd)
            assertTrue(
                tester.performSemanticsAction(
                    id = updatedNode.id,
                    action = PixelSemanticsAction.SET_SELECTION,
                    arguments = PixelSemanticsActionArguments(selectionStart = 2, selectionEnd = 5),
                ),
            )
            assertEquals(2, state.selectionStart)
            assertEquals(5, state.selectionEnd)
        } finally {
            tester.dispose()
        }
    }

    /** Decorated state overload shares TextField token colors and capability normalization. */
    @Test
    fun stateAwareDecorationSharesTextFieldCapabilitiesAndColors() {
        /** Unique normal decoration content sentinel. */
        val normal = PixelColor.fromRgb(31, 101, 173)
        /** Unique error decoration content sentinel. */
        val danger = PixelColor.fromRgb(199, 37, 79)
        /** Unique loading decoration content sentinel. */
        val warning = PixelColor.fromRgb(227, 151, 19)
        /** Unique disabled decoration content sentinel. */
        val disabled = PixelColor.fromRgb(83, 89, 97)
        /** Scheme that makes every TextField content state directly observable above the input. */
        val colors = PixelColorScheme.Dark.copy(
            onBackground = normal,
            danger = danger,
            warning = warning,
            onDisabled = disabled,
        )
        /** Complete theme graph retaining the production TextField component family. */
        val theme = PixelThemeTokens.Default.copy(colors = colors)
        /** Matrix covering every capability-distinct decorated TextField state in this milestone. */
        val cases = listOf(
            DecorationStateCase(
                name = "normal",
                states = PixelControlStateSet.Normal,
                expectedColor = normal,
                expectedEnabled = true,
                expectedEditable = true,
            ),
            DecorationStateCase(
                name = "error",
                states = PixelControlStateSet.of(PixelControlState.Error),
                error = "Invalid",
                expectedColor = danger,
                expectedEnabled = true,
                expectedEditable = true,
            ),
            DecorationStateCase(
                name = "disabled",
                states = PixelControlStateSet.of(PixelControlState.Disabled),
                expectedColor = disabled,
                expectedEnabled = false,
                expectedEditable = false,
            ),
            DecorationStateCase(
                name = "loading",
                states = PixelControlStateSet.of(PixelControlState.Loading),
                expectedColor = warning,
                expectedEnabled = false,
                expectedEditable = false,
            ),
            DecorationStateCase(
                name = "read-only",
                states = PixelControlStateSet.Normal,
                readOnly = true,
                expectedColor = normal,
                expectedEnabled = true,
                expectedEditable = false,
            ),
        )

        cases.forEach { stateCase ->
            /** Independent controller prevents capability state from leaking between matrix rows. */
            val controller = PixelTextFieldController()
            /** Non-empty controlled value makes editable and read-only surfaces equivalent in size. */
            val state = controller.create(initialText = "VALUE")
            /** Independent runtime isolates exact state paint and actions. */
            val tester = PixelTester()
            try {
                tester.pumpWidget(
                    widget = PixelTheme(
                        tokens = theme,
                        child = TextField(
                            state = state,
                            controller = controller,
                            states = stateCase.states,
                            decoration = FormFieldDecoration(
                                label = "I",
                                helper = "Support",
                                error = stateCase.error,
                                counter = "5/9",
                            ),
                            readOnly = stateCase.readOnly,
                            key = stateCase.name,
                        ),
                    ),
                    logicalWidth = 30,
                    logicalHeight = 32,
                )

                /** Unique field node exposes capability actions for this matrix row. */
                val node = tester.semanticsNodes().single()
                assertEquals(stateCase.name, stateCase.expectedEnabled, node.enabled)
                assertEquals(
                    stateCase.name,
                    stateCase.expectedEditable,
                    PixelSemanticsAction.SET_TEXT in node.actions,
                )
                assertEquals(
                    stateCase.name,
                    stateCase.expectedEnabled,
                    PixelSemanticsAction.CLICK in node.actions,
                )
                assertTrue(
                    "${stateCase.name} decoration did not consume TextField content tokens",
                    tester.hasPixelAbove(yExclusive = node.top, color = stateCase.expectedColor),
                )
            } finally {
                tester.dispose()
            }
        }
    }

    /** Existing TextField/FormField descriptors remain exact and decoration names stay additive. */
    @Test
    fun existingJvmDescriptorsRemainExactAndDecorationNamesAreAdditive() {
        /** Runtime facade generated for public TextField declarations. */
        val widgetFacade = Class.forName(PIXEL_WIDGETS_FACADE)
        /** Exact TextField-related descriptors after adding the new overloads. */
        val widgetDescriptors = widgetFacade.declaredMethods.associate { method ->
            method.name to method.jvmDescriptor()
        }
        EXISTING_TEXT_FIELD_DESCRIPTORS.forEach { (name, descriptor) ->
            assertEquals(
                "Existing TextField descriptor changed for $name",
                descriptor,
                widgetDescriptors[name],
            )
        }
        DECORATED_TEXT_FIELD_NAMES.forEach { name ->
            assertNotNull("Missing additive JVM method $name", widgetDescriptors[name])
            assertNotNull("Missing additive default bridge for $name", widgetDescriptors["$name\$default"])
        }

        /** Runtime facade generated for the unchanged generic FormField declaration. */
        val formFacade = Class.forName(PIXEL_FORM_FACADE)
        /** Exact generic FormField descriptors protected from accidental convenience-parameter edits. */
        val formDescriptors = formFacade.declaredMethods.associate { method ->
            method.name to method.jvmDescriptor()
        }
        GENERIC_FORM_FIELD_DESCRIPTORS.forEach { (name, descriptor) ->
            assertEquals("Generic FormField descriptor changed for $name", descriptor, formDescriptors[name])
        }
    }

    /** Returns whether [color] appears strictly above the input semantic surface. */
    private fun PixelTester.hasPixelAbove(yExclusive: Int, color: PixelColor): Boolean {
        for (y in 0 until yExclusive.coerceAtLeast(0)) {
            for (x in 0 until STATE_TEST_WIDTH) {
                if (pixelAt(x, y) == color) return true
            }
        }
        return false
    }

    /** Builds one JVM method descriptor without Kotlin-reflection metadata. */
    private fun Method.jvmDescriptor(): String {
        /** Ordered JVM field descriptors for every declared parameter. */
        val parameters = parameterTypes.joinToString(separator = "") { type -> type.jvmTypeDescriptor() }
        return "($parameters)${returnType.jvmTypeDescriptor()}"
    }

    /** Encodes one Java reflection type in JVM field-descriptor form. */
    private fun Class<*>.jvmTypeDescriptor(): String {
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
            else -> error("Unsupported primitive reflection type: $name")
        }
    }

    /** One capability and token-color row in the decorated TextField state matrix. */
    private data class DecorationStateCase(
        /** Diagnostic matrix row name. */
        val name: String,
        /** Persistent states supplied to the state-aware public overload. */
        val states: PixelControlStateSet,
        /** Optional active decoration error. */
        val error: String? = null,
        /** Whether the field keeps focus but suppresses text edits. */
        val readOnly: Boolean = false,
        /** Exact TextField content-role color expected in decoration text. */
        val expectedColor: PixelColor,
        /** Whether click/focus capability remains exposed. */
        val expectedEnabled: Boolean,
        /** Whether set-text capability remains exposed. */
        val expectedEditable: Boolean,
    )

    private companion object {
        /** Logical width shared by state color probes. */
        const val STATE_TEST_WIDTH: Int = 30

        /** Generated top-level facade containing TextField entry points. */
        const val PIXEL_WIDGETS_FACADE: String = "com.purride.pixelui.PixelWidgetsKt"

        /** Generated top-level facade containing generic FormField entry points. */
        const val PIXEL_FORM_FACADE: String = "com.purride.pixelui.PixelFormKt"

        /** Stable readable JVM names reserved for explicit decoration overloads. */
        val DECORATED_TEXT_FIELD_NAMES: Set<String> = linkedSetOf(
            "TextFieldWithDecoration",
            "TextFieldWithControlStatesAndDecoration",
        )

        /** 未带装饰的两个 TextField 入口及其 Kotlin 默认实参桥接的精确描述符。 */
        val EXISTING_TEXT_FIELD_DESCRIPTORS: Map<String, String> = linkedMapOf(
            "TextField-6rZe5Dk" to "(Lcom/purride/pixelui/state/PixelTextFieldState;Lcom/purride/pixelui/state/PixelTextFieldController;Ljava/lang/String;Lcom/purride/pixelui/PixelTextFieldStyle;ZZZIILcom/purride/pixelui/PixelInputType;Lcom/purride/pixelui/TextAlign;Lcom/purride/pixelui/PixelTextInputAction;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelui/FocusNode;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lcom/purride/pixelui/Widget;",
            "TextField-6rZe5Dk\$default" to "(Lcom/purride/pixelui/state/PixelTextFieldState;Lcom/purride/pixelui/state/PixelTextFieldController;Ljava/lang/String;Lcom/purride/pixelui/PixelTextFieldStyle;ZZZIILcom/purride/pixelui/PixelInputType;Lcom/purride/pixelui/TextAlign;Lcom/purride/pixelui/PixelTextInputAction;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelui/FocusNode;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "TextFieldWithControlStates" to "(Lcom/purride/pixelui/state/PixelTextFieldState;Lcom/purride/pixelui/state/PixelTextFieldController;ILjava/lang/String;Lcom/purride/pixelui/PixelTextFieldStyle;ZZZIILcom/purride/pixelui/PixelInputType;Lcom/purride/pixelui/TextAlign;Lcom/purride/pixelui/PixelTextInputAction;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelui/FocusNode;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lcom/purride/pixelui/Widget;",
            "TextFieldWithControlStates\$default" to "(Lcom/purride/pixelui/state/PixelTextFieldState;Lcom/purride/pixelui/state/PixelTextFieldController;ILjava/lang/String;Lcom/purride/pixelui/PixelTextFieldStyle;ZZZIILcom/purride/pixelui/PixelInputType;Lcom/purride/pixelui/TextAlign;Lcom/purride/pixelui/PixelTextInputAction;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelcore/PixelColor;Lcom/purride/pixelui/FocusNode;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
        )

        /** Exact generic FormField method and Kotlin default bridge descriptors. */
        val GENERIC_FORM_FIELD_DESCRIPTORS: Map<String, String> = linkedMapOf(
            "FormField" to "(Lcom/purride/pixelui/FormFieldState;Lkotlin/jvm/functions/Function1;Ljava/lang/String;Lcom/purride/pixelui/FocusNode;Ljava/lang/Object;Lcom/purride/pixelui/AsyncValidator;Lkotlin/jvm/functions/Function2;)Lcom/purride/pixelui/Widget;",
            "FormField\$default" to "(Lcom/purride/pixelui/FormFieldState;Lkotlin/jvm/functions/Function1;Ljava/lang/String;Lcom/purride/pixelui/FocusNode;Ljava/lang/Object;Lcom/purride/pixelui/AsyncValidator;Lkotlin/jvm/functions/Function2;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
        )
    }
}
