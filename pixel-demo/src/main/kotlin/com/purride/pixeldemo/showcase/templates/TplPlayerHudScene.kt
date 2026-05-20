package com.purride.pixeldemo.showcase.templates

import android.view.Choreographer
import com.purride.pixelcore.AxisMotionController
import com.purride.pixelcore.AxisMotionState
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
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
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv
import kotlin.math.roundToInt

object TplPlayerHudScene : DemoScene {
    override val id = "tpl_player_hud"
    override val title = "模板 · 播放器 HUD"
    override val description = "封面 + 歌词滚动（AxisMotionController）+ 进度控件 — 完整的播放界面"

    override fun build(env: DemoEnv): Widget = TplPlayerHudWidget()
}

private val lyrics = listOf(
    "▶ pixel pixel pixel",
    "  draw a square in the sky",
    "▶ render render render",
    "  every frame a lullaby",
    "▶ scroll scroll scroll",
    "  beneath the phosphor glow",
    "▶ tap tap tap",
    "  watch the buffer flow",
    "▶ end of side A",
)

private const val LYRIC_HEIGHT = 14
private const val PROGRESS_WIDTH = 160
private const val PROGRESS_DOT = 8

private class TplPlayerHudWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TplPlayerHudState()

    class TplPlayerHudState : State<TplPlayerHudWidget>() {
        private val lyricMotion = AxisMotionController(settleDurationMs = 4000L)
        private var lyricState: AxisMotionState = AxisMotionState()
        private val progressMotion = AxisMotionController(settleDurationMs = 30_000L)
        private var progressState: AxisMotionState = AxisMotionState()
        private var lastFrameNs = 0L
        private var playing = true

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!isMountedSafe()) return
                val deltaMs = if (lastFrameNs == 0L) 16L else ((frameTimeNanos - lastFrameNs) / 1_000_000L).coerceAtLeast(1L)
                lastFrameNs = frameTimeNanos
                if (playing) {
                    // lyric scroll: advance until end then loop
                    if (!lyricMotion.isActive(lyricState)) {
                        lyricState = lyricMotion.settleTo(
                            lyricMotion.create(),
                            ((lyrics.size - 1) * LYRIC_HEIGHT).toFloat(),
                        )
                    }
                    lyricState = lyricMotion.step(lyricState, deltaMs)

                    if (!progressMotion.isActive(progressState)) {
                        progressState = progressMotion.settleTo(
                            progressMotion.create(),
                            (PROGRESS_WIDTH - PROGRESS_DOT).toFloat(),
                        )
                    }
                    progressState = progressMotion.step(progressState, deltaMs)
                    setState { /* tick */ }
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        @Volatile
        private var mounted = false

        private fun isMountedSafe() = mounted

        override fun initState() {
            super.initState()
            mounted = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }

        override fun dispose() {
            mounted = false
            super.dispose()
        }

        override fun build(context: BuildContext): Widget {
            val scrollY = lyricMotion.visualOffsetPx(lyricState).roundToInt()
            val progressX = progressMotion.visualOffsetPx(progressState).roundToInt()
                .coerceIn(0, PROGRESS_WIDTH - PROGRESS_DOT)
            val activeLine = (scrollY / LYRIC_HEIGHT).coerceIn(0, lyrics.size - 1)

            val cover = Container(
                width = 60, height = 60,
                fillTone = PixelTone.ACCENT,
                borderTone = PixelTone.ON,
                child = Center(child = Text("♪", style = TextStyle.Default)),
            )

            val lyricView = Container(
                fillTone = PixelTone.OFF,
                borderTone = PixelTone.ON,
                child = Padding(
                    child = Column(
                        children = lyrics.mapIndexed { i, line ->
                            Text(
                                line,
                                style = if (i == activeLine) TextStyle.Accent else TextStyle.Default,
                                softWrap = false,
                                maxLines = 1,
                            )
                        },
                        spacing = LYRIC_HEIGHT - 8,
                        crossAxisAlignment = CrossAxisAlignment.START,
                    ),
                    all = 4,
                ),
            )

            val progressBar = Stack(
                children = listOf(
                    Container(
                        width = PROGRESS_WIDTH,
                        height = PROGRESS_DOT + 2,
                        fillTone = PixelTone.OFF,
                        borderTone = PixelTone.ON,
                    ),
                    Positioned(
                        left = progressX, top = 1,
                        child = Container(
                            width = PROGRESS_DOT, height = PROGRESS_DOT,
                            fillTone = PixelTone.ACCENT, borderTone = null,
                        ),
                    ),
                ),
            )

            val controls = Row(
                children = listOf(
                    OutlinedButton("◀◀", onPressed = {
                        setState {
                            lyricState = lyricMotion.create()
                            progressState = progressMotion.create()
                        }
                    }),
                    OutlinedButton(
                        if (playing) "❚❚" else "▶",
                        onPressed = { setState { playing = !playing } },
                        selected = playing,
                    ),
                    OutlinedButton("▶▶", onPressed = {
                        setState {
                            lyricState = lyricMotion.settleTo(lyricState, ((lyrics.size - 1) * LYRIC_HEIGHT).toFloat())
                            progressState = progressMotion.settleTo(progressState, (PROGRESS_WIDTH - PROGRESS_DOT).toFloat())
                        }
                    }),
                ),
                spacing = 2,
                mainAxisAlignment = MainAxisAlignment.CENTER,
            )

            return Padding(
                child = Column(
                    children = listOf(
                        Row(
                            children = listOf(
                                cover,
                                SizedBox(width = 4),
                                Expanded(
                                    child = Column(
                                        children = listOf(
                                            Text("Pixel Lullaby", style = TextStyle.Accent),
                                            Text("by pixel-engine", style = TextStyle.Default),
                                            SizedBox(height = 2),
                                            Text("line ${activeLine + 1}/${lyrics.size}", style = TextStyle.Default),
                                        ),
                                        spacing = 1,
                                        crossAxisAlignment = CrossAxisAlignment.START,
                                    ),
                                ),
                            ),
                        ),
                        SizedBox(height = 4),
                        Expanded(child = lyricView),
                        SizedBox(height = 4),
                        Center(child = progressBar),
                        SizedBox(height = 4),
                        controls,
                    ),
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    mainAxisSize = MainAxisSize.MAX,
                ),
                all = 4,
            )
        }
    }
}
