package com.purride.pixelshowcase

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.purride.pixeldesign.ProductThemeBrightness
import com.purride.pixellockscreen.ui.LockscreenRootHost
import com.purride.pixellockscreen.ui.PatternCredentialFeedback
import com.purride.pixellockscreen.ui.PatternCredentialHost
import com.purride.pixellockscreen.ui.PatternCredentialListener

/** 使用真实普通锁屏和图案宿主提供确定输入与测试背景的离线预览页。 */
class LockscreenPreviewActivity : AppCompatActivity() {
    /** 当前全部预览选项；每次交互通过 copy 原子替换。 */
    private var configuration: LockscreenPreviewConfiguration = LockscreenPreviewConfiguration()

    /** 同时承载背景和真实锁屏宿主、并可切换宽高比的预览画布。 */
    private lateinit var previewStage: LockscreenPreviewStage

    /** 每次配置变化后重建的紧凑控制区域。 */
    private lateinit var controlsContainer: LinearLayout

    /** 创建不依赖设备状态、系统时钟或电量服务的预览界面。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(buildRootView())
        renderConfiguration()
    }

    /** Activity 销毁时显式释放真实像素宿主。 */
    override fun onDestroy() {
        if (::previewStage.isInitialized) previewStage.dispose()
        super.onDestroy()
    }

    /** 构建预览画布和可滚动控制面板的 Android 宿主层级。 */
    private fun buildRootView(): View {
        /** 页面根布局使用中性深色，避免与被测壁纸背景混淆。 */
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PANEL_BACKGROUND_COLOR)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        /** 页面标题明确该页是确定输入的离线预览。 */
        val title = TextView(this).apply {
            setText(R.string.lockscreen_preview_title)
            setTextColor(PANEL_TEXT_COLOR)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        previewStage = LockscreenPreviewStage(this)
        controlsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        /** 控制区允许小屏滚动，不能反向挤压预览画布到不可见。 */
        val controlsScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(
                controlsContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(
            previewStage,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(
            controlsScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(CONTROL_PANEL_HEIGHT_DP),
            ),
        )
        return root
    }

    /** 把当前配置同步到真实锁屏宿主、预览比例和全部控制项。 */
    private fun renderConfiguration() {
        previewStage.orientation = configuration.orientation
        previewStage.backgroundKind = configuration.background
        previewStage.scene = configuration.scene
        previewStage.lockscreenHost.update(
            state = configuration.toUiState(),
            family = configuration.family,
            brightness = configuration.brightness,
        )
        previewStage.patternHost.update(
            state = configuration.toPatternUiState(),
            family = configuration.family,
            brightness = configuration.brightness,
        )
        rebuildControls()
    }

    /** 重建控制行，使所有选中态文字和底色始终与当前配置一致。 */
    private fun rebuildControls() {
        controlsContainer.removeAllViews()
        controlsContainer.addView(
            optionRow(
                label = "SCENE",
                options = LockscreenPreviewScene.entries.map { scene ->
                    ControlOption(
                        label = scene.label,
                        selected = scene == configuration.scene,
                        onClick = { updateConfiguration { copy(scene = scene) } },
                    )
                },
            ),
        )
        controlsContainer.addView(
            navigationRow(
                label = "THEME",
                value = configuration.family.displayLabel,
                onPrevious = {
                    updateConfiguration {
                        copy(family = cyclePreviewFamily(family, step = -1))
                    }
                },
                onNext = {
                    updateConfiguration {
                        copy(family = cyclePreviewFamily(family, step = 1))
                    }
                },
            ),
        )
        controlsContainer.addView(
            optionRow(
                label = "MODE",
                options = listOf(
                    ControlOption("DAY", configuration.brightness == ProductThemeBrightness.LIGHT) {
                        updateConfiguration { copy(brightness = ProductThemeBrightness.LIGHT) }
                    },
                    ControlOption("NIGHT", configuration.brightness == ProductThemeBrightness.DARK) {
                        updateConfiguration { copy(brightness = ProductThemeBrightness.DARK) }
                    },
                ),
            ),
        )
        if (configuration.scene == LockscreenPreviewScene.CLOCK) {
            controlsContainer.addView(
                optionRow(
                    label = "BATTERY",
                    options = LockscreenPreviewBattery.entries.map { battery ->
                        ControlOption(
                            label = "${battery.percent}%",
                            selected = battery == configuration.battery,
                            onClick = { updateConfiguration { copy(battery = battery) } },
                        )
                    },
                ),
            )
            controlsContainer.addView(
                optionRow(
                    label = "POWER",
                    options = listOf(
                        ControlOption("NORMAL", !configuration.isCharging) {
                            updateConfiguration { copy(isCharging = false) }
                        },
                        ControlOption("CHARGING", configuration.isCharging) {
                            updateConfiguration { copy(isCharging = true) }
                        },
                    ),
                ),
            )
        } else {
            controlsContainer.addView(
                optionRow(
                    label = "STATE",
                    options = PatternCredentialFeedback.entries.map { feedback ->
                        ControlOption(
                            label = feedback.name,
                            selected = feedback == configuration.patternFeedback,
                            onClick = { updateConfiguration { copy(patternFeedback = feedback) } },
                        )
                    },
                ),
            )
        }
        controlsContainer.addView(
            optionRow(
                label = "FRAME",
                options = LockscreenPreviewOrientation.entries.map { orientation ->
                    ControlOption(
                        label = orientation.label,
                        selected = orientation == configuration.orientation,
                        onClick = { updateConfiguration { copy(orientation = orientation) } },
                    )
                },
            ),
        )
        controlsContainer.addView(
            navigationRow(
                label = "BACKGROUND",
                value = configuration.background.label,
                onPrevious = {
                    updateConfiguration {
                        copy(background = cyclePreviewBackground(background, step = -1))
                    }
                },
                onNext = {
                    updateConfiguration {
                        copy(background = cyclePreviewBackground(background, step = 1))
                    }
                },
            ),
        )
    }

    /** 应用一个纯配置变换并同步预览，不启动定时器或连续动画。 */
    private fun updateConfiguration(transform: LockscreenPreviewConfiguration.() -> LockscreenPreviewConfiguration) {
        configuration = configuration.transform()
        renderConfiguration()
    }

    /** 构建带前后按钮的循环枚举控制行。 */
    private fun navigationRow(
        label: String,
        value: String,
        onPrevious: () -> Unit,
        onNext: () -> Unit,
    ): View = labeledControlRow(
        label = label,
        controls = listOf(
            ControlOption("<", selected = false, onClick = onPrevious),
            ControlOption(value, selected = true, onClick = {}),
            ControlOption(">", selected = false, onClick = onNext),
        ),
    )

    /** 构建一组选项共享同一标签的控制行。 */
    private fun optionRow(label: String, options: List<ControlOption>): View = labeledControlRow(label, options)

    /** 构建固定标签和可横向滚动按钮组，避免窄屏裁切控制项。 */
    private fun labeledControlRow(label: String, controls: List<ControlOption>): View {
        /** 单行容器把标签宽度固定，便于快速纵向比较各组状态。 */
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        /** 当前控制组的稳定名称。 */
        val labelView = TextView(this).apply {
            text = label
            setTextColor(PANEL_MUTED_COLOR)
            textSize = 11f
            gravity = Gravity.CENTER_VERTICAL
        }
        /** 按钮容器允许主题名称等长文本超过窄屏宽度。 */
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            controls.forEach { option ->
                addView(controlButton(option))
            }
        }
        /** 仅按钮区域横向滚动，左侧类别标签始终可见。 */
        val horizontalScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buttonRow)
        }
        row.addView(labelView, LinearLayout.LayoutParams(dp(CONTROL_LABEL_WIDTH_DP), dp(CONTROL_ROW_HEIGHT_DP)))
        row.addView(horizontalScroll, LinearLayout.LayoutParams(0, dp(CONTROL_ROW_HEIGHT_DP), 1f))
        return row
    }

    /** 将一个控制描述转换为具有明确选中态的紧凑 Android 按钮。 */
    private fun controlButton(option: ControlOption): Button = Button(this).apply {
        text = option.label
        isAllCaps = false
        textSize = 11f
        minWidth = dp(52)
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(10), 0, dp(10), 0)
        setTextColor(if (option.selected) ACTIVE_TEXT_COLOR else PANEL_TEXT_COLOR)
        backgroundTintList = ColorStateList.valueOf(
            if (option.selected) ACTIVE_BACKGROUND_COLOR else INACTIVE_BACKGROUND_COLOR,
        )
        setOnClickListener { option.onClick() }
    }

    /** 把密度无关像素转换为当前屏幕的整数物理像素。 */
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** 控制按钮所需的显示文字、选中态和点击行为。 */
    private data class ControlOption(
        /** 按钮文字。 */
        val label: String,
        /** 当前选项是否选中。 */
        val selected: Boolean,
        /** 点击后执行的单次配置变换。 */
        val onClick: () -> Unit,
    )

    private companion object {
        /** 控制面板预留的固定高度。 */
        const val CONTROL_PANEL_HEIGHT_DP = 266

        /** 控制类别标签的固定宽度。 */
        const val CONTROL_LABEL_WIDTH_DP = 82

        /** 每个控制行的固定高度。 */
        const val CONTROL_ROW_HEIGHT_DP = 38

        /** 预览工具外围面板颜色。 */
        const val PANEL_BACKGROUND_COLOR = 0xFF11141C.toInt()

        /** 控制面板主要文字颜色。 */
        const val PANEL_TEXT_COLOR = 0xFFE8EEF8.toInt()

        /** 控制面板次要标签颜色。 */
        const val PANEL_MUTED_COLOR = 0xFF9AA8BC.toInt()

        /** 选中按钮底色。 */
        const val ACTIVE_BACKGROUND_COLOR = 0xFFE8EEF8.toInt()

        /** 选中按钮文字颜色。 */
        const val ACTIVE_TEXT_COLOR = 0xFF11141C.toInt()

        /** 未选中按钮底色。 */
        const val INACTIVE_BACKGROUND_COLOR = 0xFF303746.toInt()
    }
}

/** 居中约束真实锁屏宿主宽高比，并让背景与透明像素画布完全重合。 */
private class LockscreenPreviewStage(context: Context) : ViewGroup(context) {
    /** 实际绘制四类可读性测试背景的底层 View。 */
    private val backgroundView = LockscreenPreviewBackgroundView(context)

    /** Showcase 和未来系统适配器共同使用的真实静态锁屏宿主。 */
    val lockscreenHost: LockscreenRootHost = LockscreenRootHost(context)

    /** Showcase 与 SystemUI 共用的真实图案交互宿主。 */
    val patternHost: PatternCredentialHost = PatternCredentialHost(context, PreviewPatternListener)

    /** 当前展示的宿主场景，只改变两个真实宿主的可见性。 */
    var scene: LockscreenPreviewScene = LockscreenPreviewScene.CLOCK
        set(value) {
            if (field == value) return
            field = value
            updateHostVisibility()
        }

    /** 当前预览比例方向，变化后重新测量两个重合子 View。 */
    var orientation: LockscreenPreviewOrientation = LockscreenPreviewOrientation.PORTRAIT
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    /** 当前透明宿主下方的测试背景类型。 */
    var backgroundKind: LockscreenPreviewBackground = LockscreenPreviewBackground.HIGH_CONTRAST
        set(value) {
            if (field == value) return
            field = value
            backgroundView.kind = value
        }

    /** 初始化背景和宿主的固定前后层级。 */
    init {
        setBackgroundColor(STAGE_BACKGROUND_COLOR)
        addView(backgroundView)
        addView(lockscreenHost)
        addView(patternHost)
        updateHostVisibility()
    }

    /** 根据可用区域内切当前横屏或竖屏比例，并精确测量两个重合子 View。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        /** 父级提供的最大预览区域宽度。 */
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        /** 父级提供的最大预览区域高度。 */
        val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
        /** 当前方向的宽高比例基数。 */
        val ratioWidth = if (orientation == LockscreenPreviewOrientation.PORTRAIT) 9 else 16
        /** 当前方向的宽高比例基数。 */
        val ratioHeight = if (orientation == LockscreenPreviewOrientation.PORTRAIT) 16 else 9
        /** 优先使用全部宽度计算出的候选高度。 */
        val widthBoundHeight = availableWidth * ratioHeight / ratioWidth
        /** 最终被测画布宽度。 */
        val contentWidth: Int
        /** 最终被测画布高度。 */
        val contentHeight: Int
        if (widthBoundHeight <= availableHeight) {
            contentWidth = availableWidth
            contentHeight = widthBoundHeight
        } else {
            contentHeight = availableHeight
            contentWidth = availableHeight * ratioWidth / ratioHeight
        }
        /** 两个子 View 必须共享完全相同的精确尺寸。 */
        val childWidthSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY)
        /** 两个子 View 必须共享完全相同的精确尺寸。 */
        val childHeightSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
        backgroundView.measure(childWidthSpec, childHeightSpec)
        lockscreenHost.measure(childWidthSpec, childHeightSpec)
        patternHost.measure(childWidthSpec, childHeightSpec)
        setMeasuredDimension(availableWidth, availableHeight)
    }

    /** 将背景和透明宿主叠放在父区域正中央。 */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        /** 居中后的子 View 左边界。 */
        val childLeft = (measuredWidth - backgroundView.measuredWidth) / 2
        /** 居中后的子 View 上边界。 */
        val childTop = (measuredHeight - backgroundView.measuredHeight) / 2
        /** 居中后的子 View 右边界。 */
        val childRight = childLeft + backgroundView.measuredWidth
        /** 居中后的子 View 下边界。 */
        val childBottom = childTop + backgroundView.measuredHeight
        backgroundView.layout(childLeft, childTop, childRight, childBottom)
        lockscreenHost.layout(childLeft, childTop, childRight, childBottom)
        patternHost.layout(childLeft, childTop, childRight, childBottom)
    }

    /** 释放真实静态宿主持有的 Pixel Engine 运行时。 */
    fun dispose() {
        lockscreenHost.dispose()
        patternHost.dispose()
    }

    /** 保证任一时刻只有一个真实宿主可见并接收输入。 */
    private fun updateHostVisibility() {
        lockscreenHost.visibility = if (scene == LockscreenPreviewScene.CLOCK) View.VISIBLE else View.GONE
        patternHost.visibility = if (scene == LockscreenPreviewScene.PATTERN) View.VISIBLE else View.GONE
    }

    private companion object {
        /** 预览框以外的中性深色。 */
        const val STAGE_BACKGROUND_COLOR = 0xFF080A0F.toInt()
    }
}

/** 离线预览只验证交互绘制，不保存或校验任何图案格子。 */
private data object PreviewPatternListener : PatternCredentialListener {
    /** 预览不需要转发系统用户活动。 */
    override fun onPatternStarted() = Unit

    /** 预览故意丢弃逐格输入。 */
    override fun onPatternCellAdded(cellId: Int) = Unit

    /** 预览只完成路径动画，不执行系统校验。 */
    override fun onPatternCompleted(cellCount: Int) = Unit

    /** 预览取消不需要额外状态。 */
    override fun onPatternCancelled() = Unit

    /** 预览监听器自身没有外部依赖，异常仅转换为稳定失败。 */
    override fun onInteractionFailure(throwable: Throwable) {
        throw IllegalStateException("pattern_preview_interaction", throwable)
    }
}

/** 绘制浅、深、中灰和黑白高反差四类确定测试背景。 */
private class LockscreenPreviewBackgroundView(context: Context) : View(context) {
    /** 复用的无抗锯齿画笔，确保高反差图案边界稳定。 */
    private val paint = Paint().apply { isAntiAlias = false }

    /** 当前背景类型，变化后只触发一次静态重绘。 */
    var kind: LockscreenPreviewBackground = LockscreenPreviewBackground.HIGH_CONTRAST
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /** 根据当前类型绘制纯色或黑白棋盘测试图案。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (kind) {
            LockscreenPreviewBackground.LIGHT -> canvas.drawColor(LIGHT_BACKGROUND_COLOR)
            LockscreenPreviewBackground.DARK -> canvas.drawColor(DARK_BACKGROUND_COLOR)
            LockscreenPreviewBackground.GRAY -> canvas.drawColor(GRAY_BACKGROUND_COLOR)
            LockscreenPreviewBackground.HIGH_CONTRAST -> drawHighContrastPattern(canvas)
        }
    }

    /** 绘制同时包含极亮、极暗与中间边界的固定棋盘图案。 */
    private fun drawHighContrastPattern(canvas: Canvas) {
        /** 棋盘格边长随密度变化，但保持硬边无抗锯齿。 */
        val cellSize = (HIGH_CONTRAST_CELL_DP * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        /** 当前正在绘制的格子顶部。 */
        var top = 0
        /** 当前棋盘行号。 */
        var row = 0
        while (top < height) {
            /** 当前正在绘制的格子左侧。 */
            var left = 0
            /** 当前棋盘列号。 */
            var column = 0
            while (left < width) {
                paint.color = if ((row + column) % 2 == 0) Color.BLACK else Color.WHITE
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    minOf(left + cellSize, width).toFloat(),
                    minOf(top + cellSize, height).toFloat(),
                    paint,
                )
                left += cellSize
                column += 1
            }
            top += cellSize
            row += 1
        }
    }

    private companion object {
        /** 浅色壁纸测试色。 */
        const val LIGHT_BACKGROUND_COLOR = 0xFFF4F1E8.toInt()

        /** 深色壁纸测试色。 */
        const val DARK_BACKGROUND_COLOR = 0xFF090B12.toInt()

        /** 中灰壁纸测试色。 */
        const val GRAY_BACKGROUND_COLOR = 0xFF777777.toInt()

        /** 高反差棋盘格的密度无关边长。 */
        const val HIGH_CONTRAST_CELL_DP = 32
    }
}
