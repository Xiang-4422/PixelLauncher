package com.purride.pixelui.foundation

import com.purride.pixelui.Column
import com.purride.pixelui.Form
import com.purride.pixelui.FormController
import com.purride.pixelui.FormField
import com.purride.pixelui.FormFieldState
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Text
import com.purride.pixelui.testing.PixelTester
import com.purride.pixelui.testing.find
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
