package com.purride.pixeldemo.showcase.templates

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object TplFileBrowserScene : DemoScene {
    override val id = "tpl_file_browser"
    override val title = "模板 · 文件浏览器"
    override val description = "面包屑 + 列表 + 进入下级 — scene 内部自管导航栈"

    override fun build(env: DemoEnv): Widget = TplFileBrowserWidget()
}

private data class Node(val name: String, val children: List<Node> = emptyList())

private val tree = Node(
    "/", listOf(
        Node("home", listOf(
            Node("user", listOf(
                Node("docs", listOf(Node("readme.md"), Node("design.md"))),
                Node("pictures", listOf(Node("pixel.png"), Node("logo.png"))),
                Node("music", listOf(Node("track1.mp3"), Node("track2.mp3"), Node("track3.mp3"))),
            )),
            Node("guest"),
        )),
        Node("etc", listOf(Node("hosts"), Node("fstab"))),
        Node("var", listOf(
            Node("log", listOf(Node("system.log"), Node("kernel.log"))),
        )),
        Node("usr", listOf(Node("bin"), Node("lib"), Node("share"))),
    ),
)

private class TplFileBrowserWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TplFileBrowserState()

    class TplFileBrowserState : State<TplFileBrowserWidget>() {
        private val listState = PixelListState()
        private val listCtrl = ScrollController()
        private var stack: MutableList<Node> = mutableListOf(tree)

        private val current: Node get() = stack.last()

        private fun enter(node: Node) {
            if (node.children.isEmpty()) return
            setState { stack.add(node) }
        }

        private fun pop() {
            if (stack.size <= 1) return
            setState { stack.removeAt(stack.size - 1) }
        }

        override fun build(context: BuildContext): Widget {
            val crumb = stack.joinToString(" / ") { it.name }
            val node = current
            val isLeafView = node.children.isEmpty()

            return Column(
                children = listOf(
                    Container(
                        fillTone = PixelTone.OFF,
                        borderTone = PixelTone.ON,
                        child = Padding(
                            child = Row(
                                children = listOf(
                                    OutlinedButton(
                                        "←",
                                        onPressed = if (stack.size > 1) ::pop else null,
                                    ),
                                    SizedBox(width = 4),
                                    Expanded(
                                        child = Text(crumb, style = TextStyle.Accent, softWrap = true),
                                    ),
                                ),
                                spacing = 2,
                            ),
                            horizontal = 4, vertical = 3,
                        ),
                    ),
                    Expanded(
                        child = if (isLeafView) {
                            Padding(
                                child = Column(
                                    children = listOf(
                                        Text("文件: ${node.name}", style = TextStyle.Accent),
                                        SizedBox(height = 4),
                                        Text("(叶节点，无下级)", style = TextStyle.Default),
                                    ),
                                    spacing = 2,
                                    crossAxisAlignment = CrossAxisAlignment.START,
                                ),
                                all = 4,
                            )
                        } else {
                            ListViewBuilder(
                                state = listState,
                                controller = listCtrl,
                                itemCount = node.children.size,
                                itemBuilder = { i ->
                                    val child = node.children[i]
                                    val isDir = child.children.isNotEmpty()
                                    GestureDetector(
                                        onTap = { enter(child) },
                                        child = Padding(
                                            child = Row(
                                                children = listOf(
                                                    Text(
                                                        if (isDir) "[DIR]" else "[FILE]",
                                                        style = if (isDir) TextStyle.Accent else TextStyle.Default,
                                                    ),
                                                    SizedBox(width = 4),
                                                    Expanded(
                                                        child = Text(child.name, style = TextStyle.Default),
                                                    ),
                                                    if (isDir) Text(">", style = TextStyle.Accent)
                                                    else Text("·", style = TextStyle.Default),
                                                ),
                                                spacing = 2,
                                            ),
                                            horizontal = 4, vertical = 3,
                                        ),
                                    )
                                },
                            )
                        },
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )
        }
    }
}
