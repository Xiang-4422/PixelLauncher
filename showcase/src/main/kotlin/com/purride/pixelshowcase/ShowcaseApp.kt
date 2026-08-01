package com.purride.pixelshowcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Checkbox
import com.purride.pixelui.CustomPaint
import com.purride.pixelui.PixelDebugOverlay
import com.purride.pixelui.PixelHostFrameStats
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.Positioned
import com.purride.pixelui.SizedBox
import com.purride.pixelui.ValueListenableBuilder
import com.purride.pixelui.ValueNotifier
import com.purride.pixelui.Divider
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.Gap
import com.purride.pixelui.Icon
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelSystemIcon
import com.purride.pixelui.PixelSystemIconSize
import com.purride.pixelui.PixelSystemIcons
import com.purride.pixelui.PixelNavigator
import com.purride.pixelui.PixelNavigatorState
import com.purride.pixelui.PixelRouteDestination
import com.purride.pixelui.PixelRouteRequest
import com.purride.pixelui.PixelRouteTransition
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Row
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.SingleChildScrollView
import com.purride.pixelui.Slider
import com.purride.pixelui.Stack
import com.purride.pixelui.Stepper
import com.purride.pixelui.Switch
import com.purride.pixelui.Text
import com.purride.pixelui.TextAlign
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.ScrollController
import com.purride.pixelui.pixelRouteDestination
import kotlin.time.Duration.Companion.milliseconds

/** Showcase 内全部可导航的演示目的地。 */
enum class ShowcaseRoute(val routeName: String) {
    HOME("home"),
    DEMOS("demos"),
    WIDGETS("widgets"),
    TODO("todo"),
    STOPWATCH("stopwatch"),
    TIMER("timer"),
    /** 独立像素系统图标的多规格与主题预览页。 */
    ICONS("icons"),
    THEME("theme"),
    ABOUT("about"),
}

/** typed route 参数。 */
data class ShowcaseRouteArguments(val route: ShowcaseRoute)

/**
 * showcase 的应用层：路由、页面、组件画廊的全部状态与构建。
 *
 * 这一层本身就是展品——用引擎的 Navigator（带过渡动画）、组件库与状态驱动
 * 重建，搭出一个真实的多页应用。视觉场景只是其中一个页面。
 */
class ShowcaseAppHost(
    private val hostView: PixelHostView,
    private val director: DemoDirector,
) {
    // ── 组件画廊的演示状态 ────────────────────────────────────────────────────
    private var switchOn = true
    private var checkboxOn = false
    private var sliderValue = 0.6f
    private var stepperValue = 3
    private var segmentIndex = 0
    private var dialogVisible = false

    private val nameController = TextEditingController()
    private val nameState = nameController.create()
    private val galleryScrollController = ScrollController()
    private val galleryScrollState = galleryScrollController.create()

    // ── 示例应用状态 ──────────────────────────────────────────────────────────
    private val todoState = TodoState()
    private val todoInputController = TextEditingController()
    private val todoInputState = todoInputController.create()
    private val todoListController = ScrollController()
    private val todoListState = todoListController.create()
    private val stopwatch = StopwatchController(
        vsync = hostView.tickerProvider,
        onFrame = { hostView.invalidate() },
    )
    private val lapListController = ScrollController()
    private val lapListState = lapListController.create()
    private val beeper = ShowcaseBeeper()
    private val timer = TimerController(
        vsync = hostView.tickerProvider,
        onFrame = { hostView.invalidate() },
        onPhaseChanged = { phase ->
            when (phase) {
                TimerController.Phase.RUNNING -> beeper.confirm()
                TimerController.Phase.FINISHED -> beeper.alarmStart()
                TimerController.Phase.SETUP -> beeper.alarmStop()
                TimerController.Phase.PAUSED -> Unit
            }
        },
    )
    private val homeScrollController = ScrollController()
    private val homeScrollState = homeScrollController.create()
    /** 图标预览页独立持有的滚动控制器。 */
    private val iconScrollController = ScrollController()
    /** 图标预览页与控制器绑定的稳定滚动状态。 */
    private val iconScrollState = iconScrollController.create()

    // ── 性能 HUD ──────────────────────────────────────────────────────────────
    private var hudVisible = false
    private val frameStats = ValueNotifier<PixelHostFrameStats?>(null)

    /** 打开时挂帧统计观察者并开启完整诊断采样；关闭全部还原，零热路径成本。 */
    private fun setHudVisible(visible: Boolean) {
        hudVisible = visible
        if (visible) {
            hostView.frameDiagnosticsEnabled = true
            hostView.frameStatsObserver = { frameStats.value = it }
        } else {
            hostView.frameStatsObserver = null
            hostView.frameDiagnosticsEnabled = false
            frameStats.value = null
        }
        hostView.invalidate()
    }

    private var navigatorState: PixelNavigatorState? = null
    private var currentRoute = ShowcaseRoute.HOME

    private val routeDestinations: Map<ShowcaseRoute, PixelRouteDestination<ShowcaseRouteArguments, Unit>> =
        ShowcaseRoute.entries.associateWith { route ->
            pixelRouteDestination<ShowcaseRouteArguments, Unit>(
                id = route.routeName,
                maintainState = true,
                transition = PixelRouteTransition.SlideHorizontal,
            ) { context, _ ->
                navigatorState = PixelNavigator.of(context)
                buildRoute(route)
            }
        }

    /** 根 widget：主题 provider 包住 Navigator——换 palette 时引擎组件全树跟随。 */
    fun buildRoot(): Widget = PixelTheme(
        tokens = ShowcaseTheme.palette.engineTokens,
        child = Stack(
            children = listOf(
                PixelNavigator(
                    initialRequest = requestFor(ShowcaseRoute.HOME),
                    vsync = hostView.tickerProvider,
                    transitionDuration = 420.milliseconds,
                    defaultTransition = PixelRouteTransition.SlideHorizontal,
                    transitionBuilder = ParticleRouteTransition,
                    key = "showcase-navigator",
                ),
                Positioned(
                    top = 0,
                    right = 0,
                    key = "hud-anchor",
                    child = ValueListenableBuilder(frameStats, key = "hud") { _, stats ->
                        if (hudVisible) {
                            PixelDebugOverlay(stats, inspector = hostView.inspect())
                        } else {
                            SizedBox(width = 0, height = 0)
                        }
                    },
                ),
            ),
        ),
    )

    /** 释放示例应用持有的 ticker 与音频轨；Activity 销毁时统一调用。 */
    fun dispose() {
        stopwatch.dispose()
        timer.dispose()
        beeper.dispose()
    }

    /** BACK：对话框 → 子页 → 交还系统（退出）。 */
    fun handleBack(): Boolean {
        if (dialogVisible) {
            mutate { dialogVisible = false }
            return true
        }
        if (currentRoute != ShowcaseRoute.HOME) {
            navigateHome()
            return true
        }
        return false
    }

    private fun navigate(route: ShowcaseRoute) {
        if (route == currentRoute) return
        currentRoute = route
        if (route == ShowcaseRoute.DEMOS) director.resume() else director.pause()
        navigatorState?.push(requestFor(route))
        hostView.invalidate()
    }

    private fun navigateHome() {
        if (currentRoute == ShowcaseRoute.STOPWATCH) stopwatch.pause()
        if (currentRoute == ShowcaseRoute.TIMER) timer.pausePage()
        currentRoute = ShowcaseRoute.HOME
        director.pause()
        navigatorState?.pop()
        hostView.invalidate()
    }

    private fun requestFor(route: ShowcaseRoute): PixelRouteRequest<ShowcaseRouteArguments, Unit> =
        PixelRouteRequest(
            destination = checkNotNull(routeDestinations[route]),
            arguments = ShowcaseRouteArguments(route),
        )

    /** 状态变更 + 触发重建：引擎的 content lambda 每个渲染波次读最新状态。 */
    private fun mutate(block: () -> Unit) {
        block()
        hostView.invalidate()
    }

    // ── 页面 ──────────────────────────────────────────────────────────────────

    private fun buildRoute(route: ShowcaseRoute): Widget = when (route) {
        ShowcaseRoute.HOME -> homePage()
        ShowcaseRoute.DEMOS -> DemoCanvas(director)
        ShowcaseRoute.WIDGETS -> widgetsPage()
        ShowcaseRoute.TODO -> TodoPage(
            state = todoState,
            inputState = todoInputState,
            inputController = todoInputController,
            listState = todoListState,
            listController = todoListController,
            header = pageHeader("TODO"),
            onChanged = {
                beeper.tick()
                mutate { }
            },
        )
        ShowcaseRoute.STOPWATCH -> StopwatchPage(
            controller = stopwatch,
            listState = lapListState,
            listController = lapListController,
            header = pageHeader("STOPWATCH"),
        )
        ShowcaseRoute.TIMER -> TimerPage(
            controller = timer,
            header = pageHeader("TIMER"),
        )
        ShowcaseRoute.ICONS -> iconsPage()
        ShowcaseRoute.THEME -> themePage()
        ShowcaseRoute.ABOUT -> aboutPage()
    }

    private fun homePage(): Widget = Container(
        fillColor = ShowcaseTheme.BACKGROUND,
        child = Padding(
            padding = EdgeInsets.symmetric(horizontal = 10, vertical = 8),
            child = Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                spacing = 3,
                children = listOf(
                    Gap(10),
                    Text("PIXEL ENGINE", color = ShowcaseTheme.TITLE, textAlign = TextAlign.CENTER),
                    Text("SHOWCASE", color = ShowcaseTheme.DIM, textAlign = TextAlign.CENTER),
                    Gap(12),
                    Expanded(
                        child = SingleChildScrollView(
                            child = Column(
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                spacing = 3,
                                children = listOf(
                                    menuTile(
                                        title = "DEMOS",
                                        subtitle = "10 VISUAL SCENES",
                                        route = ShowcaseRoute.DEMOS,
                                    ),
                                    menuTile(
                                        title = "WIDGETS",
                                        subtitle = "WIDGET GALLERY",
                                        route = ShowcaseRoute.WIDGETS,
                                    ),
                                    menuTile(
                                        title = "TODO",
                                        subtitle = "STATE + LIST",
                                        route = ShowcaseRoute.TODO,
                                    ),
                                    menuTile(
                                        title = "STOPWATCH",
                                        subtitle = "TICKER TIME",
                                        route = ShowcaseRoute.STOPWATCH,
                                    ),
                                    menuTile(
                                        title = "TIMER",
                                        subtitle = "COUNTDOWN ALERT",
                                        route = ShowcaseRoute.TIMER,
                                    ),
                                    menuTile(
                                        title = "ICONS",
                                        subtitle = "PIXEL SYSTEM SET",
                                        route = ShowcaseRoute.ICONS,
                                    ),
                                    menuTile(
                                        title = "THEME",
                                        subtitle = "5 MACHINE MOODS",
                                        route = ShowcaseRoute.THEME,
                                    ),
                                    menuTile(
                                        title = "ABOUT",
                                        subtitle = "WHAT IS THIS",
                                        route = ShowcaseRoute.ABOUT,
                                    ),
                                ),
                            ),
                            state = homeScrollState,
                            controller = homeScrollController,
                        ),
                    ),
                    Text("TAP A CARD TO ENTER", color = ShowcaseTheme.FAINT, textAlign = TextAlign.CENTER),
                ),
            ),
        ),
    )

    private fun menuTile(title: String, subtitle: String, route: ShowcaseRoute): Widget = Container(
        borderColor = ShowcaseTheme.BORDER,
        padding = EdgeInsets.symmetric(horizontal = 4, vertical = 2),
        child = ListTile(
            title = Text(title, color = ShowcaseTheme.TITLE),
            subtitle = Text(subtitle, color = ShowcaseTheme.DIM),
            trailing = Text(">", color = ShowcaseTheme.DIM),
            onTap = { navigate(route) },
            semanticLabel = title,
        ),
    )

    // ── 组件画廊 ──────────────────────────────────────────────────────────────

    private fun widgetsPage(): Widget {
        val page = Container(
            fillColor = ShowcaseTheme.BACKGROUND,
            child = Padding(
                padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
                child = Column(
                    mainAxisSize = MainAxisSize.MAX,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                    spacing = 0,
                    children = listOf(
                        pageHeader("WIDGETS"),
                        Expanded(
                            child = SingleChildScrollView(
                                child = Column(
                                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                    spacing = 4,
                                    children = gallerySections(),
                                ),
                                state = galleryScrollState,
                                controller = galleryScrollController,
                            ),
                        ),
                    ),
                ),
            ),
        )
        if (!dialogVisible) return page
        return Stack(
            children = listOf(
                page,
                ConfirmDialog(
                    title = "PIXEL DIALOG",
                    message = "MODAL LAYER WITH BARRIER, BACK AND OUTSIDE-TAP DISMISS.",
                    onConfirm = { mutate { dialogVisible = false } },
                    onCancel = { mutate { dialogVisible = false } },
                    confirmText = "OK",
                    cancelText = "CLOSE",
                ),
            ),
        )
    }

    private fun gallerySections(): List<Widget> = listOf(
        sectionHeader("CONTROLS"),
        labeledRow("SWITCH", Switch(checked = switchOn, onChanged = { mutate { switchOn = it } })),
        labeledRow("CHECKBOX", Checkbox(checked = checkboxOn, onChanged = { mutate { checkboxOn = it } })),
        labeledRow("STEPPER", Stepper(value = stepperValue, range = 0..9, onChanged = { mutate { stepperValue = it } })),
        SegmentedControl(
            labels = listOf("DAY", "NIGHT", "AUTO"),
            selectedIndex = segmentIndex,
            onSelected = { mutate { segmentIndex = it } },
        ),

        sectionHeader("SLIDER + PROGRESS"),
        Slider(value = sliderValue, onDrag = { mutate { sliderValue = it } }),
        ProgressBar(progress = sliderValue),
        Text("DRAG THE SLIDER, THE BAR FOLLOWS", color = ShowcaseTheme.FAINT),

        sectionHeader("INPUT"),
        TextField(
            state = nameState,
            controller = nameController,
            placeholder = "TYPE SOMETHING",
        ),

        sectionHeader("DIALOG"),
        TextButton(text = "OPEN DIALOG", onPressed = { mutate { dialogVisible = true } }),

        sectionHeader("LIST"),
        ListTile(
            title = Text("FIRST ITEM", color = ShowcaseTheme.TITLE),
            subtitle = Text("WITH SUBTITLE", color = ShowcaseTheme.DIM),
            trailing = Text("42", color = ShowcaseTheme.DIM),
            onTap = {},
        ),
        Divider(),
        ListTile(
            title = Text("SECOND ITEM", color = ShowcaseTheme.TITLE),
            subtitle = Text("TAP DOES NOTHING", color = ShowcaseTheme.DIM),
            onTap = {},
        ),
        Gap(8),
    )

    private fun labeledRow(label: String, control: Widget): Widget = Row(
        spacing = 4,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        children = listOf(
            Expanded(child = Text(label, color = ShowcaseTheme.DIM)),
            control,
        ),
    )

    private fun sectionHeader(text: String): Widget = Padding(
        padding = EdgeInsets.only(top = 6, bottom = 1),
        child = Text(text, color = ShowcaseTheme.FAINT),
    )

    private fun pageHeader(title: String): Widget = Padding(
        padding = EdgeInsets.only(bottom = 4),
        child = Row(
            spacing = 4,
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            children = listOf(
                TextButton(text = "< BACK", onPressed = { navigateHome() }),
                Expanded(child = Text(title, color = ShowcaseTheme.TITLE, textAlign = TextAlign.END)),
            ),
        ),
    )

    // ── 系统图标 ──────────────────────────────────────────────────────────────

    /** 展示所有系统图标在四档画布规格和当前主题颜色下的实际栅格结果。 */
    private fun iconsPage(): Widget = Container(
        fillColor = ShowcaseTheme.BACKGROUND,
        child = Padding(
            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
            child = Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                spacing = 0,
                children = listOf(
                    pageHeader("ICONS"),
                    Expanded(
                        child = SingleChildScrollView(
                            child = Column(
                                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                                spacing = 4,
                                children = listOf(
                                    Text(
                                        "INDEPENDENT FROM FONT FAMILY",
                                        color = ShowcaseTheme.FAINT,
                                    ),
                                    iconCategory(
                                        title = "STYLE",
                                        icons = listOf(
                                            PixelSystemIcon.SQUARE,
                                            PixelSystemIcon.CIRCLE,
                                            PixelSystemIcon.DIAMOND,
                                        ),
                                    ),
                                    iconCategory(
                                        title = "ALIGN",
                                        icons = listOf(
                                            PixelSystemIcon.ALIGN_LEFT,
                                            PixelSystemIcon.ALIGN_CENTER,
                                            PixelSystemIcon.ALIGN_RIGHT,
                                        ),
                                    ),
                                    iconCategory(
                                        title = "MODE",
                                        icons = listOf(
                                            PixelSystemIcon.DAY,
                                            PixelSystemIcon.AUTO,
                                            PixelSystemIcon.NIGHT,
                                        ),
                                    ),
                                    iconCategory(
                                        title = "ACTIONS",
                                        icons = listOf(
                                            PixelSystemIcon.BACK,
                                            PixelSystemIcon.FORWARD,
                                            PixelSystemIcon.ARROW_UP,
                                            PixelSystemIcon.ARROW_DOWN,
                                            PixelSystemIcon.VOICEMAIL,
                                            PixelSystemIcon.CLOSE,
                                            PixelSystemIcon.SEARCH,
                                            PixelSystemIcon.EDIT,
                                            PixelSystemIcon.DELETE,
                                            PixelSystemIcon.ADD,
                                            PixelSystemIcon.REMOVE,
                                        ),
                                    ),
                                    Gap(8),
                                ),
                            ),
                            state = iconScrollState,
                            controller = iconScrollController,
                        ),
                    ),
                ),
            ),
        ),
    )

    /** 为一个语义分类依次展示 7、9、11 与 15 像素图标。 */
    private fun iconCategory(title: String, icons: List<PixelSystemIcon>): Widget = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        spacing = 3,
        children = listOf(sectionHeader(title)) + PixelSystemIconSize.entries.map { size ->
            iconSizePreview(size = size, icons = icons)
        },
    )

    /** 展示同一规格的一组图标，窄屏下由 Wrap 自动换行。 */
    private fun iconSizePreview(size: PixelSystemIconSize, icons: List<PixelSystemIcon>): Widget = Column(
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
        spacing = 2,
        children = listOf(
            Text("${size.pixels}PX", color = ShowcaseTheme.DIM),
            Wrap(
                spacing = 3,
                runSpacing = 3,
                children = icons.map { icon -> iconPreviewTile(icon = icon, size = size) },
            ),
        ),
    )

    /** 使用当前 Showcase 主题色绘制一个带名称的图标预览块。 */
    private fun iconPreviewTile(icon: PixelSystemIcon, size: PixelSystemIconSize): Widget = Container(
        borderColor = ShowcaseTheme.BORDER,
        padding = EdgeInsets.all(2),
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.CENTER,
            spacing = 2,
            children = listOf(
                Icon(
                    PixelSystemIcons.tinted(
                        icon = icon,
                        size = size,
                        color = ShowcaseTheme.TITLE,
                    ),
                ),
                Text(iconPreviewLabel(icon), color = ShowcaseTheme.DIM),
            ),
        ),
    )

    /** 返回图标画廊使用的紧凑名称，完整操作语义仍由图标枚举保存。 */
    private fun iconPreviewLabel(icon: PixelSystemIcon): String = when (icon) {
        PixelSystemIcon.SQUARE -> "SQUARE"
        PixelSystemIcon.CIRCLE -> "CIRCLE"
        PixelSystemIcon.DIAMOND -> "DIAMOND"
        PixelSystemIcon.DAY -> "DAY"
        PixelSystemIcon.AUTO -> "AUTO"
        PixelSystemIcon.NIGHT -> "NIGHT"
        PixelSystemIcon.ALIGN_LEFT -> "LEFT"
        PixelSystemIcon.ALIGN_CENTER -> "CENTER"
        PixelSystemIcon.ALIGN_RIGHT -> "RIGHT"
        PixelSystemIcon.BACK -> "BACK"
        PixelSystemIcon.FORWARD -> "FORWARD"
        PixelSystemIcon.ARROW_UP -> "UP"
        PixelSystemIcon.ARROW_DOWN -> "DOWN"
        PixelSystemIcon.VOICEMAIL -> "VOICEMAIL"
        PixelSystemIcon.CLOSE -> "CLOSE"
        PixelSystemIcon.SEARCH -> "SEARCH"
        PixelSystemIcon.EDIT -> "EDIT"
        PixelSystemIcon.DELETE -> "DELETE"
        PixelSystemIcon.ADD -> "ADD"
        PixelSystemIcon.REMOVE -> "REMOVE"
    }

    // ── 主题 ──────────────────────────────────────────────────────────────────

    /** 主题页：一套 palette 一张卡，点选立即换整机气质。 */
    private fun themePage(): Widget = Container(
        fillColor = ShowcaseTheme.BACKGROUND,
        child = Padding(
            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
            child = Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                spacing = 3,
                children = listOf(
                    pageHeader("THEME"),
                    Text(
                        "PICK A MACHINE MOOD",
                        color = ShowcaseTheme.FAINT,
                    ),
                    Gap(2),
                ) + ShowcasePalette.entries.map { paletteTile(it) },
            ),
        ),
    )

    private fun paletteTile(palette: ShowcasePalette): Widget {
        val selected = ShowcaseTheme.palette == palette
        return Container(
            borderColor = if (selected) ShowcaseTheme.TITLE else ShowcaseTheme.BORDER,
            padding = EdgeInsets.symmetric(horizontal = 4, vertical = 2),
            child = ListTile(
                title = Text(palette.label, color = ShowcaseTheme.TITLE),
                subtitle = Text(palette.subtitle, color = ShowcaseTheme.DIM),
                trailing = paletteSwatch(palette),
                onTap = { mutate { ShowcaseTheme.palette = palette } },
                semanticLabel = palette.label,
            ),
        )
    }

    /** 色板预览条：这套 palette 的四个主要颜色，一色一块。 */
    private fun paletteSwatch(palette: ShowcasePalette): Widget {
        val colors = listOf(palette.title, palette.dim, palette.faint, palette.alert)
        return CustomPaint(
            width = colors.size * SWATCH_STEP - 1,
            height = SWATCH_SIZE,
            key = "swatch-${palette.name}",
        ) {
            colors.forEachIndexed { index, color ->
                fillRect(index * SWATCH_STEP, 0, SWATCH_SIZE, SWATCH_SIZE, color)
            }
        }
    }

    // ── ABOUT ─────────────────────────────────────────────────────────────────

    private fun aboutPage(): Widget = Container(
        fillColor = ShowcaseTheme.BACKGROUND,
        child = Padding(
            padding = EdgeInsets.symmetric(horizontal = 8, vertical = 6),
            child = Column(
                mainAxisSize = MainAxisSize.MAX,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                spacing = 3,
                children = listOf(
                    pageHeader("ABOUT"),
                    Text("PIXEL ENGINE", color = ShowcaseTheme.TITLE),
                    Text("A RETAINED-MODE UI ENGINE", color = ShowcaseTheme.DIM, softWrap = true, maxLines = 3),
                    Text("WHERE EVERY UNIT IS ONE", color = ShowcaseTheme.DIM, softWrap = true, maxLines = 3),
                    Text("LOGICAL PIXEL.", color = ShowcaseTheme.DIM, softWrap = true, maxLines = 3),
                    Gap(6),
                    Text("THIS APP DEPENDS ONLY ON", color = ShowcaseTheme.DIM),
                    Text("THE ENGINE MODULE.", color = ShowcaseTheme.DIM),
                    Gap(6),
                    Text("NAVIGATION, TRANSITIONS,", color = ShowcaseTheme.DIM),
                    Text("WIDGETS, TEXT, GESTURES,", color = ShowcaseTheme.DIM),
                    Text("AND EVERY DEMO FRAME ARE", color = ShowcaseTheme.DIM),
                    Text("ALL DRAWN BY THE ENGINE.", color = ShowcaseTheme.DIM),
                    Gap(8),
                    Divider(),
                    labeledRow(
                        "PERF HUD",
                        Switch(checked = hudVisible, onChanged = { setHudVisible(it) }),
                    ),
                    Text("FPS / FRAME PHASES / DROPS", color = ShowcaseTheme.FAINT),
                ),
            ),
        ),
    )

}

/** showcase 的极简主题：一底四阶，全应用共享。 */
/** 主题页色板预览块的边长与步进（块 + 1px 间隔）。 */
private const val SWATCH_SIZE = 7
private const val SWATCH_STEP = SWATCH_SIZE + 1

/** 应用自绘部分的当前配色：全部委托到选中的 palette，切换即全局生效。 */
object ShowcaseTheme {
    var palette: ShowcasePalette = ShowcasePalette.MIDNIGHT

    val BACKGROUND: PixelColor get() = palette.background
    val TITLE: PixelColor get() = palette.title
    val DIM: PixelColor get() = palette.dim
    val FAINT: PixelColor get() = palette.faint
    val BORDER: PixelColor get() = palette.border
    val ALERT: PixelColor get() = palette.alert
}
