package com.purride.pixeldemo.showcase

import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BottomSheet
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Checkbox
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.Dialog
import com.purride.pixelui.Dropdown
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.FormFieldDecoration
import com.purride.pixelui.IconButton
import com.purride.pixelui.ListTile
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Menu
import com.purride.pixelui.NavigationBar
import com.purride.pixelui.NavigationRail
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelColorRole
import com.purride.pixelui.PixelComponentColorTokens
import com.purride.pixelui.PixelComponentTokens
import com.purride.pixelui.PixelControlState
import com.purride.pixelui.PixelControlStateSet
import com.purride.pixelui.PixelIconData
import com.purride.pixelui.PixelMenuItem
import com.purride.pixelui.PixelNavigationDestination
import com.purride.pixelui.PixelSurface
import com.purride.pixelui.PixelSurfaceDecoration
import com.purride.pixelui.PixelTheme
import com.purride.pixelui.PixelThemeTokens
import com.purride.pixelui.ProgressBar
import com.purride.pixelui.Radio
import com.purride.pixelui.RefreshIndicator
import com.purride.pixelui.Scrollbar
import com.purride.pixelui.SegmentedControl
import com.purride.pixelui.Semantics
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Slidable
import com.purride.pixelui.Slider
import com.purride.pixelui.Snackbar
import com.purride.pixelui.State
import com.purride.pixelui.StatefulWidget
import com.purride.pixelui.Switch
import com.purride.pixelui.Tabs
import com.purride.pixelui.Text
import com.purride.pixelui.TextButton
import com.purride.pixelui.TextField
import com.purride.pixelui.Toast
import com.purride.pixelui.Tooltip
import com.purride.pixelui.ValueAdjuster
import com.purride.pixelui.Widget
import com.purride.pixelui.Wrap
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixeldemo.catalog.DemoCatalog
import kotlin.time.Duration.Companion.milliseconds

/**
 * 可发现的 M5-2 主题展厅，展示完整主题图谱与所有标准组件族的状态契约。
 *
 * 延迟构建避免独立读取本文件数据时反向初始化 [DemoCatalog]，同时 Catalog 自身注册该
 * 场景时所需的 `theme` 分类已经完成初始化。
 */
internal val ThemeShowcaseScene: ComponentExampleScene by lazy {
    ComponentExampleScene(
        id = "theme_showcase",
        title = "Theme Showcase",
        summary = "Light、Dark、高对比度与自定义主题的完整 token 和组件状态矩阵",
        category = DemoCatalog.theme,
        tags = setOf(
            "theme",
            "tokens",
            "light",
            "dark",
            "high-contrast",
            "custom",
            "component-state",
            "accessibility",
        ),
        apis = setOf(
            "PixelTheme",
            "PixelThemeTokens",
            "PixelColorScheme",
            "PixelTypographyTokens",
            "PixelSpacingTokens",
            "PixelSizeTokens",
            "PixelRadiusTokens",
            "PixelBorderTokens",
            "PixelElevationTokens",
            "PixelMotionThemeData",
            "PixelComponentTokens",
            "PixelLabelTokens",
            "PixelControlStateSet",
            "OutlinedButton",
            "TextButton",
            "IconButton",
            "PixelIconData",
            "TextField",
            "FormFieldDecoration",
            "ListTile",
            "Checkbox",
            "Radio",
            "Switch",
            "Slider",
            "Tabs",
            "SegmentedControl",
            "NavigationBar",
            "NavigationRail",
            "PixelNavigationDestination",
            "ValueAdjuster",
            "Menu",
            "Dropdown",
            "Slidable",
            "Dialog",
            "BottomSheet",
            "Toast",
            "Snackbar",
            "Tooltip",
            "ProgressBar",
            "RefreshIndicator",
            "Scrollbar",
        ),
        bodyBuilder = { themeShowcaseBodyForTest() },
    )
}

/** 当前主题语义节点和 retained widget 共用的稳定测试键。 */
internal const val ThemeShowcaseActivePresetKey: String = "theme-showcase-active-preset"

/**
 * 描述展厅中的一个可切换主题。
 *
 * @property id 用于测试键、语义值和持久识别的稳定标识。
 * @property buttonLabel 主题选择按钮显示的短标签。
 * @property description 对该主题用途和对比度策略的简短说明。
 * @property tokens 注入预览子树的完整不可变 token 图谱。
 */
internal data class ThemeShowcasePreset(
    val id: String,
    val buttonLabel: String,
    val description: String,
    val tokens: PixelThemeTokens,
)

/**
 * 一个可视化基础 token 样本。
 *
 * @property name token 在主题模型中的稳定属性名。
 * @property value 面向开发者显示的可读值。
 * @property previewColor 仅颜色 token 使用的像素色块。
 */
internal data class ThemeTokenSample(
    val name: String,
    val value: String,
    val previewColor: PixelColor? = null,
)

/**
 * 一个完整基础 token 分组。
 *
 * @property id 与 PixelThemeTokens 属性一致的稳定分组标识。
 * @property title 展厅中的分组标题。
 * @property samples 该分组的全部基础 token 样本。
 */
internal data class ThemeFoundationSampleGroup(
    val id: String,
    val title: String,
    val samples: List<ThemeTokenSample>,
)

/**
 * 一个标准组件族及其主题 token 解析入口。
 *
 * @property id 与 PixelComponentTokens 属性一致的稳定标识。
 * @property title 展厅中显示的组件族名称。
 * @property resolveTokens 从完整组件 token 图谱选出该组件族。
 */
internal data class ThemeComponentFamily(
    val id: String,
    val title: String,
    val resolveTokens: (PixelComponentTokens) -> PixelComponentColorTokens,
)

/**
 * 一个由真实公开工厂构建的生产组件样本。
 *
 * @property id 与组件 token 属性和画廊构建分支共用的稳定标识。
 * @property factoryName 必须直接调用的公开组件工厂名称。
 * @property semanticLabel 真实组件在离屏验收中必须导出的主语义标签。
 */
internal data class ThemeProductionComponentSample(
    val id: String,
    val factoryName: String,
    val semanticLabel: String,
)

/**
 * 状态矩阵的一列。
 *
 * @property id 用于稳定测试键的状态标识。
 * @property title 完整状态名称。
 * @property abbreviation 紧凑预览单元中显示的缩写。
 * @property states 交给组件 token 解析器的标准状态集合。
 */
internal data class ThemeShowcaseState(
    val id: String,
    val title: String,
    val abbreviation: String,
    val states: PixelControlStateSet,
)

/** 新增图标控件共用的确定性 5×5 alpha 蒙版。 */
private val ThemeShowcaseControlIcon: PixelIconData = PixelIconData(
    bitmap = PixelBitmap(
        width = 5,
        height = 5,
        pixels = intArrayOf(
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.White.argb,
            PixelColor.Transparent.argb,
        ),
    ),
)

/** NavigationBar 与 NavigationRail 共用的稳定业务目的地。 */
private val ThemeShowcaseNavigationDestinations: List<PixelNavigationDestination> = listOf(
    PixelNavigationDestination(
        id = "home",
        label = "HOME",
        icon = ThemeShowcaseControlIcon,
    ),
    PixelNavigationDestination(
        id = "search",
        label = "SEARCH",
        icon = ThemeShowcaseControlIcon,
    ),
    PixelNavigationDestination(
        id = "settings",
        label = "SETTINGS",
        icon = ThemeShowcaseControlIcon,
    ),
)

/** 自定义主题使用的完整 token 图谱，刻意同时改变全部基础 token 类型。 */
private val ThemeShowcaseCustomTokens: PixelThemeTokens = PixelThemeTokens.Dark.copy(
    colors = PixelThemeTokens.Dark.colors.copy(
        background = PixelColor.fromRgb(10, 8, 25),
        surface = PixelColor.fromRgb(24, 18, 42),
        surfaceVariant = PixelColor.fromRgb(48, 35, 70),
        onSurfaceVariant = PixelColor.fromRgb(210, 194, 235),
        outline = PixelColor.fromRgb(205, 164, 255),
        outlineVariant = PixelColor.fromRgb(112, 84, 145),
        primary = PixelColor.fromRgb(70, 235, 215),
        onPrimary = PixelColor.fromRgb(6, 28, 28),
        danger = PixelColor.fromRgb(255, 95, 170),
        onDanger = PixelColor.fromRgb(35, 0, 18),
        warning = PixelColor.fromRgb(255, 210, 75),
        onWarning = PixelColor.fromRgb(40, 24, 0),
        track = PixelColor.fromRgb(72, 52, 94),
        focus = PixelColor.fromRgb(100, 245, 255),
        selection = PixelColor.fromRgb(255, 125, 215),
        shadow = PixelColor.fromRgb(3, 1, 12),
    ),
    typography = PixelThemeTokens.Dark.typography.copy(
        body = PixelThemeTokens.Dark.typography.body.copy(lineSpacing = 1),
        label = PixelThemeTokens.Dark.typography.label.copy(letterSpacing = 1),
        title = PixelThemeTokens.Dark.typography.title.copy(
            colorRole = PixelColorRole.Primary,
            fontScale = 2,
        ),
    ),
    spacing = PixelThemeTokens.Dark.spacing.copy(
        small = 3,
        medium = 5,
        large = 9,
        extraLarge = 14,
    ),
    sizes = PixelThemeTokens.Dark.sizes.copy(
        trackHeight = 8,
        compactControlHeight = 14,
        controlHeight = 18,
        touchTarget = 26,
        overlayMinimumWidth = 44,
    ),
    radii = PixelThemeTokens.Dark.radii.copy(
        small = 2,
        medium = 3,
        large = 5,
    ),
    borders = PixelThemeTokens.Dark.borders.copy(
        thick = 3,
        focus = 2,
    ),
    elevations = PixelThemeTokens.Dark.elevations.copy(
        low = 2,
        medium = 3,
        high = 5,
    ),
    motion = PixelThemeTokens.Dark.motion.copy(
        feedback = PixelThemeTokens.Dark.motion.feedback.copy(duration = 130.milliseconds),
        selection = PixelThemeTokens.Dark.motion.selection.copy(duration = 190.milliseconds),
    ),
    labels = PixelThemeTokens.Dark.labels.copy(
        confirm = "APPLY",
        cancel = "UNDO",
        loading = "SYNCING",
    ),
)

/** 展厅按需求固定提供的五组完整主题，顺序同时定义键盘遍历顺序。 */
internal val ThemeShowcasePresets: List<ThemeShowcasePreset> = listOf(
    ThemeShowcasePreset(
        id = "light",
        buttonLabel = "LIGHT",
        description = "明亮画布、深色文字与标准对比度",
        tokens = PixelThemeTokens.Light,
    ),
    ThemeShowcasePreset(
        id = "dark",
        buttonLabel = "DARK",
        description = "默认暗色像素画布与标准对比度",
        tokens = PixelThemeTokens.Dark,
    ),
    ThemeShowcasePreset(
        id = "high-contrast-dark",
        buttonLabel = "HC DARK",
        description = "暗色画布、高对比文本与高辨识焦点",
        tokens = PixelThemeTokens.HighContrastDark,
    ),
    ThemeShowcasePreset(
        id = "high-contrast-light",
        buttonLabel = "HC LIGHT",
        description = "亮色画布、高对比文本与高辨识焦点",
        tokens = PixelThemeTokens.HighContrastLight,
    ),
    ThemeShowcasePreset(
        id = "custom",
        buttonLabel = "CUSTOM",
        description = "消费者同时覆写颜色、排版、几何、动效与文案",
        tokens = ThemeShowcaseCustomTokens,
    ),
)

/** PixelComponentTokens 中全部 25 个标准组件族，顺序与公共构造器保持一致。 */
internal val ThemeShowcaseComponentFamilies: List<ThemeComponentFamily> = listOf(
    ThemeComponentFamily("button", "OutlinedButton") { components -> components.button },
    ThemeComponentFamily("textButton", "TextButton") { components -> components.textButton },
    ThemeComponentFamily("iconButton", "IconButton") { components -> components.iconButton },
    ThemeComponentFamily("textField", "TextField") { components -> components.textField },
    ThemeComponentFamily("listTile", "ListTile") { components -> components.listTile },
    ThemeComponentFamily("checkbox", "Checkbox") { components -> components.checkbox },
    ThemeComponentFamily("radio", "Radio") { components -> components.radio },
    ThemeComponentFamily("switch", "Switch") { components -> components.switch },
    ThemeComponentFamily("slider", "Slider") { components -> components.slider },
    ThemeComponentFamily("tabs", "Tabs") { components -> components.tabs },
    ThemeComponentFamily("segmented", "SegmentedControl") { components -> components.segmented },
    ThemeComponentFamily("navigationBar", "NavigationBar") { components -> components.navigationBar },
    ThemeComponentFamily("navigationRail", "NavigationRail") { components -> components.navigationRail },
    ThemeComponentFamily("valueAdjuster", "ValueAdjuster") { components -> components.valueAdjuster },
    ThemeComponentFamily("menu", "Menu") { components -> components.menu },
    ThemeComponentFamily("dropdown", "Dropdown") { components -> components.dropdown },
    ThemeComponentFamily("slidable", "Slidable") { components -> components.slidable },
    ThemeComponentFamily("dialog", "Dialog") { components -> components.dialog },
    ThemeComponentFamily("bottomSheet", "BottomSheet") { components -> components.bottomSheet },
    ThemeComponentFamily("toast", "Toast") { components -> components.toast },
    ThemeComponentFamily("snackbar", "Snackbar") { components -> components.snackbar },
    ThemeComponentFamily("tooltip", "Tooltip") { components -> components.tooltip },
    ThemeComponentFamily("progress", "ProgressBar") { components -> components.progress },
    ThemeComponentFamily("refresh", "RefreshIndicator") { components -> components.refresh },
    ThemeComponentFamily("scrollbar", "Scrollbar") { components -> components.scrollbar },
)

/** 生产组件画廊中的精确 25 项公开工厂注册表，顺序与组件 token 图谱一致。 */
internal val ThemeShowcaseProductionComponents: List<ThemeProductionComponentSample> = listOf(
    ThemeProductionComponentSample("button", "OutlinedButton", "GALLERY OUTLINED BUTTON"),
    ThemeProductionComponentSample("textButton", "TextButton", "GALLERY TEXT BUTTON"),
    ThemeProductionComponentSample("iconButton", "IconButton", "GALLERY ICON BUTTON"),
    ThemeProductionComponentSample("textField", "TextField", "GALLERY TEXT FIELD *"),
    ThemeProductionComponentSample("listTile", "ListTile", "GALLERY LIST TILE"),
    ThemeProductionComponentSample("checkbox", "Checkbox", "GALLERY CHECKBOX"),
    ThemeProductionComponentSample("radio", "Radio", "GALLERY RADIO"),
    ThemeProductionComponentSample("switch", "Switch", "GALLERY SWITCH"),
    ThemeProductionComponentSample("slider", "Slider", "GALLERY SLIDER"),
    ThemeProductionComponentSample("tabs", "Tabs", "GALLERY TAB A"),
    ThemeProductionComponentSample("segmented", "SegmentedControl", "GALLERY SEGMENT A"),
    ThemeProductionComponentSample(
        "navigationBar",
        "NavigationBar",
        "GALLERY NAVIGATION BAR",
    ),
    ThemeProductionComponentSample(
        "navigationRail",
        "NavigationRail",
        "GALLERY NAVIGATION RAIL",
    ),
    ThemeProductionComponentSample("valueAdjuster", "ValueAdjuster", "GALLERY ADJUSTER"),
    ThemeProductionComponentSample("menu", "Menu", "GALLERY MENU"),
    ThemeProductionComponentSample("dropdown", "Dropdown", "GALLERY DROPDOWN"),
    ThemeProductionComponentSample("slidable", "Slidable", "GALLERY SLIDABLE"),
    ThemeProductionComponentSample("dialog", "Dialog", "GALLERY DIALOG"),
    ThemeProductionComponentSample("bottomSheet", "BottomSheet", "GALLERY BOTTOM SHEET"),
    ThemeProductionComponentSample("toast", "Toast", "GALLERY TOAST"),
    ThemeProductionComponentSample("snackbar", "Snackbar", "GALLERY SNACKBAR"),
    ThemeProductionComponentSample("tooltip", "Tooltip", "GALLERY TOOLTIP"),
    ThemeProductionComponentSample("progress", "ProgressBar", "Progress"),
    ThemeProductionComponentSample("refresh", "RefreshIndicator", "GALLERY REFRESH"),
    ThemeProductionComponentSample("scrollbar", "Scrollbar", "GALLERY SCROLLBAR"),
)

/** 所有标准组件共享的八列状态矩阵。 */
internal val ThemeShowcaseStates: List<ThemeShowcaseState> = listOf(
    ThemeShowcaseState("normal", "Normal", "N", PixelControlStateSet.Normal),
    ThemeShowcaseState(
        "hovered",
        "Hovered",
        "H",
        PixelControlStateSet.of(PixelControlState.Hovered),
    ),
    ThemeShowcaseState(
        "pressed",
        "Pressed",
        "P",
        PixelControlStateSet.of(PixelControlState.Pressed),
    ),
    ThemeShowcaseState(
        "focused",
        "Focused",
        "F",
        PixelControlStateSet.of(PixelControlState.Focused),
    ),
    ThemeShowcaseState(
        "selected",
        "Selected",
        "S",
        PixelControlStateSet.of(PixelControlState.Selected),
    ),
    ThemeShowcaseState(
        "disabled",
        "Disabled",
        "D",
        PixelControlStateSet.of(PixelControlState.Disabled),
    ),
    ThemeShowcaseState(
        "error",
        "Error",
        "E",
        PixelControlStateSet.of(PixelControlState.Error),
    ),
    ThemeShowcaseState(
        "loading",
        "Loading",
        "L",
        PixelControlStateSet.of(PixelControlState.Loading),
    ),
)

/** 创建一个主题选择按钮的稳定测试键。 */
internal fun themeShowcasePresetKey(presetId: String): String = "theme-showcase-preset-$presetId"

/** 创建一个组件族与状态交叉单元的稳定测试键。 */
internal fun themeShowcaseMatrixKey(familyId: String, stateId: String): String =
    "theme-showcase-matrix-$familyId-$stateId"

/** 创建一个真实生产组件预览的稳定测试键。 */
internal fun themeShowcaseProductionKey(familyId: String): String =
    "theme-showcase-production-$familyId"

/** 每个真实组件独享的有界预览高度，避免安全浮层占用整页约束。 */
private const val ThemeShowcaseProductionPreviewHeight: Int = 56

/** 构建不依赖 DemoEnv 的真实展厅主体，供 catalog 与离屏测试共同复用。 */
internal fun themeShowcaseBodyForTest(key: Any? = null): Widget = ThemeShowcaseBody(key = key)

/** 持有当前主题选择，并用标准按钮提供键盘、DPAD 与语义切换入口。 */
private class ThemeShowcaseBody(
    /** Catalog 与离屏测试可选传入的 retained 根 identity。 */
    override val key: Any? = null,
) : StatefulWidget(key = key) {
    /** 创建展厅主题选择状态。 */
    override fun createState(): State<out StatefulWidget> = ThemeShowcaseBodyState()
}

/** 管理展厅当前主题，并按选中的完整 token 图谱重建预览子树。 */
private class ThemeShowcaseBodyState : State<ThemeShowcaseBody>() {
    /** 当前主题在 ThemeShowcasePresets 中的稳定下标。 */
    private var selectedPresetIndex: Int = 0

    /** 真实 TextField 在整个展厅 retained 生命周期中复用的公开控制器。 */
    private val productionTextController: PixelTextFieldController = PixelTextFieldController()

    /** 与 [productionTextController] 成对持有、跨主题切换保留文本的公开输入状态。 */
    private val productionTextState = productionTextController.create(initialText = "PIXEL")

    /** 装饰 counter 使用的调用方格式化字符数，随真实输入回调更新。 */
    private var productionTextLength: Int = productionTextState.text.length

    /** 真实 Checkbox 的受控勾选状态。 */
    private var productionCheckboxChecked: Boolean = false

    /** 真实 Radio 的受控单选状态。 */
    private var productionRadioSelected: Boolean = false

    /** 真实 Switch 的受控开关状态。 */
    private var productionSwitchChecked: Boolean = true

    /** 真实 Slider 与 ProgressBar 共用的受控归一化值。 */
    private var productionSliderValue: Float = 0.42f

    /** 真实 Tabs 当前选中的受控下标。 */
    private var productionTabIndex: Int = 0

    /** 真实 SegmentedControl 当前选中的受控下标。 */
    private var productionSegmentIndex: Int = 1

    /** 真实 NavigationBar 当前选中的稳定目的地 id。 */
    private var productionNavigationBarSelection: String = "home"

    /** 真实 NavigationRail 当前选中的稳定目的地 id。 */
    private var productionNavigationRailSelection: String = "search"

    /** 真实 ValueAdjuster 当前显示的受控整数。 */
    private var productionAdjustedValue: Int = 2

    /** 真实 Menu 当前选中的受控命令。 */
    private var productionMenuSelection: String = "COPY"

    /** 真实 Dropdown 当前显示的受控值。 */
    private var productionDropdownSelection: String = "A"

    /** 真实 Dropdown 弹层当前是否展开。 */
    private var productionDropdownExpanded: Boolean = false

    /** Scrollbar 预览中由真实 ListViewBuilder 共用的公开列表控制器。 */
    private val productionScrollbarController: PixelListController = PixelListController()

    /** Scrollbar 与其真实列表视口共用的 retained 滚动状态。 */
    private val productionScrollbarState = productionScrollbarController.create()

    /** RefreshIndicator 子列表在整个 retained 生命周期中复用的公开控制器。 */
    private val productionRefreshListController: PixelListController = PixelListController()

    /** RefreshIndicator 子列表与真实列表视口共用的 retained 滚动状态。 */
    private val productionRefreshListState = productionRefreshListController.create()

    /** 驱动真实 RefreshIndicator 拉动和刷新阶段的公开控制器。 */
    private val productionRefreshController: PixelRefreshIndicatorController =
        PixelRefreshIndicatorController()

    /** 与 [productionRefreshController] 成对持有的 retained 刷新阶段状态。 */
    private val productionRefreshState = productionRefreshController.create()

    /** 构建主题选择器、真实组件画廊、基础 token 样本和完整组件状态矩阵。 */
    override fun build(context: BuildContext): Widget {
        /** 本帧选中的完整主题预设。 */
        val preset = ThemeShowcasePresets[selectedPresetIndex]
        /** 本帧所有预览共同读取的 token 图谱。 */
        val tokens = preset.tokens
        return PixelTheme(
            tokens = tokens,
            child = Container(
                fillColor = tokens.colors.background,
                padding = EdgeInsets.all(tokens.spacing.medium),
                child = Column(
                    children = listOf(
                        showcaseHeader(preset = preset),
                        productionComponentGallery(tokens = tokens),
                        foundationSection(tokens = tokens),
                        componentMatrixSection(tokens = tokens),
                    ),
                    spacing = tokens.spacing.large,
                    mainAxisSize = MainAxisSize.MIN,
                    crossAxisAlignment = CrossAxisAlignment.STRETCH,
                ),
            ),
        )
    }

    /** 用精确注册表顺序构建 25 个真实公开组件工厂的生产画廊。 */
    private fun productionComponentGallery(tokens: PixelThemeTokens): Widget {
        return Column(
            children = listOf(
                Text(
                    "PRODUCTION COMPONENTS (${ThemeShowcaseProductionComponents.size})",
                    style = tokens.typography.title.resolve(tokens.colors),
                ),
                Text(
                    "Each preview below is the public production factory, not a token swatch.",
                    style = tokens.typography.caption.resolve(tokens.colors),
                    softWrap = true,
                    maxLines = 2,
                ),
            ) + ThemeShowcaseProductionComponents.map { sample ->
                productionComponentPreview(sample = sample, tokens = tokens)
            },
            spacing = tokens.spacing.medium,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    /** 把一个真实生产组件放进独立有界视口，避免安全浮层吞占整页高度。 */
    private fun productionComponentPreview(
        sample: ThemeProductionComponentSample,
        tokens: PixelThemeTokens,
    ): Widget {
        /** 当前注册项直接映射出的真实公开组件。 */
        val component = buildProductionComponent(sample)
        return Column(
            children = listOf(
                Text(sample.factoryName, style = tokens.typography.label.resolve(tokens.colors)),
                Container(
                    height = ThemeShowcaseProductionPreviewHeight,
                    child = component,
                ),
            ),
            spacing = tokens.spacing.extraSmall,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    /**
     * 从注册项直接调用同名公开生产工厂。
     *
     * 此处刻意保留一对一 `when`，让审查和测试都能确认没有 PixelSurface 或别名替代。
     */
    private fun buildProductionComponent(sample: ThemeProductionComponentSample): Widget {
        /** 由公开工厂和测试共同持有的稳定组件 identity。 */
        val key = themeShowcaseProductionKey(sample.id)
        return when (sample.id) {
            "button" -> OutlinedButton(
                text = sample.semanticLabel,
                onPressed = {},
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "textButton" -> TextButton(
                text = sample.semanticLabel,
                onPressed = {},
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "iconButton" -> IconButton(
                icon = ThemeShowcaseControlIcon,
                onPressed = {},
                semanticLabel = sample.semanticLabel,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "textField" -> TextField(
                state = productionTextState,
                controller = productionTextController,
                states = PixelControlStateSet.Normal,
                decoration = FormFieldDecoration(
                    label = "PROFILE NAME",
                    helper = "EDITABLE PUBLIC FIELD",
                    required = true,
                    counter = "$productionTextLength/24",
                ),
                placeholder = "PIXEL",
                onChanged = ::updateProductionTextDecoration,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "listTile" -> ListTile(
                title = Text("REAL LIST TILE BODY"),
                states = PixelControlStateSet.Normal,
                onTap = {},
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "checkbox" -> Checkbox(
                checked = productionCheckboxChecked,
                onChanged = { checked ->
                    setState { productionCheckboxChecked = checked }
                },
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "radio" -> Radio(
                selected = productionRadioSelected,
                onSelected = ::selectProductionRadio,
                semanticLabel = sample.semanticLabel,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "switch" -> Switch(
                checked = productionSwitchChecked,
                onChanged = { checked ->
                    setState { productionSwitchChecked = checked }
                },
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "slider" -> Slider(
                value = productionSliderValue,
                states = PixelControlStateSet.Normal,
                onDrag = ::updateProductionSlider,
                onRelease = {},
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "tabs" -> Tabs(
                labels = listOf("GALLERY TAB A", "GALLERY TAB B"),
                selectedIndex = productionTabIndex,
                onSelected = ::selectProductionTab,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "segmented" -> SegmentedControl(
                labels = listOf("GALLERY SEGMENT A", "GALLERY SEGMENT B"),
                selectedIndex = productionSegmentIndex,
                onSelected = ::selectProductionSegment,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "navigationBar" -> NavigationBar(
                destinations = ThemeShowcaseNavigationDestinations,
                selectedId = productionNavigationBarSelection,
                onSelected = ::selectProductionNavigationBarDestination,
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "navigationRail" -> NavigationRail(
                destinations = ThemeShowcaseNavigationDestinations,
                selectedId = productionNavigationRailSelection,
                onSelected = ::selectProductionNavigationRailDestination,
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "valueAdjuster" -> ValueAdjuster(
                valueText = productionAdjustedValue.toString(),
                onDecrease = { adjustProductionValue(delta = -1) },
                onIncrease = { adjustProductionValue(delta = 1) },
                states = PixelControlStateSet.Normal,
                label = sample.semanticLabel,
                key = key,
            )
            "menu" -> Menu(
                items = listOf(
                    PixelMenuItem(
                        label = "GALLERY MENU COPY",
                        selected = productionMenuSelection == "COPY",
                        onSelected = { selectProductionMenuCommand("COPY") },
                        key = "$key-copy",
                    ),
                    PixelMenuItem(
                        label = "GALLERY MENU PASTE",
                        selected = productionMenuSelection == "PASTE",
                        onSelected = { selectProductionMenuCommand("PASTE") },
                        key = "$key-paste",
                    ),
                ),
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "dropdown" -> Dropdown(
                label = "MODE",
                selectedText = productionDropdownSelection,
                expanded = productionDropdownExpanded,
                onToggle = ::toggleProductionDropdown,
                items = listOf(
                    PixelMenuItem(
                        label = "A",
                        selected = productionDropdownSelection == "A",
                        onSelected = { selectProductionDropdownValue("A") },
                        key = "$key-a",
                    ),
                    PixelMenuItem(
                        label = "B",
                        selected = productionDropdownSelection == "B",
                        onSelected = { selectProductionDropdownValue("B") },
                        key = "$key-b",
                    ),
                ),
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "slidable" -> Slidable(
                child = Text("REAL SLIDABLE BODY"),
                states = PixelControlStateSet.Normal,
                onTap = {},
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "dialog" -> Dialog(
                content = Text("REAL DIALOG BODY"),
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "bottomSheet" -> BottomSheet(
                content = Text("REAL BOTTOM SHEET BODY"),
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                onDismissRequest = {},
                modal = false,
                key = key,
            )
            "toast" -> Toast(
                message = sample.semanticLabel,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "snackbar" -> Snackbar(
                message = sample.semanticLabel,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "tooltip" -> Tooltip(
                message = "VISIBLE PRODUCTION TOOLTIP",
                visible = true,
                child = SizedBox(width = 24, height = 10, child = Text("TIP TARGET")),
                states = PixelControlStateSet.Normal,
                semanticLabel = sample.semanticLabel,
                key = key,
            )
            "progress" -> ProgressBar(
                progress = productionSliderValue,
                states = PixelControlStateSet.Normal,
                key = key,
            )
            "refresh" -> {
                /** 可真实滚动且溢出当前预览视口的刷新列表。 */
                val list = ListViewBuilder(
                    itemCount = 16,
                    itemBuilder = { index ->
                        SizedBox(height = 8, child = Text("REFRESH ROW $index"))
                    },
                    state = productionRefreshListState,
                    controller = productionRefreshListController,
                    itemExtent = 8,
                    key = "$key-list",
                )
                RefreshIndicator(
                    child = list,
                    state = productionRefreshState,
                    controller = productionRefreshController,
                    states = PixelControlStateSet.Normal,
                    onRefresh = {
                        productionRefreshController.completeRefresh(productionRefreshState)
                    },
                    semanticLabel = sample.semanticLabel,
                    key = key,
                )
            }
            "scrollbar" -> {
                /** 与 Scrollbar 共享控制器和状态的真实溢出列表视口。 */
                val list = ListViewBuilder(
                    itemCount = 20,
                    itemBuilder = { index ->
                        SizedBox(height = 8, child = Text("SCROLL ROW $index"))
                    },
                    state = productionScrollbarState,
                    controller = productionScrollbarController,
                    itemExtent = 8,
                    key = "$key-list",
                )
                Scrollbar(
                    child = list,
                    state = productionScrollbarState,
                    states = PixelControlStateSet.Normal,
                    semanticLabel = sample.semanticLabel,
                    key = key,
                )
            }
            else -> error("Unregistered production component family: ${sample.id}")
        }
    }

    /** 构建当前主题说明和五个可键盘激活的主题按钮。 */
    private fun showcaseHeader(preset: ThemeShowcasePreset): Widget {
        /** 当前主题用于标题的解析样式。 */
        val titleStyle = preset.tokens.typography.title.resolve(preset.tokens.colors)
        /** 当前主题用于正文的解析样式。 */
        val bodyStyle = preset.tokens.typography.body.resolve(preset.tokens.colors)
        /** 五个按钮，选中项显式携带 Selected 状态。 */
        val presetButtons = ThemeShowcasePresets.mapIndexed { index, candidate ->
            /** 当前候选按钮的持久状态集合。 */
            val buttonStates = if (index == selectedPresetIndex) {
                PixelControlStateSet.of(PixelControlState.Selected)
            } else {
                PixelControlStateSet.Normal
            }
            OutlinedButton(
                text = candidate.buttonLabel,
                onPressed = { selectPreset(index) },
                states = buttonStates,
                key = themeShowcasePresetKey(candidate.id),
            )
        }
        return Column(
            children = listOf(
                Text("THEME SHOWCASE", style = titleStyle),
                Semantics(
                    label = "Active theme",
                    value = preset.id,
                    key = ThemeShowcaseActivePresetKey,
                    child = Text("ACTIVE THEME: ${preset.id.uppercase()}", style = bodyStyle),
                ),
                Text(
                    "${preset.tokens.brightness.name} / ${preset.tokens.contrast.name} — ${preset.description}",
                    style = preset.tokens.typography.caption.resolve(preset.tokens.colors),
                    softWrap = true,
                    maxLines = 3,
                ),
                Semantics(
                    label = "Theme preset chooser",
                    child = Wrap(
                        spacing = preset.tokens.spacing.small,
                        runSpacing = preset.tokens.spacing.small,
                        children = presetButtons,
                    ),
                ),
            ),
            spacing = preset.tokens.spacing.small,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        )
    }

    /** 选择有效预设并触发一次 retained 子树更新。 */
    private fun selectPreset(index: Int) {
        if (index !in ThemeShowcasePresets.indices || index == selectedPresetIndex) return
        setState { selectedPresetIndex = index }
    }

    /**
     * 用真实 TextField 回调更新调用方拥有的 counter 文本。
     *
     * @param text 控制器完成本次编辑后导出的完整文本。
     */
    private fun updateProductionTextDecoration(text: String) {
        /** 当前文本对应的新 counter 数值。 */
        val nextLength = text.length
        if (nextLength == productionTextLength) return
        setState { productionTextLength = nextLength }
    }

    /** 受理真实 Radio 的单向选择请求，重复选择保持幂等。 */
    private fun selectProductionRadio() {
        if (productionRadioSelected) return
        setState { productionRadioSelected = true }
    }

    /** 提交 Slider 的安全受控值，并由同一状态同步 ProgressBar。 */
    private fun updateProductionSlider(value: Float) {
        /** 对非有限输入提供确定的生产预览回退值。 */
        val nextValue = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        if (nextValue == productionSliderValue) return
        setState { productionSliderValue = nextValue }
    }

    /** 选择真实 Tabs 中的有效页签，并保留该状态跨主题更新。 */
    private fun selectProductionTab(index: Int) {
        if (index !in 0..1 || index == productionTabIndex) return
        setState { productionTabIndex = index }
    }

    /** 选择真实 SegmentedControl 中的有效分段。 */
    private fun selectProductionSegment(index: Int) {
        if (index !in 0..1 || index == productionSegmentIndex) return
        setState { productionSegmentIndex = index }
    }

    /**
     * 将真实 NavigationBar 切换到一个已注册的稳定业务目的地。
     *
     * @param destinationId NavigationBar 请求选中的稳定业务 id。
     */
    private fun selectProductionNavigationBarDestination(destinationId: String) {
        if (
            ThemeShowcaseNavigationDestinations.none { destination ->
                destination.id == destinationId
            } || destinationId == productionNavigationBarSelection
        ) {
            return
        }
        setState { productionNavigationBarSelection = destinationId }
    }

    /**
     * 将真实 NavigationRail 切换到一个已注册的稳定业务目的地。
     *
     * @param destinationId NavigationRail 请求选中的稳定业务 id。
     */
    private fun selectProductionNavigationRailDestination(destinationId: String) {
        if (
            ThemeShowcaseNavigationDestinations.none { destination ->
                destination.id == destinationId
            } || destinationId == productionNavigationRailSelection
        ) {
            return
        }
        setState { productionNavigationRailSelection = destinationId }
    }

    /** 在稳定的 0..9 演示范围内调整真实 ValueAdjuster 数值。 */
    private fun adjustProductionValue(delta: Int) {
        /** 钳位后的下一受控数值，避免演示操作无限增长。 */
        val nextValue = (productionAdjustedValue + delta).coerceIn(0, 9)
        if (nextValue == productionAdjustedValue) return
        setState { productionAdjustedValue = nextValue }
    }

    /** 记录真实 Menu 的受控选中命令。 */
    private fun selectProductionMenuCommand(command: String) {
        if (command !in setOf("COPY", "PASTE") || command == productionMenuSelection) return
        setState { productionMenuSelection = command }
    }

    /** 切换真实 Dropdown 的受控展开阶段。 */
    private fun toggleProductionDropdown() {
        setState { productionDropdownExpanded = !productionDropdownExpanded }
    }

    /** 选择真实 Dropdown 值，并在同一次状态提交中关闭弹层。 */
    private fun selectProductionDropdownValue(value: String) {
        if (value !in setOf("A", "B")) return
        setState {
            productionDropdownSelection = value
            productionDropdownExpanded = false
        }
    }
}

/** 构建 colors 到 labels 的九个完整基础 token 分组。 */
internal fun themeShowcaseFoundationSamples(tokens: PixelThemeTokens): List<ThemeFoundationSampleGroup> {
    /** 全部 22 个语义颜色角色，顺序与公共枚举一致。 */
    val colorSamples = PixelColorRole.entries.map { role ->
        /** 当前语义角色在所选主题中的具体像素颜色。 */
        val color = tokens.colors.resolve(role)
        ThemeTokenSample(role.name, color.toThemeHex(), previewColor = color)
    }
    return listOf(
        ThemeFoundationSampleGroup("colors", "COLORS", colorSamples),
        ThemeFoundationSampleGroup(
            "typography",
            "TYPOGRAPHY",
            listOf(
                ThemeTokenSample("body", typographyValue(tokens.typography.body)),
                ThemeTokenSample("label", typographyValue(tokens.typography.label)),
                ThemeTokenSample("title", typographyValue(tokens.typography.title)),
                ThemeTokenSample("caption", typographyValue(tokens.typography.caption)),
                ThemeTokenSample("button", typographyValue(tokens.typography.button)),
                ThemeTokenSample("input", typographyValue(tokens.typography.input)),
            ),
        ),
        ThemeFoundationSampleGroup(
            "spacing",
            "SPACING",
            listOf(
                ThemeTokenSample("none", "${tokens.spacing.none}px"),
                ThemeTokenSample("extraSmall", "${tokens.spacing.extraSmall}px"),
                ThemeTokenSample("small", "${tokens.spacing.small}px"),
                ThemeTokenSample("medium", "${tokens.spacing.medium}px"),
                ThemeTokenSample("large", "${tokens.spacing.large}px"),
                ThemeTokenSample("extraLarge", "${tokens.spacing.extraLarge}px"),
            ),
        ),
        ThemeFoundationSampleGroup(
            "sizes",
            "SIZES",
            listOf(
                ThemeTokenSample("iconSmall", "${tokens.sizes.iconSmall}px"),
                ThemeTokenSample("iconMedium", "${tokens.sizes.iconMedium}px"),
                ThemeTokenSample("iconLarge", "${tokens.sizes.iconLarge}px"),
                ThemeTokenSample("selectionControlExtent", "${tokens.sizes.selectionControlExtent}px"),
                ThemeTokenSample("switchWidth", "${tokens.sizes.switchWidth}px"),
                ThemeTokenSample("trackHeight", "${tokens.sizes.trackHeight}px"),
                ThemeTokenSample("compactControlHeight", "${tokens.sizes.compactControlHeight}px"),
                ThemeTokenSample("controlHeight", "${tokens.sizes.controlHeight}px"),
                ThemeTokenSample("touchTarget", "${tokens.sizes.touchTarget}px"),
                ThemeTokenSample("overlayMinimumWidth", "${tokens.sizes.overlayMinimumWidth}px"),
            ),
        ),
        ThemeFoundationSampleGroup(
            "radii",
            "RADII",
            listOf(
                ThemeTokenSample("none", "${tokens.radii.none}px"),
                ThemeTokenSample("small", "${tokens.radii.small}px"),
                ThemeTokenSample("medium", "${tokens.radii.medium}px"),
                ThemeTokenSample("large", "${tokens.radii.large}px"),
                ThemeTokenSample("pill", "${tokens.radii.pill}px"),
            ),
        ),
        ThemeFoundationSampleGroup(
            "borders",
            "BORDERS",
            listOf(
                ThemeTokenSample("none", "${tokens.borders.none}px"),
                ThemeTokenSample("thin", "${tokens.borders.thin}px"),
                ThemeTokenSample("thick", "${tokens.borders.thick}px"),
                ThemeTokenSample("focus", "${tokens.borders.focus}px"),
            ),
        ),
        ThemeFoundationSampleGroup(
            "elevations",
            "ELEVATIONS",
            listOf(
                ThemeTokenSample("none", "${tokens.elevations.none}px"),
                ThemeTokenSample("low", "${tokens.elevations.low}px"),
                ThemeTokenSample("medium", "${tokens.elevations.medium}px"),
                ThemeTokenSample("high", "${tokens.elevations.high}px"),
            ),
        ),
        ThemeFoundationSampleGroup(
            "motion",
            "MOTION",
            listOf(
                ThemeTokenSample("feedback", motionValue(tokens.motion.feedback)),
                ThemeTokenSample("selection", motionValue(tokens.motion.selection)),
                ThemeTokenSample("slidableSettle", motionValue(tokens.motion.slidableSettle)),
                ThemeTokenSample("dialogEnter", motionValue(tokens.motion.dialogEnter)),
                ThemeTokenSample("dialogExit", motionValue(tokens.motion.dialogExit)),
                ThemeTokenSample("popoverEnter", motionValue(tokens.motion.popoverEnter)),
                ThemeTokenSample("popoverExit", motionValue(tokens.motion.popoverExit)),
                ThemeTokenSample("route", motionValue(tokens.motion.route)),
            ),
        ),
        ThemeFoundationSampleGroup(
            "labels",
            "LABELS",
            listOf(
                ThemeTokenSample("confirm", tokens.labels.confirm),
                ThemeTokenSample("cancel", tokens.labels.cancel),
                ThemeTokenSample("dismiss", tokens.labels.dismiss),
                ThemeTokenSample("empty", tokens.labels.empty),
                ThemeTokenSample("error", tokens.labels.error),
                ThemeTokenSample("loading", tokens.labels.loading),
                ThemeTokenSample("button", tokens.labels.button),
                ThemeTokenSample("textButton", tokens.labels.textButton),
                ThemeTokenSample("textField", tokens.labels.textField),
                ThemeTokenSample("listTile", tokens.labels.listTile),
                ThemeTokenSample("checkbox", tokens.labels.checkbox),
                ThemeTokenSample("switch", tokens.labels.switch),
                ThemeTokenSample("slider", tokens.labels.slider),
                ThemeTokenSample("tabs", tokens.labels.tabs),
                ThemeTokenSample("segmentedControl", tokens.labels.segmentedControl),
                ThemeTokenSample("valueAdjuster", tokens.labels.valueAdjuster),
                ThemeTokenSample("decrease", tokens.labels.decrease),
                ThemeTokenSample("increase", tokens.labels.increase),
                ThemeTokenSample("menu", tokens.labels.menu),
                ThemeTokenSample("dropdown", tokens.labels.dropdown),
                ThemeTokenSample("dialog", tokens.labels.dialog),
                ThemeTokenSample("bottomSheet", tokens.labels.bottomSheet),
                ThemeTokenSample("toast", tokens.labels.toast),
                ThemeTokenSample("snackbar", tokens.labels.snackbar),
                ThemeTokenSample("tooltip", tokens.labels.tooltip),
                ThemeTokenSample("progress", tokens.labels.progress),
                ThemeTokenSample("refresh", tokens.labels.refresh),
                ThemeTokenSample("scrollbar", tokens.labels.scrollbar),
                ThemeTokenSample("slidable", tokens.labels.slidable),
            ),
        ),
    )
}

/** 构建完整基础 token 样本区。 */
private fun foundationSection(tokens: PixelThemeTokens): Widget {
    /** 当前主题解析出的九个完整基础 token 分组。 */
    val groups = themeShowcaseFoundationSamples(tokens)
    return Column(
        children = listOf(
            Text("FOUNDATION TOKENS (${groups.sumOf { group -> group.samples.size }})", style = tokens.typography.title.resolve(tokens.colors)),
        ) + groups.map { group -> foundationGroup(group = group, tokens = tokens) },
        spacing = tokens.spacing.medium,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}

/** 构建一个基础 token 分组及其全部样本卡片。 */
private fun foundationGroup(group: ThemeFoundationSampleGroup, tokens: PixelThemeTokens): Widget {
    return Column(
        children = listOf(
            Text("${group.title} (${group.samples.size})", style = tokens.typography.label.resolve(tokens.colors)),
            Wrap(
                spacing = tokens.spacing.small,
                runSpacing = tokens.spacing.small,
                children = group.samples.map { sample -> foundationSample(sample = sample, tokens = tokens) },
            ),
        ),
        spacing = tokens.spacing.extraSmall,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}

/** 构建一个带可选色块的基础 token 样本卡片。 */
private fun foundationSample(sample: ThemeTokenSample, tokens: PixelThemeTokens): Widget {
    /** 颜色 token 独有的硬边色块；其他 token 不占用空白色块。 */
    val colorPreview = sample.previewColor?.let { color ->
        PixelSurface(
            decoration = PixelSurfaceDecoration(
                fillColor = color,
                borderColor = tokens.colors.outline,
                borderWidth = tokens.borders.thin,
                cornerRadius = tokens.radii.small,
            ),
            width = tokens.sizes.iconLarge,
            height = tokens.sizes.iconSmall,
        )
    }
    return PixelSurface(
        decoration = PixelSurfaceDecoration(
            fillColor = tokens.colors.surface,
            borderColor = tokens.colors.outlineVariant,
            borderWidth = tokens.borders.thin,
            cornerRadius = tokens.radii.small,
        ),
        width = 72,
        padding = EdgeInsets.all(tokens.spacing.extraSmall),
        child = Column(
            children = listOfNotNull(
                colorPreview,
                Text(sample.name, style = tokens.typography.label.resolve(tokens.colors)),
                Text(
                    sample.value,
                    style = tokens.typography.caption.resolve(tokens.colors),
                    softWrap = true,
                    maxLines = 3,
                ),
            ),
            spacing = tokens.spacing.extraSmall,
            mainAxisSize = MainAxisSize.MIN,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
        ),
    )
}

/** 构建 25 个组件族乘以 8 个标准状态的完整矩阵。 */
private fun componentMatrixSection(tokens: PixelThemeTokens): Widget {
    return Column(
        children = listOf(
            Text(
                "COMPONENT TOKENS (${ThemeShowcaseComponentFamilies.size} FAMILIES × ${ThemeShowcaseStates.size} STATES)",
                style = tokens.typography.title.resolve(tokens.colors),
                softWrap = true,
                maxLines = 2,
            ),
            Text(
                "N Normal · H Hovered · P Pressed · F Focused · S Selected · D Disabled · E Error · L Loading",
                style = tokens.typography.caption.resolve(tokens.colors),
                softWrap = true,
                maxLines = 3,
            ),
        ) + ThemeShowcaseComponentFamilies.map { family -> componentFamilyRow(family = family, tokens = tokens) },
        spacing = tokens.spacing.medium,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}

/** 构建一个组件族的八状态预览行。 */
private fun componentFamilyRow(family: ThemeComponentFamily, tokens: PixelThemeTokens): Widget {
    return Column(
        children = listOf(
            Text(family.title, style = tokens.typography.label.resolve(tokens.colors)),
            Wrap(
                spacing = tokens.spacing.small,
                runSpacing = tokens.spacing.small,
                children = ThemeShowcaseStates.map { state ->
                    componentStateSample(family = family, state = state, theme = tokens)
                },
            ),
        ),
        spacing = tokens.spacing.extraSmall,
        mainAxisSize = MainAxisSize.MIN,
        crossAxisAlignment = CrossAxisAlignment.STRETCH,
    )
}

/**
 * 用标准组件 token 的实际解析结果绘制一个状态单元。
 *
 * Focused 使用独立外层指示器，避免覆盖 Selected、Error 等基础状态颜色。
 */
private fun componentStateSample(
    family: ThemeComponentFamily,
    state: ThemeShowcaseState,
    theme: PixelThemeTokens,
): Widget {
    /** 当前组件族的语义颜色与像素几何 token。 */
    val component = family.resolveTokens(theme.components)
    /** 当前状态解析出的可选内容颜色。 */
    val contentColor = component.resolveContentColor(state.states, theme.colors) ?: theme.colors.onSurface
    /** 当前状态解析出的硬阴影偏移。 */
    val shadowOffset = component.resolveElevation(theme.elevations)
    /** 组件基础表面，直接消费该族的状态颜色和共享几何 token。 */
    val baseSurface = PixelSurface(
        decoration = PixelSurfaceDecoration(
            fillColor = component.resolveContainerColor(state.states, theme.colors),
            borderColor = component.resolveBorderColor(state.states, theme.colors),
            borderWidth = component.resolveBorderWidth(theme.borders),
            cornerRadius = component.resolveCornerRadius(theme.radii),
            shadowColor = theme.colors.shadow.takeIf { shadowOffset > 0 },
            shadowOffset = shadowOffset,
        ),
        width = maxOf(32, component.resolveMinimumWidth(theme.sizes)),
        height = maxOf(14, component.resolveMinimumHeight(theme.sizes)),
        padding = component.resolvePadding(theme.spacing),
        child = Text(
            state.abbreviation,
            style = theme.typography.label.resolve(theme.colors).copy(color = contentColor),
        ),
    )
    /** Focused 状态独立解析的附加焦点层。 */
    val focusIndicator = component.focusIndicatorFor(state.states)
    /** 仅在 Focused 列绘制的外层焦点边界。 */
    val visual = focusIndicator?.let { indicator ->
        PixelSurface(
            decoration = PixelSurfaceDecoration(
                borderColor = indicator.resolveColor(theme.colors),
                borderWidth = indicator.resolveWidth(theme.borders),
                cornerRadius = component.resolveCornerRadius(theme.radii),
            ),
            padding = EdgeInsets.all(indicator.inset),
            child = baseSurface,
        )
    } ?: baseSurface
    return Semantics(
        label = "${family.title} ${state.title}",
        value = state.id,
        enabled = PixelControlState.Disabled !in state.states && PixelControlState.Loading !in state.states,
        focused = PixelControlState.Focused in state.states,
        selected = PixelControlState.Selected in state.states,
        error = "Error state".takeIf { PixelControlState.Error in state.states },
        hint = "Loading".takeIf { PixelControlState.Loading in state.states },
        excludeDescendants = true,
        key = themeShowcaseMatrixKey(family.id, state.id),
        child = visual,
    )
}

/** 把排版 token 压缩为适合样本卡片的稳定可读值。 */
private fun typographyValue(token: com.purride.pixelui.PixelTypographyToken): String =
    "role=${token.colorRole.name} scale=${token.fontScale} line=${token.lineHeight ?: "auto"} +${token.lineSpacing}/${token.letterSpacing}"

/** 把动效 token 压缩为时长、延迟、曲线角色与转场类型。 */
private fun motionValue(token: com.purride.pixelui.PixelMotionSpec): String =
    "${token.duration.inWholeMilliseconds}ms +${token.delay.inWholeMilliseconds} ${token.transition.name}/${token.role.name}"

/** 把 ARGB 像素颜色转成固定八位十六进制文本。 */
private fun PixelColor.toThemeHex(): String = "#${argb.toUInt().toString(16).padStart(8, '0').uppercase()}"
