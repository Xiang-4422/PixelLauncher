package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.FocusScope
import com.purride.pixelui.FocusScopeNode
import com.purride.pixelui.Form
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.FormSubmitState
import com.purride.pixelui.FormValidator
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelFocusDirection
import com.purride.pixelui.PixelFocusManager
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.PixelSemanticsNode
import com.purride.pixelui.ReadingOrderFocusTraversalPolicy
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.Yellow
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle

object FocusFormSemanticsScene : DemoScene {
    override val id = "deep_focus_form_semantics"
    override val title = "焦点表单语义"
    override val summary = "FocusScope、FormController、FormField、Semantics 与节点模型"
    override val category = DemoCatalog.navigation
    override val tags = setOf("focus", "form", "semantics", "validation", "keyboard")
    override val apis = setOf(
        "FocusScope",
        "Focus",
        "FocusNode",
        "FocusScopeNode",
        "PixelFocusDirection",
        "ReadingOrderFocusTraversalPolicy",
        "PixelFocusManager",
        "PixelKeyEvent",
        "Form",
        "FormController",
        "FormField",
        "FormFieldState",
        "FormValidator",
        "FormSubmitState",
        "Semantics",
        "PixelSemanticsNode",
        "PixelSemanticRole",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = FocusFormBody())
}

private class FocusFormBody(
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FocusFormState()

    private class FocusFormState : State<FocusFormBody>() {
        private val nameNode = FocusNode(debugLabel = "name")
        private val codeNode = FocusNode(debugLabel = "code")
        private val formController = FormController(
            validators = listOf<FormValidator> { values ->
                if ((values["code"] as? String).orEmpty().length < 3) {
                    mapOf("code" to "code must have 3 chars")
                } else {
                    emptyMap()
                }
            },
        )
        private val nameState = FormFieldState("PIXEL")
        private val codeState = FormFieldState("42")
        private var lastAction = "READY"

        override fun build(context: BuildContext): Widget {
            val semanticsNode = PixelSemanticsNode(
                label = "demo semantic button",
                role = PixelSemanticRole.BUTTON,
                enabled = true,
                focused = nameNode.isFocused || codeNode.isFocused,
                left = 2,
                top = 2,
                width = 80,
                height = 10,
            )
            return Column(
                children = listOf(
                    sectionTitle("Focus"),
                    samplePanel(
                        title = "Reading order traversal",
                        color = Cyan,
                        child = FocusScope(
                            node = formController.focusScopeNode,
                            traversalPolicy = ReadingOrderFocusTraversalPolicy,
                            child = Column(
                                children = listOf(
                                    focusChip("NAME", nameNode, Cyan),
                                    focusChip("CODE", codeNode, Pink),
                                    Row(
                                        children = listOf(
                                            OutlinedButton(
                                                text = "NAME",
                                                onPressed = {
                                                    nameNode.requestFocus()
                                                    lastAction = "focus name"
                                                    setState {}
                                                },
                                                borderColor = Cyan,
                                            ),
                                            OutlinedButton(
                                                text = "NEXT",
                                                onPressed = {
                                                    formController.focusScopeNode.focusInDirection(PixelFocusDirection.NEXT)
                                                    lastAction = PixelFocusDirection.NEXT.name
                                                    setState {}
                                                },
                                                borderColor = Accent,
                                            ),
                                            OutlinedButton(
                                                text = "TAB",
                                                onPressed = {
                                                    PixelFocusManager.dispatchKeyEvent(PixelKeyEvent(PixelKey.TAB))
                                                    lastAction = "dispatch tab"
                                                    setState {}
                                                },
                                                borderColor = Yellow,
                                            ),
                                        ),
                                        spacing = 2,
                                    ),
                                ),
                                spacing = 3,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            ),
                        ),
                    ),
                    sectionTitle("Form"),
                    samplePanel(
                        title = "FormController / FormField",
                        color = Green,
                        child = Form(
                            controller = formController,
                            child = Column(
                                children = listOf(
                                    formField("name", nameState, nameNode, Cyan),
                                    formField("code", codeState, codeNode, Pink),
                                    Row(
                                        children = listOf(
                                            OutlinedButton(
                                                text = "FIX",
                                                onPressed = {
                                                    codeState.setValue("428")
                                                    lastAction = "fixed code"
                                                    setState {}
                                                },
                                                borderColor = Green,
                                            ),
                                            OutlinedButton(
                                                text = "VALIDATE",
                                                onPressed = {
                                                    val ok = formController.validate()
                                                    lastAction = "valid=$ok"
                                                    setState {}
                                                },
                                                borderColor = Accent,
                                            ),
                                        ),
                                        spacing = 2,
                                    ),
                                    Text(
                                        "state=${formController.submitState} valid=${formController.isValid} fields=${formController.fieldCount}",
                                        style = TextStyle(color = Muted),
                                    ),
                                ),
                                spacing = 3,
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            ),
                        ),
                    ),
                    samplePanel(
                        title = "Semantics / PixelSemanticsNode",
                        color = Purple,
                        child = Semantics(
                            label = semanticsNode.label,
                            role = semanticsNode.role,
                            focused = semanticsNode.focused,
                            child = Container(
                                padding = EdgeInsets.all(2),
                                borderColor = Purple,
                                child = Text(
                                    "${semanticsNode.role} focused=${semanticsNode.focused} action=$lastAction",
                                    style = TextStyle(color = PixelColor.White),
                                ),
                            ),
                        ),
                    ),
                ),
                spacing = 4,
                mainAxisSize = MainAxisSize.MIN,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }

        private fun focusChip(label: String, node: FocusNode, color: PixelColor): Widget =
            Focus(
                node = node,
                autofocus = label == "NAME",
                child = Container(
                    padding = EdgeInsets.all(2),
                    borderColor = if (node.isFocused) Accent else color,
                    fillColor = if (node.isFocused) PixelColor.fromRgb(24, 28, 12) else null,
                    child = Text("$label focused=${node.isFocused}", style = TextStyle(color = color)),
                ),
            )

        private fun formField(
            id: String,
            fieldState: FormFieldState<String>,
            focusNode: FocusNode,
            color: PixelColor,
        ): Widget =
            FormField(
                state = fieldState,
                fieldId = id,
                focusNode = focusNode,
                validator = { value -> if (value.isBlank()) "$id required" else null },
            ) { _: BuildContext, state: FormFieldState<String> ->
                Row(
                    children = listOf(
                        Container(width = 32, child = Text(id.uppercase(), style = TextStyle(color = color))),
                        Text(state.value, style = TextStyle(color = PixelColor.White)),
                        Text(state.errorText ?: "OK", style = TextStyle(color = if (state.hasError) Pink else Green)),
                    ),
                    spacing = 2,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                )
            }
    }
}
