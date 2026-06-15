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
import com.purride.pixelui.widgets.navigation.PixelDeepLinkResolver
import com.purride.pixelui.widgets.navigation.PixelRoute
import com.purride.pixelui.widgets.navigation.PixelRouteScrollRestoration
import com.purride.pixelui.widgets.navigation.PixelRouteTransitionBuilder
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object NavigatorStackScene : DemoScene {
    override val id = "navigator_stack"
    override val title = "NAVIGATOR"
    override val description = "Navigator stack, results, deep links, guard and route restore"

    override fun build(env: DemoEnv): Widget {
        val vsync = PixelTickerProvider(env.hostView.frameScheduler)
        val resultState = DemoResultState()
        return PixelNavigator(
            initialRoute = route("HOME", 0, resultState),
            vsync = vsync,
            key = "navigator-demo",
        )
    }

    private fun route(label: String, depth: Int, resultState: DemoResultState): PixelRoute {
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
                        Text(
                            "result=${resultState.value}",
                            style = TextStyle(color = PixelColor.fromRgb(180, 180, 180)),
                        ),
                        OutlinedButton(
                            text = "PUSH",
                            onPressed = { nav.push(route("D${depth + 1}", depth + 1, resultState)) },
                        ),
                        OutlinedButton(
                            text = "LOCK",
                            onPressed = { nav.push(route("LOCK", depth + 1, resultState)) },
                        ),
                        OutlinedButton(
                            text = "SCROLL",
                            onPressed = { nav.push(scrollRoute(depth + 1, resultState)) },
                        ),
                        OutlinedButton(
                            text = "CUSTOM",
                            onPressed = { nav.push(customRoute(depth + 1)) },
                        ),
                        OutlinedButton(
                            text = "RESULT",
                            onPressed = {
                                nav.push(resultRoute()) { result ->
                                    resultState.value = result?.toString() ?: "null"
                                }
                            },
                        ),
                        OutlinedButton(
                            text = "DEEP",
                            onPressed = {
                                nav.handleDeepLink(
                                    uri = "pixel://demo/deep?from=$label",
                                    resolver = PixelDeepLinkResolver { link ->
                                        if (link.host == "demo" && link.pathSegments == listOf("deep")) {
                                            listOf(
                                                route("HOME", 0, resultState),
                                                deepLinkRoute(link.queryParameter("from").orEmpty()),
                                            )
                                        } else {
                                            null
                                        }
                                    },
                                )
                            },
                        ),
                        OutlinedButton(
                            text = "REPL",
                            onPressed = { nav.replace(route("R$depth", depth, resultState)) },
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

    private fun resultRoute(): PixelRoute {
        return PixelRoute(
            name = "RESULT",
            builder = { context ->
                val nav = PixelNavigator.of(context)
                Column(
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                    spacing = 2,
                    children = listOf(
                        Text("CHOOSE RESULT", style = TextStyle(color = PixelColor.White)),
                        OutlinedButton(text = "SAVE", onPressed = { nav.pop("SAVE") }),
                        OutlinedButton(text = "CANCEL", onPressed = { nav.pop("CANCEL") }),
                    ),
                )
            },
        )
    }

    private fun deepLinkRoute(source: String): PixelRoute {
        return PixelRoute(
            name = "DEEP",
            builder = { context ->
                val nav = PixelNavigator.of(context)
                Column(
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                    spacing = 2,
                    children = listOf(
                        Text("DEEP LINK", style = TextStyle(color = PixelColor.fromRgb(255, 200, 60))),
                        Text("FROM $source", style = TextStyle(color = PixelColor.fromRgb(180, 180, 180))),
                        OutlinedButton(text = "POP", onPressed = { nav.pop() }),
                    ),
                )
            },
        )
    }

    private fun scrollRoute(depth: Int, resultState: DemoResultState): PixelRoute {
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
                                    onPressed = { nav.push(route("FROM_SCROLL", depth + 1, resultState)) },
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

    private class DemoResultState(
        var value: String = "NONE",
    )
}
