package com.purride.pixelui.widgets.navigation

import android.os.Bundle
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Builder
import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.InheritedWidget
import com.purride.pixelui.Opacity
import com.purride.pixelui.PixelBackHandler
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Stack
import com.purride.pixelui.Widget
import com.purride.pixelui.dependOnInheritedWidgetOfExactType
import com.purride.pixelui.internal.HitTestResult
import com.purride.pixelui.internal.MultiChildRenderObject
import com.purride.pixelui.internal.MultiChildRenderObjectWidget
import com.purride.pixelui.internal.PaintContext
import com.purride.pixelui.internal.PixelClickTarget
import com.purride.pixelui.internal.PixelListTarget
import com.purride.pixelui.internal.PixelPagerTarget
import com.purride.pixelui.internal.PixelSliderTarget
import com.purride.pixelui.internal.PixelTextInputTarget
import com.purride.pixelui.internal.RenderBox
import com.purride.pixelui.internal.RenderConstraints
import com.purride.pixelui.internal.RenderObject
import com.purride.pixelui.internal.RenderSize
import com.purride.pixelui.animation.Curve
import com.purride.pixelui.animation.CurvedAnimation
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelAnimationController
import com.purride.pixelui.animation.PixelAnimationStatus
import com.purride.pixelui.animation.PixelTickerProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import java.util.IdentityHashMap

/**
 * Default [Bundle] key used by [PixelNavigatorState.saveToBundle] and
 * [PixelNavigatorState.restoreFromBundle].
 */
public const val PixelNavigatorBundleKey: String = "com.purride.pixelui.navigator.routeNames"

public data class PixelRoute(
    val name: String,
    val builder: (BuildContext) -> Widget,
    val transition: PixelRouteTransition? = null,
    val canPop: (() -> Boolean)? = null,
    val onEnter: (() -> Unit)? = null,
    val onExit: (() -> Unit)? = null,
    val onDispose: (() -> Unit)? = null,
    val transitionBuilder: PixelRouteTransitionBuilder? = null,
)

public data class PixelNavigatorSnapshot(
    val routeNames: List<String>,
)

/**
 * Saves this route-name snapshot into an Android [Bundle].
 */
public fun PixelNavigatorSnapshot.saveToBundle(
    outState: Bundle,
    key: String = PixelNavigatorBundleKey,
) {
    require(key.isNotBlank()) { "PixelNavigator snapshot Bundle key must not be blank" }
    outState.putStringArrayList(key, ArrayList(routeNames))
}

/**
 * Reads a [PixelNavigatorSnapshot] previously saved into this Android [Bundle].
 */
public fun Bundle.getPixelNavigatorSnapshot(
    key: String = PixelNavigatorBundleKey,
): PixelNavigatorSnapshot? {
    require(key.isNotBlank()) { "PixelNavigator snapshot Bundle key must not be blank" }
    val names = getStringArrayList(key) ?: return null
    if (names.isEmpty()) return null
    return PixelNavigatorSnapshot(routeNames = names.toList())
}

public enum class PixelRouteTransition {
    None,
    Fade,
    SlideHorizontal,
    SlideVertical,
}

public enum class PixelNavigatorOperation {
    Push,
    Pop,
    Replace,
}

/**
 * Builds one frame of a custom route transition.
 *
 * [progress] advances from 0f to 1f. Navigator owns the ticker and completes route disposal
 * only after the custom transition settles.
 */
public fun interface PixelRouteTransitionBuilder {
    public fun build(
        progress: Float,
        operation: PixelNavigatorOperation,
        outgoing: Widget,
        incoming: Widget,
    ): Widget
}

public class PixelNavigatorState internal constructor(initialRoute: PixelRoute) : ChangeNotifier() {
    private val routes = mutableListOf(initialRoute)
    private val routeResultCallbacks = mutableListOf<((Any?) -> Unit)?>(null)
    private val pendingDisposeRoutes = mutableListOf<PixelRoute>()
    private val pendingResultDeliveries = mutableListOf<PendingRouteResultDelivery>()
    private val routeRestorationBuckets = IdentityHashMap<PixelRoute, PixelRouteRestorationBucket>()
    internal var activeTransition: PixelNavigatorTransitionRecord? = null
        private set

    public val canPop: Boolean
        get() = routes.size > 1

    public val currentRoute: PixelRoute
        get() = routes.last()

    public val stack: List<PixelRoute>
        get() = routes.toList()

    init {
        initialRoute.onEnter?.invoke()
    }

    public fun push(route: PixelRoute) {
        pushInternal(route, onResult = null)
    }

    public fun push(
        route: PixelRoute,
        onResult: (Any?) -> Unit,
    ) {
        pushInternal(route, onResult)
    }

    private fun pushInternal(
        route: PixelRoute,
        onResult: ((Any?) -> Unit)?,
    ) {
        val outgoing = currentRoute
        outgoing.onExit?.invoke()
        routes += route
        routeResultCallbacks += onResult
        route.onEnter?.invoke()
        startTransition(
            outgoingRoute = outgoing,
            incomingRoute = route,
            operation = PixelNavigatorOperation.Push,
        )
        notifyListeners()
    }

    public fun pop(): Boolean = pop(result = null)

    public fun pop(result: Any?): Boolean {
        if (!canPop) return false
        if (currentRoute.canPop?.invoke() == false) return false
        val outgoing = routes.removeAt(routes.lastIndex)
        val resultCallback = routeResultCallbacks.removeAt(routeResultCallbacks.lastIndex)
        val incoming = currentRoute
        outgoing.onExit?.invoke()
        incoming.onEnter?.invoke()
        pendingDisposeRoutes += outgoing
        resultCallback?.let { callback ->
            pendingResultDeliveries += PendingRouteResultDelivery(callback, result)
        }
        startTransition(
            outgoingRoute = outgoing,
            incomingRoute = incoming,
            operation = PixelNavigatorOperation.Pop,
        )
        notifyListeners()
        return true
    }

    public fun maybePop(): Boolean = pop()

    public fun maybePop(result: Any?): Boolean = pop(result)

    public fun popToRoot(animated: Boolean = true) {
        if (routes.size <= 1) return
        val outgoing = currentRoute
        val removed = routes.drop(1)
        val removedCallbacks = routeResultCallbacks.drop(1)
        val root = routes.first()
        outgoing.onExit?.invoke()
        routes.clear()
        routes += root
        routeResultCallbacks.clear()
        routeResultCallbacks += null
        root.onEnter?.invoke()
        pendingDisposeRoutes += removed
        removedCallbacks.forEach { callback ->
            callback?.let { pendingResultDeliveries += PendingRouteResultDelivery(it, null) }
        }
        activeTransition = if (animated) {
            startTransition(
                outgoingRoute = outgoing,
                incomingRoute = root,
                operation = PixelNavigatorOperation.Pop,
            )
        } else {
            disposePendingRoutes()
            null
        }
        notifyListeners()
    }

    public fun replace(route: PixelRoute, animated: Boolean = true) {
        val outgoing = currentRoute
        outgoing.onExit?.invoke()
        routes[routes.lastIndex] = route
        route.onEnter?.invoke()
        pendingDisposeRoutes += outgoing
        activeTransition = if (animated) {
            startTransition(
                outgoingRoute = outgoing,
                incomingRoute = route,
                operation = PixelNavigatorOperation.Replace,
            )
        } else {
            disposePendingRoutes()
            null
        }
        notifyListeners()
    }

    public fun snapshot(): PixelNavigatorSnapshot {
        return PixelNavigatorSnapshot(routeNames = routes.map { route -> route.name })
    }

    public fun saveToBundle(
        outState: Bundle,
        key: String = PixelNavigatorBundleKey,
    ) {
        snapshot().saveToBundle(outState, key)
    }

    public fun restore(
        snapshot: PixelNavigatorSnapshot,
        routeRegistry: Map<String, PixelRoute>,
    ) {
        if (snapshot.routeNames.isEmpty()) return
        val restored = snapshot.routeNames.map { name ->
            routeRegistry[name] ?: error("PixelNavigator.restore() missing route '$name' in routeRegistry")
        }
        replaceRouteStack(restored, animated = false)
    }

    public fun restoreFromBundle(
        savedInstanceState: Bundle?,
        routeRegistry: Map<String, PixelRoute>,
        key: String = PixelNavigatorBundleKey,
    ): Boolean {
        val snapshot = savedInstanceState?.getPixelNavigatorSnapshot(key) ?: return false
        restore(snapshot, routeRegistry)
        return true
    }

    public fun handleDeepLink(
        uri: String,
        resolver: PixelDeepLinkResolver,
        animated: Boolean = true,
    ): Boolean {
        return handleDeepLink(PixelDeepLink.parse(uri), resolver, animated)
    }

    public fun handleDeepLink(
        link: PixelDeepLink,
        resolver: PixelDeepLinkResolver,
        animated: Boolean = true,
    ): Boolean {
        val resolved = resolver.resolve(link) ?: return false
        require(resolved.isNotEmpty()) {
            "PixelDeepLinkResolver returned an empty route stack for '${link.rawUri}'"
        }
        replaceRouteStack(resolved, animated)
        return true
    }

    internal fun completeTransition(id: Long) {
        if (activeTransition?.id != id) return
        activeTransition = null
        disposePendingRoutes()
        notifyListeners()
    }

    internal fun restorationBucket(route: PixelRoute): PixelRouteRestorationBucket {
        return routeRestorationBuckets.getOrPut(route) { PixelRouteRestorationBucket() }
    }

    private fun startTransition(
        outgoingRoute: PixelRoute,
        incomingRoute: PixelRoute,
        operation: PixelNavigatorOperation,
    ): PixelNavigatorTransitionRecord {
        if (activeTransition != null) {
            disposePendingRoutes()
        }
        return PixelNavigatorTransitionRecord(
            id = nextTransitionId(),
            outgoingRoute = outgoingRoute,
            incomingRoute = incomingRoute,
            operation = operation,
        ).also { transition ->
            activeTransition = transition
        }
    }

    private fun replaceRouteStack(
        restored: List<PixelRoute>,
        animated: Boolean,
    ) {
        val outgoing = currentRoute
        val incoming = restored.last()
        outgoing.onExit?.invoke()
        routeResultCallbacks.drop(1).forEach { callback ->
            callback?.let { pendingResultDeliveries += PendingRouteResultDelivery(it, null) }
        }
        pendingDisposeRoutes += routes.filter { oldRoute ->
            restored.none { restoredRoute -> restoredRoute === oldRoute }
        }
        routes.clear()
        routes += restored
        routeResultCallbacks.clear()
        repeat(restored.size) { routeResultCallbacks += null }
        incoming.onEnter?.invoke()
        activeTransition = if (animated && outgoing !== incoming) {
            startTransition(
                outgoingRoute = outgoing,
                incomingRoute = incoming,
                operation = PixelNavigatorOperation.Replace,
            )
        } else {
            disposePendingRoutes()
            null
        }
        notifyListeners()
    }

    private fun disposePendingRoutes() {
        val routesToDispose = pendingDisposeRoutes.toList()
        val resultsToDeliver = pendingResultDeliveries.toList()
        pendingDisposeRoutes.clear()
        pendingResultDeliveries.clear()
        routesToDispose.forEach { route ->
            routeRestorationBuckets.remove(route)
            route.onDispose?.invoke()
        }
        resultsToDeliver.forEach { delivery ->
            delivery.callback(delivery.result)
        }
    }

    private fun nextTransitionId(): Long = ++nextTransitionIdValue

    private var nextTransitionIdValue: Long = 0L
}

public class PixelNavigator(
    public val initialRoute: PixelRoute,
    public val vsync: PixelTickerProvider,
    public val transitionDuration: Duration = 200.milliseconds,
    public val defaultTransition: PixelRouteTransition = PixelRouteTransition.SlideHorizontal,
    override val key: Any? = null,
    public val transitionBuilder: PixelRouteTransitionBuilder? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = PixelNavigatorWidgetState()

    public companion object {
        public fun maybeOf(context: BuildContext): PixelNavigatorState? {
            return context.dependOnInheritedWidgetOfExactType<PixelNavigatorScope>()?.navigatorState
        }

        public fun of(context: BuildContext): PixelNavigatorState {
            return maybeOf(context) ?: error("PixelNavigator.of() called with a context that has no PixelNavigator")
        }
    }
}

private class PixelNavigatorWidgetState : State<PixelNavigator>() {
    private lateinit var navigatorState: PixelNavigatorState

    override fun initState() {
        navigatorState = PixelNavigatorState(widget.initialRoute)
    }

    override fun build(context: BuildContext): Widget {
        context.watch(navigatorState)
        val transitionRecord = navigatorState.activeTransition
        val currentRoute = navigatorState.currentRoute
        val child = if (transitionRecord == null) {
            routeChild(currentRoute)
        } else {
            val transition = transitionRecord.incomingRoute.transition ?: widget.defaultTransition
            val incoming = routeChild(transitionRecord.incomingRoute)
            val outgoing = routeChild(transitionRecord.outgoingRoute, suffix = "outgoing")
            val customBuilder = resolveTransitionBuilder(transitionRecord)
            if (customBuilder != null) {
                PixelRouteCustomTransition(
                    id = transitionRecord.id,
                    operation = transitionRecord.operation,
                    duration = widget.transitionDuration,
                    vsync = widget.vsync,
                    builder = customBuilder,
                    onSettled = { navigatorState.completeTransition(transitionRecord.id) },
                    outgoing = outgoing,
                    incoming = incoming,
                )
            } else when (transition) {
                PixelRouteTransition.None -> {
                    navigatorState.completeTransition(transitionRecord.id)
                    incoming
                }
                PixelRouteTransition.Fade -> PixelRouteFadeTransition(
                    id = transitionRecord.id,
                    duration = widget.transitionDuration,
                    vsync = widget.vsync,
                    onSettled = { navigatorState.completeTransition(transitionRecord.id) },
                    outgoing = outgoing,
                    incoming = incoming,
                )
                PixelRouteTransition.SlideHorizontal,
                PixelRouteTransition.SlideVertical,
                -> PixelRouteSlideTransition(
                    id = transitionRecord.id,
                    transition = transition,
                    operation = transitionRecord.operation,
                    duration = widget.transitionDuration,
                    vsync = widget.vsync,
                    onSettled = { navigatorState.completeTransition(transitionRecord.id) },
                    outgoing = outgoing,
                    incoming = incoming,
                )
            }
        }
        return PixelNavigatorScope(
            navigatorState = navigatorState,
            child = PixelBackHandler(
                enabled = navigatorState.canPop,
                onBack = { navigatorState.pop() },
                child = child,
                key = "navigator-back",
            ),
            key = "navigator-scope",
        )
    }

    private fun resolveTransitionBuilder(
        transition: PixelNavigatorTransitionRecord,
    ): PixelRouteTransitionBuilder? {
        val routeBuilder = when (transition.operation) {
            PixelNavigatorOperation.Pop -> transition.outgoingRoute.transitionBuilder
            PixelNavigatorOperation.Push,
            PixelNavigatorOperation.Replace,
            -> transition.incomingRoute.transitionBuilder
        }
        return routeBuilder ?: widget.transitionBuilder
    }

    private fun routeChild(route: PixelRoute, suffix: String = "incoming"): Widget {
        return PixelRouteStorageScope(
            bucket = navigatorState.restorationBucket(route),
            child = Builder(key = "route:${route.name}:$suffix") { routeContext ->
                route.builder(routeContext)
            },
            key = "route-storage:${route.name}:$suffix",
        )
    }
}

private class PixelRouteCustomTransition(
    val id: Long,
    val operation: PixelNavigatorOperation,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val builder: PixelRouteTransitionBuilder,
    val onSettled: () -> Unit,
    val outgoing: Widget,
    val incoming: Widget,
) : StatefulWidget(key = "route-custom:$id") {
    override fun createState(): State<out StatefulWidget> = PixelRouteCustomTransitionState()
}

private class PixelRouteCustomTransitionState : State<PixelRouteCustomTransition>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var settled = false

    override fun initState() {
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = Curves.Step(8))
        controller.addListener {
            if (!settled && controller.status == PixelAnimationStatus.Completed) {
                settled = true
                widget.onSettled()
            }
        }
        controller.forward(from = 0f)
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return widget.builder.build(
            progress = curved.value,
            operation = widget.operation,
            outgoing = widget.outgoing,
            incoming = widget.incoming,
        )
    }
}

private class PixelRouteFadeTransition(
    val id: Long,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val onSettled: () -> Unit,
    val outgoing: Widget,
    val incoming: Widget,
) : StatefulWidget(key = "route-fade:$id") {
    override fun createState(): State<out StatefulWidget> = PixelRouteFadeTransitionState()
}

private class PixelRouteFadeTransitionState : State<PixelRouteFadeTransition>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var settled = false

    override fun initState() {
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = Curves.Step(8))
        controller.addListener {
            if (!settled && controller.status == PixelAnimationStatus.Completed) {
                settled = true
                widget.onSettled()
            }
        }
        controller.forward(from = 0f)
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        val progress = curved.value
        return Stack(
            children = listOf(
                Opacity(opacity = 1f - progress, child = widget.outgoing),
                Opacity(opacity = progress, child = widget.incoming),
            ),
        )
    }
}

private class PixelRouteSlideTransition(
    val id: Long,
    val transition: PixelRouteTransition,
    val operation: PixelNavigatorOperation,
    val duration: Duration,
    val vsync: PixelTickerProvider,
    val onSettled: () -> Unit,
    val outgoing: Widget,
    val incoming: Widget,
) : StatefulWidget(key = "route-slide:$id") {
    override fun createState(): State<out StatefulWidget> = PixelRouteSlideTransitionState()
}

private class PixelRouteSlideTransitionState : State<PixelRouteSlideTransition>() {
    private lateinit var controller: PixelAnimationController
    private lateinit var curved: CurvedAnimation
    private var settled = false

    override fun initState() {
        controller = PixelAnimationController(duration = widget.duration, vsync = widget.vsync)
        curved = CurvedAnimation(parent = controller, curve = Curves.Step(8))
        controller.addListener {
            if (!settled && controller.status == PixelAnimationStatus.Completed) {
                settled = true
                widget.onSettled()
            }
        }
        controller.forward(from = 0f)
    }

    override fun dispose() {
        controller.dispose()
    }

    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        return PixelRouteSlideRenderWidget(
            progress = curved.value,
            transition = widget.transition,
            operation = widget.operation,
            children = listOf(widget.outgoing, widget.incoming),
            key = "route-slide-render:${widget.id}",
        )
    }
}

private class PixelRouteSlideRenderWidget(
    val progress: Float,
    val transition: PixelRouteTransition,
    val operation: PixelNavigatorOperation,
    override val children: List<Widget>,
    override val key: Any? = null,
) : MultiChildRenderObjectWidget(children = children, key = key) {
    override fun createRenderObject(context: BuildContext): RenderObject {
        return RenderRouteSlideTransition(
            progress = progress,
            transition = transition,
            operation = operation,
        )
    }

    override fun updateRenderObject(context: BuildContext, renderObject: RenderObject) {
        (renderObject as RenderRouteSlideTransition).update(
            progress = progress,
            transition = transition,
            operation = operation,
        )
    }
}

private class RenderRouteSlideTransition(
    private var progress: Float,
    private var transition: PixelRouteTransition,
    private var operation: PixelNavigatorOperation,
) : MultiChildRenderObject() {
    fun update(
        progress: Float,
        transition: PixelRouteTransition,
        operation: PixelNavigatorOperation,
    ) {
        if (this.progress == progress && this.transition == transition && this.operation == operation) return
        this.progress = progress
        this.transition = transition
        this.operation = operation
        markNeedsPaint()
    }

    override fun layout(constraints: RenderConstraints) {
        size = RenderSize(width = constraints.maxWidth, height = constraints.maxHeight)
        children.filterIsInstance<RenderBox>().forEach { child ->
            child.layout(RenderConstraints(maxWidth = size.width, maxHeight = size.height))
        }
    }

    override fun paint(context: PaintContext, offsetX: Int, offsetY: Int) {
        val boxes = children.filterIsInstance<RenderBox>()
        val outgoing = boxes.getOrNull(0)
        val incoming = boxes.getOrNull(1) ?: return
        val distance = if (transition == PixelRouteTransition.SlideVertical) size.height else size.width
        val direction = if (operation == PixelNavigatorOperation.Pop) -1 else 1
        val incomingOffset = ((1f - progress) * distance * direction).toInt()
        val outgoingOffset = (-progress * distance * direction).toInt()
        if (transition == PixelRouteTransition.SlideVertical) {
            outgoing?.paint(context, offsetX, offsetY + outgoingOffset)
            incoming.paint(context, offsetX, offsetY + incomingOffset)
        } else {
            outgoing?.paint(context, offsetX + outgoingOffset, offsetY)
            incoming.paint(context, offsetX + incomingOffset, offsetY)
        }
    }

    override fun hitTest(localX: Int, localY: Int, result: HitTestResult) {
        incomingBox()?.hitTest(localX, localY, result)
    }

    override fun collectClickTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelClickTarget>) {
        incomingBox()?.collectClickTargets(offsetX, offsetY, targets)
    }

    override fun collectPagerTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelPagerTarget>) {
        incomingBox()?.collectPagerTargets(offsetX, offsetY, targets)
    }

    override fun collectListTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelListTarget>) {
        incomingBox()?.collectListTargets(offsetX, offsetY, targets)
    }

    override fun collectTextInputTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelTextInputTarget>) {
        incomingBox()?.collectTextInputTargets(offsetX, offsetY, targets)
    }

    override fun collectSliderTargets(offsetX: Int, offsetY: Int, targets: MutableList<PixelSliderTarget>) {
        incomingBox()?.collectSliderTargets(offsetX, offsetY, targets)
    }

    private fun incomingBox(): RenderBox? = children.filterIsInstance<RenderBox>().getOrNull(1)
}

private class PixelNavigatorScope(
    val navigatorState: PixelNavigatorState,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelNavigatorScope)?.navigatorState !== navigatorState
    }
}

internal class PixelRouteStorageScope(
    val bucket: PixelRouteRestorationBucket,
    override val child: Widget,
    override val key: Any? = null,
) : InheritedWidget(child = child, key = key) {
    override fun updateShouldNotify(oldWidget: InheritedWidget): Boolean {
        return (oldWidget as? PixelRouteStorageScope)?.bucket !== bucket
    }
}

internal class PixelRouteRestorationBucket {
    private val scrollStates = mutableMapOf<String, com.purride.pixelui.state.PixelListSavedState>()

    fun readScrollState(restorationId: String): com.purride.pixelui.state.PixelListSavedState? {
        return scrollStates[restorationId]
    }

    fun writeScrollState(
        restorationId: String,
        savedState: com.purride.pixelui.state.PixelListSavedState,
    ) {
        scrollStates[restorationId] = savedState
    }
}

internal data class PixelNavigatorTransitionRecord(
    val id: Long,
    val outgoingRoute: PixelRoute,
    val incomingRoute: PixelRoute,
    val operation: PixelNavigatorOperation,
)

private data class PendingRouteResultDelivery(
    val callback: (Any?) -> Unit,
    val result: Any?,
)
