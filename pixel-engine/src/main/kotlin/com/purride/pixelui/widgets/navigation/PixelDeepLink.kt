package com.purride.pixelui.widgets.navigation

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
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
    public fun queryParameter(name: String): String? = queryParameters[name]?.firstOrNull()

    public companion object {
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
 * Resolves a parsed deep link to the complete route stack that should become active.
 *
 * Return `null` when the link is not recognized. A handled link must return at least one route.
 */
public fun interface PixelDeepLinkResolver {
    public fun resolve(link: PixelDeepLink): List<PixelRoute>?
}
