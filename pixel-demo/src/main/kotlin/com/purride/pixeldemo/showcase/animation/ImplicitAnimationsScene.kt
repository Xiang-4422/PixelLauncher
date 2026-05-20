package com.purride.pixeldemo.showcase.animation

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Stack
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.Curves
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.host.PixelFrameScheduler
import com.purride.pixelui.widgets.animated.AnimatedContainer
import com.purride.pixelui.widgets.animated.AnimatedOpacity
import com.purride.pixelui.widgets.animated.AnimatedPositioned
import com.purride.pixelui.widgets.animated.AnimatedSwitcher
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.time.Duration.Companion.milliseconds

object ImplicitAnimationsScene : DemoScene {
    override val id = "implicit_animations"
    override val title = "Implicit Animations"
    override val description = "可展开通知列表：AnimatedContainer + AnimatedOpacity + AnimatedSwitcher 协同"

    override fun build(env: DemoEnv): Widget =
        ImplicitAnimationsWidget(frameScheduler = env.hostView.frameScheduler)
}

private data class Notification(
    val id: String,
    val title: String,
    val detail: String,
    val read: Boolean = false,
)

private val INITIAL_NOTIFICATIONS = listOf(
    Notification("n1", "BUILD OK", "pixel-engine:assembleDebug passed in 11s", read = false),
    Notification("n2", "TEST PASS", "30 of 30 unit tests green. No regressions.", read = false),
    Notification("n3", "DEPLOY", "Demo APK pushed to device. Ready to test.", read = true),
    Notification("n4", "LINT WARN", "2 unused imports in AnimationCoreScene.kt", read = false),
)

// badge positions: left edge when unread > 0, right edge (hidden) when all read
private const val BADGE_VISIBLE_LEFT = 0
private const val BADGE_HIDDEN_LEFT = 140   // off-screen right

private class ImplicitAnimationsWidget(
    private val frameScheduler: PixelFrameScheduler,
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ImplicitAnimationsState()

    inner class ImplicitAnimationsState : State<ImplicitAnimationsWidget>() {
        private lateinit var vsync: PixelTickerProvider
        private var notifications = INITIAL_NOTIFICATIONS
        private var expandedId: String? = null

        override fun initState() {
            vsync = PixelTickerProvider(widget.frameScheduler)
        }

        private fun toggleExpand(id: String) {
            setState {
                expandedId = if (expandedId == id) null else id
                notifications = notifications.map {
                    if (it.id == id) it.copy(read = true) else it
                }
            }
        }

        private fun markAllRead() {
            setState {
                notifications = notifications.map { it.copy(read = true) }
                expandedId = null
            }
        }

        private fun reset() {
            setState {
                notifications = INITIAL_NOTIFICATIONS
                expandedId = null
            }
        }

        override fun build(context: BuildContext): Widget {
            val unread = notifications.count { !it.read }

            return Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    // ── header ───────────────────────────────────────────────
                    notificationHeader(unread),

                    // ── notification rows ─────────────────────────────────────
                    Expanded(
                        child = Column(
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            spacing = 1,
                            children = notifications.map { notif ->
                                notificationRow(notif)
                            },
                        ),
                    ),

                    // ── footer controls ──────────────────────────────────────
                    Padding(
                        child = Row(
                            spacing = 4,
                            children = listOf(
                                OutlinedButton(
                                    text = "MARK ALL READ",
                                    onPressed = if (unread > 0) ::markAllRead else null,
                                ),
                                OutlinedButton(
                                    text = "RESET",
                                    onPressed = ::reset,
                                ),
                            ),
                        ),
                        horizontal = 4,
                        vertical = 4,
                    ),
                ),
            )
        }

        private fun notificationHeader(unread: Int): Widget {
            return Padding(
                child = Stack(
                    children = listOf(
                        Row(
                            mainAxisSize = MainAxisSize.MAX,
                            mainAxisAlignment = MainAxisAlignment.START,
                            children = listOf(
                                Text("NOTIFICATIONS", style = TextStyle.Accent),
                                Expanded(child = SizedBox()),
                            ),
                        ),
                        // Animated badge slides in from right when there are unreads
                        AnimatedPositioned(
                            duration = 300.milliseconds,
                            vsync = vsync,
                            curve = Curves.EaseOut,
                            right = if (unread > 0) 0 else -20,
                            top = 0,
                            child = AnimatedSwitcher(
                                key = "badge",
                                duration = 200.milliseconds,
                                vsync = vsync,
                                child = Container(
                                    key = "badge-$unread",
                                    width = 16, height = 8,
                                    fillTone = PixelTone.ACCENT,
                                    borderTone = null,
                                    child = Center(
                                        child = Text("$unread", style = TextStyle.Default),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                horizontal = 4,
                vertical = 3,
            )
        }

        private fun notificationRow(notif: Notification): Widget {
            val isExpanded = expandedId == notif.id
            val isRead = notif.read

            return GestureDetector(
                onTap = { toggleExpand(notif.id) },
                child = Padding(
                    child = Column(
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        children = listOf(
                            // row header: indicator + title + read state
                            Row(
                                mainAxisSize = MainAxisSize.MAX,
                                spacing = 3,
                                children = listOf(
                                    // unread dot (animated opacity: visible when unread)
                                    AnimatedOpacity(
                                        opacity = if (isRead) 0f else 1f,
                                        duration = 400.milliseconds,
                                        vsync = vsync,
                                        child = Container(
                                            width = 4, height = 4,
                                            fillTone = PixelTone.ACCENT,
                                            borderTone = null,
                                        ),
                                    ),
                                    // expand indicator via AnimatedSwitcher
                                    AnimatedSwitcher(
                                        key = "ind-${notif.id}",
                                        duration = 150.milliseconds,
                                        vsync = vsync,
                                        child = Text(
                                            if (isExpanded) "▼" else "▶",
                                            key = if (isExpanded) "open" else "shut",
                                            style = TextStyle.Default,
                                        ),
                                    ),
                                    Expanded(
                                        child = Text(
                                            notif.title,
                                            style = if (isRead) TextStyle.Default else TextStyle.Accent,
                                        ),
                                    ),
                                ),
                            ),
                            // expandable detail body
                            AnimatedContainer(
                                duration = 300.milliseconds,
                                vsync = vsync,
                                curve = Curves.EaseOut,
                                height = if (isExpanded) 20 else 0,
                                child = AnimatedOpacity(
                                    opacity = if (isExpanded) 1f else 0f,
                                    duration = 250.milliseconds,
                                    vsync = vsync,
                                    child = Padding(
                                        child = Text(
                                            notif.detail,
                                            style = TextStyle.Default,
                                            softWrap = true,
                                            maxLines = 2,
                                        ),
                                        horizontal = 8,
                                        vertical = 2,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    horizontal = 4,
                    vertical = 2,
                ),
            )
        }
    }
}
