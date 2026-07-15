package com.purride.pixelui.widgets

import com.purride.pixelui.ActivityIndicator
import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.AppScaffold
import com.purride.pixelui.Badge
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.Checkbox
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.Dialog
import com.purride.pixelui.Divider
import com.purride.pixelui.EmptyState
import com.purride.pixelui.ListTile
import com.purride.pixelui.LoadStateView
import com.purride.pixelui.ModalBarrier
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelLoadingBar
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Snackbar
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.Toast
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks legacy PixelComponents binary entry points beside the explicit state-aware JVM names. */
class PixelComponentsJvmCompatibilityTest {
    /** Every legacy method and Kotlin default bridge retains its exact pre-state JVM descriptor. */
    @Test
    fun legacyDescriptorsAndDefaultBridgesRemainExact() {
        /** Runtime facade class generated for the top-level PixelComponents declarations. */
        val facade = Class.forName(PIXEL_COMPONENTS_FACADE)
        /** Exact runtime descriptor snapshot indexed by unmangled or value-class-mangled JVM name. */
        val actualDescriptors = facade.declaredMethods.associate { method ->
            method.name to method.jvmDescriptor()
        }

        LEGACY_DESCRIPTORS.forEach { (methodName, expectedDescriptor) ->
            assertEquals(
                "Legacy descriptor changed for $methodName",
                expectedDescriptor,
                actualDescriptors[methodName],
            )
        }
    }

    /** Every required state overload has a stable readable JVM name and a generated default bridge. */
    @Test
    fun stateAwareOverloadsExposeStableJvmNames() {
        /** Runtime methods used to reject accidental Kotlin value-class name mangling. */
        val methods = Class.forName(PIXEL_COMPONENTS_FACADE).declaredMethods
        /** All generated method names, including Kotlin `$default` bridges. */
        val methodNames = methods.mapTo(linkedSetOf()) { method -> method.name }

        REQUIRED_STATE_JVM_NAMES.forEach { stableName ->
            assertTrue("Missing stable state overload $stableName", stableName in methodNames)
            assertTrue(
                "Missing default bridge for $stableName",
                "${stableName}\$default" in methodNames,
            )
            assertTrue(
                "State overload $stableName was unexpectedly value-class mangled",
                methodNames.none { name -> name.startsWith("$stableName-") },
            )
            /** Exact non-default state method used to verify the value-class state mask is present. */
            val stateMethod = methods.single { method -> method.name == stableName }
            assertTrue(
                "$stableName must carry the unboxed PixelControlStateSet mask",
                stateMethod.parameterTypes.any { parameter -> parameter == Int::class.javaPrimitiveType },
            )
            assertEquals(Widget::class.java, stateMethod.returnType)
        }
    }

    /** Kotlin source can resolve both old calls and all new state-aware overloads in one module. */
    @Test
    fun legacyAndStateAwareKotlinCallsCompileTogether() {
        /** Tester supplies the ticker provider required by the animated loading-bar source calls. */
        val tester = PixelTester()
        try {
            /** Canonical state argument that selects every required state-aware overload. */
            val states = PixelControlStateSet.Normal
            /** Source-level constructions spanning every migrated public component family. */
            val widgets = listOf<Widget>(
                ListTile(title = Text("L"), onTap = {}),
                ListTile(title = Text("L"), states = states, onTap = {}),
                Checkbox(checked = false, onChanged = {}),
                Checkbox(checked = false, onChanged = {}, states = states),
                Switch(checked = false, onChanged = {}),
                Switch(checked = false, onChanged = {}, states = states),
                Dialog(content = Text("D"), modal = false),
                Dialog(content = Text("D"), states = states, modal = false),
                BottomSheet(content = Text("B"), modal = false),
                BottomSheet(content = Text("B"), states = states, modal = false),
                ConfirmDialog(title = "C", message = "M", onConfirm = {}),
                ConfirmDialog(title = "C", message = "M", onConfirm = {}, states = states),
                ModalBarrier(),
                ModalBarrier(states = states),
                Toast(message = "T"),
                Toast(message = "T", states = states),
                Snackbar(message = "S"),
                Snackbar(message = "S", states = states),
                Tabs(labels = listOf("A"), selectedIndex = 0, onSelected = {}),
                Tabs(labels = listOf("A"), selectedIndex = 0, onSelected = {}, states = states),
                SegmentedControl(labels = listOf("A"), selectedIndex = 0, onSelected = {}),
                SegmentedControl(
                    labels = listOf("A"),
                    selectedIndex = 0,
                    onSelected = {},
                    states = states,
                ),
                ValueAdjuster(valueText = "1", onDecrease = {}, onIncrease = {}),
                ValueAdjuster(
                    valueText = "1",
                    onDecrease = {},
                    onIncrease = {},
                    states = states,
                ),
                Stepper(value = 1, range = 0..2, onChanged = {}),
                Stepper(value = 1, range = 0..2, onChanged = {}, states = states),
                ProgressBar(progress = 0.5f),
                ProgressBar(progress = 0.5f, states = states),
                PixelLoadingBar(progress = 0.5f),
                PixelLoadingBar(progress = 0.5f, states = states),
                AnimatedPixelLoadingBar(vsync = tester.vsync, playing = false),
                AnimatedPixelLoadingBar(vsync = tester.vsync, states = states, playing = false),
                ActivityIndicator(),
                ActivityIndicator(states = states),
                LoadStateView(
                    snapshot = PixelAsyncSnapshot.Success("legacy"),
                    content = { value -> Text(value) },
                ),
                LoadStateView(
                    snapshot = PixelAsyncSnapshot.Success("state"),
                    states = states,
                    content = { value -> Text(value) },
                ),
                EmptyState(title = "E"),
                EmptyState(states = states, title = "E"),
                Badge(child = Text("B"), label = Text("1")),
                Badge(child = Text("B"), label = Text("1"), states = states),
                Divider(),
                Divider(states = states),
                AppScaffold(body = Text("A")),
                AppScaffold(body = Text("A"), states = states),
            )

            assertEquals(REQUIRED_STATE_JVM_NAMES.size * 2, widgets.size)
            assertNotNull(widgets.first())
        } finally {
            tester.dispose()
        }
    }

    /** Builds the JVM method descriptor without relying on Kotlin reflection metadata. */
    private fun Method.jvmDescriptor(): String {
        /** Ordered parameter descriptors following the JVM class-file encoding. */
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

    private companion object {
        /** Generated top-level facade containing all PixelComponents JVM methods. */
        const val PIXEL_COMPONENTS_FACADE: String = "com.purride.pixelui.PixelComponentsKt"

        /** Stable readable names assigned to every state-aware overload in this milestone. */
        val REQUIRED_STATE_JVM_NAMES: Set<String> = linkedSetOf(
            "ListTileWithControlStates",
            "CheckboxWithControlStates",
            "SwitchWithControlStates",
            "DialogWithControlStates",
            "BottomSheetWithControlStates",
            "ConfirmDialogWithControlStates",
            "ModalBarrierWithControlStates",
            "ToastWithControlStates",
            "SnackbarWithControlStates",
            "TabsWithControlStates",
            "SegmentedControlWithControlStates",
            "ValueAdjusterWithControlStates",
            "StepperWithControlStates",
            "ProgressBarWithControlStates",
            "PixelLoadingBarWithControlStates",
            "AnimatedPixelLoadingBarWithControlStates",
            "ActivityIndicatorWithControlStates",
            "LoadStateViewWithControlStates",
            "EmptyStateWithControlStates",
            "BadgeWithControlStates",
            "DividerWithControlStates",
            "AppScaffoldWithControlStates",
        )

        /** Exact pre-migration method and `$default` descriptors for all migrated legacy facades. */
        val LEGACY_DESCRIPTORS: Map<String, String> = linkedMapOf(
            "ListTile" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lkotlin/jvm/functions/Function0;ZLjava/lang/String;Ljava/lang/Object;Lcom/purride/pixelui/PixelSemanticRole;ZLjava/lang/Boolean;Ljava/lang/Boolean;)Lcom/purride/pixelui/Widget;",
            "ListTile\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lkotlin/jvm/functions/Function0;ZLjava/lang/String;Ljava/lang/Object;Lcom/purride/pixelui/PixelSemanticRole;ZLjava/lang/Boolean;Ljava/lang/Boolean;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Checkbox-zcjAOrI" to "(ZLkotlin/jvm/functions/Function1;ZIILjava/lang/String;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Checkbox-zcjAOrI\$default" to "(ZLkotlin/jvm/functions/Function1;ZIILjava/lang/String;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Switch-zcjAOrI" to "(ZLkotlin/jvm/functions/Function1;ZIILjava/lang/String;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Switch-zcjAOrI\$default" to "(ZLkotlin/jvm/functions/Function1;ZIILjava/lang/String;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Dialog-nipCVRc" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/util/List;IILjava/lang/Object;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Z)Lcom/purride/pixelui/Widget;",
            "Dialog-nipCVRc\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/util/List;IILjava/lang/Object;Ljava/lang/String;Lkotlin/jvm/functions/Function0;ZILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "BottomSheet-nipCVRc" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/util/List;IILjava/lang/Object;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Z)Lcom/purride/pixelui/Widget;",
            "BottomSheet-nipCVRc\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/util/List;IILjava/lang/Object;Ljava/lang/String;Lkotlin/jvm/functions/Function0;ZILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ConfirmDialog-XOXv8iY" to "(Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Ljava/lang/String;Ljava/lang/String;IILcom/purride/pixelui/PixelTextStyle;Lcom/purride/pixelui/PixelTextStyle;Lcom/purride/pixelui/PixelButtonStyle;Lcom/purride/pixelui/PixelTextButtonStyle;Ljava/lang/Integer;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ConfirmDialog-XOXv8iY\$default" to "(Ljava/lang/String;Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Ljava/lang/String;Ljava/lang/String;IILcom/purride/pixelui/PixelTextStyle;Lcom/purride/pixelui/PixelTextStyle;Lcom/purride/pixelui/PixelButtonStyle;Lcom/purride/pixelui/PixelTextButtonStyle;Ljava/lang/Integer;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ModalBarrier-SEeGgR8" to "(IZLkotlin/jvm/functions/Function0;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ModalBarrier-SEeGgR8\$default" to "(IZLkotlin/jvm/functions/Function0;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Toast-K2hdtbI" to "(Ljava/lang/String;ILcom/purride/pixelui/PixelTextStyle;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Toast-K2hdtbI\$default" to "(Ljava/lang/String;ILcom/purride/pixelui/PixelTextStyle;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Snackbar-7fJbCWk" to "(Ljava/lang/String;Lcom/purride/pixelui/Widget;ILcom/purride/pixelui/PixelTextStyle;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Snackbar-7fJbCWk\$default" to "(Ljava/lang/String;Lcom/purride/pixelui/Widget;ILcom/purride/pixelui/PixelTextStyle;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Tabs" to "(Ljava/util/List;ILkotlin/jvm/functions/Function1;Ljava/lang/Object;Z)Lcom/purride/pixelui/Widget;",
            "Tabs\$default" to "(Ljava/util/List;ILkotlin/jvm/functions/Function1;Ljava/lang/Object;ZILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "SegmentedControl" to "(Ljava/util/List;ILkotlin/jvm/functions/Function1;Ljava/lang/Object;Z)Lcom/purride/pixelui/Widget;",
            "SegmentedControl\$default" to "(Ljava/util/List;ILkotlin/jvm/functions/Function1;Ljava/lang/Object;ZILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ValueAdjuster" to "(Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Ljava/lang/String;ZILcom/purride/pixelui/ValueAdjusterStyle;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ValueAdjuster\$default" to "(Ljava/lang/String;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;Ljava/lang/String;ZILcom/purride/pixelui/ValueAdjusterStyle;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Stepper" to "(ILkotlin/ranges/IntRange;Lkotlin/jvm/functions/Function1;ILjava/lang/String;Ljava/lang/String;ZILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Stepper\$default" to "(ILkotlin/ranges/IntRange;Lkotlin/jvm/functions/Function1;ILjava/lang/String;Ljava/lang/String;ZILjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ProgressBar-WmmbFQo" to "(FIIIILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ProgressBar-WmmbFQo\$default" to "(FIIIILjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "PixelLoadingBar-nipCVRc" to "(FIIIIIIZLjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "PixelLoadingBar-nipCVRc\$default" to "(FIIIIIIZLjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "AnimatedPixelLoadingBar-SnMDlrA" to "(Lcom/purride/pixelui/animation/PixelTickerProvider;IIIIIIIIZLjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "AnimatedPixelLoadingBar-SnMDlrA\$default" to "(Lcom/purride/pixelui/animation/PixelTickerProvider;IIIIIIIIZLjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ActivityIndicator-ShX4CGY" to "(IILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "ActivityIndicator-ShX4CGY\$default" to "(IILjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "LoadStateView" to "(Lcom/purride/pixelui/PixelAsyncSnapshot;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lkotlin/jvm/functions/Function1;)Lcom/purride/pixelui/Widget;",
            "LoadStateView\$default" to "(Lcom/purride/pixelui/PixelAsyncSnapshot;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lkotlin/jvm/functions/Function1;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "EmptyState" to "(Ljava/lang/String;Ljava/lang/String;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/lang/Integer;Lcom/purride/pixelui/PixelTextStyle;Lcom/purride/pixelui/PixelTextStyle;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "EmptyState\$default" to "(Ljava/lang/String;Ljava/lang/String;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/lang/Integer;Lcom/purride/pixelui/PixelTextStyle;Lcom/purride/pixelui/PixelTextStyle;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Badge" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Badge\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Divider-eccRPRw" to "(IILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "Divider-eccRPRw\$default" to "(IILjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "AppScaffold" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;",
            "AppScaffold\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
        )
    }
}
