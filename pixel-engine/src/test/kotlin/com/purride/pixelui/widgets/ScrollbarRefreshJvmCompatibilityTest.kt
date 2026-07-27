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

/** 锁定 scrollbar/refresh 简洁入口与稳定状态化入口名称并存的二进制表面。 */
class ScrollbarRefreshJvmCompatibilityTest {
    /** 三个家族都同时暴露简洁入口与状态化入口，且两者 JVM 描述符互不相同。 */
    @Test
    fun conciseAndStateAwareOverloadsExposeDistinctJvmEntryPoints() {
        REQUIRED_STATE_METHODS.forEach { (facadeName, stableName) ->
            /** 该家族的简洁入口基础名。 */
            val family = stableName.removeSuffix("WithControlStates")
            /** 同时包含两个入口的运行时顶层 facade。 Runtime top-level facade containing both entry points. */
            val methods = Class.forName(facadeName).declaredMethods
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
            val stateAware = methods.single { method -> method.name == stableName }
            assertTrue(
                "$family concise and state-aware descriptors must differ",
                concise.jvmDescriptor() != stateAware.jvmDescriptor(),
            )
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

    /** Kotlin 源码同时解析全部简洁调用与必填状态重载。 Kotlin source resolves every concise call and every required-state overload together. */
    @Test
    fun conciseAndStateAwareCallsCompileTogether() {
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
        /** 六个构造证明简洁与状态化源码签名可以共存。 Six constructions proving concise and state-aware source signatures coexist. */
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

    }
}
