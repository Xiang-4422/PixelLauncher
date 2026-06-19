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
import com.purride.pixelui.PixelRoute
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.Row
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
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
    override val summary = "PixelNavigator 路由栈、transition、deep link 与 snapshot"
    override val category = DemoCatalog.navigation
    override val tags = setOf("navigator", "route", "deeplink", "snapshot", "transition")
    override val apis = setOf(
        "PixelNavigator",
        "PixelNavigatorState",
        "PixelNavigatorOperation",
        "PixelNavigatorSnapshot",
        "PixelRoute",
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
                        ),
                        spacing = 1,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
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

private fun navMetric(label: String, value: String): Widget =
    Row(
        children = listOf(
            Container(width = 52, child = Text(label, style = TextStyle(color = Muted))),
            Text(value, style = TextStyle(color = PixelColor.White)),
        ),
        spacing = 2,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
    )
