package com.purride.pixeldemo.showcase

import android.os.Bundle
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorOperation
import com.purride.pixelui.PixelMultiStackNavigator
import com.purride.pixelui.PixelMultiStackNavigatorController
import com.purride.pixelui.PixelNavigatorPersistentSnapshotSchemaVersion
import com.purride.pixelui.PixelNavigatorStack
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelRouteStateKey
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.PixelDeepLinkArgumentDecoder
import com.purride.pixelui.PixelDeepLinkArgumentFailure
import com.purride.pixelui.PixelDeepLinkArgumentFailureReason
import com.purride.pixelui.PixelDeepLinkDecodeRejected
import com.purride.pixelui.PixelDeepLinkDecoded
import com.purride.pixelui.PixelTypedDeepLinkMatcher
import com.purride.pixelui.PixelTypedDeepLinkResolver
import com.purride.pixelui.PixelTypedDeepLinkRoute
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.handleTypedDeepLink
import com.purride.pixelui.pixelRouteDestination
import com.purride.pixelui.widgets.navigation.PixelDeepLink
import com.purride.pixelui.widgets.navigation.PixelNavigatorSnapshot
import com.purride.pixelui.widgets.navigation.getPixelNavigatorSnapshot
import com.purride.pixelui.widgets.navigation.saveToBundle
import com.purride.pixeldemo.catalog.DemoCatalog
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.Accent
import com.purride.pixeldemo.scaffold.Blue
import com.purride.pixeldemo.scaffold.ComponentShowcaseScaffold
import com.purride.pixeldemo.scaffold.Cyan
import com.purride.pixeldemo.scaffold.DemoEnv
import com.purride.pixeldemo.scaffold.Green
import com.purride.pixeldemo.scaffold.Muted
import com.purride.pixeldemo.scaffold.Pink
import com.purride.pixeldemo.scaffold.Purple
import com.purride.pixeldemo.scaffold.samplePanel
import com.purride.pixeldemo.scaffold.sectionTitle
import kotlin.time.Duration.Companion.milliseconds

object NavigationDeepDiveScene : DemoScene {
    override val id = "deep_navigation_runtime"
    override val title = "导航运行时"
    override val summary = "RouteEntry、版本化恢复、多返回栈、类型安全 deep link 与 predictive back"
    override val category = DemoCatalog.navigation
    override val tags = setOf(
        "navigator",
        "route-entry",
        "typed-result",
        "deeplink",
        "snapshot",
        "multi-stack",
        "predictive-back",
    )
    override val apis = setOf(
        "PixelNavigator",
        "PixelNavigatorState",
        "PixelNavigatorOperation",
        "PixelNavigatorSnapshot",
        "PixelNavigatorSnapshotCodec",
        "PixelRouteSnapshotRegistry",
        "PixelMultiStackNavigator",
        "PixelMultiStackNavigatorController",
        "PixelTypedNavigatorStack",
        "PixelTypedDeepLinkResolver",
        "PixelPredictiveBackEvent",
        "PixelRoute",
        "PixelRouteDestination",
        "PixelRouteEntry",
        "PixelRouteRequest",
        "PixelRouteOutcome",
        "PixelRouteStateBucket",
        "PixelRouteTransition",
        "PixelRouteTransitionBuilder",
        "PixelRouteScrollRestoration",
        "PixelDeepLink",
    )
    override val isFullScreen = true

    override fun build(env: DemoEnv): Widget =
        ComponentShowcaseScaffold(item = this, env = env, body = body(env))

    private fun body(env: DemoEnv): Widget {
        val saved = PixelNavigatorSnapshot(listOf("root", "details"))
        val bundle = Bundle()
        saved.saveToBundle(bundle)
        val restored = bundle.getPixelNavigatorSnapshot()
        val deepLink = PixelDeepLink.parse("pixel://demo/details?id=42#panel")

        return Column(
            children = listOf(
                sectionTitle("路由栈"),
                samplePanel(
                    title = "Push / pop / replace",
                    color = Blue,
                    child = Container(
                        height = 72,
                        borderColor = Blue,
                        child = PixelNavigator(
                            initialRoute = rootRoute(),
                            vsync = env.vsync,
                            transitionDuration = 180.milliseconds,
                            defaultTransition = PixelRouteTransition.SlideHorizontal,
                        ),
                    ),
                ),
                samplePanel(
                    title = "Snapshot / Bundle / DeepLink",
                    color = Purple,
                    child = Column(
                        children = listOf(
                            navMetric("snapshot", saved.routeNames.joinToString(">")),
                            navMetric("restored", restored?.routeNames?.joinToString(">") ?: "<none>"),
                            navMetric("deeplink", "${deepLink.host}/${deepLink.pathSegments.joinToString("/")}?id=${deepLink.queryParameter("id")}"),
                            navMetric("operation", PixelNavigatorOperation.Push.name),
                            navMetric(
                                "schema",
                                "typed-v$PixelNavigatorPersistentSnapshotSchemaVersion",
                            ),
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                ),
                samplePanel(
                    title = "Retained multi-stack",
                    color = Cyan,
                    child = Container(
                        height = 56,
                        borderColor = Cyan,
                        child = multiStackDemo(env),
                    ),
                ),
            ),
            spacing = 4,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }
}

private fun rootRoute(): PixelRoute {
    // Shared typed key demonstrates that every concrete entry still receives a separate bucket.
    val visitKey = PixelRouteStateKey<Int>("visit")
    // Reusable destination is pushed with different arguments while preserving compile-time result typing.
    val typedDetails = pixelRouteDestination<String, String?>(
        id = "typed-details",
        maintainState = true,
        transition = PixelRouteTransition.Fade,
    ) { context, scope ->
        if (visitKey !in scope.stateBucket) {
            scope.stateBucket.write(visitKey, 1)
        }
        routePanel(
            title = "TYPED ${scope.arguments}",
            color = Green,
            stackLabel = "${entryStackLabel(context)}  bucket=${scope.stateBucket.read(visitKey)}",
            actions = listOf(
                OutlinedButton(
                    text = "SUCCESS NULL",
                    onPressed = { scope.complete(null) },
                    borderColor = Green,
                ),
                OutlinedButton(
                    text = "CANCEL",
                    onPressed = { scope.cancel() },
                    borderColor = Accent,
                ),
            ),
        )
    }
    // Matcher and decoder are paired with typedDetails, so invalid IDs never mutate the stack.
    val typedDeepLinks = PixelTypedDeepLinkResolver(
        listOf(
            PixelTypedDeepLinkRoute(
                destination = typedDetails,
                matcher = PixelTypedDeepLinkMatcher { link ->
                    link.scheme == "pixel" &&
                        link.host == "demo" &&
                        link.pathSegments == listOf("details")
                },
                argumentDecoder = PixelDeepLinkArgumentDecoder { link ->
                    val id = link.queryParameter("id")
                    if (id.isNullOrBlank()) {
                        PixelDeepLinkDecodeRejected(
                            PixelDeepLinkArgumentFailure(
                                reason = PixelDeepLinkArgumentFailureReason.Missing,
                                parameterName = "id",
                                message = "details deep link requires id",
                            ),
                        )
                    } else {
                        PixelDeepLinkDecoded(id)
                    }
                },
            ),
        ),
    )
    lateinit var details: PixelRoute
    lateinit var alt: PixelRoute
    val root = PixelRoute(
        name = "root",
        transition = PixelRouteTransition.None,
        builder = { context ->
            routePanel(
                title = "ROOT",
                color = Cyan,
                stackLabel = stackLabel(context),
                actions = listOf(
                    OutlinedButton(
                        text = "PUSH",
                        onPressed = { PixelNavigator.of(context).push(details) },
                        borderColor = Accent,
                    ),
                    OutlinedButton(
                        text = "TYPED A",
                        onPressed = {
                            PixelNavigator.of(context).push(
                                PixelRouteRequest(typedDetails, "A"),
                            )
                        },
                        borderColor = Green,
                    ),
                    OutlinedButton(
                        text = "TYPED B",
                        onPressed = {
                            PixelNavigator.of(context).push(
                                PixelRouteRequest(typedDetails, "B"),
                            )
                        },
                        borderColor = Green,
                    ),
                    OutlinedButton(
                        text = "LINK 42",
                        onPressed = {
                            PixelNavigator.of(context).handleTypedDeepLink(
                                "pixel://demo/details?id=42",
                                typedDeepLinks,
                            )
                        },
                        borderColor = Purple,
                    ),
                    OutlinedButton(
                        text = "REPLACE",
                        onPressed = { PixelNavigator.of(context).replace(alt) },
                        borderColor = Pink,
                    ),
                ),
            )
        },
    )
    details = PixelRoute(
        name = "details",
        transition = PixelRouteTransition.Fade,
        builder = { context ->
            routePanel(
                title = "DETAILS",
                color = Green,
                stackLabel = stackLabel(context),
                actions = listOf(
                    OutlinedButton(
                        text = "POP",
                        onPressed = { PixelNavigator.of(context).pop("done") },
                        borderColor = Green,
                    ),
                    OutlinedButton(
                        text = "ROOT",
                        onPressed = { PixelNavigator.of(context).popToRoot() },
                        borderColor = Accent,
                    ),
                ),
            )
        },
    )
    alt = PixelRoute(
        name = "alternate",
        transition = PixelRouteTransition.SlideVertical,
        builder = { context ->
            routePanel(
                title = "ALT",
                color = Pink,
                stackLabel = stackLabel(context),
                actions = listOf(
                    OutlinedButton(
                        text = "RESTORE",
                        onPressed = { PixelNavigator.of(context).replace(root, animated = false) },
                        borderColor = Pink,
                    ),
                ),
            )
        },
    )
    return root
}

/** Builds two always-mounted bottom-navigation-style stacks with isolated back histories. */
private fun multiStackDemo(env: DemoEnv): Widget {
    // Demo-local controller owns the selected tab and both independent histories.
    val controller = PixelMultiStackNavigatorController(initialStackId = "home")
    // Home root exposes the explicit tab-switch operation.
    val home = PixelRoute(
        name = "home",
        transition = PixelRouteTransition.None,
        builder = {
            routePanel(
                title = "TAB HOME",
                color = Cyan,
                stackLabel = "inactive tab remains mounted",
                actions = listOf(
                    OutlinedButton(
                        text = "SETTINGS",
                        onPressed = { controller.selectStack("settings") },
                        borderColor = Purple,
                    ),
                ),
            )
        },
    )
    // Settings root proves switching back does not recreate the hidden home Navigator.
    val settings = PixelRoute(
        name = "settings",
        transition = PixelRouteTransition.None,
        builder = {
            routePanel(
                title = "TAB SETTINGS",
                color = Purple,
                stackLabel = "back returns to initial tab",
                actions = listOf(
                    OutlinedButton(
                        text = "HOME",
                        onPressed = { controller.selectStack("home") },
                        borderColor = Cyan,
                    ),
                ),
            )
        },
    )
    return PixelMultiStackNavigator(
        stacks = listOf(
            PixelNavigatorStack("home", home),
            PixelNavigatorStack("settings", settings),
        ),
        controller = controller,
        vsync = env.vsync,
        defaultTransition = PixelRouteTransition.None,
    )
}

private fun routePanel(
    title: String,
    color: PixelColor,
    stackLabel: String,
    actions: List<Widget>,
): Widget =
    Container(
        padding = EdgeInsets.all(3),
        borderColor = color,
        child = Column(
            children = listOf(
                Text(title, style = TextStyle(color = color)),
                Text(stackLabel, style = TextStyle(color = Muted)),
                Row(children = actions, spacing = 2),
            ),
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )

private fun stackLabel(context: BuildContext): String {
    return PixelNavigator.of(context).stack.joinToString(">") { it.name }
}

/** Formats destination and entry identity so duplicate typed pushes remain visibly distinct. */
private fun entryStackLabel(context: BuildContext): String {
    return PixelNavigator.of(context).entries.joinToString(">") { entry ->
        "${entry.destination.id}#${entry.id.value}"
    }
}

private fun navMetric(label: String, value: String): Widget =
    Row(
        children = listOf(
            Container(width = 52, child = Text(label, style = TextStyle(color = Muted))),
            Text(value, style = TextStyle(color = PixelColor.White)),
        ),
        spacing = 2,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )
