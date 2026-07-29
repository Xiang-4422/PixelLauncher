package com.purride.pixelshowcase

import com.purride.pixelcore.PixelBitmapFont
import com.purride.pixelcore.PixelBuffer
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.Divider
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.Gap
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.Padding
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.Stepper
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTicker
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * 示例应用：组件画廊证明"有零件"，这里证明"能装整车"——
 * 两个有真实状态与交互逻辑的小应用，全部由引擎组件与状态驱动模型搭成。
 */

// ── 大数字 ────────────────────────────────────────────────────────────────────

/**
 * 放大文本：内置字形画到离屏小画布，逐墨迹像素放大绘制。
 * 引擎字号固定，"大字"是数据变换而不是字体特性——和沙钟同一条思路。
 */
fun BigText(text: String, scale: Int, color: PixelColor): Widget {
    val font = PixelBitmapFont.Default
    val textWidth = font.measureText(text).coerceAtLeast(1)
    val textHeight = font.measureHeight(text).coerceAtLeast(1)
    val small = PixelBuffer(width = textWidth, height = textHeight)
    font.drawText(small, text, x = 0, y = 0, color = PixelColor.White)
    return CustomPaint(
        width = textWidth * scale,
        height = textHeight * scale,
        key = "big-$text-$scale",
    ) {
        for (y in 0 until textHeight) {
            for (x in 0 until textWidth) {
                if (small.pixels[y * textWidth + x] == 0) continue
                fillRect(x * scale, y * scale, scale, scale, color)
            }
        }
    }
}

// ── TODO ─────────────────────────────────────────────────────────────────────

data class TodoItem(val id: Int, val text: String, val done: Boolean)

/** TODO 应用的状态：纯数据，UI 每次从它重建。 */
class TodoState {
    var items: List<TodoItem> = listOf(
        TodoItem(1, "TRY THE DEMOS", true),
        TodoItem(2, "DRAG THE SLIDER", false),
        TodoItem(3, "ADD A TASK", false),
    )
        private set
    private var nextId = 4

    fun add(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        items = items + TodoItem(nextId++, trimmed.uppercase(), done = false)
    }

    fun toggle(id: Int) {
        items = items.map { if (it.id == id) it.copy(done = !it.done) else it }
    }

    fun remove(id: Int) {
        items = items.filterNot { it.id == id }
    }
}

/** TODO 页面：输入 + 动态列表 + 统计，数据驱动的增删改一屏演完。 */
fun TodoPage(
    state: TodoState,
    inputState: PixelTextFieldState,
    inputController: PixelTextFieldController,
    listState: PixelListState,
    listController: PixelListController,
    header: Widget,
    onChanged: () -> Unit,
): Widget {
    val submit = {
        state.add(inputState.text)
        inputController.updateText(state = inputState, text = "", selectionStart = 0)
        onChanged()
    }
    val remaining = state.items.count { !it.done }
    return Container(
        fillColor = ShowcaseTheme.BACKGROUND,
        child = Padding(
            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
            child = Column(
                mainAxisSize = com.purride.pixelui.MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                spacing = 3,
                children = listOf(
                    header,
                    Row(
                        spacing = 3,
                        crossAxisAlignment = CrossAxisAlignment.CENTER,
                        children = listOf(
                            Expanded(
                                child = TextField(
                                    state = inputState,
                                    controller = inputController,
                                    placeholder = "NEW TASK",
                                    onSubmitted = { submit() },
                                ),
                            ),
                            TextButton(text = "ADD", onPressed = submit),
                        ),
                    ),
                    Expanded(
                        child = ListViewBuilder(
                            itemCount = state.items.size,
                            state = listState,
                            controller = listController,
                            spacing = 1,
                            itemBuilder = { index -> todoRow(state.items[index], state, onChanged) },
                        ),
                    ),
                    Divider(),
                    Text(
                        "$remaining OF ${state.items.size} REMAINING",
                        color = ShowcaseTheme.FAINT,
                        textAlign = TextAlign.CENTER,
                    ),
                ),
            ),
        ),
    )
}

private fun todoRow(item: TodoItem, state: TodoState, onChanged: () -> Unit): Widget = Row(
    spacing = 3,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    children = listOf(
        Checkbox(
            checked = item.done,
            onChanged = {
                state.toggle(item.id)
                onChanged()
            },
        ),
        Expanded(
            child = Text(
                item.text,
                color = if (item.done) ShowcaseTheme.FAINT else ShowcaseTheme.TITLE,
            ),
        ),
        TextButton(
            text = "X",
            onPressed = {
                state.remove(item.id)
                onChanged()
            },
        ),
    ),
)

// ── 秒表 ─────────────────────────────────────────────────────────────────────

/** 秒表：ticker 驱动的时间状态机，运行时每帧请求重建。 */
class StopwatchController(
    vsync: PixelTickerProvider,
    private val onFrame: () -> Unit,
) {
    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        segmentNanos = elapsedNanos
        onFrame()
    }

    var isRunning = false
        private set
    var laps: List<Long> = emptyList()
        private set

    private var accumulatedNanos = 0L
    private var segmentNanos = 0L

    val totalMillis: Long
        get() = (accumulatedNanos + if (isRunning) segmentNanos else 0L) / 1_000_000L

    fun toggle() {
        if (isRunning) {
            accumulatedNanos += segmentNanos
            segmentNanos = 0L
            ticker.stop()
        } else {
            segmentNanos = 0L
            ticker.start()
        }
        isRunning = !isRunning
        onFrame()
    }

    fun lap() {
        if (isRunning) {
            laps = listOf(totalMillis) + laps
            onFrame()
        }
    }

    fun reset() {
        ticker.stop()
        isRunning = false
        accumulatedNanos = 0L
        segmentNanos = 0L
        laps = emptyList()
        onFrame()
    }

    /** 离开页面：暂停走针（回来保持已计时长）。 */
    fun pause() {
        if (isRunning) toggle()
    }

    fun dispose() {
        ticker.dispose()
    }

    companion object {
        fun format(millis: Long): String {
            val minutes = millis / 60_000
            val seconds = (millis % 60_000) / 1_000
            val centis = (millis % 1_000) / 10
            return "%02d:%02d.%02d".format(minutes, seconds, centis)
        }
    }
}

/** 秒表页面：大数字 + 三键 + 圈速列表。 */
fun StopwatchPage(
    controller: StopwatchController,
    listState: PixelListState,
    listController: PixelListController,
    header: Widget,
): Widget = Container(
    fillColor = ShowcaseTheme.BACKGROUND,
    child = Padding(
        padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
        child = Column(
            mainAxisSize = com.purride.pixelui.MainAxisSize.MAX,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            spacing = 4,
            children = listOf(
                header,
                Gap(6),
                Row(
                    mainAxisAlignment = MainAxisAlignment.CENTER,
                    children = listOf(
                        BigText(
                            text = StopwatchController.format(controller.totalMillis),
                            scale = 3,
                            color = ShowcaseTheme.TITLE,
                        ),
                    ),
                ),
                Gap(4),
                Row(
                    spacing = 3,
                    children = listOf(
                        Expanded(
                            child = TextButton(
                                text = if (controller.isRunning) "PAUSE" else "START",
                                onPressed = { controller.toggle() },
                            ),
                        ),
                        Expanded(
                            child = TextButton(
                                text = "LAP",
                                onPressed = { controller.lap() },
                                enabled = controller.isRunning,
                            ),
                        ),
                        Expanded(
                            child = TextButton(text = "RESET", onPressed = { controller.reset() }),
                        ),
                    ),
                ),
                Divider(),
                Expanded(
                    child = ListViewBuilder(
                        itemCount = controller.laps.size,
                        state = listState,
                        controller = listController,
                        spacing = 1,
                        itemBuilder = { index ->
                            lapRow(
                                number = controller.laps.size - index,
                                millis = controller.laps[index],
                            )
                        },
                    ),
                ),
            ),
        ),
    ),
)

private fun lapRow(number: Int, millis: Long): Widget = Row(
    spacing = 3,
    children = listOf(
        Expanded(child = Text("LAP $number", color = ShowcaseTheme.DIM)),
        Text(StopwatchController.format(millis), color = ShowcaseTheme.TITLE),
    ),
)

// ── 倒计时器 ─────────────────────────────────────────────────────────────────

/**
 * 倒计时器：与秒表同一套 ticker 累计法，方向相反——剩余 = 设定 − 已走。
 * 到点后 ticker 不停，超出的时长直接当闪烁相位用，不需要第二个时钟。
 */
class TimerController(
    vsync: PixelTickerProvider,
    private val onFrame: () -> Unit,
) {
    enum class Phase { SETUP, RUNNING, PAUSED, FINISHED }

    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        segmentNanos = elapsedNanos
        if (phase == Phase.RUNNING && remainingMillis <= 0L) phase = Phase.FINISHED
        onFrame()
    }

    var phase = Phase.SETUP
        private set
    var minutes = 1
        private set
    var seconds = 30
        private set

    private var accumulatedNanos = 0L
    private var segmentNanos = 0L

    val totalMillis: Long
        get() = (minutes * 60L + seconds) * 1_000L

    private val elapsedMillis: Long
        get() = (accumulatedNanos + segmentNanos) / 1_000_000L

    val remainingMillis: Long
        get() = (totalMillis - elapsedMillis).coerceAtLeast(0L)

    /** 到点后的闪烁开关：超时时长按 400ms 翻转，占空比一半亮一半灭。 */
    val flashOn: Boolean
        get() = phase == Phase.FINISHED && ((elapsedMillis - totalMillis) / FLASH_MILLIS) % 2 == 0L

    fun updateMinutes(value: Int) {
        if (phase == Phase.SETUP) minutes = value.coerceIn(0, 99)
        onFrame()
    }

    fun updateSeconds(value: Int) {
        if (phase == Phase.SETUP) seconds = value.coerceIn(0, 59)
        onFrame()
    }

    fun start() {
        if (phase != Phase.SETUP || totalMillis == 0L) return
        phase = Phase.RUNNING
        segmentNanos = 0L
        ticker.start()
        onFrame()
    }

    fun pause() {
        if (phase != Phase.RUNNING) return
        accumulatedNanos += segmentNanos
        segmentNanos = 0L
        ticker.stop()
        phase = Phase.PAUSED
        onFrame()
    }

    fun resume() {
        if (phase != Phase.PAUSED) return
        phase = Phase.RUNNING
        segmentNanos = 0L
        ticker.start()
        onFrame()
    }

    fun reset() {
        ticker.stop()
        phase = Phase.SETUP
        accumulatedNanos = 0L
        segmentNanos = 0L
        onFrame()
    }

    /** 离开页面：走针暂停，响铃直接归位——闪烁在看不见的页面上没有意义。 */
    fun pausePage() {
        when (phase) {
            Phase.RUNNING -> pause()
            Phase.FINISHED -> reset()
            else -> Unit
        }
    }

    fun dispose() {
        ticker.dispose()
    }

    companion object {
        private const val FLASH_MILLIS = 400L

        /** 倒计时显示到整秒，向上取整：剩 0.2s 显示 00:01，到 0 才显示 00:00。 */
        fun format(millis: Long): String {
            val totalSeconds = (millis + 999) / 1_000
            return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }
    }
}

/** 倒计时页面：设定 → 走针 → 到点全屏闪烁，一个状态机四种画面。 */
fun TimerPage(controller: TimerController, header: Widget): Widget {
    val finished = controller.phase == TimerController.Phase.FINISHED
    val flash = controller.flashOn
    val display = if (controller.phase == TimerController.Phase.SETUP) {
        TimerController.format(controller.totalMillis)
    } else {
        TimerController.format(controller.remainingMillis)
    }
    val progress = if (controller.totalMillis == 0L) {
        0f
    } else {
        controller.remainingMillis.toFloat() / controller.totalMillis.toFloat()
    }
    return Container(
        fillColor = if (flash) ShowcaseTheme.ALERT else ShowcaseTheme.BACKGROUND,
        child = Padding(
            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
            child = Column(
                mainAxisSize = com.purride.pixelui.MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                spacing = 4,
                children = listOf(
                    header,
                    Gap(8),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            BigText(
                                text = display,
                                scale = 4,
                                color = if (flash) ShowcaseTheme.BACKGROUND else ShowcaseTheme.TITLE,
                            ),
                        ),
                    ),
                    Gap(2),
                    if (finished) {
                        Text(
                            "TIME UP",
                            color = if (flash) ShowcaseTheme.BACKGROUND else ShowcaseTheme.ALERT,
                            textAlign = TextAlign.CENTER,
                        )
                    } else {
                        ProgressBar(progress = progress)
                    },
                    Gap(4),
                ) + timerControls(controller),
            ),
        ),
    )
}

/** 当前相位对应的设置区与按钮行。 */
private fun timerControls(controller: TimerController): List<Widget> = when (controller.phase) {
    TimerController.Phase.SETUP -> listOf(
        timerStepperRow("MINUTES", controller.minutes, 0..99) { controller.updateMinutes(it) },
        timerStepperRow("SECONDS", controller.seconds, 0..59) { controller.updateSeconds(it) },
        Gap(4),
        TextButton(text = "START", onPressed = { controller.start() }),
    )
    TimerController.Phase.RUNNING -> listOf(
        Row(
            spacing = 3,
            children = listOf(
                Expanded(child = TextButton(text = "PAUSE", onPressed = { controller.pause() })),
                Expanded(child = TextButton(text = "RESET", onPressed = { controller.reset() })),
            ),
        ),
    )
    TimerController.Phase.PAUSED -> listOf(
        Row(
            spacing = 3,
            children = listOf(
                Expanded(child = TextButton(text = "RESUME", onPressed = { controller.resume() })),
                Expanded(child = TextButton(text = "RESET", onPressed = { controller.reset() })),
            ),
        ),
    )
    TimerController.Phase.FINISHED -> listOf(
        TextButton(text = "STOP", onPressed = { controller.reset() }),
    )
}

private fun timerStepperRow(
    label: String,
    value: Int,
    range: IntRange,
    onChanged: (Int) -> Unit,
): Widget = Row(
    spacing = 4,
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    children = listOf(
        Expanded(child = Text(label, color = ShowcaseTheme.DIM)),
        Stepper(value = value, range = range, onChanged = onChanged),
    ),
)
