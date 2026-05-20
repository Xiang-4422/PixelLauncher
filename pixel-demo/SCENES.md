# pixel-demo 场景说明

pixel-demo 共 31 个 showcase scene，按 6 个 section 分组。每个 scene 对应 `DemoCatalog` 中的一项，可从菜单直接进入。

---

## Foundation — 基础布局

### hello_pixel · Hello Pixel
最简场景。屏幕正中渲染一行 `HELLO PIXEL` 文字，验证引擎能正常启动、`Center` + `Text` 最基本的组合可以工作。

### layout_primitives · 布局原语
用五个色块演示 **Row / Column / Stack** 三种排列容器，底部三个按钮切换模式。直观对比同一组子节点在不同容器下的排布结果。

### align_sizing · 对齐与尺寸
用 9 个固定高度的演示盒，每格对应一种布局原语：
`Padding` / `Align(topStart)` / `Align(bottomEnd)` / `Align(center)` / `Center` / `SizedBox` / `Expanded` / `Flexible(loose)` / `Spacer`。
逐格展示每个原语对子元素位置与尺寸的影响。

### text_matrix · 文本参数矩阵
上半屏显示一段中英混排长文本，下方四组控制按钮实时切换：
- **softWrap**：OFF / ON
- **maxLines**：1 / 2 / 4
- **overflow**：CLIP / ELLIPSIS
- **textAlign**：START / CENTER / END

组合调参，观察参数对文字折行与裁剪行为的影响。

### rich_text · 富文本
一段多 span 混排的 `RichText`，含 Default / Accent / 自定义 tone 三种字色交替出现。验证同一行内多种样式 span 能正确拼接渲染。

### container_decoration · 容器与装饰
纵向对比三种容器组件的功能差异：
- **Container**：带 width / height / fillTone / borderTone / padding / alignment 全参数
- **DecoratedBox**：无尺寸约束，尺寸由 child 决定，仅负责装饰
- **ContainerDirectional**：与 Container 类似，但 padding 使用 start/end 方向语义，LTR/RTL 自动对称

---

## Interaction — 交互

### gesture_tap · 点击手势
屏幕中央一个点击区域，用 `GestureDetector` 捕获 `onTap`，上方实时显示事件名和累计点击次数。验证手势识别与 `setState` 驱动 UI 刷新。

### button_states · 按钮状态矩阵
展示 `OutlinedButton` 在五种状态下的外观：
`Default` / `Accent` / `disabled` / `selected` / `pressed`。
底部可在 Default / Accent 两种样式之间切换，横向对比视觉差异。

### text_field_basics · 文本输入基础
单行 `TextField` + 多行 `TextField`（minLines=2 / maxLines=4），实时显示"当前值"和"字符数"。演示 `TextEditingController` 双向绑定与 `PixelTextFieldState` 状态管理。

### ime_types · 输入键盘类型
逐一列出 `PixelInputType` 全枚举与 `TextInputAction` 全枚举，每种配一个 TextField，触发系统对应键盘类型和 IME Action 按钮：
- **InputType**：TEXT / NUMBER / NUMBER_PASSWORD / EMAIL / PHONE / URL / PASSWORD
- **InputAction**：DONE / NEXT / GO / SEARCH / SEND

### text_input_host_commands · 宿主侧输入命令
聚焦 TextField 后，点击三个按钮分别调用宿主 API：
- `updateFocusedTextInput("HOST_UPDATE")`：向聚焦输入框注入文字
- `clearFocusedTextInput()`：清空聚焦输入框内容
- `submitFocusedTextInput()`：触发提交动作

展示宿主 Java/Kotlin 代码在运行时直接操控引擎内输入状态的能力。

---

## Scroll — 滚动

### single_child_scroll · 单子节点滚动
一个内容超出屏幕的长 `Column` 用 `SingleChildScrollView` 包裹，验证最基本的整体内容滚动场景。

### list_eager · 列表（全量渲染）
约 20 项的 `ListView`（非 lazy，全量渲染），演示 eager 列表的渲染与滚动，适用于项目数量固定且少的场景。

### list_virtual_fixed · 列表（虚拟固定高）
`ListViewBuilder` 固定 `itemHeight`，lazy 渲染，底部可切换 1k / 5k / 10k 数据量，切档时复用 `ScrollController` 不跳回顶部。验证大数据量下虚拟列表的滚动流畅性。

### list_variable_height · 列表（变高）
`ListViewBuilder` 变高版，每项内容不同导致高度各异，引擎通过估算高度管理滚动偏移。可切换 1k / 5k 数据量。

### list_separated · 分隔列表
`ListViewSeparatedBuilder`，item 与 separator 交替渲染，验证分隔条正确插入位置及其在滚动时的渲染正确性。

### pager_horizontal · 横向 Pager
`PageViewBuilder` 横向翻页，可切换 3 / 10 / 100 页档位。演示水平分页手势与 `PixelPagerState` 状态管理。

### pager_vertical · 纵向 Pager
同上，改为纵向翻页。验证垂直分页手势与横向共用同一套 Pager 基础设施。

### scroll_controller_commands · 滚动控制器命令
按钮触发 `ScrollController` 的命令式 API：
- `jumpToStart` / `jumpToEnd`：跳到首/尾
- `showItem(random)`：滚动到随机一项
- `fling(+v)` / `fling(-v)`：正向/反向惯性滑动

### page_controller_commands · Pager 控制器命令
按钮触发 `PageController` 的命令式 API：
- `jumpToPage(random)`：跳到随机页
- `nextPage` / `previousPage`：前后翻页

---

## Theme — 主题

### palette_toggle · 调色板与像素形状
上半屏实时显示当前 Theme 和 Shape 名称，下方两组控制按钮：
- **PixelTheme**（5 种配色）：GREEN_PHOSPHOR / AMBER_CRT / ICE_LCD / MONO_LCD / NIGHT_MONO
- **PixelShape**（3 种像素形状）：SQUARE / CIRCLE / DIAMOND

分别调用 `hostView.setPalette` 和 `applyPreferredProfile` 实时切换全局渲染风格。

### theme_tokens · 主题 token 调参
切换三种 `ThemeData` 变体（默认 / 强调文字 / 暗面背景），同时用同一套 Text + Container + Button 观察 token 改变后各组件的外观联动，演示 `PixelThemeData.withTokens` 的局部 token 覆盖能力。

### theme_state_matrix · 组件状态矩阵
切换 Button / TextField 两个主体，纵向列出各自在真实可触发状态下的外观：
- Button：Default / Accent / disabled / selected / pressed
- TextField：Default / disabled / readOnly

在主题切换时同步观察视觉一致性。

### rtl_mirror · RTL 镜像
切换 LTR / RTL，调用 `hostView.textDirection`，屏幕内文本和按钮组布局随之水平镜像翻转。验证引擎对双向文字（BiDi）布局方向的支持。

---

## Extension — 扩展点

### custom_render_object · 自定义 RenderObject
使用 `HollowSquareWidget`——一个实现 `PixelLeafRenderObjectWidget` 的自定义组件，直接操作 `PixelBuffer` 逐像素绘制空心方块。底部可切换边长（20 / 40 / 60）和色调（ON / ACCENT / OFF）。展示不借助任何内置 widget、直接扩展渲染管线的最底层能力。

### custom_pager_policy · 自定义分页手势策略
Scene 通过 `pagerGesturePolicy` 字段返回 `TunablePagerGesturePolicy`，其 `axisBias` 参数决定分页手势的触发灵敏度。底部三档切换 bias（0.5 / 1.2 / 3.0），bias 越大越需要更明确的横向滑动才能翻页。演示手势识别层的策略扩展接口。

### custom_rasterizer · 自定义 Rasterizer
切换 8px default / 10px emphasis 两种字形包，通过 `DefaultTextRasterizer` InheritedWidget 覆盖当前子树的字形渲染器。对比同一段文字在两种字号下的渲染效果，演示渲染器的局部替换能力。

### perf_overlay · 性能 Overlay
叠加两个实时性能指标：
- **FPS**：通过 `Choreographer.FrameCallback` 统计每秒帧数
- **Heap**：通过 `Runtime.totalMemory() - freeMemory()` 采样堆内存

Body 跑一个 5000 项 `ListViewBuilder` 制造渲染负载，实时观察引擎在高负载下的帧率与内存表现。

---

## Integration — 集成

### host_hot_swap · Host 热替换
点击"树 A / B / C"按钮，直接调用 `hostView.setContent { ... }` 替换整棵 widget 树。验证宿主代码在运行时切换 UI 根节点时，引擎能正确销毁旧树、构建新树，计数器记录替换次数。

### config_change_preserve · 配置变更保留
页面同时持有三处状态：
- List 滚动位置（ScrollController）
- Pager 当前页（PageController）
- TextField 输入内容（TextEditingController）

旋转屏幕触发 `configChange` 后，三处状态全部保留。依赖 `android:configChanges="orientation|screenSize"` 使 Activity 不重建，State 成员变量在 DemoActivity 生命周期内持久存活。

### empty_loading_error · 空 / 加载 / 错误三态
底部三个按钮驱动 `ValueNotifier<ContentState>`，内容区用 `ValueListenableBuilder` 切换三种视图：
- **空态**：居中提示文字 + 重试按钮
- **加载态**：5 行 OFF 色骨架占位条
- **错误态**：错误文字 + 重试按钮

演示响应式多态内容 UI 的标准写法，`ValueNotifier` 替代 `setState` 实现局部重建。
