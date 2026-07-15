package com.purride.pixelui.widgets.navigation

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * 定义 `PixelDeepLink` 在 `PixelDeepLink` 中承担的数据与行为边界。
 *
 * Platform-neutral parsed deep link passed to [PixelDeepLinkResolver].
 */
public data class PixelDeepLink(
    val rawUri: String,
    val scheme: String?,
    val host: String?,
    val pathSegments: List<String>,
    val queryParameters: Map<String, List<String>>,
    val fragment: String?,
) {
    /** 查询 `PixelDeepLink` 的 `queryParameter` 派生结果；该读取不会改变已保存状态。 */
    public fun queryParameter(name: String): String? = queryParameters[name]?.firstOrNull()

    /** 集中提供 `PixelDeepLink` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 从调用方输入解析或构造 `PixelDeepLink`，无效输入按声明契约拒绝。 */
        public fun parse(uri: String): PixelDeepLink {
            require(uri.isNotBlank()) { "PixelDeepLink URI must not be blank" }
            val parsed = try {
                URI(uri)
            } catch (error: Exception) {
                throw IllegalArgumentException("Invalid PixelDeepLink URI '$uri': ${error.message}", error)
            }
            val pathSegments = parsed.rawPath
                .orEmpty()
                .split('/')
                .filter(String::isNotEmpty)
                .map(::decodePathComponent)
            val queryParameters = buildMap<String, MutableList<String>> {
                parsed.rawQuery
                    ?.split('&')
                    ?.filter(String::isNotEmpty)
                    ?.forEach { pair ->
                        val separator = pair.indexOf('=')
                        val rawName = if (separator >= 0) pair.substring(0, separator) else pair
                        val rawValue = if (separator >= 0) pair.substring(separator + 1) else ""
                        val name = decodeQueryComponent(rawName)
                        val value = decodeQueryComponent(rawValue)
                        getOrPut(name) { mutableListOf() } += value
                    }
            }.mapValues { (_, values) -> values.toList() }
            return PixelDeepLink(
                rawUri = uri,
                scheme = parsed.scheme?.lowercase(),
                host = parsed.host?.lowercase(),
                pathSegments = pathSegments,
                queryParameters = queryParameters,
                fragment = parsed.rawFragment?.let(::decodePathComponent),
            )
        }

        private fun decodePathComponent(value: String): String {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }

        private fun decodeQueryComponent(value: String): String {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }
    }
}

/**
 * 定义 `PixelDeepLinkResolver` 在 `PixelDeepLink` 中的可替换调用契约。
 *
 * Resolves a parsed deep link to the complete route stack that should become active.
 *
 * Return `null` when the link is not recognized. A handled link must return at least one route.
 */
public fun interface PixelDeepLinkResolver {
    /** 按 `PixelDeepLink` 的规则解析 `resolve` 目标，并返回稳定的匹配结果。 */
    public fun resolve(link: PixelDeepLink): List<PixelRoute>?
}
