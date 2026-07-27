package com.purride.pixelui

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.time.Duration.Companion.milliseconds

/** 锁定唯一主题模型的继承解析行为与 token 数据类的 JVM 表面。 */
class PixelThemeResolutionTest {
    /** 唯一的 token 构造入口既提供完整 token 图，也为缺省 Motion 提供回落。 */
    @Test
    fun tokenProviderSuppliesTokensAndMotionFallback() {
        /** Motion value that identifies the complete theme fallback path. */
        val themedMotion = PixelMotionThemeData.Default.copy(
            feedback = PixelMotionSpec(
                duration = 37.milliseconds,
                role = PixelMotionRole.Feedback,
            ),
        )
        /** Complete token graph inherited by the probe. */
        val expectedTokens = PixelThemeTokens.Light.copy(motion = themedMotion)
        /** Mutable capture populated during retained build. */
        val capture = TokenThemeCapture()
        /** Retained test harness. */
        val tester = PixelTester()

        tester.pumpWidget(
            PixelTheme(
                tokens = expectedTokens,
                child = TokenThemeProbe(capture),
            ),
            logicalWidth = 2,
            logicalHeight = 2,
        )

        assertSame(expectedTokens, capture.tokens)
        assertSame(expectedTokens, capture.maybeTokens)
        assertSame(themedMotion, capture.motion)
        assertSame(themedMotion, capture.maybeMotion)
        tester.dispose()
    }

    /** An explicit PixelMotionTheme wins over motion stored in the enclosing complete theme. */
    @Test
    fun explicitMotionProviderOverridesCompleteThemeMotion() {
        /** Motion stored in the enclosing complete theme. */
        val themedMotion = PixelMotionThemeData.Default.copy(
            feedback = PixelMotionSpec(17.milliseconds, role = PixelMotionRole.Feedback),
        )
        /** Nearest explicit motion provider expected to win. */
        val explicitMotion = PixelMotionThemeData.Default.copy(
            feedback = PixelMotionSpec(91.milliseconds, role = PixelMotionRole.Feedback),
        )
        /** Mutable capture populated during retained build. */
        val capture = TokenThemeCapture()
        /** Retained test harness. */
        val tester = PixelTester()

        tester.pumpWidget(
            PixelTheme(
                tokens = PixelThemeTokens.Dark.copy(motion = themedMotion),
                child = PixelMotionTheme(
                    data = explicitMotion,
                    child = TokenThemeProbe(capture),
                ),
            ),
            logicalWidth = 2,
            logicalHeight = 2,
        )

        assertSame(explicitMotion, capture.motion)
        assertSame(explicitMotion, capture.maybeMotion)
        tester.dispose()
    }

    /** 缺少提供者时 `of` 回落到 Default，`maybeOf` 仍然报告 null。 */
    @Test
    fun missingThemeUsesTokenDefaults() {
        /** Mutable capture populated during retained build. */
        val capture = TokenThemeCapture()
        /** Retained test harness. */
        val tester = PixelTester()

        tester.pumpWidget(TokenThemeProbe(capture), logicalWidth = 2, logicalHeight = 2)

        assertSame(PixelThemeTokens.Default, capture.tokens)
        assertNull(capture.maybeTokens)
        assertSame(PixelMotionThemeData.Default, capture.motion)
        assertNull(capture.maybeMotion)
        tester.dispose()
    }

    /** PixelTheme 只暴露一个 token 构造入口，不再保留任何旧主题模型入口。 */
    @Test
    fun pixelThemeExposesOnlyTokenConstructorAndAccessor() {
        /** 默认实参构造桥接使用的 Kotlin marker 类。 Kotlin marker class used by the default-argument constructor bridge. */
        val markerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        /** 唯一的公开构造函数，参数为完整 token 图。 */
        val tokenConstructor = PixelTheme::class.java.getConstructor(
            PixelThemeTokens::class.java,
            Widget::class.java,
            Any::class.java,
        )
        /** 默认 key 的构造桥接，供编译期使用默认实参的 Kotlin 调用点。 */
        val tokenDefaultConstructor = PixelTheme::class.java.getConstructor(
            PixelThemeTokens::class.java,
            Widget::class.java,
            Any::class.java,
            Int::class.javaPrimitiveType!!,
            markerClass,
        )
        /** 首参不是 token 图的公开构造函数，用于证明没有第二种主题模型入口。 */
        val nonTokenConstructors = PixelTheme::class.java.constructors.filterNot { constructor ->
            constructor.parameterTypes.firstOrNull() == PixelThemeTokens::class.java
        }

        assertTrue(Modifier.isPublic(tokenConstructor.modifiers))
        assertTrue(Modifier.isPublic(tokenDefaultConstructor.modifiers))
        assertEquals(emptyList<Any>(), nonTokenConstructors)
        assertNotNull(PixelTheme::class.java.getDeclaredMethod("getTokens"))
        assertTrue(
            PixelTheme::class.java.declaredMethods.none { method -> method.name == "getData" },
        )
    }

    /** PixelThemeTokens 保持十二字段数据类的构造、component 与 copy 表面。 */
    @Test
    fun pixelThemeTokensExposesCompleteDataClassJvmSurface() {
        /** 默认实参构造桥接使用的 Kotlin marker 类。 Kotlin marker class used by the default-argument constructor bridge. */
        val markerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        /** token 图的有序构造参数类型。 */
        val tokenParameterTypes = listOf(
            PixelThemeBrightness::class.java,
            PixelThemeContrast::class.java,
            PixelColorScheme::class.java,
            PixelTypographyTokens::class.java,
            PixelSpacingTokens::class.java,
            PixelSizeTokens::class.java,
            PixelRadiusTokens::class.java,
            PixelBorderTokens::class.java,
            PixelElevationTokens::class.java,
            PixelMotionThemeData::class.java,
            PixelComponentTokens::class.java,
            PixelLabelTokens::class.java,
        )
        /** 完整参数的主构造函数。 */
        val primaryConstructor = PixelThemeTokens::class.java.getConstructor(
            *tokenParameterTypes.toTypedArray(),
        )
        /** 全默认无参构造函数。 */
        val noArgConstructor = PixelThemeTokens::class.java.getConstructor()
        /** 位掩码默认实参构造桥接。 */
        val defaultConstructor = PixelThemeTokens::class.java.getConstructor(
            *(tokenParameterTypes + Int::class.javaPrimitiveType!! + markerClass).toTypedArray(),
        )
        /** 完整参数的数据类 copy 方法。 */
        val copyMethod = PixelThemeTokens::class.java.getDeclaredMethod(
            "copy",
            *tokenParameterTypes.toTypedArray(),
        )

        assertTrue(Modifier.isPublic(primaryConstructor.modifiers))
        assertTrue(Modifier.isPublic(noArgConstructor.modifiers))
        assertTrue(Modifier.isPublic(defaultConstructor.modifiers))
        assertTrue(Modifier.isPublic(copyMethod.modifiers))
        (1..tokenParameterTypes.size).forEach { componentIndex ->
            assertNotNull(
                PixelThemeTokens::class.java.getDeclaredMethod("component$componentIndex"),
            )
        }
    }

    /** PixelColorScheme 保持二十二个语义颜色角色的 inline-value 数据类表面。 */
    @Test
    fun pixelColorSchemeExposesTwentyTwoInlineValueRoles() {
        /** 内联颜色主构造函数外围生成的公开构造签名。 */
        val publicSignatures = PixelColorScheme::class.java.constructors.map { constructor ->
            constructor.parameterTypes.map(Class<*>::getName)
        }
        /** 二十二个 unbox 后的 PixelColor 值加上合成 marker。 */
        val primaryBridge = List(22) { "int" } + "kotlin.jvm.internal.DefaultConstructorMarker"
        /** 名称被 mangle 的 copy 方法，保留二十二个 unbox 颜色。 */
        val copyMethod = PixelColorScheme::class.java.declaredMethods.single { method ->
            method.name.startsWith("copy-") && !method.name.contains("\$default")
        }
        /** 为每个语义颜色角色生成的 component 方法。 */
        val componentMethods = PixelColorScheme::class.java.declaredMethods.filter { method ->
            method.name.startsWith("component") && !method.name.contains("\$default")
        }

        assertTrue(primaryBridge in publicSignatures)
        assertEquals(22, copyMethod.parameterCount)
        assertEquals(22, componentMethods.size)
    }

    /** PixelLabelTokens 保持二十九个标签的数据类 JVM 表面。 */
    @Test
    fun pixelLabelTokensRetainsDataClassJvmSurface() {
        /** 默认实参构造桥接使用的 Kotlin marker 类。 Kotlin marker class used by the default-argument constructor bridge. */
        val markerClass = Class.forName("kotlin.jvm.internal.DefaultConstructorMarker")
        /** 29 个标签的主构造函数与 copy 的精确有序参数类型。 Exact ordered parameter types of the 29-label primary constructor and copy. */
        val labelParameterTypes = List(29) { String::class.java }
        /** 恰好包含冻结标签集合的公开主构造函数。 Public primary constructor containing exactly the frozen label set. */
        val primaryConstructor = PixelLabelTokens::class.java.getConstructor(
            *labelParameterTypes.toTypedArray(),
        )
        /** 全默认无参构造函数。 All-default no-argument constructor. */
        val noArgConstructor = PixelLabelTokens::class.java.getConstructor()
        /** 编译期 Kotlin 调用点使用的单掩码默认实参构造函数。 One-mask default-argument constructor used by compiled Kotlin call sites. */
        val defaultConstructor = PixelLabelTokens::class.java.getConstructor(
            *(labelParameterTypes + Int::class.javaPrimitiveType!! + markerClass).toTypedArray(),
        )
        /** 完整参数的数据类 copy 方法。 Complete data-class copy method. */
        val copyMethod = PixelLabelTokens::class.java.getDeclaredMethod(
            "copy",
            *labelParameterTypes.toTypedArray(),
        )
        /** Static default-copy bridge used by precompiled Kotlin named/default calls. */
        val defaultCopyMethod = PixelLabelTokens::class.java.getDeclaredMethod(
            "copy\$default",
            *(
                listOf(PixelLabelTokens::class.java) +
                    labelParameterTypes +
                    listOf(Int::class.javaPrimitiveType!!, Any::class.java)
                ).toTypedArray(),
        )

        assertTrue(Modifier.isPublic(primaryConstructor.modifiers))
        assertTrue(Modifier.isPublic(noArgConstructor.modifiers))
        assertTrue(Modifier.isPublic(defaultConstructor.modifiers))
        assertTrue(Modifier.isPublic(copyMethod.modifiers))
        assertTrue(Modifier.isPublic(defaultCopyMethod.modifiers))
        assertTrue(Modifier.isStatic(defaultCopyMethod.modifiers))
        (1..29).forEach { componentIndex ->
            assertNotNull(PixelLabelTokens::class.java.getDeclaredMethod("component$componentIndex"))
        }
    }
}

/** Mutable assertion sink populated by [TokenThemeProbe]. */
private class TokenThemeCapture {
    /** Complete tokens resolved by the probe. */
    var tokens: PixelThemeTokens? = null

    /** 探针解析出的可空继承 token 图。 Nullable inherited tokens resolved by the probe. */
    var maybeTokens: PixelThemeTokens? = null

    /** Effective motion tokens resolved by the probe. */
    var motion: PixelMotionThemeData? = null

    /** Nullable effective motion tokens resolved by the probe. */
    var maybeMotion: PixelMotionThemeData? = null
}

/** 读取全部继承主题访问器的无状态 retained 探针。 Stateless retained probe that reads every inherited theme accessor. */
private class TokenThemeProbe(
    /** Sink receiving values read during build. */
    private val capture: TokenThemeCapture,
) : StatelessWidget() {
    /** Captures inherited theme values and returns a fixed paintable leaf. */
    override fun build(context: BuildContext): Widget {
        capture.tokens = PixelTheme.of(context)
        capture.maybeTokens = PixelTheme.maybeOf(context)
        capture.motion = PixelMotionTheme.of(context)
        capture.maybeMotion = PixelMotionTheme.maybeOf(context)
        return Container(width = 1, height = 1, fillColor = PixelColor.White, borderColor = null)
    }
}
