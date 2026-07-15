package com.purride.pixelui.widgets

import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SizedBox
import com.purride.pixelui.SwipeRefreshScaffold
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import java.lang.reflect.Method
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks legacy scrollbar/refresh JVM descriptors beside stable state-aware entry names. */
class ScrollbarRefreshJvmCompatibilityTest {
    /** All three legacy methods and Kotlin default bridges retain their exact descriptors. */
    @Test
    fun legacyDescriptorsAndDefaultBridgesRemainExact() {
        LEGACY_DESCRIPTORS.forEach { (facadeName, expectedMethods) ->
            /** Runtime top-level facade selected by the expected descriptor group. */
            val facade = Class.forName(facadeName)
            /** Exact descriptors indexed by generated JVM method name. */
            val actual = facade.declaredMethods.associate { method -> method.name to method.jvmDescriptor() }
            expectedMethods.forEach { (methodName, expectedDescriptor) ->
                assertEquals(
                    "Legacy descriptor changed for $facadeName#$methodName",
                    expectedDescriptor,
                    actual[methodName],
                )
            }
        }
    }

    /** Required-state overloads expose readable stable names and generated default bridges. */
    @Test
    fun stateAwareOverloadsExposeStableJvmNames() {
        REQUIRED_STATE_METHODS.forEach { (facadeName, stableName) ->
            /** All methods from the relevant generated top-level facade. */
            val methods = Class.forName(facadeName).declaredMethods
            /** Generated JVM names including Kotlin default bridges. */
            val names = methods.mapTo(linkedSetOf()) { method -> method.name }
            assertTrue("Missing state overload $stableName", stableName in names)
            assertTrue("Missing default bridge for $stableName", "${stableName}\$default" in names)
            assertTrue(
                "$stableName was unexpectedly value-class mangled",
                names.none { name -> name.startsWith("$stableName-") },
            )
            /** Non-default method whose unboxed state mask and return type are inspected. */
            val method = methods.single { candidate -> candidate.name == stableName }
            assertTrue(method.parameterTypes.any { type -> type == Int::class.javaPrimitiveType })
            assertEquals(Widget::class.java, method.returnType)
        }
    }

    /** Kotlin source resolves every old call and every required-state overload together. */
    @Test
    fun legacyAndStateAwareCallsCompileTogether() {
        /** Shared list controller used only to construct both scrollbar call forms. */
        val listController = PixelListController()
        /** Shared list state used by the source-level overload check. */
        val listState = listController.create()
        /** Shared refresh controller used by RefreshIndicator and scaffold calls. */
        val refreshController = PixelRefreshIndicatorController()
        /** Shared refresh state used by the source-level overload check. */
        val refreshState = refreshController.create()
        /** Required state argument selecting each new overload. */
        val states = PixelControlStateSet.Normal
        /** Passive child sufficient for source overload resolution. */
        val child = SizedBox(width = 8, height = 8)
        /** Six constructions proving legacy and new source signatures coexist. */
        val widgets = listOf(
            Scrollbar(child = child, state = listState),
            Scrollbar(child = child, state = listState, states = states),
            RefreshIndicator(
                child = child,
                state = refreshState,
                controller = refreshController,
                onRefresh = {},
            ),
            RefreshIndicator(
                child = child,
                state = refreshState,
                controller = refreshController,
                states = states,
                onRefresh = {},
            ),
            SwipeRefreshScaffold(
                body = child,
                state = refreshState,
                controller = refreshController,
                onRefresh = {},
            ),
            SwipeRefreshScaffold(
                body = child,
                state = refreshState,
                controller = refreshController,
                states = states,
                onRefresh = {},
            ),
        )
        assertEquals(6, widgets.size)
    }

    /** Builds an exact JVM descriptor without Kotlin reflection metadata. */
    private fun Method.jvmDescriptor(): String {
        /** Ordered encoded parameter descriptors. */
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
        /** Generated facade containing Scrollbar and RefreshIndicator. */
        const val PIXEL_WIDGETS_FACADE: String = "com.purride.pixelui.PixelWidgetsKt"

        /** Generated facade containing SwipeRefreshScaffold. */
        const val SWIPE_REFRESH_FACADE: String = "com.purride.pixelui.SwipeRefreshScaffoldKt"

        /** Stable required-state names paired with their generated facades. */
        val REQUIRED_STATE_METHODS: List<Pair<String, String>> = listOf(
            PIXEL_WIDGETS_FACADE to "ScrollbarWithControlStates",
            PIXEL_WIDGETS_FACADE to "RefreshIndicatorWithControlStates",
            SWIPE_REFRESH_FACADE to "SwipeRefreshScaffoldWithControlStates",
        )

        /** Exact pre-state descriptors grouped by generated top-level facade. */
        val LEGACY_DESCRIPTORS: Map<String, Map<String, String>> = linkedMapOf(
            PIXEL_WIDGETS_FACADE to linkedMapOf(
                "Scrollbar-AD1dkFU" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/state/PixelListState;ILcom/purride/pixelcore/PixelColor;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
                "Scrollbar-AD1dkFU\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/state/PixelListState;ILcom/purride/pixelcore/PixelColor;ILjava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
                "RefreshIndicator-swMSA7U" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/state/PixelRefreshIndicatorState;Lcom/purride/pixelui/state/PixelRefreshIndicatorController;Lkotlin/jvm/functions/Function0;IZIIILjava/lang/Object;Ljava/lang/String;)Lcom/purride/pixelui/Widget;",
                "RefreshIndicator-swMSA7U\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/state/PixelRefreshIndicatorState;Lcom/purride/pixelui/state/PixelRefreshIndicatorController;Lkotlin/jvm/functions/Function0;IZIIILjava/lang/Object;Ljava/lang/String;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            ),
            SWIPE_REFRESH_FACADE to linkedMapOf(
                "SwipeRefreshScaffold-1vmSQpk" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/state/PixelRefreshIndicatorState;Lcom/purride/pixelui/state/PixelRefreshIndicatorController;Lkotlin/jvm/functions/Function0;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;IZIIILjava/lang/Object;Ljava/lang/String;)Lcom/purride/pixelui/Widget;",
                "SwipeRefreshScaffold-1vmSQpk\$default" to "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/state/PixelRefreshIndicatorState;Lcom/purride/pixelui/state/PixelRefreshIndicatorController;Lkotlin/jvm/functions/Function0;Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/Widget;IZIIILjava/lang/Object;Ljava/lang/String;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;",
            ),
        )
    }
}
