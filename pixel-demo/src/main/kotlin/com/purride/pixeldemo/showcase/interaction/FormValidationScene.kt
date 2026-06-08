package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Form
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextFieldStyle
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.DemoScaffold

object FormValidationScene : DemoScene {
    override val id = "form_validation"
    override val title = "Form validation"
    override val description = "Form / FormField / Validator 基础校验和 reset"

    override fun build(env: DemoEnv): Widget = FormValidationWidget()
}

private class FormValidationWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FormValidationState()

    class FormValidationState : State<FormValidationWidget>() {
        private val formController = FormController()
        private val nameField = FormFieldState("")
        private val textController = TextEditingController()
        private val textState = PixelTextFieldState()
        private var lastResult = "IDLE"

        override fun build(context: BuildContext): Widget {
            return DemoScaffold(
                title = "Form",
                description = "输入至少 3 个字符后校验通过",
                body = Form(
                    controller = formController,
                    child = Column(
                        children = listOf(
                            FormField(
                                state = nameField,
                                validator = { value -> if (value.trim().length < 3) "请输入至少 3 个字符" else null },
                            ) { _, field ->
                                Column(
                                    children = listOfNotNull(
                                        Text("NAME", style = TextStyle(color = PixelColor.fromRgb(230, 180, 60))),
                                        TextField(
                                            state = textState,
                                            controller = textController,
                                            placeholder = "Ada",
                                            style = TextFieldStyle(
                                                borderColor = if (field.hasError) {
                                                    PixelColor.fromRgb(220, 90, 80)
                                                } else {
                                                    PixelColor.White
                                                },
                                            ),
                                            onChanged = { value -> field.setValue(value) },
                                        ),
                                        field.errorText?.let { Text(it, style = TextStyle(color = PixelColor.fromRgb(220, 90, 80))) },
                                    ),
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                )
                            },
                            Text("RESULT: $lastResult", style = TextStyle.Default),
                        ),
                        spacing = 4,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                controls = listOf(
                    OutlinedButton("VALIDATE", onPressed = {
                        setState {
                            lastResult = if (formController.validate()) "VALID" else "INVALID"
                        }
                    }),
                    OutlinedButton("RESET", onPressed = {
                        setState {
                            formController.reset()
                            textController.clear(textState)
                            lastResult = "IDLE"
                        }
                    }),
                ),
            )
        }
    }
}
