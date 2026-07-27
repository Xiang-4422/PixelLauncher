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

/** 锁定 PixelComponents 简洁入口与显式状态化 JVM 名称并存的二进制表面。 */
class PixelComponentsJvmCompatibilityTest {
    /** 每个组件家族都同时暴露简洁入口与状态化入口，且两者 JVM 描述符互不相同。 */
    @Test
    fun conciseAndStateAwareOverloadsExposeDistinctJvmEntryPoints() {
        /** Runtime facade class generated for the top-level PixelComponents declarations. */
        val methods = Class.forName(PIXEL_COMPONENTS_FACADE).declaredMethods
        CONCISE_FAMILIES.forEach { family ->
            /** 简洁入口方法；含 inline value class 参数时名称会被 mangle。 */
            val conciseCandidates = methods.filter { method ->
                (method.name == family || method.name.startsWith("$family-")) &&
                    !method.name.endsWith("\$default")
            }
            assertEquals("Expected one concise entry point for $family", 1, conciseCandidates.size)
            /** 唯一的简洁入口方法。 */
            val concise = conciseCandidates.single()
            assertEquals(Widget::class.java, concise.returnType)
            assertTrue(
                "Missing default bridge for ${concise.name}",
                methods.any { method -> method.name == "${concise.name}\$default" },
            )
            /** 对应的状态化入口方法。 */
            val stateAware = methods.single { method ->
                method.name == "${family}WithControlStates"
            }
            assertTrue(
                "$family concise and state-aware descriptors must differ",
                concise.jvmDescriptor() != stateAware.jvmDescriptor(),
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

    /** 同一模块的 Kotlin 源码能同时解析简洁调用与全部状态化重载。 Kotlin source can resolve both concise calls and all state-aware overloads in one module. */
    @Test
    fun conciseAndStateAwareKotlinCallsCompileTogether() {
        /** Tester supplies the ticker provider required by the animated loading-bar source calls. */
        val tester = PixelTester()
        try {
            /** Canonical state argument that selects every required state-aware overload. */
            val states = PixelControlStateSet.Normal
            /** 覆盖全部公开组件家族的源码级构造。 Source-level constructions spanning every public component family. */
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
                    snapshot = PixelAsyncSnapshot.Success("concise"),
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

        /** 为每个状态化重载分配的稳定可读名称。 Stable readable names assigned to every state-aware overload. */
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

        /** 每个同时提供简洁入口与状态化入口的组件家族基础名。 */
        val CONCISE_FAMILIES: Set<String> = REQUIRED_STATE_JVM_NAMES.mapTo(linkedSetOf()) { name ->
            name.removeSuffix("WithControlStates")
        }
    }
}
