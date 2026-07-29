package com.purride.pixelshowcase

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Alignment
import com.purride.pixelui.Column
import com.purride.pixelui.ConfirmDialog
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Divider
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.Gap
import com.purride.pixelui.ListTile
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelHostView
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
import com.purride.pixelui.ScrollController
import com.purride.pixelui.pixelRouteDestination
import kotlin.time.Duration.Companion.milliseconds

/** showcase 的三个目的地。 */
enum class ShowcaseRoute(val routeName: String) {
    HOME("home"),
    DEMOS("demos"),
    WIDGETS("widgets"),
    TODO("todo"),
    STOPWATCH("stopwatch"),
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

    /** 根 widget：Navigator 承担页面栈与过渡动画。 */
    fun buildRoot(): Widget = PixelNavigator(
        initialRequest = requestFor(ShowcaseRoute.HOME),
        vsync = hostView.tickerProvider,
        transitionDuration = 220.milliseconds,
        defaultTransition = PixelRouteTransition.SlideHorizontal,
        key = "showcase-navigator",
    )

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
            onChanged = { mutate { } },
        )
        ShowcaseRoute.STOPWATCH -> StopwatchPage(
            controller = stopwatch,
            listState = lapListState,
            listController = lapListController,
            header = pageHeader("STOPWATCH"),
        )
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
                        title = "ABOUT",
                        subtitle = "WHAT IS THIS",
                        route = ShowcaseRoute.ABOUT,
                    ),
                    Expanded(child = Gap(0)),
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
                ),
            ),
        ),
    )

}

/** showcase 的极简主题：一底四阶，全应用共享。 */
object ShowcaseTheme {
    val BACKGROUND = PixelColor.fromRgb(10, 14, 26)
    val TITLE = PixelColor.fromRgb(236, 244, 255)
    val DIM = PixelColor.fromRgb(140, 165, 200)
    val FAINT = PixelColor.fromRgb(80, 100, 130)
    val BORDER = PixelColor.fromRgb(70, 95, 130)
}
