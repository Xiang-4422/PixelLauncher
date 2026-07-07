package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Expanded
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelLoadingBar
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.SizedBox
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.widget.SettingsPixelSizeControl

class LoadingPreviewScreen(
    private val theme: LauncherTheme,
    private val screenProfile: ScreenProfile,
    private val vsync: PixelTickerProvider,
    override val key: Any? = null,
) : StatefulWidget(key = key) {

    override fun createState(): State<out StatefulWidget> = LoadingPreviewState()

    private inner class LoadingPreviewState : State<LoadingPreviewScreen>() {
        private val scrollController = PixelListController()
        private val scrollState = scrollController.create()
        private var selectedSpeedIndex = DEFAULT_LOADING_PREVIEW_SPEED_INDEX
        private var previewHeight = DEFAULT_LOADING_PREVIEW_HEIGHT
        private var previewBlockWidth = DEFAULT_LOADING_PREVIEW_BLOCK_WIDTH
        private var previewTrailRatioPercent = DEFAULT_LOADING_PREVIEW_TRAIL_RATIO_PERCENT

        override fun build(context: BuildContext): Widget {
            val speed = LOADING_PREVIEW_SPEEDS[selectedSpeedIndex]
            val barWidth = (widget.screenProfile.logicalWidth - LauncherSpacing.CONTENT_HORIZONTAL * 2)
                .coerceAtLeast(12)
                .coerceAtMost(120)
            val blockWidth = previewBlockWidth.coerceIn(1, barWidth)
            val trailWidth = loadingPreviewTrailWidth(blockWidth, previewTrailRatioPercent)
            return Column(
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                mainAxisSize = MainAxisSize.MAX,
                spacing = 0,
                children = listOf(
                    Expanded(
                        child = SingleChildScrollView(
                            state = scrollState,
                            controller = scrollController,
                            child = Padding(
                                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                                vertical = LauncherSpacing.CONTENT_VERTICAL,
                                child = Column(
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    mainAxisSize = MainAxisSize.MIN,
                                    spacing = LauncherSpacing.SETTINGS_SECTION_GAP,
                                    children = listOf(
                                        loadingPreviewGroup(
                                            label = "LIVE SCAN",
                                            theme = widget.theme,
                                            child = AnimatedPixelLoadingBar(
                                                vsync = widget.vsync,
                                                width = barWidth,
                                                height = previewHeight,
                                                color = widget.theme.semantic.info,
                                                blockWidth = blockWidth,
                                                trailWidth = trailWidth,
                                                cycleFrames = speed.cycleFrames,
                                                key = "loading-preview-live",
                                            ),
                                        ),
                                        loadingPreviewGroup(
                                            label = "PROGRESS 35",
                                            theme = widget.theme,
                                            child = PixelLoadingBar(
                                                progress = 0.35f,
                                                width = barWidth,
                                                height = previewHeight,
                                                color = widget.theme.semantic.warning,
                                                blockWidth = blockWidth,
                                                trailWidth = trailWidth,
                                                key = "loading-preview-35",
                                            ),
                                        ),
                                        loadingPreviewGroup(
                                            label = "REVERSE 65",
                                            theme = widget.theme,
                                            child = PixelLoadingBar(
                                                progress = 0.65f,
                                                width = barWidth,
                                                height = previewHeight,
                                                color = widget.theme.semantic.success,
                                                blockWidth = blockWidth,
                                                trailWidth = trailWidth,
                                                reversed = true,
                                                key = "loading-preview-reverse",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                    loadingPreviewControls(
                        speed = speed,
                        height = previewHeight,
                        blockWidth = blockWidth,
                        trailRatioPercent = previewTrailRatioPercent,
                        theme = widget.theme,
                        onSpeedDecrease = boundedAction(selectedSpeedIndex > 0) { adjustSpeed(-1) },
                        onSpeedIncrease = boundedAction(selectedSpeedIndex < LOADING_PREVIEW_SPEEDS.lastIndex) {
                            adjustSpeed(+1)
                        },
                        onHeightDecrease = boundedAction(previewHeight > LOADING_PREVIEW_MIN_HEIGHT) {
                            adjustHeight(-LOADING_PREVIEW_HEIGHT_STEP)
                        },
                        onHeightIncrease = boundedAction(previewHeight < LOADING_PREVIEW_MAX_HEIGHT) {
                            adjustHeight(+LOADING_PREVIEW_HEIGHT_STEP)
                        },
                        onBlockDecrease = boundedAction(previewBlockWidth > LOADING_PREVIEW_MIN_BLOCK_WIDTH) {
                            adjustBlockWidth(-LOADING_PREVIEW_BLOCK_WIDTH_STEP)
                        },
                        onBlockIncrease = boundedAction(previewBlockWidth < LOADING_PREVIEW_MAX_BLOCK_WIDTH) {
                            adjustBlockWidth(+LOADING_PREVIEW_BLOCK_WIDTH_STEP)
                        },
                        onTrailRatioDecrease = boundedAction(
                            previewTrailRatioPercent > LOADING_PREVIEW_MIN_TRAIL_RATIO_PERCENT,
                        ) {
                            adjustTrailRatio(-LOADING_PREVIEW_TRAIL_RATIO_STEP_PERCENT)
                        },
                        onTrailRatioIncrease = boundedAction(
                            previewTrailRatioPercent < LOADING_PREVIEW_MAX_TRAIL_RATIO_PERCENT,
                        ) {
                            adjustTrailRatio(+LOADING_PREVIEW_TRAIL_RATIO_STEP_PERCENT)
                        },
                    ),
                ),
            )
        }

        private fun adjustSpeed(delta: Int) {
            setState {
                selectedSpeedIndex = (selectedSpeedIndex + delta).coerceIn(0, LOADING_PREVIEW_SPEEDS.lastIndex)
            }
        }

        private fun adjustHeight(delta: Int) {
            setState {
                previewHeight = (previewHeight + delta)
                    .coerceIn(LOADING_PREVIEW_MIN_HEIGHT, LOADING_PREVIEW_MAX_HEIGHT)
            }
        }

        private fun adjustBlockWidth(delta: Int) {
            setState {
                previewBlockWidth = (previewBlockWidth + delta)
                    .coerceIn(LOADING_PREVIEW_MIN_BLOCK_WIDTH, LOADING_PREVIEW_MAX_BLOCK_WIDTH)
            }
        }

        private fun adjustTrailRatio(delta: Int) {
            setState {
                previewTrailRatioPercent = (previewTrailRatioPercent + delta)
                    .coerceIn(LOADING_PREVIEW_MIN_TRAIL_RATIO_PERCENT, LOADING_PREVIEW_MAX_TRAIL_RATIO_PERCENT)
            }
        }
    }
}

private data class LoadingPreviewSpeed(
    val label: String,
    val cycleFrames: Int,
)

private const val DEFAULT_LOADING_PREVIEW_SPEED_INDEX = 1
private const val DEFAULT_LOADING_PREVIEW_HEIGHT = 9
private const val DEFAULT_LOADING_PREVIEW_BLOCK_WIDTH = 11
private const val DEFAULT_LOADING_PREVIEW_TRAIL_RATIO_PERCENT = 65
private const val LOADING_PREVIEW_MIN_HEIGHT = 5
private const val LOADING_PREVIEW_MAX_HEIGHT = 17
private const val LOADING_PREVIEW_HEIGHT_STEP = 2
private const val LOADING_PREVIEW_MIN_BLOCK_WIDTH = 5
private const val LOADING_PREVIEW_MAX_BLOCK_WIDTH = 21
private const val LOADING_PREVIEW_BLOCK_WIDTH_STEP = 1
private const val LOADING_PREVIEW_MIN_TRAIL_RATIO_PERCENT = 0
private const val LOADING_PREVIEW_MAX_TRAIL_RATIO_PERCENT = 65
private const val LOADING_PREVIEW_TRAIL_RATIO_STEP_PERCENT = 5
private val LOADING_PREVIEW_SPEEDS = listOf(
    LoadingPreviewSpeed(label = "0.5X", cycleFrames = 144),
    LoadingPreviewSpeed(label = "1X", cycleFrames = 96),
    LoadingPreviewSpeed(label = "1.5X", cycleFrames = 64),
    LoadingPreviewSpeed(label = "2X", cycleFrames = 48),
)

private fun loadingPreviewControls(
    speed: LoadingPreviewSpeed,
    height: Int,
    blockWidth: Int,
    trailRatioPercent: Int,
    theme: LauncherTheme,
    onSpeedDecrease: (() -> Unit)?,
    onSpeedIncrease: (() -> Unit)?,
    onHeightDecrease: (() -> Unit)?,
    onHeightIncrease: (() -> Unit)?,
    onBlockDecrease: (() -> Unit)?,
    onBlockIncrease: (() -> Unit)?,
    onTrailRatioDecrease: (() -> Unit)?,
    onTrailRatioIncrease: (() -> Unit)?,
): Widget = Padding(
    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
    vertical = LauncherSpacing.ROW_SPACING,
    child = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        mainAxisSize = MainAxisSize.MIN,
        spacing = LauncherSpacing.ROW_SPACING,
        children = listOf(
            SettingsPixelSizeControl(
                title = "SPD",
                valueLabel = speed.label,
                theme = theme,
                onDecrease = onSpeedDecrease,
                onIncrease = onSpeedIncrease,
                key = "loading-preview-speed-control",
            ),
            SettingsPixelSizeControl(
                title = "HEIGHT",
                valueLabel = "${height}PX",
                theme = theme,
                onDecrease = onHeightDecrease,
                onIncrease = onHeightIncrease,
                key = "loading-preview-height-control",
            ),
            SettingsPixelSizeControl(
                title = "BLOCK",
                valueLabel = "${blockWidth}PX",
                theme = theme,
                onDecrease = onBlockDecrease,
                onIncrease = onBlockIncrease,
                key = "loading-preview-block-control",
            ),
            SettingsPixelSizeControl(
                title = "TRAIL",
                valueLabel = "$trailRatioPercent%",
                theme = theme,
                onDecrease = onTrailRatioDecrease,
                onIncrease = onTrailRatioIncrease,
                key = "loading-preview-trail-control",
            ),
        ),
    ),
)

private fun loadingPreviewTrailWidth(blockWidth: Int, ratioPercent: Int): Int =
    ((blockWidth * ratioPercent.coerceIn(0, 100)) + 50) / 100

private fun boundedAction(
    enabled: Boolean,
    action: () -> Unit,
): (() -> Unit)? = if (enabled) action else null

private fun loadingPreviewGroup(
    label: String,
    theme: LauncherTheme,
    child: Widget,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MIN,
    spacing = LauncherSpacing.ROW_SPACING,
    children = listOf(
        loadingPreviewText(label, theme.text.muted),
        child,
        SizedBox(height = 1),
    ),
)

private fun loadingPreviewText(
    text: String,
    color: PixelColor,
): Widget = Text(
    text,
    style = TextStyle(color = color),
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)
