package com.purride.pixelui

import java.nio.charset.StandardCharsets
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** M8-1 路由快照与导航状态机属性测试，所有失败都能由固定种子复现。 */
class M81NavigationPropertyFuzzTest {
    /** 随机 typed stack 必须完整往返，任意单字节损坏不得替换安全根。 */
    @Test
    fun typedSnapshotsRoundTripAndCorruptionIsAtomic() {
        /** 本测试固定使用的随机种子。 */
        val seed = 2026071405L
        /** 只由固定种子驱动的伪随机源。 */
        val random = Random(seed)

        repeat(250) { iteration ->
            /** 当前轮源进程使用的 destination。 */
            val sourceDestination = destination("property-route")
            /** 当前轮源进程使用的显式恢复注册表。 */
            val sourceRegistry = PixelRouteSnapshotRegistry(
                listOf(PropertySnapshotAdapter(sourceDestination)),
            )
            /** 当前轮随机栈深度。 */
            val entryCount = 1 + random.nextInt(20)
            /** 当前轮期望的 typed 参数和 route-local counter。 */
            val expected = MutableList(entryCount) { index ->
                ExpectedEntry(
                    arguments = PropertyArguments("entry-$iteration-$index", random.nextInt()),
                    counter = random.nextInt(),
                )
            }
            /** 以第一个期望条目为根创建的源 Navigator。 */
            val source = PixelNavigatorState(
                PixelRouteRequest(sourceDestination, expected.first().arguments),
            )
            typedEntry(source.currentEntry).stateBucket.write(CounterStateKey, expected.first().counter)
            expected.drop(1).forEach { item ->
                /** 为当前期望条目创建的独立 typed entry。 */
                val entry = source.push(PixelRouteRequest(sourceDestination, item.arguments))
                entry.stateBucket.write(CounterStateKey, item.counter)
            }
            /** 当前源栈的完整、带校验和快照。 */
            val encoded = requireEncoded(source.persistentSnapshot(sourceRegistry))

            /** 模拟新进程重新创建的 destination 实例。 */
            val restoredDestination = destination("property-route")
            /** 模拟新进程重新创建的恢复注册表。 */
            val restoredRegistry = PixelRouteSnapshotRegistry(
                listOf(PropertySnapshotAdapter(restoredDestination)),
            )
            /** 恢复前先挂载一个必须被原子替换的安全根。 */
            val restored = PixelNavigatorState(
                PixelRouteRequest(restoredDestination, PropertyArguments("fallback", -1)),
            )
            /** 对完整快照执行公开恢复操作的结果。 */
            val restoredResult = restored.restorePersistentSnapshot(encoded.bytes, restoredRegistry)
            assertTrue("seed=$seed iteration=$iteration", restoredResult is PixelNavigatorSnapshotDecodeResult.Decoded)
            assertEquals(expected.size, restored.entries.size)
            expected.forEachIndexed { index, item ->
                /** 当前索引恢复出的 typed entry。 */
                val actual = typedEntry(restored.entries[index])
                assertEquals(item.arguments, actual.arguments)
                assertEquals(item.counter, actual.stateBucket.read(CounterStateKey))
            }

            /** 专门验证损坏输入原子性的独立安全 Navigator。 */
            val guarded = PixelNavigatorState(
                PixelRouteRequest(restoredDestination, PropertyArguments("guard", iteration)),
            )
            /** 损坏恢复前必须保持对象身份的安全根。 */
            val guardedRoot = guarded.currentEntry
            /** 从 getter 获得并只改动一字节的快照副本。 */
            val corrupted = encoded.bytes
            /** 当前轮被翻转的确定性字节索引。 */
            val corruptIndex = random.nextInt(corrupted.size)
            corrupted[corruptIndex] = (corrupted[corruptIndex].toInt() xor 0x01).toByte()
            /** 损坏快照的结构化恢复结果。 */
            val corruptResult = guarded.restorePersistentSnapshot(corrupted, restoredRegistry)
            assertTrue(corruptResult is PixelNavigatorSnapshotDecodeResult.Rejected)
            assertSame(guardedRoot, guarded.currentEntry)
            assertEquals(1, guarded.entries.size)

            /** 截断长度始终小于完整长度。 */
            val truncatedLength = random.nextInt(encoded.bytes.size)
            /** 当前轮截断后的不可信快照。 */
            val truncated = encoded.bytes.copyOf(truncatedLength)
            /** 截断快照的结构化恢复结果。 */
            val truncatedResult = guarded.restorePersistentSnapshot(truncated, restoredRegistry)
            assertTrue(truncatedResult is PixelNavigatorSnapshotDecodeResult.Rejected)
            assertSame(guardedRoot, guarded.currentEntry)

            source.disposeNavigator()
            restored.disposeNavigator()
            guarded.disposeNavigator()
        }
    }

    /** 随机 push/pop/remove/replace/clear 必须逐步符合参考模型且每个 entry 只释放一次。 */
    @Test
    fun navigatorStateMachineMatchesReferenceModel() {
        /** 本测试固定使用的随机种子。 */
        val seed = 2026071406L
        /** 只由固定种子驱动的伪随机源。 */
        val random = Random(seed)
        /** 每个实际路由对象对应的一次释放计数。 */
        val disposeCounts = linkedMapOf<String, Int>()
        /** 为路由生成全局唯一名称的递增编号。 */
        var nextRouteNumber = 0
        /** 创建一个带释放探针的唯一类型化路由请求。 */
        fun createRoute(): PixelRouteRequest<Unit, Any?> {
            /** 当前新路由的稳定唯一名称。 */
            val name = "route-${nextRouteNumber++}"
            disposeCounts[name] = 0
            return testRouteRequest(
                name = name,
                builder = { Text(name) },
                transition = PixelRouteTransition.None,
                onDispose = { disposeCounts[name] = checkNotNull(disposeCounts[name]) + 1 },
            )
        }

        /** 参考模型和实际 Navigator 共享的初始根。 */
        val root = createRoute()
        /** 被测 Navigator 状态机。 */
        val navigator = PixelNavigatorState(root)
        /** 只保存期望目标标识的最小参考模型。 */
        val model = mutableListOf(root.destination.id)

        repeat(5_000) { iteration ->
            when (random.nextInt(5)) {
                0 -> {
                    /** 当前 push 创建的新路由。 */
                    val route = createRoute()
                    navigator.push(route)
                    model += route.destination.id
                }
                1 -> {
                    /** 参考模型判断本轮是否允许 pop。 */
                    val expectedResult = model.size > 1
                    /** 实际 pop 返回值。 */
                    val actualResult = navigator.pop()
                    assertEquals("seed=$seed iteration=$iteration", expectedResult, actualResult)
                    if (expectedResult) model.removeAt(model.lastIndex)
                }
                2 -> {
                    /** 当前栈中被随机选择的条目索引。 */
                    val index = random.nextInt(model.size)
                    /** 单条根栈不允许移除，其余位置都允许。 */
                    val expectedResult = model.size > 1
                    /** 实际栈中与参考索引对应的 entry。 */
                    val entry = navigator.entries[index]
                    /** 非动画 remove 的实际返回值。 */
                    val actualResult = navigator.remove(entry, animated = false)
                    assertEquals("seed=$seed iteration=$iteration", expectedResult, actualResult)
                    if (expectedResult) model.removeAt(index)
                }
                3 -> {
                    /** 当前 replace 创建的新路由。 */
                    val route = createRoute()
                    navigator.replace(route, animated = false)
                    model[model.lastIndex] = route.destination.id
                }
                else -> {
                    /** 参考模型判断当前 clear 是否会改变栈。 */
                    val expectedResult = model.size > 1
                    /** 非动画 clear 的实际返回值。 */
                    val actualResult = navigator.clear(animated = false)
                    assertEquals("seed=$seed iteration=$iteration", expectedResult, actualResult)
                    if (expectedResult) {
                        /** clear 必须保留的参考根名称。 */
                        val rootName = model.first()
                        model.clear()
                        model += rootName
                    }
                }
            }
            assertEquals(
                "seed=$seed iteration=$iteration",
                model,
                navigator.entries.map { entry -> entry.destination.id },
            )
            assertEquals(model.size > 1, navigator.canPop)
            assertFalse(navigator.entries.map { entry -> entry.id }.let { ids -> ids.size != ids.toSet().size })
        }

        navigator.disposeNavigator()
        /** 任何创建过的路由对象都必须对应恰好一个已释放 entry。 */
        disposeCounts.forEach { (name, count) ->
            assertEquals("$name 必须恰好释放一次", 1, count)
        }
    }

    /** 创建一个只渲染参数标签的稳定 typed destination。 */
    private fun destination(id: String): PixelRouteDestination<PropertyArguments, Unit> {
        return pixelRouteDestination(id) { _, scope -> Text(scope.arguments.label) }
    }

    /** 将异构 entry 收窄为本测试唯一使用的参数和结果类型。 */
    @Suppress("UNCHECKED_CAST")
    private fun typedEntry(entry: PixelRouteEntry<*, *>): PixelRouteEntry<PropertyArguments, Unit> {
        return entry as PixelRouteEntry<PropertyArguments, Unit>
    }

    /** 取得成功编码结果；任何结构化拒绝都直接让属性测试失败。 */
    private fun requireEncoded(
        result: PixelNavigatorSnapshotEncodeResult,
    ): PixelNavigatorSnapshotEncodeResult.Encoded {
        if (result is PixelNavigatorSnapshotEncodeResult.Encoded) return result
        /** 编码失败时携带稳定诊断信息的拒绝结果。 */
        val rejected = result as PixelNavigatorSnapshotEncodeResult.Rejected
        error("快照编码被拒绝：${rejected.failure}")
    }

    /** 路由属性测试使用的 typed 参数。 */
    private data class PropertyArguments(
        /** 用于检查顺序和参数边界的标签。 */
        val label: String,
        /** 用于覆盖完整 Int 空间的数值。 */
        val value: Int,
    )

    /** 一条期望恢复的 typed entry 数据。 */
    private data class ExpectedEntry(
        /** 目标参数。 */
        val arguments: PropertyArguments,
        /** 目标 route-local counter。 */
        val counter: Int,
    )

    /** 使用 NUL 分隔符实现确定性参数编码的测试 codec。 */
    private object PropertyArgumentsCodec : PixelRoutePayloadCodec<PropertyArguments> {
        /** 当前参数协议版本。 */
        override val schemaVersion: Int = 1

        /** 将标签和整数编码为稳定 UTF-8 字节。 */
        override fun encode(value: PropertyArguments): ByteArray {
            return "${value.label}\u0000${value.value}".toByteArray(StandardCharsets.UTF_8)
        }

        /** 只接受当前协议和完整的标签、整数结构。 */
        override fun decode(
            schemaVersion: Int,
            payload: ByteArray,
        ): PixelRoutePayloadDecodeResult<PropertyArguments> {
            if (schemaVersion != this.schemaVersion) {
                return PixelRoutePayloadDecodeResult.Rejected("不支持的参数协议 $schemaVersion")
            }
            /** 解码后的 UTF-8 文本。 */
            val text = String(payload, StandardCharsets.UTF_8)
            /** 标签和整数之间最后一个 NUL 分隔符。 */
            val separator = text.lastIndexOf('\u0000')
            /** 分隔符之前的标签。 */
            val label = if (separator >= 0) text.substring(0, separator) else ""
            /** 分隔符之后的整数。 */
            val value = if (separator >= 0) text.substring(separator + 1).toIntOrNull() else null
            return if (label.isBlank() || value == null) {
                PixelRoutePayloadDecodeResult.Rejected("参数结构不完整")
            } else {
                PixelRoutePayloadDecodeResult.Decoded(PropertyArguments(label, value))
            }
        }
    }

    /** 只允许一个整数 counter 进入持久快照的测试适配器。 */
    private class PropertySnapshotAdapter(
        /** 与本适配器严格绑定的 destination 实例。 */
        destination: PixelRouteDestination<PropertyArguments, Unit>,
    ) : PixelRouteSnapshotAdapter<PropertyArguments, Unit>(destination, PropertyArgumentsCodec) {
        /** 当前 route-local state 协议版本。 */
        override val stateSchemaVersion: Int = 1

        /** 只编码显式允许的 counter。 */
        override fun encodeRouteState(
            entry: PixelRouteEntry<PropertyArguments, Unit>,
        ): Map<String, ByteArray> {
            /** 当前 entry 的必需 counter。 */
            val counter = checkNotNull(entry.stateBucket.read(CounterStateKey))
            return mapOf(CounterPayloadKey to counter.toString().toByteArray(StandardCharsets.UTF_8))
        }

        /** 只恢复当前协议中的唯一 counter key。 */
        override fun decodeRouteState(
            schemaVersion: Int,
            payloads: Map<String, ByteArray>,
        ): PixelRouteStateDecodeResult {
            if (schemaVersion != stateSchemaVersion || payloads.keys != setOf(CounterPayloadKey)) {
                return PixelRouteStateDecodeResult.Rejected("route-local state 结构不受支持")
            }
            /** counter 对应的 UTF-8 字节。 */
            val bytes = checkNotNull(payloads[CounterPayloadKey])
            /** 严格解析出的 counter。 */
            val counter = String(bytes, StandardCharsets.UTF_8).toIntOrNull()
                ?: return PixelRouteStateDecodeResult.Rejected("counter 不是整数")
            return PixelRouteStateDecodeResult.Decoded(
                PixelRouteStateRestorer { bucket -> bucket.write(CounterStateKey, counter) },
            )
        }
    }

    private companion object {
        /** 内存 route-local state 的 identity key。 */
        val CounterStateKey: PixelRouteStateKey<Int> = PixelRouteStateKey("counter")

        /** 快照协议中的稳定 counter key。 */
        const val CounterPayloadKey: String = "counter"
    }
}
