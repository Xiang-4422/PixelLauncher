package com.purride.pixelui.foundation

import com.purride.pixelui.Column
import com.purride.pixelui.AsyncFormValidator
import com.purride.pixelui.AsyncValidator
import com.purride.pixelui.Form
import com.purride.pixelui.FormSubmitState
import com.purride.pixelui.FormSubmitter
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.FocusNode
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelTextInputAction
import com.purride.pixelui.Text
import com.purride.pixelui.TextField
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PixelFormTest {
    @Test
    fun validateSetsFieldErrorAndUpdatesFormValidity() {
        val tester = PixelTester()
        val form = FormController()
        val name = FormFieldState("")
        try {
            tester.pumpWidget(
                Form(
                    controller = form,
                    child = Column(
                        children = listOf(
                            FormField(
                                state = name,
                                validator = { value -> if (value.isBlank()) "required" else null },
                            ) { _, field ->
                                Column(
                                    children = listOfNotNull(
                                        Text("NAME"),
                                        field.errorText?.let { Text(it) },
                                    ),
                                )
                            },
                            OutlinedButton("VALIDATE", onPressed = { form.validate() }),
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 32,
            )

            assertEquals(1, form.fieldCount)
            assertTrue(form.isValid)

            tester.tap(find.byText("VALIDATE"))

            assertFalse(form.isValid)
            assertTrue(name.hasError)
            assertEquals("required", name.errorText)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun setValueAndResetRoundTripFieldState() {
        val tester = PixelTester()
        val form = FormController()
        val name = FormFieldState("Ada")
        try {
            tester.pumpWidget(
                Form(
                    controller = form,
                    child = Column(
                        children = listOf(
                            FormField(
                                state = name,
                                validator = { value -> if (value.length < 3) "short" else null },
                            ) { _, field ->
                                Text("VALUE ${field.value}")
                            },
                            OutlinedButton("SET", onPressed = { name.setValue("Al") }),
                            OutlinedButton("VALIDATE", onPressed = { form.validate() }),
                            OutlinedButton("RESET", onPressed = { form.reset() }),
                        ),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 40,
            )

            tester.tap(find.byText("SET"))
            assertEquals("Al", name.value)

            tester.tap(find.byText("VALIDATE"))
            assertEquals("short", name.errorText)

            tester.tap(find.byText("RESET"))
            assertEquals("Ada", name.value)
            assertFalse(name.hasError)
            assertTrue(form.isValid)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun crossFieldValidatorUsesNamedValueSnapshot() {
        val password = FormFieldState("secret")
        val confirmation = FormFieldState("different")
        val form = FormController(
            validators = listOf { values ->
                if (values["password"] == values["confirmation"]) {
                    emptyMap()
                } else {
                    mapOf("confirmation" to "does not match")
                }
            },
        )
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Form(
                    controller = form,
                    child = Column(
                        children = listOf(
                            FormField(state = password, fieldId = "password") { _, field ->
                                Text("PASSWORD ${field.value}")
                            },
                            FormField(state = confirmation, fieldId = "confirmation") { _, field ->
                                Text("CONFIRM ${field.value}")
                            },
                        ),
                    ),
                ),
                logicalWidth = 80,
                logicalHeight = 24,
            )

            assertFalse(form.validate())
            assertEquals("does not match", confirmation.errorText)

            confirmation.setValue("secret")
            assertTrue(form.validate())
            assertEquals(null, confirmation.errorText)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun formFieldFocusNodesDriveTextInputNextWithoutManualFocusWrappers() {
        val form = FormController()
        val firstField = FormFieldState("")
        val secondField = FormFieldState("")
        val firstFocus = FocusNode("first")
        val secondFocus = FocusNode("second")
        val firstController = PixelTextFieldController()
        val secondController = PixelTextFieldController()
        val firstText = firstController.create()
        val secondText = secondController.create()
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                Form(
                    controller = form,
                    child = Column(
                        children = listOf(
                            FormField(
                                state = firstField,
                                fieldId = "first",
                                focusNode = firstFocus,
                            ) { _, field ->
                                TextField(
                                    state = firstText,
                                    controller = firstController,
                                    placeholder = "FIRST",
                                    textInputAction = PixelTextInputAction.NEXT,
                                    onChanged = field::setValue,
                                )
                            },
                            FormField(
                                state = secondField,
                                fieldId = "second",
                                focusNode = secondFocus,
                            ) { _, field ->
                                TextField(
                                    state = secondText,
                                    controller = secondController,
                                    placeholder = "SECOND",
                                    onChanged = field::setValue,
                                )
                            },
                        ),
                    ),
                ),
                logicalWidth = 72,
                logicalHeight = 28,
            )

            tester.enterText(find.byText("FIRST"), "Ada")
            assertTrue(firstFocus.isFocused)
            tester.submitTextInput()

            assertTrue(secondFocus.isFocused)
            assertTrue(secondText.isFocused)
            tester.enterText(find.byText("SECOND"), "Lovelace")
            assertEquals("Lovelace", secondField.value)
        } finally {
            tester.dispose()
        }
    }

    @Test
    fun duplicateFieldIdsReportTheConflictingId() {
        val form = FormController()
        val first = form.registerField(FormFieldState("a"), fieldId = "name")
        try {
            form.registerField(FormFieldState("b"), fieldId = "name")
            fail("duplicate fieldId should fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("name"))
        } finally {
            first.dispose()
        }
    }

    @Test
    fun crossFieldValidatorRejectsUnknownFieldIds() {
        val form = FormController(
            validators = listOf { mapOf("missing" to "invalid") },
        )
        val registration = form.registerField(
            state = FormFieldState("value"),
            fieldId = "known",
        )
        try {
            form.validate()
            fail("unknown cross-field id should fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("missing"))
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun validateAsyncRunsFieldAndCrossFieldValidators() {
        val name = FormFieldState("Ada")
        var fieldComplete: ((String?) -> Unit)? = null
        var crossComplete: ((Map<String, String>) -> Unit)? = null
        val form = FormController(
            asyncValidators = listOf(
                AsyncFormValidator { values, complete ->
                    assertEquals("Ada", values["name"])
                    crossComplete = complete
                    return@AsyncFormValidator { }
                },
            ),
        )
        val registration = form.registerField(
            state = name,
            fieldId = "name",
            asyncValidator = AsyncValidator { value, complete ->
                assertEquals("Ada", value)
                fieldComplete = complete
                return@AsyncValidator { }
            },
        )
        try {
            var result: Boolean? = null

            form.validateAsync { valid -> result = valid }

            assertEquals(FormSubmitState.VALIDATING, form.submitState)
            assertTrue(form.isValidating)
            fieldComplete?.invoke(null)
            assertEquals(null, result)

            crossComplete?.invoke(mapOf("name" to "taken"))

            assertEquals(false, result)
            assertEquals(FormSubmitState.IDLE, form.submitState)
            assertEquals("taken", name.errorText)
            assertFalse(form.isValid)
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun validateAsyncCancelIgnoresLateFieldCompletion() {
        val name = FormFieldState("Ada")
        var cancelled = false
        var completeField: ((String?) -> Unit)? = null
        val form = FormController()
        val registration = form.registerField(
            state = name,
            asyncValidator = AsyncValidator { _, complete ->
                completeField = complete
                return@AsyncValidator { cancelled = true }
            },
        )
        try {
            var result: Boolean? = null
            val operation = form.validateAsync { valid -> result = valid }

            operation.cancel()
            completeField?.invoke("late")

            assertTrue(cancelled)
            assertEquals(null, result)
            assertEquals(null, name.errorText)
            assertEquals(FormSubmitState.IDLE, form.submitState)
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun submitTracksSubmittingSucceededAndFailureStates() {
        val name = FormFieldState("Ada")
        val form = FormController()
        val registration = form.registerField(state = name, fieldId = "name")
        try {
            var submitComplete: ((String?) -> Unit)? = null
            var result: Boolean? = null

            form.submit(
                submitter = FormSubmitter { values, complete ->
                    assertEquals("Ada", values["name"])
                    submitComplete = complete
                    return@FormSubmitter { }
                },
                onComplete = { ok -> result = ok },
            )

            assertEquals(FormSubmitState.SUBMITTING, form.submitState)
            assertTrue(form.isSubmitting)

            submitComplete?.invoke("network")

            assertEquals(FormSubmitState.FAILED, form.submitState)
            assertEquals("network", form.submitErrorText)
            assertEquals(false, result)

            form.clearSubmitState()
            assertEquals(FormSubmitState.IDLE, form.submitState)

            form.submit(
                submitter = FormSubmitter { _, complete ->
                    complete(null)
                    return@FormSubmitter { }
                },
                onComplete = { ok -> result = ok },
            )

            assertEquals(FormSubmitState.SUCCEEDED, form.submitState)
            assertEquals(null, form.submitErrorText)
            assertEquals(true, result)
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun submitStopsBeforeSubmitterWhenValidationFails() {
        val name = FormFieldState("")
        val form = FormController()
        val registration = form.registerField(
            state = name,
            validator = { value -> if (value.isBlank()) "required" else null },
        )
        try {
            var submitted = false
            var result: Boolean? = null

            form.submit(
                submitter = FormSubmitter { _, _ ->
                    submitted = true
                    return@FormSubmitter { }
                },
                onComplete = { ok -> result = ok },
            )

            assertFalse(submitted)
            assertEquals(false, result)
            assertEquals("required", name.errorText)
            assertEquals(FormSubmitState.FAILED, form.submitState)
        } finally {
            registration.dispose()
        }
    }

    @Test
    fun formFieldRegistersAsyncValidatorFromWidget() {
        val tester = PixelTester()
        val form = FormController()
        val name = FormFieldState("Ada")
        var completeField: ((String?) -> Unit)? = null
        try {
            tester.pumpWidget(
                Form(
                    controller = form,
                    child = FormField(
                        state = name,
                        asyncValidator = AsyncValidator { _, complete ->
                            completeField = complete
                            return@AsyncValidator { }
                        },
                    ) { _, field ->
                        Text("VALUE ${field.value}")
                    },
                ),
                logicalWidth = 72,
                logicalHeight = 24,
            )

            var result: Boolean? = null
            form.validateAsync { valid -> result = valid }
            completeField?.invoke("remote")

            assertEquals(false, result)
            assertEquals("remote", name.errorText)
        } finally {
            tester.dispose()
        }
    }
}
