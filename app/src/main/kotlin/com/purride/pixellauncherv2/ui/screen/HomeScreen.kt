package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
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
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * HOME 屏幕（pixel-engine 渲染）。
 *
 * 结构：
 *  - 固定信息区（日期、天气、闹钟、通信状态、屏幕使用统计）
 *  - Expanded 弹性空白
 *  - 底栏：CONTACT 按钮（左）/ SMS 按钮（右）
 *
 * @param uiState        当前 UI 状态快照
 * @param theme          当前颜色主题
 * @param onOpenContacts 点击 CONTACT → 打开通讯录
 * @param onOpenSms      点击 SMS → 进入短信模块
 */
class HomeScreen(
    private val uiState: LauncherUiState,
    private val theme: LauncherTheme,
    private val onOpenContacts: () -> Unit,
    private val onOpenSms: () -> Unit,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = HomeState()

    private inner class HomeState : State<HomeScreen>() {

        override fun build(context: BuildContext): Widget {
            val s = widget.uiState
            val t = widget.theme

            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    Padding(
                        horizontal = 2,
                        vertical = 2,
                        child = Column(
                            crossAxisAlignment = CrossAxisAlignment.STRETCH,
                            mainAxisSize = MainAxisSize.MIN,
                            spacing = 2,
                            children = buildInfoColumn(s, t),
                        ),
                    ),
                    Expanded(child = SizedBox(width = 0, height = 0)),
                    Padding(
                        horizontal = 2,
                        vertical = 2,
                        child = Row(
                            spacing = 0,
                            children = listOf(
                                OutlinedButton(
                                    text = "CONTACT",
                                    onPressed = widget.onOpenContacts,
                                    borderColor = t.button.border,
                                ),
                                Expanded(child = SizedBox(width = 0, height = 0)),
                                OutlinedButton(
                                    text = "SMS",
                                    onPressed = widget.onOpenSms,
                                    borderColor = t.button.border,
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }

        private fun buildInfoColumn(s: LauncherUiState, t: LauncherTheme): List<Widget> =
            buildList {
                // Date row (primary color)
                add(
                    Text(
                        s.currentDateText.ifBlank { "--- --- --" },
                        style = TextStyle(color = t.text.primary),
                    ),
                )
                // Info rows (dim color): weather, alarm (conditional), comms (conditional), usage
                s.toHomeInfoRows().forEach { line ->
                    add(Text(line, style = TextStyle(color = t.text.secondary)))
                }
            }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

/**
 * 从 [LauncherUiState] 生成 HOME 信息行列表（不含日期行）：
 * 天气提示（常驻）、闹钟（有值时）、通信计数（非零时）、屏幕使用统计（常驻）。
 */
private fun LauncherUiState.toHomeInfoRows(): List<String> = buildList {
    // Weather / rain hint（常驻；空时显示占位符）
    add(rainHintText.ifBlank { "--" })

    // Alarm（仅当有设置且不是占位符时显示）
    if (nextAlarmText.isNotBlank() && nextAlarmText != "--:--") {
        add("ALARM $nextAlarmText")
    }

    // Missed calls + unread SMS（仅非零时显示）
    val commParts = buildList {
        if (missedCallCount > 0) add("CALL $missedCallCount")
        if (unreadSmsCount > 0) add("SMS $unreadSmsCount")
    }
    if (commParts.isNotEmpty()) {
        add(commParts.joinToString("  "))
    }

    // Screen usage（常驻）
    add("USE ${screenUsageTimeText.ifBlank { "--:--" }}  OPEN ${screenOpenCountText.ifBlank { "--" }}")
}
