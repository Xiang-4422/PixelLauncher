package com.purride.pixeldemo.showcase.templates
import com.purride.pixelcore.PixelColor


import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.Padding
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

object TplCalculatorScene : DemoScene {
    override val id = "tpl_calculator"
    override val title = "模板 · 计算器"
    override val description = "顶部数显 + 4×5 按钮网格 — 密集按钮密集状态的经典模板"

    override fun build(env: DemoEnv): Widget = TplCalculatorWidget()
}

private enum class CalcOp { NONE, ADD, SUB, MUL, DIV }

private class TplCalculatorWidget(override val key: Any? = null) : StatefulWidget(key = key) {
    override fun createState(): State<out StatefulWidget> = TplCalculatorState()

    class TplCalculatorState : State<TplCalculatorWidget>() {
        private var display = "0"
        private var operand: Double? = null
        private var pendingOp: CalcOp = CalcOp.NONE
        private var resetOnNext = false

        private fun inputDigit(d: Int) {
            setState {
                if (resetOnNext || display == "0") {
                    display = d.toString()
                    resetOnNext = false
                } else if (display.length < 12) {
                    display += d.toString()
                }
            }
        }

        private fun applyPending(value: Double): Double {
            val left = operand ?: return value
            return when (pendingOp) {
                CalcOp.ADD -> left + value
                CalcOp.SUB -> left - value
                CalcOp.MUL -> left * value
                CalcOp.DIV -> if (value == 0.0) Double.NaN else left / value
                CalcOp.NONE -> value
            }
        }

        private fun setOp(op: CalcOp) {
            setState {
                val current = display.toDoubleOrNull() ?: 0.0
                val result = applyPending(current)
                operand = result
                pendingOp = op
                display = formatNum(result)
                resetOnNext = true
            }
        }

        private fun evaluate() {
            setState {
                val current = display.toDoubleOrNull() ?: 0.0
                val result = applyPending(current)
                display = formatNum(result)
                operand = null
                pendingOp = CalcOp.NONE
                resetOnNext = true
            }
        }

        private fun clear() {
            setState {
                display = "0"
                operand = null
                pendingOp = CalcOp.NONE
                resetOnNext = false
            }
        }

        private fun formatNum(d: Double): String {
            if (d.isNaN()) return "ERR"
            if (d == d.toLong().toDouble()) return d.toLong().toString()
            return ((d * 1000).toLong() / 1000.0).toString()
        }

        private fun btn(label: String, onTap: () -> Unit, accent: Boolean = false): Widget =
            Expanded(
                child = OutlinedButton(
                    text = label,
                    onPressed = onTap,
                    borderColor = if (accent) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                ),
            )

        override fun build(context: BuildContext): Widget {
            val rows = listOf(
                Row(
                    children = listOf(
                        btn("C", ::clear, accent = true),
                        btn("÷", { setOp(CalcOp.DIV) }, accent = pendingOp == CalcOp.DIV),
                        btn("×", { setOp(CalcOp.MUL) }, accent = pendingOp == CalcOp.MUL),
                        btn("←", {
                            setState {
                                display = if (display.length <= 1) "0" else display.dropLast(1)
                            }
                        }),
                    ),
                    spacing = 2,
                    mainAxisSize = MainAxisSize.MAX,
                ),
                Row(
                    children = listOf(
                        btn("7", { inputDigit(7) }),
                        btn("8", { inputDigit(8) }),
                        btn("9", { inputDigit(9) }),
                        btn("−", { setOp(CalcOp.SUB) }, accent = pendingOp == CalcOp.SUB),
                    ),
                    spacing = 2,
                    mainAxisSize = MainAxisSize.MAX,
                ),
                Row(
                    children = listOf(
                        btn("4", { inputDigit(4) }),
                        btn("5", { inputDigit(5) }),
                        btn("6", { inputDigit(6) }),
                        btn("+", { setOp(CalcOp.ADD) }, accent = pendingOp == CalcOp.ADD),
                    ),
                    spacing = 2,
                    mainAxisSize = MainAxisSize.MAX,
                ),
                Row(
                    children = listOf(
                        btn("1", { inputDigit(1) }),
                        btn("2", { inputDigit(2) }),
                        btn("3", { inputDigit(3) }),
                        btn("=", ::evaluate, accent = true),
                    ),
                    spacing = 2,
                    mainAxisSize = MainAxisSize.MAX,
                ),
                Row(
                    children = listOf(
                        btn("0", { inputDigit(0) }),
                        btn(".", {
                            setState {
                                if (!display.contains(".")) display += "."
                            }
                        }),
                        btn("±", {
                            setState {
                                display = if (display.startsWith("-")) display.drop(1) else "-$display"
                            }
                        }),
                        btn("%", {
                            setState {
                                val v = display.toDoubleOrNull() ?: 0.0
                                display = formatNum(v / 100.0)
                            }
                        }),
                    ),
                    spacing = 2,
                    mainAxisSize = MainAxisSize.MAX,
                ),
            )

            return Padding(
                child = Column(
                    children = listOf(
                        Container(
                            fillColor = PixelColor.White,
                            borderColor = PixelColor.fromRgb(200, 100, 0),
                            child = Padding(
                                child = Row(
                                    children = listOf(
                                        Expanded(
                                            child = Text(
                                                display,
                                                style = TextStyle(color = PixelColor.fromRgb(200, 100, 0)),
                                                textAlign = com.purride.pixelui.TextAlign.END,
                                            ),
                                        ),
                                    ),
                                    mainAxisAlignment = MainAxisAlignment.END,
                                ),
                                horizontal = 4, vertical = 4,
                            ),
                        ),
                        SizedBox(height = 4),
                    ) + rows.flatMap { listOf(it, SizedBox(height = 2)) },
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
                all = 4,
            )
        }
    }
}
