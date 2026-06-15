package com.purride.pixeldemo.showcase.composition

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.ListView
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Transform
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.widgets.navigation.PixelNavigator
import com.purride.pixelui.widgets.navigation.PixelNavigatorOperation
import com.purride.pixelui.widgets.navigation.PixelRoute
import com.purride.pixelui.widgets.navigation.PixelRouteScrollRestoration
import com.purride.pixelui.widgets.navigation.PixelRouteTransitionBuilder
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
                            text = "CUSTOM",
                            onPressed = { nav.push(customRoute(depth + 1)) },
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

    private fun customRoute(depth: Int): PixelRoute {
        return PixelRoute(
            name = "CUSTOM$depth",
            builder = { context ->
                val nav = PixelNavigator.of(context)
                Column(
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                    spacing = 2,
                    children = listOf(
                        Text("CUSTOM TRANSITION", style = TextStyle(color = PixelColor.fromRgb(80, 220, 180))),
                        OutlinedButton(text = "POP", onPressed = { nav.pop() }),
                    ),
                )
            },
            transitionBuilder = PixelRouteTransitionBuilder { progress, operation, outgoing, incoming ->
                val direction = if (operation == PixelNavigatorOperation.Pop) -1 else 1
                val incomingX = ((1f - progress) * 16 * direction).toInt()
                val outgoingX = (-progress * 8 * direction).toInt()
                Stack(
                    children = listOf(
                        Transform.translate(IntOffset(outgoingX, 0), outgoing),
                        Transform.translate(IntOffset(incomingX, 0), incoming),
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
