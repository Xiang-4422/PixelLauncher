package com.purride.pixeldemo.showcase.interaction

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelInputType
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldState
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object ImeTypesScene : DemoScene {
    override val id = "ime_types"
    override val title = "输入键盘类型"
    override val description = "PixelInputType 全枚举 + TextInputAction 全枚举"

    override fun build(env: DemoEnv): Widget = ImeTypesWidget()
}

private val inputTypes = PixelInputType.entries
private val inputActions = TextInputAction.entries

private class ImeTypesWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = ImeTypesState()

    class ImeTypesState : State<ImeTypesWidget>() {
        private val scrollState = PixelListState()
        private val scrollController = ScrollController()
        private val typeStates = inputTypes.map { PixelTextFieldState() }
        private val typeCtrls = inputTypes.map { TextEditingController() }
        private val actionStates = inputActions.map { PixelTextFieldState() }
        private val actionCtrls = inputActions.map { TextEditingController() }

        override fun build(context: BuildContext): Widget {
            val items = mutableListOf<Widget>()
            items += Text("PixelInputType", style = TextStyle.Accent)
            inputTypes.forEachIndexed { i, type ->
                items += Text(type.name, style = TextStyle.Default)
                items += TextField(
                    state = typeStates[i],
                    controller = typeCtrls[i],
                    placeholder = type.name,
                    inputType = type,
                )
            }
            items += Text("TextInputAction", style = TextStyle.Accent)
            inputActions.forEachIndexed { i, action ->
                items += Text(action.name, style = TextStyle.Default)
                items += TextField(
                    state = actionStates[i],
                    controller = actionCtrls[i],
                    placeholder = action.name,
                    textInputAction = action,
                )
            }
            return SingleChildScrollView(
                state = scrollState,
                controller = scrollController,
                child = Padding(
                    child = Column(
                        children = items,
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    ),
                    all = 6,
                ),
            )
        }
    }
}
