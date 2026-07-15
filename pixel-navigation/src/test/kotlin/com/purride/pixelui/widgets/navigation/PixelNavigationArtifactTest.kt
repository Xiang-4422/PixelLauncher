package com.purride.pixelui.widgets.navigation

import com.purride.pixelui.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证独立 pixel-navigation artifact 的公开路由行为，不依赖聚合 pixel-engine。 */
class PixelNavigationArtifactTest {
    /** 验证 URI 解码会保留路径、重复查询参数、加号和片段的公开语义。 */
    @Test
    fun parsedDeepLinkPreservesDecodedStructure() {
        /** 包含编码路径、重复参数和片段的消费者输入。 */
        val link = PixelDeepLink.parse(
            "pixel://app/profile%20card?tag=one&tag=two+words&literal=a%2Bb#details%201",
        )

        assertEquals("pixel", link.scheme)
        assertEquals("app", link.host)
        assertEquals(listOf("profile card"), link.pathSegments)
        assertEquals(listOf("one", "two words"), link.queryParameters["tag"])
        assertEquals("a+b", link.queryParameter("literal"))
        assertEquals("details 1", link.fragment)
    }

    /** 验证类型化深链成功创建目标 entry，非法参数则保持原栈不变。 */
    @Test
    fun typedDeepLinkMutatesOnlyAfterSuccessfulArgumentDecoding() {
        /** 作为所有深链尝试回退点的稳定根路由。 */
        val navigator = PixelNavigatorState(
            PixelRoute(
                name = "root",
                transition = PixelRouteTransition.None,
                builder = { Text("ROOT") },
            ),
        )
        /** 类型化目标确保 URI 参数不能绕过编译期 destination 约束。 */
        val destination = pixelRouteDestination<ProfileArguments, Unit>(
            id = "profile",
            transition = PixelRouteTransition.None,
        ) { _, scope ->
            Text("PROFILE ${scope.arguments.userId}")
        }
        /** 唯一受注册的 profile 深链规则。 */
        val resolver = PixelTypedDeepLinkResolver(
            listOf(
                PixelTypedDeepLinkRoute(
                    destination = destination,
                    matcher = PixelTypedDeepLinkMatcher { link ->
                        link.scheme == "pixel" && link.host == "app" &&
                            link.pathSegments == listOf("profile")
                    },
                    argumentDecoder = PixelDeepLinkArgumentDecoder { link ->
                        /** 等待转换为正整数的原始用户标识。 */
                        val rawId = link.queryParameter("id")
                        /** 仅允许正整数进入类型化目标。 */
                        val userId = rawId?.toIntOrNull()?.takeIf { value -> value > 0 }
                            ?: return@PixelDeepLinkArgumentDecoder PixelDeepLinkDecodeRejected(
                                PixelDeepLinkArgumentFailure(
                                    reason = if (rawId == null) {
                                        PixelDeepLinkArgumentFailureReason.Missing
                                    } else {
                                        PixelDeepLinkArgumentFailureReason.Invalid
                                    },
                                    parameterName = "id",
                                    rawValue = rawId,
                                    message = "profile id 必须是正整数",
                                ),
                            )
                        PixelDeepLinkDecoded(ProfileArguments(userId))
                    },
                ),
            ),
        )
        /** 非法请求前后的根 entry 必须保持同一对象。 */
        val rootEntry = navigator.currentEntry

        /** 应被结构化拒绝且不得产生 entry 的非法请求结果。 */
        val rejected = navigator.handleTypedDeepLink("pixel://app/profile?id=bad", resolver)

        assertTrue(rejected is PixelTypedDeepLinkRejected)
        assertSame(rootEntry, navigator.currentEntry)
        assertEquals(1, navigator.entries.size)

        /** 应成功压入类型化 entry 的合法请求结果。 */
        val navigated = navigator.handleTypedDeepLink("pixel://app/profile?id=42", resolver)

        assertTrue(navigated is PixelTypedDeepLinkNavigated)
        assertEquals(2, navigator.entries.size)
        assertEquals(ProfileArguments(42), navigator.currentEntry.arguments)
    }
}

/** 独立 artifact 测试中的类型化 profile 参数。 */
private data class ProfileArguments(
    /** 必须由深链解析得到的正整数用户标识。 */
    val userId: Int,
)
