package com.purride.pixeldemo.showcase.composition

import com.purride.pixelcore.PixelTone
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Positioned
import com.purride.pixelui.PositionedFill
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

object ModalOverlayScene : DemoScene {
    override val id = "modal_overlay"
    override val title = "Modal Overlay"
    override val description = "Stack + PositionedFill 实现模态遮罩，点击遮罩外部关闭"

    override fun build(env: DemoEnv): Widget = ModalOverlayWidget()
}

private class ModalOverlayWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ModalOverlayState()

    class ModalOverlayState : State<ModalOverlayWidget>() {
        private var showing = false
        private var dismissedTimes = 0

        override fun build(context: BuildContext): Widget {
            val baseContent = Column(
                children = listOf(
                    Expanded(
                        child = Center(
                            child = Column(
                                children = listOf(
                                    Text("Modal dismissed: $dismissedTimes 次", style = TextStyle.Accent),
                                    SizedBox(height = 4),
                                    Text("点击下方 OPEN 唤起模态弹窗", style = TextStyle.Default, softWrap = true),
                                ),
                                spacing = 2,
                                crossAxisAlignment = CrossAxisAlignment.CENTER,
                            ),
                        ),
                    ),
                    SizedBox(height = 2),
                    Padding(
                        child = Row(
                            children = listOf(
                                OutlinedButton("OPEN", onPressed = { setState { showing = true } }),
                            ),
                            spacing = 2,
                            mainAxisAlignment = MainAxisAlignment.CENTER,
                        ),
                        horizontal = 4,
                        vertical = 2,
                    ),
                ),
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
            )

            if (!showing) return baseContent

            val dialog = Container(
                width = 160, height = 90,
                fillTone = PixelTone.ON,
                borderTone = PixelTone.ACCENT,
                child = Padding(
                    child = Column(
                        children = listOf(
                            Text("MODAL", style = TextStyle.Accent),
                            SizedBox(height = 2),
                            Text("点击遮罩外部关闭", style = TextStyle.Default, softWrap = true),
                            Expanded(child = SizedBox()),
                            Row(
                                children = listOf(
                                    OutlinedButton(
                                        "DISMISS",
                                        onPressed = {
                                            setState {
                                                showing = false
                                                dismissedTimes++
                                            }
                                        },
                                    ),
                                ),
                                spacing = 2,
                                mainAxisAlignment = MainAxisAlignment.END,
                            ),
                        ),
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        mainAxisSize = MainAxisSize.MAX,
                    ),
                    all = 4,
                ),
            )

            return Stack(
                children = listOf(
                    baseContent,
                    PositionedFill(
                        child = GestureDetector(
                            onTap = {
                                setState {
                                    showing = false
                                    dismissedTimes++
                                }
                            },
                            child = Container(
                                fillTone = PixelTone.OFF,
                                borderTone = null,
                            ),
                        ),
                    ),
                    Positioned(
                        left = 20, top = 40, right = 20, bottom = 40,
                        child = Center(child = dialog),
                    ),
                ),
            )
        }
    }
}
