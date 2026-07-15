package com.purride.pixelui.foundation

import com.purride.pixelui.Focus
import com.purride.pixelui.FocusNode
import com.purride.pixelui.PixelKey
import com.purride.pixelui.PixelKeyEvent
import com.purride.pixelui.PixelTextInputEvent
import com.purride.pixelui.Text
import com.purride.pixelui.Widget
import com.purride.pixelui.testing.PixelTester
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies exact-text focus dispatch, legacy fallback, runtime isolation, and frozen key-event ABI. */
class PixelTextInputDispatchTest {
    /** The public event retains exact UTF-16 content and ordinary immutable data-class semantics. */
    @Test
    fun publicTextInputEventPreservesExactPayload() {
        /** Event containing one supplementary scalar plus a combining sequence without normalization. */
        val event = PixelTextInputEvent("\uD83D\uDE00e\u0301")
        /** Independent copy proving the public copy contract retains every original code unit. */
        val copied = event.copy()

        assertEquals("\uD83D\uDE00e\u0301", event.text)
        assertEquals(event, copied)
    }

    /** Focused text handlers bubble toward ancestors and stop immediately after one consumes input. */
    @Test
    fun textInputBubblesFromFocusedNodeToParentUntilConsumed() {
        /** Ordered trace proving both bubbling direction and exact payload preservation. */
        val trace = mutableListOf<String>()
        /** Parent focus node that consumes the payload after its child declines it. */
        val parentNode = FocusNode("parent")
        /** Initially focused child node where text dispatch begins. */
        val childNode = FocusNode("child")
        /** Off-screen runtime used to exercise the public declarative Focus overload. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = parentNode,
                    child = Focus(
                        node = childNode,
                        autofocus = true,
                        child = Text("CHILD"),
                        onTextInput = { event ->
                            trace += "child:${event.text}"
                            false
                        },
                    ),
                    onTextInput = { event ->
                        trace += "parent:${event.text}"
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressText("\uD83D\uDE00"))
            assertEquals(listOf("child:\uD83D\uDE00", "parent:\uD83D\uDE00"), trace)
        } finally {
            tester.dispose()
        }
    }

    /** The String handler runs first and an unconsumed BMP scalar reaches the legacy Char handler. */
    @Test
    fun unconsumedSingleBmpTextFallsBackToCharacterKeyInOrder() {
        /** Ordered trace distinguishing the additive String phase from the compatibility key phase. */
        val trace = mutableListOf<String>()
        /** Focus node configured with both the new and legacy handlers. */
        val node = FocusNode("fallback")
        /** Off-screen runtime driving exact text through the public tester API. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("FALLBACK"),
                    onTextInput = { event ->
                        trace += "text:${event.text}"
                        false
                    },
                    onKeyEvent = { event ->
                        trace += "key:${event.character}"
                        event.key == PixelKey.CHARACTER
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressText("A"))
            assertEquals(listOf("text:A", "key:A"), trace)
        } finally {
            tester.dispose()
        }
    }

    /** A consumed String payload never duplicates delivery through the old CHARACTER event. */
    @Test
    fun consumedTextSuppressesLegacyCharacterFallback() {
        /** Number of legacy character callbacks that must remain zero. */
        var legacyDispatchCount = 0
        /** Focus node whose exact-text handler consumes the BMP payload. */
        val node = FocusNode("consumed")
        /** Off-screen runtime driving the input event. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("CONSUMED"),
                    onTextInput = { true },
                    onKeyEvent = { event ->
                        if (event.key == PixelKey.CHARACTER) legacyDispatchCount += 1
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressText("A"))
            assertEquals(0, legacyDispatchCount)
        } finally {
            tester.dispose()
        }
    }

    /** Supplementary and multi-code-point text stays exact and never becomes surrogate key events. */
    @Test
    fun supplementaryAndMultiCodePointTextNeverNarrowsToCharFallback() {
        /** Exact String payloads observed by the additive handler. */
        val textPayloads = mutableListOf<String>()
        /** Legacy characters that must stay empty for non-representable payloads. */
        val legacyCharacters = mutableListOf<Char?>()
        /** Focus node declining exact text so the fallback eligibility rule is exercised. */
        val node = FocusNode("unicode")
        /** Off-screen runtime dispatching supplementary and combining sequences. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("UNICODE"),
                    onTextInput = { event ->
                        textPayloads += event.text
                        false
                    },
                    onKeyEvent = { event ->
                        legacyCharacters += event.character
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertFalse(tester.pressText("\uD83D\uDE00"))
            assertFalse(tester.pressText("e\u0301"))
            assertEquals(listOf("\uD83D\uDE00", "e\u0301"), textPayloads)
            assertTrue(legacyCharacters.isEmpty())
        } finally {
            tester.dispose()
        }
    }

    /** Simultaneous tester runtimes deliver exact text only to their own primary focus chain. */
    @Test
    fun simultaneousRuntimesKeepTextInputIsolated() {
        /** Payloads observed by the first runtime. */
        val firstPayloads = mutableListOf<String>()
        /** Payloads observed by the second runtime. */
        val secondPayloads = mutableListOf<String>()
        /** First independently retained test runtime. */
        val firstTester = PixelTester()
        /** Second independently retained test runtime. */
        val secondTester = PixelTester()
        try {
            firstTester.pumpWidget(textFocus("first", firstPayloads), 24, 12)
            secondTester.pumpWidget(textFocus("second", secondPayloads), 24, 12)

            assertTrue(firstTester.pressText("\uD83D\uDE00"))
            assertEquals(listOf("\uD83D\uDE00"), firstPayloads)
            assertTrue(secondPayloads.isEmpty())

            assertTrue(secondTester.pressText("e\u0301"))
            assertEquals(listOf("\uD83D\uDE00"), firstPayloads)
            assertEquals(listOf("e\u0301"), secondPayloads)
        } finally {
            firstTester.dispose()
            secondTester.dispose()
        }
    }

    /** The legacy pressKey Char behavior remains available independently of exact-text dispatch. */
    @Test
    fun legacyPressKeyCharacterBehaviorRemainsUnchanged() {
        /** Legacy event captured by the focused node. */
        var captured: PixelKeyEvent? = null
        /** Focus node retaining the pre-existing key handler API. */
        val node = FocusNode("legacy")
        /** Off-screen runtime driving the original tester method. */
        val tester = PixelTester()
        try {
            tester.pumpWidget(
                widget = Focus(
                    node = node,
                    autofocus = true,
                    child = Text("LEGACY"),
                    onKeyEvent = { event ->
                        captured = event
                        true
                    },
                ),
                logicalWidth = 32,
                logicalHeight = 12,
            )

            assertTrue(tester.pressKey(PixelKey.CHARACTER, 'Z'))
            assertEquals(PixelKeyEvent(PixelKey.CHARACTER, 'Z'), captured)
        } finally {
            tester.dispose()
        }
    }

    /** PixelKeyEvent keeps its two-field data-class constructor, copy, component, and default ABI. */
    @Test
    fun pixelKeyEventRetainsFrozenJvmDescriptors() {
        /** Runtime class whose externally linked descriptors must remain unchanged. */
        val eventClass = PixelKeyEvent::class.java
        /** Exact constructor descriptors emitted for the original two-field primary constructor. */
        val constructorDescriptors = eventClass.declaredConstructors.map(::constructorDescriptor).toSet()
        /** Exact descriptors for data-class methods referenced by old Kotlin and Java consumers. */
        val methodDescriptors = eventClass.declaredMethods
            .filter { method -> method.name in FROZEN_PIXEL_KEY_EVENT_METHOD_NAMES }
            .associate { method -> method.name to methodDescriptor(method) }
        /** Instance fields proving no third component was inserted into the frozen data class. */
        val instanceFieldNames = eventClass.declaredFields
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .map { field -> field.name }
            .toSet()

        assertEquals(FROZEN_PIXEL_KEY_EVENT_CONSTRUCTORS, constructorDescriptors)
        assertEquals(FROZEN_PIXEL_KEY_EVENT_METHODS, methodDescriptors)
        assertEquals(setOf("key", "character"), instanceFieldNames)
    }

    /** The original Focus facade and Kotlin default bridge remain alongside the additive overload. */
    @Test
    fun focusFacadeRetainsLegacyJvmDescriptors() {
        /** Generated top-level facade containing both legacy and additive Focus overloads. */
        val facade = Class.forName("com.purride.pixelui.PixelFocusKt")
        /** Every emitted Focus descriptor, including Kotlin default bridges. */
        val descriptors = facade.declaredMethods
            .filter { method -> method.name == "Focus" || method.name == "Focus\$default" }
            .map { method -> method.name to methodDescriptor(method) }
            .toSet()

        assertTrue(descriptors.contains(LEGACY_FOCUS_DESCRIPTOR))
        assertTrue(descriptors.contains(LEGACY_FOCUS_DEFAULT_DESCRIPTOR))
        assertTrue(descriptors.any { (name, descriptor) ->
            name == "Focus" && descriptor.endsWith("Lkotlin/jvm/functions/Function1;)Lcom/purride/pixelui/Widget;")
        })
    }

    /** Builds one independently focused widget whose String handler records exact payloads. */
    private fun textFocus(label: String, payloads: MutableList<String>): Widget {
        /** Stable node owned only by the tester rendering this widget. */
        val node = FocusNode(label)
        return Focus(
            node = node,
            autofocus = true,
            child = Text(label),
            onTextInput = { event ->
                payloads += event.text
                true
            },
        )
    }

    /** Encodes one constructor using JVM field-descriptor syntax. */
    private fun constructorDescriptor(constructor: Constructor<*>): String {
        /** Ordered encoded parameter types accepted by this constructor. */
        val parameters = constructor.parameterTypes.joinToString(separator = "") { type -> typeDescriptor(type) }
        return "($parameters)V"
    }

    /** Encodes one reflected method using JVM field-descriptor syntax. */
    private fun methodDescriptor(method: Method): String {
        /** Ordered encoded parameter types accepted by this method. */
        val parameters = method.parameterTypes.joinToString(separator = "") { type -> typeDescriptor(type) }
        return "($parameters)${typeDescriptor(method.returnType)}"
    }

    /** Encodes one Java reflection type as a JVM primitive, array, or reference descriptor. */
    private fun typeDescriptor(type: Class<*>): String {
        return when {
            type.isArray -> type.name.replace('.', '/')
            !type.isPrimitive -> "L${type.name.replace('.', '/')};"
            type == Boolean::class.javaPrimitiveType -> "Z"
            type == Byte::class.javaPrimitiveType -> "B"
            type == Char::class.javaPrimitiveType -> "C"
            type == Short::class.javaPrimitiveType -> "S"
            type == Int::class.javaPrimitiveType -> "I"
            type == Long::class.javaPrimitiveType -> "J"
            type == Float::class.javaPrimitiveType -> "F"
            type == Double::class.javaPrimitiveType -> "D"
            type == Void.TYPE -> "V"
            else -> error("Unsupported primitive descriptor for ${type.name}")
        }
    }

    /** Frozen data-class method names whose exact descriptors old binaries invoke. */
    private companion object {
        /** Constructor descriptors for the primary constructor and Kotlin default-argument bridge. */
        val FROZEN_PIXEL_KEY_EVENT_CONSTRUCTORS: Set<String> = setOf(
            "(Lcom/purride/pixelui/PixelKey;Ljava/lang/Character;)V",
            "(Lcom/purride/pixelui/PixelKey;Ljava/lang/Character;ILkotlin/jvm/internal/DefaultConstructorMarker;)V",
        )

        /** Data-class method names that must retain their original arity and JVM types. */
        val FROZEN_PIXEL_KEY_EVENT_METHOD_NAMES: Set<String> = setOf(
            "component1",
            "component2",
            "copy",
            "copy\$default",
        )

        /** Exact descriptors of the original two components and copy methods. */
        val FROZEN_PIXEL_KEY_EVENT_METHODS: Map<String, String> = mapOf(
            "component1" to "()Lcom/purride/pixelui/PixelKey;",
            "component2" to "()Ljava/lang/Character;",
            "copy" to "(Lcom/purride/pixelui/PixelKey;Ljava/lang/Character;)Lcom/purride/pixelui/PixelKeyEvent;",
            "copy\$default" to "(Lcom/purride/pixelui/PixelKeyEvent;Lcom/purride/pixelui/PixelKey;Ljava/lang/Character;ILjava/lang/Object;)Lcom/purride/pixelui/PixelKeyEvent;",
        )

        /** Original public Focus method descriptor preserved for previously compiled callers. */
        val LEGACY_FOCUS_DESCRIPTOR: Pair<String, String> = "Focus" to
            "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/FocusNode;ZZLkotlin/jvm/functions/Function1;Lcom/purride/pixelui/PixelFocusScrollTarget;Ljava/lang/Object;)Lcom/purride/pixelui/Widget;"

        /** Original Kotlin default bridge descriptor preserved for previously compiled callers. */
        val LEGACY_FOCUS_DEFAULT_DESCRIPTOR: Pair<String, String> = "Focus\$default" to
            "(Lcom/purride/pixelui/Widget;Lcom/purride/pixelui/FocusNode;ZZLkotlin/jvm/functions/Function1;Lcom/purride/pixelui/PixelFocusScrollTarget;Ljava/lang/Object;ILjava/lang/Object;)Lcom/purride/pixelui/Widget;"
    }
}
