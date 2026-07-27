package com.purride.pixelui

/**
 * 测试专用的类型化根/子路由请求工厂。
 *
 * 只覆盖不关心参数与结果类型的导航用例：目标参数固定为 [Unit]，结果类型保持 `Any?`，
 * 因此同一个请求可以重复入栈并各自持有独立 entry、状态桶与结果通道。
 */
internal fun testRouteRequest(
    /** 目标标识，同时作为诊断与快照使用的稳定 ID。 */
    name: String,
    /** 内建转场覆盖，`null` 表示沿用 Navigator 默认值。 */
    transition: PixelRouteTransition? = null,
    /** 自定义转场覆盖，`null` 表示沿用 Navigator 回退实现。 */
    transitionBuilder: PixelRouteTransitionBuilder? = null,
    /** 非前台 entry 是否保留已构建子树与路由局部状态。 */
    maintainState: Boolean = true,
    /** pop 守卫，返回 `false` 时拒绝出栈。 */
    canPop: (() -> Boolean)? = null,
    /** entry 进入活跃态时的回调。 */
    onEnter: (() -> Unit)? = null,
    /** entry 离开活跃态时的回调。 */
    onExit: (() -> Unit)? = null,
    /** entry 终态释放时恰好触发一次的回调。 */
    onDispose: (() -> Unit)? = null,
    /** 子树构建器，不需要访问类型化 entry scope。 */
    builder: (BuildContext) -> Widget,
): PixelRouteRequest<Unit, Any?> {
    return PixelRouteRequest(
        destination = testRouteDestination(
            name = name,
            transition = transition,
            transitionBuilder = transitionBuilder,
            maintainState = maintainState,
            canPop = canPop,
            onEnter = onEnter,
            onExit = onExit,
            onDispose = onDispose,
            builder = builder,
        ),
        arguments = Unit,
    )
}

/** 构造与 [testRouteRequest] 同语义的可复用目标，供需要显式共享目标的用例使用。 */
internal fun testRouteDestination(
    /** 目标标识，同时作为诊断与快照使用的稳定 ID。 */
    name: String,
    /** 内建转场覆盖，`null` 表示沿用 Navigator 默认值。 */
    transition: PixelRouteTransition? = null,
    /** 自定义转场覆盖，`null` 表示沿用 Navigator 回退实现。 */
    transitionBuilder: PixelRouteTransitionBuilder? = null,
    /** 非前台 entry 是否保留已构建子树与路由局部状态。 */
    maintainState: Boolean = true,
    /** pop 守卫，返回 `false` 时拒绝出栈。 */
    canPop: (() -> Boolean)? = null,
    /** entry 进入活跃态时的回调。 */
    onEnter: (() -> Unit)? = null,
    /** entry 离开活跃态时的回调。 */
    onExit: (() -> Unit)? = null,
    /** entry 终态释放时恰好触发一次的回调。 */
    onDispose: (() -> Unit)? = null,
    /** 子树构建器，不需要访问类型化 entry scope。 */
    builder: (BuildContext) -> Widget,
): PixelRouteDestination<Unit, Any?> {
    return pixelRouteDestination(
        id = name,
        maintainState = maintainState,
        transition = transition,
        transitionBuilder = transitionBuilder,
        canPop = { canPop?.invoke() ?: true },
        onEnter = { onEnter?.invoke() },
        onExit = { onExit?.invoke() },
        onDispose = { onDispose?.invoke() },
        builder = { context, _ -> builder(context) },
    )
}

/** 把类型化结果收敛为旧断言常用的可空值，成功取值、取消统一为 `null`。 */
internal fun PixelRouteOutcome<Any?>.valueOrNull(): Any? = when (this) {
    is PixelRouteOutcome.Success -> value
    is PixelRouteOutcome.Cancelled -> null
}
