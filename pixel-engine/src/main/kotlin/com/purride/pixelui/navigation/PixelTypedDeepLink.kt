package com.purride.pixelui

/** 定义 `PixelDeepLinkArgumentFailureReason` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Reason a matched deep link could not produce typed destination arguments.
 */
public enum class PixelDeepLinkArgumentFailureReason {
    /** A required path or query parameter was absent. */
    Missing,

    /** A present parameter could not be converted or violated destination validation. */
    Invalid,
}

/**
 * 表示 `PixelTypedDeepLink` 的 `PixelDeepLinkArgumentFailure` 稳定结果或事件分支。
 *
 * Structured failure returned by a typed deep-link argument decoder.
 *
 * @property reason Whether the parameter was missing or invalid.
 * @property parameterName Stable path/query parameter name used by diagnostics.
 * @property rawValue Rejected encoded value after URI decoding, or `null` when missing.
 * @property message Human-readable explanation suitable for logs and tests.
 */
public data class PixelDeepLinkArgumentFailure(
    public val reason: PixelDeepLinkArgumentFailureReason,
    public val parameterName: String,
    public val rawValue: String? = null,
    public val message: String,
) {
    init {
        require(parameterName.isNotBlank()) { "Deep-link parameterName must not be blank" }
        require(message.isNotBlank()) { "Deep-link argument failure message must not be blank" }
    }
}

/** 定义 `PixelDeepLinkDecodeResult` 在 `PixelTypedDeepLink` 中的可替换调用契约。
 *
 * Typed result produced after decoding arguments for one matched deep-link route.
 */
public sealed interface PixelDeepLinkDecodeResult<out A : Any>

/**
 * 定义 `PixelDeepLinkDecoded` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Successfully decoded non-null destination arguments.
 *
 * @property arguments Value whose Kotlin type is accepted by the registered destination.
 */
public data class PixelDeepLinkDecoded<A : Any>(
    public val arguments: A,
) : PixelDeepLinkDecodeResult<A>

/**
 * 定义 `PixelDeepLinkDecodeRejected` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Rejected destination arguments that must not mutate a Navigator.
 *
 * @property failure Structured missing or invalid parameter description.
 */
public data class PixelDeepLinkDecodeRejected(
    public val failure: PixelDeepLinkArgumentFailure,
) : PixelDeepLinkDecodeResult<Nothing>

/** 定义 `PixelDeepLinkArgumentDecoder` 在 `PixelTypedDeepLink` 中的可替换调用契约。
 *
 * Converts a matched [PixelDeepLink] into the exact argument type accepted by a destination.
 */
public fun interface PixelDeepLinkArgumentDecoder<A : Any> {
    /** 执行 `PixelTypedDeepLink` 的 `decode` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns decoded arguments or an explicit non-throwing rejection.
 */
    public fun decode(link: PixelDeepLink): PixelDeepLinkDecodeResult<A>
}

/** 定义 `PixelTypedDeepLinkMatcher` 在 `PixelTypedDeepLink` 中的可替换调用契约。
 *
 * Decides whether one registered typed route owns a parsed deep link.
 */
public fun interface PixelTypedDeepLinkMatcher {
    /** 执行 `PixelTypedDeepLink` 的 `matches` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns true only when this route's decoder should validate [link].
 */
    public fun matches(link: PixelDeepLink): Boolean
}

/**
 * 定义 `PixelTypedDeepLinkRoute` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Binds a URI matcher and argument decoder to one legal typed destination.
 *
 * Matching and decoding are separate so a missing required parameter is reported as an invalid
 * request for the intended destination rather than being silently treated as an unknown URI.
 *
 * @param A Non-null argument type accepted by [destination].
 * @param R Successful result type produced by [destination].
 * @property destination The only destination this route may instantiate.
 */
public class PixelTypedDeepLinkRoute<A : Any, R>(
    public val destination: PixelRouteDestination<A, R>,
    private val matcher: PixelTypedDeepLinkMatcher,
    private val argumentDecoder: PixelDeepLinkArgumentDecoder<A>,
) {
    /** 执行 `PixelTypedDeepLink` 的 `matches` 公开行为；具体参数、返回和副作用见下文。
 *
 * Returns whether this registration owns [link] before argument validation.
 */
    public fun matches(link: PixelDeepLink): Boolean = matcher.matches(link)

    /** 执行 `PixelTypedDeepLink` 的 `decodeArguments` 公开行为；具体参数、返回和副作用见下文。
 *
 * Decodes [link] into the argument type statically paired with [destination].
 */
    public fun decodeArguments(link: PixelDeepLink): PixelDeepLinkDecodeResult<A> {
        return argumentDecoder.decode(link)
    }

    /** Validates and applies one matched link without exposing an erased mutable request. */
    internal fun navigate(
        navigator: PixelNavigatorState,
        link: PixelDeepLink,
        mode: PixelTypedDeepLinkNavigationMode,
    ): PixelTypedDeepLinkResult {
        val decoded = try {
            decodeArguments(link)
        } catch (error: Throwable) {
            return PixelTypedDeepLinkRejected(
                rawUri = link.rawUri,
                reason = PixelTypedDeepLinkRejectionReason.ResolverFailure,
                destinationId = destination.id,
                message = "Deep-link decoder for '${destination.id}' failed: ${error.message.orEmpty()}",
            )
        }
        return when (decoded) {
            is PixelDeepLinkDecoded -> {
                val request = PixelRouteRequest(destination, decoded.arguments)
                val entry = navigator.navigateTypedDeepLink(request, mode)
                PixelTypedDeepLinkNavigated(
                    rawUri = link.rawUri,
                    destinationId = destination.id,
                    entry = entry,
                    mode = mode,
                )
            }
            is PixelDeepLinkDecodeRejected -> PixelTypedDeepLinkRejected(
                rawUri = link.rawUri,
                reason = when (decoded.failure.reason) {
                    PixelDeepLinkArgumentFailureReason.Missing ->
                        PixelTypedDeepLinkRejectionReason.MissingArgument
                    PixelDeepLinkArgumentFailureReason.Invalid ->
                        PixelTypedDeepLinkRejectionReason.InvalidArgument
                },
                destinationId = destination.id,
                argumentFailure = decoded.failure,
                message = decoded.failure.message,
            )
        }
    }
}

/** 定义 `PixelTypedDeepLinkNavigationMode` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Determines how a successfully decoded typed link changes its target Navigator.
 */
public enum class PixelTypedDeepLinkNavigationMode {
    /** Appends a fresh entry above the target Navigator's current entry. */
    Push,

    /** Replaces only the target Navigator's current entry with a fresh typed entry. */
    Replace,
}

/** 定义 `PixelTypedDeepLinkRejectionReason` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Machine-readable reason a typed deep link was rejected without navigation.
 */
public enum class PixelTypedDeepLinkRejectionReason {
    /** The raw URI could not be parsed. */
    MalformedUri,

    /** The matched destination was missing a required typed argument. */
    MissingArgument,

    /** A matched argument was present but invalid for its destination type. */
    InvalidArgument,

    /** A consumer-supplied matcher or decoder threw unexpectedly. */
    ResolverFailure,

    /** The requested multi-stack target is unknown or has not mounted yet. */
    StackUnavailable,
}

/** 定义 `PixelTypedDeepLinkResult` 在 `PixelTypedDeepLink` 中的可替换调用契约。
 *
 * Terminal result of a typed deep-link navigation request.
 */
public sealed interface PixelTypedDeepLinkResult

/**
 * 定义 `PixelTypedDeepLinkNavigated` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Successful typed deep-link navigation.
 *
 * @property rawUri Original URI handled by the resolver.
 * @property destinationId Registered destination identifier.
 * @property entry Fresh entry created with statically decoded arguments.
 * @property mode Stack mutation applied to the target Navigator.
 */
public data class PixelTypedDeepLinkNavigated(
    public val rawUri: String,
    public val destinationId: String,
    public val entry: PixelRouteEntry<*, *>,
    public val mode: PixelTypedDeepLinkNavigationMode,
) : PixelTypedDeepLinkResult

/**
 * 定义 `PixelTypedDeepLinkNotMatched` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * URI that matched no registered legal destination and left navigation unchanged.
 *
 * @property rawUri Original URI that was not recognized.
 */
public data class PixelTypedDeepLinkNotMatched(
    public val rawUri: String,
) : PixelTypedDeepLinkResult

/**
 * 定义 `PixelTypedDeepLinkRejected` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Recognized or malformed request rejected without changing the target Navigator.
 *
 * @property rawUri Original URI supplied by the caller.
 * @property reason Machine-readable rejection category.
 * @property destinationId Matched destination, or `null` before route matching.
 * @property argumentFailure Structured parameter failure when argument validation ran.
 * @property message Human-readable diagnostic description.
 */
public data class PixelTypedDeepLinkRejected(
    public val rawUri: String,
    public val reason: PixelTypedDeepLinkRejectionReason,
    public val destinationId: String? = null,
    public val argumentFailure: PixelDeepLinkArgumentFailure? = null,
    public val message: String,
) : PixelTypedDeepLinkResult {
    init {
        require(message.isNotBlank()) { "Typed deep-link rejection message must not be blank" }
    }
}

/**
 * 定义 `PixelTypedDeepLinkResolver` 在 `PixelTypedDeepLink` 中承担的数据与行为边界。
 *
 * Ordered registry of legal typed deep-link destinations.
 *
 * The first matcher returning true owns the link. Decoder failures never fall through to another
 * destination, preventing malformed parameters from navigating to an unrelated route.
 */
public class PixelTypedDeepLinkResolver(
    routes: List<PixelTypedDeepLinkRoute<*, *>>,
) {
    /** Immutable route order used for deterministic first-match resolution. */
    private val registeredRoutes: List<PixelTypedDeepLinkRoute<*, *>> = routes.toList()

    /** 公开 `PixelTypedDeepLink` 的 `destinationIds` 配置或运行值。
 *
 * Ordered destination identifiers available to this resolver.
 */
    public val destinationIds: List<String>
        get() = registeredRoutes.map { route -> route.destination.id }

    /** Resolves and applies [link] to [navigator] while containing consumer callback failures. */
    internal fun navigate(
        navigator: PixelNavigatorState,
        link: PixelDeepLink,
        mode: PixelTypedDeepLinkNavigationMode,
    ): PixelTypedDeepLinkResult {
        registeredRoutes.forEach { route ->
            val matched = try {
                route.matches(link)
            } catch (error: Throwable) {
                return PixelTypedDeepLinkRejected(
                    rawUri = link.rawUri,
                    reason = PixelTypedDeepLinkRejectionReason.ResolverFailure,
                    destinationId = route.destination.id,
                    message = "Deep-link matcher for '${route.destination.id}' failed: " +
                        error.message.orEmpty(),
                )
            }
            if (matched) return route.navigate(navigator, link, mode)
        }
        return PixelTypedDeepLinkNotMatched(rawUri = link.rawUri)
    }
}

/**
 * 执行 `PixelTypedDeepLink` 的 `handleTypedDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Parses and handles a typed deep link without throwing for malformed URI text.
 *
 * Malformed, unmatched, missing, and invalid links leave this Navigator's stack unchanged.
 */
public fun PixelNavigatorState.handleTypedDeepLink(
    uri: String,
    resolver: PixelTypedDeepLinkResolver,
    mode: PixelTypedDeepLinkNavigationMode = PixelTypedDeepLinkNavigationMode.Push,
): PixelTypedDeepLinkResult {
    val link = try {
        PixelDeepLink.parse(uri)
    } catch (error: Exception) {
        return PixelTypedDeepLinkRejected(
            rawUri = uri,
            reason = PixelTypedDeepLinkRejectionReason.MalformedUri,
            message = "Malformed deep-link URI: ${error.message.orEmpty()}",
        )
    }
    return handleTypedDeepLink(link, resolver, mode)
}

/** 执行 `PixelTypedDeepLink` 的 `handleTypedDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Handles an already parsed typed [link] through the legal destination [resolver].
 */
public fun PixelNavigatorState.handleTypedDeepLink(
    link: PixelDeepLink,
    resolver: PixelTypedDeepLinkResolver,
    mode: PixelTypedDeepLinkNavigationMode = PixelTypedDeepLinkNavigationMode.Push,
): PixelTypedDeepLinkResult {
    return resolver.navigate(this, link, mode)
}

/**
 * 执行 `PixelTypedDeepLink` 的 `handleTypedDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Routes a typed link into one independently retained stack and selects it only after success.
 *
 * Unknown or not-yet-mounted stack identifiers return [PixelTypedDeepLinkRejected] with
 * [PixelTypedDeepLinkRejectionReason.StackUnavailable] and do not change the active stack.
 */
public fun PixelMultiStackNavigatorController.handleTypedDeepLink(
    stackId: String,
    uri: String,
    resolver: PixelTypedDeepLinkResolver,
    mode: PixelTypedDeepLinkNavigationMode = PixelTypedDeepLinkNavigationMode.Push,
    selectStackOnSuccess: Boolean = true,
): PixelTypedDeepLinkResult {
    val targetNavigator = navigatorState(stackId) ?: return PixelTypedDeepLinkRejected(
        rawUri = uri,
        reason = PixelTypedDeepLinkRejectionReason.StackUnavailable,
        message = "Multi-stack Navigator '$stackId' is unknown or not mounted",
    )
    val result = targetNavigator.handleTypedDeepLink(uri, resolver, mode)
    if (selectStackOnSuccess && result is PixelTypedDeepLinkNavigated) {
        selectStack(stackId)
    }
    return result
}
