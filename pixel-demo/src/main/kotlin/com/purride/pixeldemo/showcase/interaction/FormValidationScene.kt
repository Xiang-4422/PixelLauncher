package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AsyncValidator
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Form
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.FormSubmitter
import com.purride.pixelui.FocusNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelTextInputAction
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
    override val description = "Form field focus, IME NEXT and cross-field validation"

    override fun build(env: DemoEnv): Widget = FormValidationWidget()
}

private class FormValidationWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = FormValidationState()

    class FormValidationState : State<FormValidationWidget>() {
        private val formController = FormController(
            validators = listOf { values ->
                if (values["name"] == values["confirmation"]) {
                    emptyMap()
                } else {
                    mapOf("confirmation" to "两次输入不一致")
                }
            },
        )
        private val nameField = FormFieldState("")
        private val confirmationField = FormFieldState("")
        private val nameController = TextEditingController()
        private val nameState = PixelTextFieldState()
        private val confirmationController = TextEditingController()
        private val confirmationState = PixelTextFieldState()
        private val nameFocus = FocusNode("name")
        private val confirmationFocus = FocusNode("confirmation")
        private var lastResult = "IDLE"
        private var pendingNameValidation: ((String?) -> Unit)? = null
        private var pendingSubmit: ((String?) -> Unit)? = null

        override fun build(context: BuildContext): Widget {
            return DemoScaffold(
                title = "Form",
                description = "NEXT、同步/异步校验和 submit 状态",
                body = Form(
                    controller = formController,
                    child = Column(
                        children = listOfNotNull(
                            FormField(
                                state = nameField,
                                fieldId = "name",
                                focusNode = nameFocus,
                                validator = { value -> if (value.trim().length < 3) "请输入至少 3 个字符" else null },
                                asyncValidator = AsyncValidator { _, complete ->
                                    var active = true
                                    val callback: (String?) -> Unit = { error ->
                                        if (active) {
                                            complete(error)
                                        }
                                    }
                                    pendingNameValidation = callback
                                    return@AsyncValidator {
                                        active = false
                                        if (pendingNameValidation === callback) {
                                            pendingNameValidation = null
                                        }
                                    }
                                },
                            ) { _, field ->
                                Column(
                                    children = listOfNotNull(
                                        Text("NAME", style = TextStyle(color = PixelColor.fromRgb(230, 180, 60))),
                                        TextField(
                                            state = nameState,
                                            controller = nameController,
                                            placeholder = "Ada",
                                            textInputAction = PixelTextInputAction.NEXT,
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
                            FormField(
                                state = confirmationField,
                                fieldId = "confirmation",
                                focusNode = confirmationFocus,
                            ) { _, field ->
                                Column(
                                    children = listOfNotNull(
                                        Text("CONFIRM", style = TextStyle(color = PixelColor.fromRgb(100, 190, 220))),
                                        TextField(
                                            state = confirmationState,
                                            controller = confirmationController,
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
                                        field.errorText?.let {
                                            Text(it, style = TextStyle(color = PixelColor.fromRgb(220, 90, 80)))
                                        },
                                    ),
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                )
                            },
                            Text("RESULT: $lastResult", style = TextStyle.Default),
                            Text("STATE: ${formController.submitState}", style = TextStyle.Default),
                            formController.submitErrorText?.let {
                                Text("SUBMIT ERROR: $it", style = TextStyle(color = PixelColor.fromRgb(220, 90, 80)))
                            },
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
                    OutlinedButton("ASYNC", onPressed = {
                        setState {
                            lastResult = "ASYNC..."
                            formController.validateAsync { valid ->
                                setState {
                                    lastResult = if (valid) "ASYNC VALID" else "ASYNC INVALID"
                                }
                            }
                        }
                    }),
                    OutlinedButton("REMOTE", onPressed = {
                        val complete = pendingNameValidation
                        pendingNameValidation = null
                        complete?.invoke(
                            if (nameField.value.equals("taken", ignoreCase = true)) {
                                "远端名称已存在"
                            } else {
                                null
                            },
                        )
                        setState { }
                    }),
                    OutlinedButton("SUBMIT", onPressed = {
                        setState {
                            lastResult = "SUBMIT..."
                            formController.submit(
                                submitter = FormSubmitter { _, complete ->
                                    val callback: (String?) -> Unit = { error -> complete(error) }
                                    pendingSubmit = callback
                                    return@FormSubmitter {
                                        if (pendingSubmit === callback) {
                                            pendingSubmit = null
                                        }
                                    }
                                },
                                onComplete = { ok ->
                                    setState {
                                        lastResult = if (ok) "SUBMITTED" else "SUBMIT FAILED"
                                    }
                                },
                            )
                        }
                    }),
                    OutlinedButton("DONE", onPressed = {
                        val complete = pendingSubmit
                        pendingSubmit = null
                        complete?.invoke(null)
                        setState { }
                    }),
                    OutlinedButton("RESET", onPressed = {
                        setState {
                            formController.reset()
                            nameController.clear(nameState)
                            confirmationController.clear(confirmationState)
                            lastResult = "IDLE"
                            pendingNameValidation = null
                            pendingSubmit = null
                        }
                    }),
                ),
            )
        }
    }
}
