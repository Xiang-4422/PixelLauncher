package com.purride.pixeldemo.showcase.composition

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.ListView
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.widgets.navigation.PixelNavigator
import com.purride.pixelui.widgets.navigation.PixelRoute
import com.purride.pixelui.widgets.navigation.PixelRouteScrollRestoration
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object NavigatorStackScene : DemoScene {
    override val id = "navigator_stack"
    override val title = "NAVIGATOR"
    override val description = "Navigator stack, guard, transitions and route scroll restore"

    override fun build(env: DemoEnv): Widget {
        val vsync = PixelTickerProvider(env.hostView.frameScheduler)
        return PixelNavigator(
            initialRoute = route("HOME", 0),
            vsync = vsync,
            key = "navigator-demo",
        )
    }

    private fun route(label: String, depth: Int): PixelRoute {
        return PixelRoute(
            name = label,
            canPop = { label != "LOCK" },
            builder = { context ->
                val nav = PixelNavigator.of(context)
                Column(
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                    spacing = 2,
                    children = listOf(
                        Text("ROUTE $label", style = TextStyle(color = PixelColor.White)),
                        OutlinedButton(
                            text = "PUSH",
                            onPressed = { nav.push(route("D${depth + 1}", depth + 1)) },
                        ),
                        OutlinedButton(
                            text = "LOCK",
                            onPressed = { nav.push(route("LOCK", depth + 1)) },
                        ),
                        OutlinedButton(
                            text = "SCROLL",
                            onPressed = { nav.push(scrollRoute(depth + 1)) },
                        ),
                        OutlinedButton(
                            text = "REPL",
                            onPressed = { nav.replace(route("R$depth", depth)) },
                        ),
                        OutlinedButton(
                            text = "ROOT",
                            onPressed = { nav.popToRoot() },
                        ),
                        OutlinedButton(
                            text = "POP",
                            onPressed = { nav.maybePop() },
                        ),
                        SizedBox(height = 1),
                        Text("canPop=${nav.canPop}", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                    ),
                )
            },
        )
    }

    private fun scrollRoute(depth: Int): PixelRoute {
        val controller = PixelListController()
        val state = controller.create()
        return PixelRoute(
            name = "SCROLL$depth",
            builder = { context ->
                val nav = PixelNavigator.of(context)
                PixelRouteScrollRestoration(
                    restorationId = "feed",
                    state = state,
                    controller = controller,
                    child = ListView(
                        items = buildList {
                            add(Text("SCROLL, PUSH, POP", style = TextStyle(color = PixelColor.White)))
                            add(
                                OutlinedButton(
                                    text = "DETAIL",
                                    onPressed = { nav.push(route("FROM_SCROLL", depth + 1)) },
                                ),
                            )
                            repeat(24) { index ->
                                add(Text("RESTORE ROW $index"))
                            }
                        },
                        state = state,
                        controller = controller,
                        spacing = 1,
                    ),
                )
            },
        )
    }
}
