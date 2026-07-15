package com.purride.pixelui

import android.os.Bundle
import com.purride.pixelui.widgets.navigation.getPixelMultiStackSnapshot as getPixelMultiStackSnapshotNavigation
import com.purride.pixelui.widgets.navigation.handleTypedDeepLink as handleTypedDeepLinkNavigation
import com.purride.pixelui.widgets.navigation.persistentSnapshot as captureMultiStackSnapshotNavigation
import com.purride.pixelui.widgets.navigation.restorePersistentSnapshot as restoreMultiStackSnapshotNavigation
import com.purride.pixelui.widgets.navigation.saveToBundle as saveNavigationSnapshotToBundle

/** 保留根包下 `PixelNavigator` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigator = com.purride.pixelui.widgets.navigation.PixelNavigator
/** 保留根包下 `PixelNavigatorState` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigatorState = com.purride.pixelui.widgets.navigation.PixelNavigatorState
/** 保留根包下 `PixelNavigatorOperation` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigatorOperation = com.purride.pixelui.widgets.navigation.PixelNavigatorOperation
/** 保留根包下 `PixelRoute` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRoute = com.purride.pixelui.widgets.navigation.PixelRoute
/** 保留根包下 `PixelRouteTransition` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteTransition = com.purride.pixelui.widgets.navigation.PixelRouteTransition
/** 保留根包下 `PixelRouteTransitionBuilder` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteTransitionBuilder = com.purride.pixelui.widgets.navigation.PixelRouteTransitionBuilder
/** 保留根包下 `PixelRouteScrollRestoration` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteScrollRestoration = com.purride.pixelui.widgets.navigation.PixelRouteScrollRestoration
/** 保留根包下 `PixelRouteEntryId` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteEntryId = com.purride.pixelui.widgets.navigation.PixelRouteEntryId
/** 保留根包下 `PixelRouteLifecycleState` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteLifecycleState = com.purride.pixelui.widgets.navigation.PixelRouteLifecycleState
/** 保留根包下 `PixelRouteResultState` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteResultState = com.purride.pixelui.widgets.navigation.PixelRouteResultState
/** 保留根包下 `PixelRouteCancellationReason` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteCancellationReason = com.purride.pixelui.widgets.navigation.PixelRouteCancellationReason
/** 保留根包下 `PixelRouteOutcome` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteOutcome<R> = com.purride.pixelui.widgets.navigation.PixelRouteOutcome<R>
/** 保留根包下 `PixelRouteSuccess` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteSuccess<R> =
    com.purride.pixelui.widgets.navigation.PixelRouteOutcome.Success<R>
/** 保留根包下 `PixelRouteCancelled` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteCancelled =
    com.purride.pixelui.widgets.navigation.PixelRouteOutcome.Cancelled
/** 保留根包下 `PixelRouteStateKey` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteStateKey<T> = com.purride.pixelui.widgets.navigation.PixelRouteStateKey<T>
/** 保留根包下 `PixelRouteStateBucket` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteStateBucket = com.purride.pixelui.widgets.navigation.PixelRouteStateBucket
/** 保留根包下 `PixelRouteResultChannel` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteResultChannel<R> = com.purride.pixelui.widgets.navigation.PixelRouteResultChannel<R>
/** 保留根包下 `PixelRouteDestination` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteDestination<A, R> = com.purride.pixelui.widgets.navigation.PixelRouteDestination<A, R>
/** 保留根包下 `PixelRouteRequest` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteRequest<A, R> = com.purride.pixelui.widgets.navigation.PixelRouteRequest<A, R>
/** 保留根包下 `PixelRouteEntry` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteEntry<A, R> = com.purride.pixelui.widgets.navigation.PixelRouteEntry<A, R>
/** 保留根包下 `PixelRouteEntryScope` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteEntryScope<A, R> = com.purride.pixelui.widgets.navigation.PixelRouteEntryScope<A, R>
/** 保留根包下 `PixelNavigationAction` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigationAction = com.purride.pixelui.widgets.navigation.PixelNavigationAction
/** 保留根包下 `PixelNavigationFailureReason` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigationFailureReason = com.purride.pixelui.widgets.navigation.PixelNavigationFailureReason
/** 保留根包下 `PixelNavigationFailure` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigationFailure = com.purride.pixelui.widgets.navigation.PixelNavigationFailure
/** 保留根包下 `PixelNavigationEventType` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigationEventType = com.purride.pixelui.widgets.navigation.PixelNavigationEventType
/** 保留根包下 `PixelNavigationEvent` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigationEvent = com.purride.pixelui.widgets.navigation.PixelNavigationEvent
/** 保留根包下 `PixelNavigationObserver` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigationObserver = com.purride.pixelui.widgets.navigation.PixelNavigationObserver
/** 保留根包下 `PixelRouteEntryInspection` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteEntryInspection = com.purride.pixelui.widgets.navigation.PixelRouteEntryInspection
/** 保留根包下 `PixelRouteTransitionInspection` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteTransitionInspection = com.purride.pixelui.widgets.navigation.PixelRouteTransitionInspection
/** 保留根包下 `PixelNavigatorInspectionSnapshot` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelNavigatorInspectionSnapshot =
    com.purride.pixelui.widgets.navigation.PixelNavigatorInspectionSnapshot
/** 保留根包下 `PixelNavigatorSnapshot` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelNavigatorSnapshot = com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshot
/** 保留根包下 `PixelDeepLink` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelDeepLink = com.purride.pixelui.widgets.navigation.PixelDeepLink
/** 保留根包下 `PixelDeepLinkResolver` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelDeepLinkResolver = com.purride.pixelui.widgets.navigation.PixelDeepLinkResolver
/** 保留根包下 `PixelNavigatorSnapshotCodec` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelNavigatorSnapshotCodec =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotCodec
/** 保留根包下 `PixelNavigatorRestorePlan` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigatorRestorePlan =
    com.purride.pixelui.widgets.navigation.PixelNavigatorRestorePlan
/** 保留根包下 `PixelNavigatorRestoredEntryInspection` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelNavigatorRestoredEntryInspection =
    com.purride.pixelui.widgets.navigation.PixelNavigatorRestoredEntryInspection
/** 保留根包下 `PixelNavigatorSnapshotEncodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigatorSnapshotEncodeResult =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotEncodeResult
/** 保留根包下 `PixelNavigatorSnapshotEncoded` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelNavigatorSnapshotEncoded =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotEncodeResult.Encoded
/** 保留根包下 `PixelNavigatorSnapshotEncodeRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigatorSnapshotEncodeRejected =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotEncodeResult.Rejected
/** 保留根包下 `PixelNavigatorSnapshotDecodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigatorSnapshotDecodeResult =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotDecodeResult
/** 保留根包下 `PixelNavigatorSnapshotDecoded` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelNavigatorSnapshotDecoded =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotDecodeResult.Decoded
/** 保留根包下 `PixelNavigatorSnapshotDecodeRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigatorSnapshotDecodeRejected =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotDecodeResult.Rejected
/** 保留根包下 `PixelNavigatorSnapshotFailure` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigatorSnapshotFailure =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotFailure
/** 保留根包下 `PixelNavigatorSnapshotFailureReason` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelNavigatorSnapshotFailureReason =
    com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshotFailureReason
/** 保留根包下 `PixelRoutePayloadCodec` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelRoutePayloadCodec<T> =
    com.purride.pixelui.widgets.navigation.PixelRoutePayloadCodec<T>
/** 保留根包下 `PixelRoutePayloadDecodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRoutePayloadDecodeResult<T> =
    com.purride.pixelui.widgets.navigation.PixelRoutePayloadDecodeResult<T>
/** 保留根包下 `PixelRoutePayloadDecoded` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRoutePayloadDecoded<T> =
    com.purride.pixelui.widgets.navigation.PixelRoutePayloadDecodeResult.Decoded<T>
/** 保留根包下 `PixelRoutePayloadRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRoutePayloadRejected =
    com.purride.pixelui.widgets.navigation.PixelRoutePayloadDecodeResult.Rejected
/** 保留根包下 `PixelRouteSnapshotAdapter` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelRouteSnapshotAdapter<A, R> =
    com.purride.pixelui.widgets.navigation.PixelRouteSnapshotAdapter<A, R>
/** 保留根包下 `PixelRouteSnapshotRegistry` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelRouteSnapshotRegistry =
    com.purride.pixelui.widgets.navigation.PixelRouteSnapshotRegistry
/** 保留根包下 `PixelRouteStateDecodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteStateDecodeResult =
    com.purride.pixelui.widgets.navigation.PixelRouteStateDecodeResult
/** 保留根包下 `PixelRouteStateDecoded` 路由类型的源码兼容入口；生命周期和类型约束由 navigation artifact 实现。 */
public typealias PixelRouteStateDecoded =
    com.purride.pixelui.widgets.navigation.PixelRouteStateDecodeResult.Decoded
/** 保留根包下 `PixelRouteStateRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelRouteStateRejected =
    com.purride.pixelui.widgets.navigation.PixelRouteStateDecodeResult.Rejected
/** 保留根包下 `PixelRouteStateRestorer` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelRouteStateRestorer =
    com.purride.pixelui.widgets.navigation.PixelRouteStateRestorer
/** 保留根包下 `PixelNavigatorStack` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigatorStack =
    com.purride.pixelui.widgets.navigation.PixelNavigatorStack
/** 保留根包下 `PixelNavigatorStackDefinition` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNavigatorStackDefinition =
    com.purride.pixelui.widgets.navigation.PixelNavigatorStackDefinition
/** 保留根包下 `PixelTypedNavigatorStack` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelTypedNavigatorStack<A, R> =
    com.purride.pixelui.widgets.navigation.PixelTypedNavigatorStack<A, R>
/** 保留根包下 `PixelStackSelectionResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelStackSelectionResult =
    com.purride.pixelui.widgets.navigation.PixelStackSelectionResult
/** 保留根包下 `PixelMultiStackNavigatorController` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelMultiStackNavigatorController =
    com.purride.pixelui.widgets.navigation.PixelMultiStackNavigatorController
/** 保留根包下 `PixelMultiStackNavigator` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelMultiStackNavigator =
    com.purride.pixelui.widgets.navigation.PixelMultiStackNavigator
/** 保留根包下 `PixelNestedNavigator` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelNestedNavigator =
    com.purride.pixelui.widgets.navigation.PixelNestedNavigator

/** 为 `PixelNestedNavigatorController` 保留 `PixelNavigatorAliases` 的稳定源码类型入口。
 *
 * Root-package alias for the single-host typed nested Navigator controller.
 */
public typealias PixelNestedNavigatorController =
    com.purride.pixelui.widgets.navigation.PixelNestedNavigatorController
/** 保留根包下 `PixelDeepLinkArgumentFailureReason` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelDeepLinkArgumentFailureReason =
    com.purride.pixelui.widgets.navigation.PixelDeepLinkArgumentFailureReason
/** 保留根包下 `PixelDeepLinkArgumentFailure` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelDeepLinkArgumentFailure =
    com.purride.pixelui.widgets.navigation.PixelDeepLinkArgumentFailure
/** 保留根包下 `PixelDeepLinkDecodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelDeepLinkDecodeResult<A> =
    com.purride.pixelui.widgets.navigation.PixelDeepLinkDecodeResult<A>
/** 保留根包下 `PixelDeepLinkDecoded` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelDeepLinkDecoded<A> =
    com.purride.pixelui.widgets.navigation.PixelDeepLinkDecoded<A>
/** 保留根包下 `PixelDeepLinkDecodeRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelDeepLinkDecodeRejected =
    com.purride.pixelui.widgets.navigation.PixelDeepLinkDecodeRejected
/** 保留根包下 `PixelDeepLinkArgumentDecoder` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelDeepLinkArgumentDecoder<A> =
    com.purride.pixelui.widgets.navigation.PixelDeepLinkArgumentDecoder<A>
/** 保留根包下 `PixelTypedDeepLinkMatcher` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkMatcher =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkMatcher
/** 保留根包下 `PixelTypedDeepLinkRoute` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkRoute<A, R> =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkRoute<A, R>
/** 保留根包下 `PixelTypedDeepLinkNavigationMode` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkNavigationMode =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkNavigationMode
/** 保留根包下 `PixelTypedDeepLinkRejectionReason` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkRejectionReason =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkRejectionReason
/** 保留根包下 `PixelTypedDeepLinkResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelTypedDeepLinkResult =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkResult
/** 保留根包下 `PixelTypedDeepLinkNavigated` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkNavigated =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkNavigated
/** 保留根包下 `PixelTypedDeepLinkNotMatched` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkNotMatched =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkNotMatched
/** 保留根包下 `PixelTypedDeepLinkRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelTypedDeepLinkRejected =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkRejected
/** 保留根包下 `PixelTypedDeepLinkResolver` 深链类型的源码兼容入口；匹配和拒绝规则由 navigation artifact 实现。 */
public typealias PixelTypedDeepLinkResolver =
    com.purride.pixelui.widgets.navigation.PixelTypedDeepLinkResolver
/** 保留根包下 `PixelMultiStackSnapshotFailureReason` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackSnapshotFailureReason =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotFailureReason
/** 保留根包下 `PixelMultiStackSnapshotFailure` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackSnapshotFailure =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotFailure
/** 保留根包下 `PixelMultiStackSnapshot` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelMultiStackSnapshot =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshot
/** 保留根包下 `PixelMultiStackSnapshotEncodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackSnapshotEncodeResult =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotEncodeResult
/** 保留根包下 `PixelMultiStackSnapshotEncoded` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelMultiStackSnapshotEncoded =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotEncodeResult.Encoded
/** 保留根包下 `PixelMultiStackSnapshotEncodeRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackSnapshotEncodeRejected =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotEncodeResult.Rejected
/** 保留根包下 `PixelMultiStackSnapshotDecodeResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackSnapshotDecodeResult =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotDecodeResult
/** 保留根包下 `PixelMultiStackSnapshotDecoded` 快照与恢复类型的源码兼容入口；编码版本由 navigation artifact 定义。 */
public typealias PixelMultiStackSnapshotDecoded =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotDecodeResult.Decoded
/** 保留根包下 `PixelMultiStackSnapshotDecodeRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackSnapshotDecodeRejected =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotDecodeResult.Rejected
/** 保留根包下 `PixelMultiStackRestoreResult` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackRestoreResult =
    com.purride.pixelui.widgets.navigation.PixelMultiStackRestoreResult
/** 保留根包下 `PixelMultiStackRestored` 导航类型的源码兼容入口；栈状态仍由 navigation artifact 持有。 */
public typealias PixelMultiStackRestored =
    com.purride.pixelui.widgets.navigation.PixelMultiStackRestoreResult.Restored
/** 保留根包下 `PixelMultiStackRestoreRejected` 结果模型的源码兼容入口；实际结果语义由 navigation artifact 维护。 */
public typealias PixelMultiStackRestoreRejected =
    com.purride.pixelui.widgets.navigation.PixelMultiStackRestoreResult.Rejected

/** 公开 `PixelNavigatorAliases` 的 `PixelNavigatorPersistentSnapshotSchemaVersion` 配置或运行值。
 *
 * Current schema emitted by the versioned persistent Navigator codec.
 */
public const val PixelNavigatorPersistentSnapshotSchemaVersion: Int =
    com.purride.pixelui.widgets.navigation.PixelNavigatorPersistentSnapshotSchemaVersion

/** 公开 `PixelNavigatorAliases` 的 `PixelNavigatorPersistentSnapshotBundleKey` 配置或运行值。
 *
 * Default Android Bundle key used for versioned persistent Navigator bytes.
 */
public const val PixelNavigatorPersistentSnapshotBundleKey: String =
    com.purride.pixelui.widgets.navigation.PixelNavigatorPersistentSnapshotBundleKey

/** 公开 `PixelNavigatorAliases` 的 `PixelMultiStackSnapshotSchemaVersion` 配置或运行值。
 *
 * Current outer schema grouping independently versioned child Navigator snapshots.
 */
public const val PixelMultiStackSnapshotSchemaVersion: Int =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotSchemaVersion

/** 公开 `PixelNavigatorAliases` 的 `PixelMultiStackSnapshotBundleKey` 配置或运行值。
 *
 * Default Android Bundle key for a complete multi-stack snapshot.
 */
public const val PixelMultiStackSnapshotBundleKey: String =
    com.purride.pixelui.widgets.navigation.PixelMultiStackSnapshotBundleKey

/**
 * 执行 `PixelNavigatorAliases` 的 `pixelRouteDestination` 公开行为；具体参数、返回和副作用见下文。
 *
 * Root-package convenience factory for a reusable typed route destination.
 *
 * Every request made from the returned destination still allocates an independent route entry,
 * state bucket, and result channel.
 */
public fun <A : Any, R> pixelRouteDestination(
    id: String,
    maintainState: Boolean = true,
    transition: PixelRouteTransition? = null,
    transitionBuilder: PixelRouteTransitionBuilder? = null,
    canPop: (PixelRouteEntry<A, R>) -> Boolean = { true },
    onEnter: (PixelRouteEntry<A, R>) -> Unit = {},
    onExit: (PixelRouteEntry<A, R>) -> Unit = {},
    onDispose: (PixelRouteEntry<A, R>) -> Unit = {},
    builder: (BuildContext, PixelRouteEntryScope<A, R>) -> Widget,
): PixelRouteDestination<A, R> {
    return com.purride.pixelui.widgets.navigation.pixelRouteDestination(
        id = id,
        maintainState = maintainState,
        transition = transition,
        transitionBuilder = transitionBuilder,
        canPop = canPop,
        onEnter = onEnter,
        onExit = onExit,
        onDispose = onDispose,
        builder = builder,
    )
}

/** 执行 `PixelNavigatorAliases` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Saves root-package [PixelNavigatorSnapshotEncoded] bytes into [outState].
 */
public fun PixelNavigatorSnapshotEncoded.saveToBundle(
    outState: Bundle,
    key: String = PixelNavigatorPersistentSnapshotBundleKey,
) {
    require(key.isNotBlank()) { "Persistent Navigator snapshot Bundle key must not be blank" }
    outState.putByteArray(key, bytes)
}

/** 查询 `PixelNavigatorAliases` 的 `getPixelNavigatorPersistentSnapshotBytes` 结果，不产生额外状态变更。
 *
 * Reads a defensive copy of persistent Navigator bytes from this Android Bundle.
 */
public fun Bundle.getPixelNavigatorPersistentSnapshotBytes(
    key: String = PixelNavigatorPersistentSnapshotBundleKey,
): ByteArray? {
    require(key.isNotBlank()) { "Persistent Navigator snapshot Bundle key must not be blank" }
    return getByteArray(key)?.copyOf()
}

/** 执行 `PixelNavigatorAliases` 的 `handleTypedDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Parses and applies one typed deep link through root-package navigation aliases.
 */
public fun PixelNavigatorState.handleTypedDeepLink(
    uri: String,
    resolver: PixelTypedDeepLinkResolver,
    mode: PixelTypedDeepLinkNavigationMode = PixelTypedDeepLinkNavigationMode.Push,
): PixelTypedDeepLinkResult {
    return this.handleTypedDeepLinkNavigation(uri, resolver, mode)
}

/** 执行 `PixelNavigatorAliases` 的 `handleTypedDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Applies one already parsed typed [link] through root-package navigation aliases.
 */
public fun PixelNavigatorState.handleTypedDeepLink(
    link: PixelDeepLink,
    resolver: PixelTypedDeepLinkResolver,
    mode: PixelTypedDeepLinkNavigationMode = PixelTypedDeepLinkNavigationMode.Push,
): PixelTypedDeepLinkResult {
    return this.handleTypedDeepLinkNavigation(link, resolver, mode)
}

/** 执行 `PixelNavigatorAliases` 的 `handleTypedDeepLink` 公开行为；具体参数、返回和副作用见下文。
 *
 * Routes a typed link into one named retained stack and optionally selects it after success.
 */
public fun PixelMultiStackNavigatorController.handleTypedDeepLink(
    stackId: String,
    uri: String,
    resolver: PixelTypedDeepLinkResolver,
    mode: PixelTypedDeepLinkNavigationMode = PixelTypedDeepLinkNavigationMode.Push,
    selectStackOnSuccess: Boolean = true,
): PixelTypedDeepLinkResult {
    return this.handleTypedDeepLinkNavigation(
        stackId,
        uri,
        resolver,
        mode,
        selectStackOnSuccess,
    )
}

/** 执行 `PixelNavigatorAliases` 的 `persistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Captures all mounted typed child stacks through their explicit destination registries.
 */
public fun PixelMultiStackNavigatorController.persistentSnapshot(
    registries: Map<String, PixelRouteSnapshotRegistry>,
    codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
): PixelMultiStackSnapshotEncodeResult {
    return this.captureMultiStackSnapshotNavigation(registries, codec)
}

/** 执行 `PixelNavigatorAliases` 的 `restorePersistentSnapshot` 公开行为；具体参数、返回和副作用见下文。
 *
 * Restores every decoded child plan before selecting the saved active stack.
 */
public fun PixelMultiStackNavigatorController.restorePersistentSnapshot(
    snapshot: PixelMultiStackSnapshot,
    registries: Map<String, PixelRouteSnapshotRegistry>,
    codec: PixelNavigatorSnapshotCodec = PixelNavigatorSnapshotCodec(),
): PixelMultiStackRestoreResult {
    return this.restoreMultiStackSnapshotNavigation(snapshot, registries, codec)
}

/** 执行 `PixelNavigatorAliases` 的 `saveToBundle` 公开行为；具体参数、返回和副作用见下文。
 *
 * Saves one complete multi-stack snapshot to an Android Bundle.
 */
public fun PixelMultiStackSnapshot.saveToBundle(
    outState: Bundle,
    key: String = PixelMultiStackSnapshotBundleKey,
) {
    this.saveNavigationSnapshotToBundle(outState, key)
}

/** 查询 `PixelNavigatorAliases` 的 `getPixelMultiStackSnapshot` 结果，不产生额外状态变更。
 *
 * Reads and validates a complete multi-stack snapshot from an Android Bundle.
 */
public fun Bundle.getPixelMultiStackSnapshot(
    key: String = PixelMultiStackSnapshotBundleKey,
): PixelMultiStackSnapshotDecodeResult? {
    return this.getPixelMultiStackSnapshotNavigation(key)
}
