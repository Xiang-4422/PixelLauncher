# pixel-demo 场景说明

pixel-demo 共 **52 个 showcase scene**，按 **9 个 section** 分组。每个 scene 对应 `DemoCatalog` 中的一项，可从菜单直接进入。

分组按**开发者使用意图**组织：

| Section | 回答的问题 | 数量 |
|---|---|---|
| Foundation | 这个 widget 长什么样、怎么用 | 8 |
| Interaction | 怎么处理手势 / 输入 / 按钮状态 | 5 |
| Scroll | 列表 / Pager / 滚动控制器 | 9 |
| Theme | 配色 / token / 方向 | 4 |
| Composition | 常见拼法 | 4 |
| Templates | 能用引擎拼出什么样的完整 UI | 5 |
| Extension | 怎么挂钩引擎底层 | 6 |
| Stress | 极限 / 回归基线 | 6 |
| Integration | 与宿主 Activity 怎么打通 | 5 |

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

### stack_positioned · Stack 与 Positioned
切换 BADGES / MODAL / FILL 三种叠层用法：
- **BADGES**：用 `Positioned(left/top)`+`(right/top)`+`(left/bottom)`+`(right/bottom)` 实现四角标记
- **MODAL**：用 `Positioned(left/top/right/bottom)` 居中弹层
- **FILL**：用 `PositionedFill` 整面铺满父容器

### directional_variants · 方向感知原语
LTR ↔ RTL 切换，并排对比四种 *Directional 原语：
- `PaddingDirectional(start)`
- `AlignDirectional(CENTER_START)`
- `PositionedDirectional(start)`
- `ContainerDirectional(start padding)`

并附一组 `Positioned(left)` 对照——它**不**随方向镜像，凸显 directional 变体的语义价值。

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

## Composition — 复合拼法

### nested_scroll_pager_in_list · 嵌套 · Pager-in-List
纵向 List 的每一行内部嵌入横向 `PageViewBuilder`，每个 row 独立 `PageController` + `PixelPagerState`。演示"列表项 = 横滑卡片"这种常见 reel / tab card 拼法，同时验证嵌套滚动手势仲裁。

### sticky_bottom_bar · Sticky Bottom Bar
顶部 `Expanded` 列表，底部固定 `Row(TextField + SEND)`。点击 SEND 把输入内容追加为新一行并清空输入框。演示"主内容滚动 + 固定底栏 + 软键盘交互"这一常见聊天/搜索/评论页布局。

### master_detail · Master-Detail
Row + Expanded 拆成左右两栏：左侧 70px 固定宽列表，右侧自适应详情。点击列表项切换详情。演示双栏导航的最小可行拼法。

### modal_overlay · Modal Overlay
基础内容上叠加 `Stack` + `PositionedFill` 实现透明遮罩，遮罩用 `GestureDetector` 捕获 onTap 关闭，居中 `Positioned` 浮一个对话框。统计被关闭次数。演示无 Activity-Dialog 实现的纯引擎弹窗。

---

## Templates — 完整模板

完整 UI 模板：每个 scene 对应一种典型 app 页面，整合 Foundation/Interaction/Scroll/Composition 多种能力，回答"能用 pixel-engine 做出什么样的 UI"。

### tpl_settings · 模板 · 设置页
分组标题 + 开关行 + 选项行 + 整页滚动。包含 5 个真实控件：
- 启用通知 / 通知声音 / 触感震动 三个 `GestureDetector` toggle 行
- 亮度 / 字号 两个 `OutlinedButton` segmented 选项
- "关于" 多行信息块

整页用 `SingleChildScrollView` 包裹。

### tpl_calculator · 模板 · 计算器
顶部数显条（右对齐，`fillTone=ON / borderTone=ACCENT`）+ 4×5 按钮网格（用 Row × 5 + Expanded 平均分布列宽实现"网格"）。完整可用的四则运算 + 退格 + 正负 + 百分比逻辑。演示密集按钮 + 状态机交互的最小完整实现。

### tpl_file_browser · 模板 · 文件浏览器
模拟文件树（4 级深度）。顶部 `[←] 面包屑` 横条 + 主区列表。点击 `[DIR]` 项进入下一级，点击 `←` 返回上级。Scene **内部**用 `MutableList<Node>` 自管导航栈，**不**借助 DemoNavigator——演示 scene 完全自治的导航能力。叶节点显示"无下级"占位。

### tpl_chat · 模板 · 聊天 UI
消息流 + 底部输入条。`fromMe=true` 的消息渲染为 ACCENT 填充气泡且右对齐，`fromMe=false` 的为 OFF 填充左对齐。SEND 按钮把当前输入追加为本端消息，每隔一条自动回一条"收到: …"模拟对方。演示对话气泡布局 + Text 软换行 + 列表实时增长。

### tpl_player_hud · 模板 · 播放器 HUD
封面区（ACCENT 块 + ♪ 符号）+ 标题/作者 + 滚动歌词框 + 进度条 + 三档控件（◀◀ ❚❚/▶ ▶▶）。
- **歌词**：`AxisMotionController(settleDurationMs = 4000ms)` 缓动整列向上滚，当前激活行用 ACCENT
- **进度**：另一个 `AxisMotionController(settleDurationMs = 30s)` 驱动小圆点从左滑到右
- **暂停按钮**：暂停时停止帧推进，恢复时继续

是唯一同时跑两路独立动画 + 命令式控件的模板。

---

## Extension — 扩展点

### custom_render_object · 自定义 RenderObject
使用 `HollowSquareWidget`——一个实现 `PixelLeafRenderObjectWidget` 的自定义组件，直接操作 `PixelBuffer` 逐像素绘制空心方块。底部可切换边长（20 / 40 / 60）和色调（ON / ACCENT / OFF）。展示不借助任何内置 widget、直接扩展渲染管线的最底层能力。

### custom_pager_policy · 自定义分页手势策略
Scene 通过 `pagerGesturePolicy` 字段返回 `TunablePagerGesturePolicy`，其 `axisBias` 参数决定分页手势的触发灵敏度。底部三档切换 bias（0.5 / 1.2 / 3.0），bias 越大越需要更明确的横向滑动才能翻页。演示手势识别层的策略扩展接口。

### custom_rasterizer · 自定义 Rasterizer
切换 8px default / 10px emphasis 两种字形包，通过 `DefaultTextRasterizer` InheritedWidget 覆盖当前子树的字形渲染器。对比同一段文字在两种字号下的渲染效果，演示渲染器的局部替换能力。

### manual_frame_stepper · ManualFrameScheduler
独立 `ManualFrameScheduler` 驱动一个 `AxisMotionController`：dot 在 200px 轨道上来回弹。按钮 `step 1` / `step 10` / `step 60` 手动 `advanceFrame()`。顶部实时显示 `frame index`、`offset px`、`pending callbacks`。
**用途**：理解逐帧推进 API；为未来自动化截图回归预留接入点。

### custom_scroll_physics · 自定义 ScrollPhysics
临时替换 `hostView.scrollPhysics`，对同一份 200 项列表切换四种物理参数对比：
- **默认**：`deceleration=2400, bounce=off`
- **高摩擦**：`deceleration=6000` — fling 很快停
- **低摩擦**：`deceleration=800` — fling 滑得很远
- **bounce**：`bounceEnabled=true, overscrollLimit=80, resistance=0.5` — 边界回弹

Scene 在 dispose 时还原原始 physics。

### nested_scroll_policy · 嵌套滚动策略
**纵向** Pager 内每页一个**纵向** List，演示同方向嵌套滚动的接管行为。切换两种 `NestedScrollGesturePolicy`：
- **默认（list 优先）**：内层 list 还能滚就归 list，到边界才接力给 pager
- **pager 优先**：自定义子类，pager 永远不让出，强制 pager 接管所有纵向手势

Scene 在 dispose 时还原原始 policy。

---

## Stress — 压力 / 回归基线

所有 stress scene 顶部统一挂载 `DemoMetricsOverlay`（独立组件位于 `scaffold/DemoMetricsOverlay.kt`），实时显示 FPS / Heap（KB）+ 场景自定义参数。这些 scene 作为引擎迭代时的人肉回归基线。

### stress_list_scale · 压测 · 列表规模
`ListViewBuilder` 档位 **1k / 5k / 20k / 50k**，验证虚拟列表在大数据量下的渲染与滚动开销。MetricsOverlay 显示 `N=数量`。

### stress_rebuild_storm · 压测 · 重建风暴
每帧 `Choreographer.FrameCallback` 触发 setState，三档对比：
- **OFF**：只采样，不重建（基线）
- **LEAF**：通过订阅总线，只让叶子 widget setState（局部重建）
- **TREE**：整个状态树根 setState（整树重建）

直观对比 pixel-engine 的局部 vs 整树重建开销。

### stress_deep_tree · 压测 · 深嵌套
`Padding` 嵌套 N 层（10 / 50 / 200）后包一个 ACCENT 方块。测 layout pass 累积成本随深度变化的关系。

### stress_animation_flood · 压测 · 动画洪流
M 个独立 `AxisMotionController` 同帧推进（10 / 50 / 200），每个驱动一个轨道内的 dot 在两端 ping-pong。`ListViewBuilder` 渲染所有轨道。每个 controller 的 settleDuration 略有差异（600/750/900/1050ms 轮换），避免帧锁定。

### stress_gesture_storm · 压测 · 手势风暴
同屏并发：
- 顶部 **横向 Pager**（20 页，40px 高，OFF/ON 边框）
- 中部 **Tap grid**（4 个 `GestureDetector` 块，统计 tap 累计）
- 底部 **纵向 List**（2000 行）

测手势仲裁/路由在并发触发下的吞吐。

### stress_text_heavy · 压测 · 富文本
档位 100 / 500 / 2000 个 `PixelTextSpan`（Default/Accent 交替），包在 `SingleChildScrollView` 内的长 `RichText`（softWrap=true）。观察折行 + 字形栅格化的累积成本。

---

## Integration — 宿主集成

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

### haptic_feedback · Haptic 反馈
两个按钮分别调用 `PixelHostBridge.performHapticFeedback(TAP)` / `(LONG_PRESS)`。`DemoActivity` 把 `PixelTextInputBridge` 包了一层，在 haptic 调用时映射到 Android `HapticFeedbackConstants.VIRTUAL_KEY` / `LONG_PRESS`，调用 `hostView.performHapticFeedback`。需在真机上能感受到不同震动强度。

### system_action_dispatch · SystemAction 派发
三个按钮派发自定义 `PixelSystemAction(type, payload)`：
- `open_url` + `https://example.com`
- `share` + `hello from pixel-engine`
- `close_app` + null

宿主 Activity 接到 dispatch 后用 Toast 显示 `SystemAction: <type> payload=<payload>`，演示业务可扩展的系统动作通道。

---

## 附：架构决策

- **分组依据**：现 9 段按"开发者使用意图"组织（Foundation/Interaction = 学 API，Composition/Templates = 学拼法，Stress = 跑基线，Extension/Integration = 接底层）。这比按引擎子系统分组更利于查阅。
- **`DemoMetricsOverlay`**：所有 stress scene 共享同一份采样实现，避免重复 Choreographer 模板代码。
- **`DemoCatalogCoverageTest`**：硬编码 9 sections / 52 scenes 期望数。新增 scene 须更新测试。
- **`hostBridge` 包装**：`DemoActivity` 在 `createPixelHostSetup` 之后把默认的 `PixelTextInputBridge` 用一个 `PixelHostBridge` 装饰对象包起来，让 haptic / system_action 能落到 Activity，而文本输入仍走原 bridge。
