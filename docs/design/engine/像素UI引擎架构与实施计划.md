# 像素 UI 引擎架构与实施计划

本文档是当前 PixelLauncher 仓库里，关于像素 UI 引擎拆分与建设的唯一执行口径。  
它同时承担四个职责：

- 说明当前仓库的真实基线，避免接手者误判现状
- 明确 `:pixel-core`、`:pixel-ui`、`:pixel-demo`、`:app` 的职责边界
- 给出当前阶段必须落地的公开接口与实现顺序
- 提供后续工程师可以直接接手执行的任务拆分与验收标准

如果只看一句话，这个阶段的目标是：

> 先把框架独立做成，再用 demo 自证可用，最后才让 Launcher 迁移到这套框架上。

---

## 1. 当前阶段结论

### 1.1 方向定义

当前要构建的是：

- 面向 Android 内容区的像素 UI runtime
- 运行在 Android `Activity` 内容区中的自绘 UI 系统
- 由业务状态驱动、由通用组件描述页面、由像素引擎统一负责布局、绘制与输入分发

当前不做的是：

- 完整替代 Android framework
- 跨平台框架
- 第一阶段就重做 IME、权限、Window、无障碍、导航栈
- 第一阶段就让 Launcher 渐进依赖半成品框架

### 1.2 当前阶段固定策略

当前阶段的固定策略如下：

- `:app` 先恢复为独立旧实现，不消费 `:pixel-core` 或 `:pixel-ui`
- `:pixel-core` 与 `:pixel-ui` 单独建设，不再通过兼容别名挂回 `:app`
- 新增独立 `:pixel-demo` 作为框架验证宿主
- 只有 demo 验证通过后，才开始迁移 Launcher

当前实现层面仍然保持：

- `retained build/runtime`
- `runtime orchestration`
- `bridge`
- `legacy renderer`

其中 `legacy renderer` 当前继续保留，但只作为内部可替换后端持续收口，不再承担公开 API 责任。

这意味着当前阶段不是“边拆边迁”，而是“框架先自证，再迁应用”。

---

## 2. 当前仓库真实基线

### 2.1 当前模块情况

当前仓库已经有四个模块入口：

- `:app`
- `:pixel-core`
- `:pixel-ui`
- `:pixel-demo`

`settings.gradle.kts` 当前真实内容包含：

```kotlin
rootProject.name = "PixelLauncherV2"
include(":app")
include(":pixel-core")
include(":pixel-ui")
include(":pixel-demo")
```

但要注意：

- `:pixel-core` 和 `:pixel-ui` 目前仍处于建设早期
- 当前产品真正可运行的主实现仍在 `:app`
- 当前阶段要求 `:app` 不依赖 `:pixel-core`
- 当前阶段也不让 `:app` 依赖 `:pixel-ui`

### 2.2 当前 UI 主链路

Launcher 当前真实主链路仍然是：

`MainActivity -> LauncherStateTransitions -> PixelRenderer -> PixelFrameView`

也就是：

- 单 `Activity`
- 单一 `LauncherState`
- 自定义像素渲染链路

### 2.3 当前目录角色

当前代码目录职责如下：

- `app/src/main/kotlin/com/purride/pixellauncherv2/app`
  - 宿主 Activity、系统桥接、运行时编排
- `app/src/main/kotlin/com/purride/pixellauncherv2/launcher`
  - Launcher 页面模式、状态、列表与页面布局模型
- `app/src/main/kotlin/com/purride/pixellauncherv2/render`
  - 当前产品实际在用的像素渲染实现
- `pixel-core`
  - 新框架的像素内核模块
- `pixel-ui`
  - 新框架的通用 UI runtime 与组件模块

### 2.4 当前阶段必须记住的事实

1. `:app` 当前是完整独立实现，不是框架消费者  
2. `:pixel-core` 和 `:pixel-ui` 当前是新框架建设目标，不是 Launcher 运行依赖  
3. 后续接手时，不允许再把兼容别名塞回 `:app` 形成“半迁移状态”

---

## 3. 模块职责边界

### 3.1 `:pixel-core`

`pixel-core` 的职责是像素显示内核与低层原语。

允许放入 `:pixel-core` 的内容：

- `PixelBuffer`
- `PixelPalette`
- `ScreenProfile`
- `FrameSwapBuffer`
- `PixelFrameView`
- 像素显示几何、缩放与分辨率解析
- 字体与字形底座
- 与业务无关的性能计时
- 一维位移原语
- 像素帧合成原语

禁止放入 `:pixel-core` 的内容：

- `Home / Drawer / Settings / SMS / Idle / Charge`
- `selectedIndex / listStartIndex / drawer query`
- 任何 Launcher 页面模式和业务状态
- `Pager / List / TextField` 这类 UI 语义
- 产品专属视觉效果

一句话判断标准：

> 如果另一个完全不同的像素 App 可以直接复用它，而且不需要知道 Launcher 的任何产品概念，它才应该进入 `:pixel-core`。

### 3.2 `:pixel-ui`

`pixel-ui` 的职责是通用 UI runtime 与组件层。

允许放入 `:pixel-ui` 的内容：

- `Widget`
- `BuildContext`
- `StatefulWidget / InheritedWidget / Theme / MediaQuery`
- 通用布局容器
- 通用文本与表面组件
- 通用点击、分页、焦点、命中测试
- `PageView`
- `PageController`
- `ScrollController`
- `TextEditingController`

禁止放入 `:pixel-ui` 的内容：

- Launcher 页面模式
- 业务数据仓库与系统服务
- 短信、待机、充电等产品专属组件

### 3.3 `:pixel-demo`

`pixel-demo` 是独立 Android 宿主，仅用于验证框架能力。

它的职责：

- 依赖 `:pixel-ui`
- 提供最小可运行 demo 页面
- 验证基础组件、布局、点击和分页行为

它不承担的职责：

- 不复用 Launcher 状态
- 不接产品数据
- 不承担真实产品逻辑

### 3.4 `:app`

`app` 当前仍是 PixelLauncher 产品实现本体。

它保留：

- `MainActivity`
- `LauncherState` 与 `LauncherStateTransitions`
- 当前产品渲染实现
- 各类系统服务、权限、数据仓库
- Idle、Charge、短信、设置、抽屉等产品专属 UI

这一阶段明确要求：

- `:app` 不依赖 `:pixel-core`
- `:app` 不依赖 `:pixel-ui`
- 不通过兼容别名把 `:pixel-core` 或 `:pixel-ui` 的类型偷偷接回 `:app`

---

## 4. 当前阶段公开接口

### 4.1 `:pixel-core` 第一批稳定接口

当前阶段要在 `:pixel-core` 中稳定下来的低层原语如下：

```kotlin
enum class PixelAxis {
    HORIZONTAL,
    VERTICAL,
}

data class AxisMotionState(
    val isDragging: Boolean,
    val dragOffsetPx: Float,
    val isSettling: Boolean,
    val settleStartOffsetPx: Float,
    val settleEndOffsetPx: Float,
    val settleProgress: Float,
)

class AxisMotionController(...)

object AxisBufferComposer
```

这些类型只表达：

- 沿一个轴向如何拖动
- 拖动结束后如何吸附
- 当前可视偏移是多少
- 两张像素帧如何按轴向拼成一帧

这些类型不表达：

- 当前页是谁
- 一共有多少页
- 目标页是哪一页

### 4.2 `:pixel-ui` 当前稳定公开接口

当前阶段 `:pixel-ui` 的公开主路径已经统一转成 Flutter 风格：

- `Widget`
- `BuildContext`
- `StatefulWidget / State`
- `InheritedWidget / InheritedNotifier`
- `Theme / Directionality / MediaQuery`
- `Text`
- `Container`
- `Padding`
- `Align`
- `Center`
- `SizedBox`
- `Row`
- `Column`
- `Stack / Positioned / PositionedDirectional / PositionedFill`
- `OutlinedButton`
- `TextField`
- `PageView`
- `ListView`
- `SingleChildScrollView`
- `PageController`
- `ScrollController`
- `TextEditingController`

当前执行口径是：

- 页面层优先使用 Flutter 风格公开组件和控制器
- `PixelNode`、`PixelModifier`、旧 `Pixel*` 公开组件名只保留在模块内部兼容层
- 不再继续扩展旧公开接口

### 4.3 `:pixel-ui` 当前运行时状态

当前 `pixel-ui` 不是“只有一层组件别名”，而是已经进入 retained runtime 重构阶段。

当前真实链路是：

1. `Widget`
2. `RetainedBuildRuntime / BuildOwner / Element tree`
3. `RetainedWidgetRenderRuntime`
4. `bridge：BridgeRenderSupportFactory / DefaultBridgeTreeResolver / BridgeWidgetAdapter / BridgeElementTreeRenderer / BridgeRenderRuntime`
5. `PixelRenderRuntime`

这意味着：

- retained build tree 已经成立
- 状态、环境和依赖登记已经在 retained 主链上
- `RetainedBuildRuntime` 当前只负责 retained element tree，不再直接返回 bridge tree
- `BuildOwner` 已经继续拆出 `ElementInflater / ElementChildUpdater / DirtyElementScheduler / ListenableDependencyRegistry / RootElementSlot`
- retained element 当前也已经按职责拆成 `Element / StatefulElements / InheritedElements`
- runtime 目录当前已经按 `runtime / request / assembly / support / host` 收拢
- retained 目录当前已经按 `runtime / elements / support` 收拢
- bridge 目录当前已经按 `runtime / resolve / elements / widgets / modifier` 收拢
- 最终绘制仍然落到 legacy renderer
- 当前主线任务是继续切 retained 主链和 legacy renderer 的边界，而不是启动 `:app` 迁移

---

## 5. 实施顺序

### Phase A. 恢复 `:app` 独立实现

完成定义：

- `:app` 已恢复为独立旧实现
- `:app` 不再编译依赖 `:pixel-core`
- `:app` 不再编译依赖 `:pixel-ui`
- `:app:assembleDebug` 可独立通过

执行原则：

- 直接用 Git 恢复 `app/`，不在 `app/` 中继续保留过渡层
- 不回退 `settings.gradle.kts`，保留框架模块入口

### Phase B. 完善 `:pixel-core`

完成定义：

- `pixel-core` 有可编译、可测试的像素底层能力
- 当前的分页实现被拆成轴向位移与合成原语
- `pixel-core` 不含分页语义和产品语义

当前阶段优先级：

1. `PixelBuffer`
2. `PixelPalette`
3. `ScreenProfile`
4. `FrameSwapBuffer`
5. `PixelFrameView`
6. `PixelAxis / AxisMotionState / AxisMotionController / AxisBufferComposer`

### Phase C. 完善 `:pixel-ui`

完成定义：

- `pixel-ui` 具备 retained build/runtime 与 Flutter 风格公开入口
- `pixel-demo` 已经通过 retained 主链稳定跑在真实设备上
- retained runtime 与 legacy render bridge 的职责边界持续收口

当前阶段优先级：

1. direct pipeline widget：`Text / DecoratedBox / Padding / Align / Center / SizedBox / Container / Row / Column / Stack / Positioned / TextField / OutlinedButton / PageView / ListView / SingleChildScrollView` 已经从 legacy/bridge fallback 迁出，源码统一收在 `internal/widgets`
2. render object pipeline：继续补齐 `RenderObjectWidget / RenderObjectElement / RenderBox / RenderSurface / RenderText / RenderFlex / RenderStack / RenderPagerViewport / RenderScrollViewport / PipelineOwner`
3. scroll 替换链路：`PageView / ListView / SingleChildScrollView` 已经建立 direct render object，旧 scroll widget 目录已清空，下一步删除不再使用的 legacy render support 与 bridge adapter
4. bridge/legacy 删除边界：bridge resolver、legacy widget adapter 与 legacy render support 只在未替换组件仍依赖时保留，不再做纯兼容型重构
5. demo 与测试验收：每一阶段都保持 `pixel-demo` 可安装运行，并用单测覆盖新增 pipeline 行为

### Phase D. 新增 `:pixel-demo`

完成定义：

- `pixel-demo` 可编译、可安装、可运行
- `pixel-demo` 依赖链为 `:pixel-demo -> :pixel-ui -> :pixel-core`
- demo 页能验证基础组件和分页交互

固定 demo 页面：

- 文本与字体页
- 调色板与像素形状页
- 文本输入页
- 横向分页页
- 纵向分页页
- 纵向列表页
- 基础布局与点击反馈页

### Phase E. 迁移 Launcher

只有当前面四个阶段全部通过后，才开始。

迁移原则：

- 先迁简单静态页和 pager 外壳
- 不先迁 Drawer 列表
- 不先迁短信页
- 不先迁 Idle / Charge
- 不允许在框架未稳定前做“边迁边兼容”的折中实现

---

## 6. 测试与验收

### 6.1 `:app`

当前阶段必须满足：

- `:app:assembleDebug` 通过
- 重新安装和启动 Launcher 成功
- 代码中不再出现对 `com.purride.pixelcore` 或 `com.purride.pixelui` 的编译引用

### 6.2 `:pixel-core`

当前阶段必须覆盖：

- 轴向运动测试
- 横向与纵向在相同阈值、释放规则下行为一致
- 轴向合成测试
- 两个轴向的帧拼接结果正确

### 6.3 `:pixel-ui`

当前阶段必须覆盖：

- `PixelPager` 横向分页
- `PixelPager` 纵向分页
- `PixelList` 纵向滚动与边界夹紧
- 列表视口裁剪与点击命中
- 分页边界夹紧
- 阈值翻页
- 速度翻页
- 分页快照正确性

### 6.4 `:pixel-demo`

当前阶段必须满足：

- Demo 可安装运行
- 能演示文本渲染
- 能演示基础布局
- 能演示点击反馈
- 能演示横向分页
- 能演示纵向分页
- 能演示纵向列表滚动与列表项点击

---

## 7. 当前进度基线

| 状态 | 里程碑 | 完成定义 |
| --- | --- | --- |
| 已完成 | `:app` 恢复独立实现 | Launcher 不再依赖 `:pixel-core`，可独立构建运行 |
| 已完成 | `:pixel-core` / `:pixel-ui` 模块入口存在 | 仓库已经具备独立框架模块入口 |
| 已完成 | 框架边界冻结 | `core/ui/demo/app` 的职责边界已确定，本文档是当前执行口径 |
| 已完成 | `:pixel-core` 轴向原语重构 | `core` 已经落下 `PixelAxis / AxisMotionController / AxisBufferComposer` |
| 已完成 | `:pixel-core` 字体底座首轮落地 | `core` 已具备字形包解析、位图字形源与 `PixelFontEngine` |
| 已完成 | 默认字体链路统一到文本引擎 | `PixelBitmapFont` 已改为走 `PixelFontEngine`，默认文本链路与真实字体底座对齐 |
| 已完成 | 节点级文本栅格器覆盖 | `pixel-ui` 文本节点已支持按节点覆盖文本栅格器，宿主与节点两层扩展点都已打通 |
| 已完成 | `pixel-ui` 文本样式对象 | 文本色阶与文本栅格器已收敛为 `PixelTextStyle`，页面层不再直接暴露底层字体实现 |
| 已完成 | `pixel-ui` 基础按钮组件 | 按钮已收敛为 `PixelButton` 与 `PixelButtonStyle`，demo 不再重复手写按钮结构 |
| 已完成 | `:pixel-ui` Flutter 风格公开层 | 页面层主路径已转到 `Widget / BuildContext / Text / Container / Row / Column / PageView / ListView / TextField` |
| 已完成 | retained build/runtime 首轮落地 | `BuildOwner / Element / Stateful / Inherited` 已成立，并已在 demo 真实使用 |
| 已完成 | legacy renderer 主链拆分 | `PixelRenderRuntime` 已不再承担文本、输入、viewport、布局、测量的全部细节 |
| 已完成 | `pixel-ui` 基础列表组件 | `pixel-ui` 已具备 `ListView`、`PixelListState`、`ScrollController`，并支持列表视口裁剪、触摸滚动与基础惯性滚动 |
| 已完成 | `pixel-ui` 同轴复合手势仲裁 | 纵向 `Pager` 内部嵌套纵向 `List` 时，列表优先消费自身还能处理的拖动 |
| 已完成 | `pixel-ui` 列表到分页滚动接力 | 列表滑到边界后，同一次纵向手势可直接接力给外层分页，无需抬手重新触发 |
| 已完成 | `pixel-ui` 最小文本输入链路 | `pixel-ui` 已具备 `PixelTextField`、文本输入目标、宿主输入桥接接线和基础测试 |
| 已完成 | `pixel-ui` 基础权重布局 | `Row/Column` 已支持基于 `Modifier.weight(...)` 按比例分配剩余空间 |
| 已完成 | `pixel-ui` 交叉轴对齐 | `Row/Column` 已支持 `START / CENTER / END` 交叉轴对齐，demo 已补可视化验证 |
| 已完成 | `pixel-ui` 主轴排布首轮落地 | `Row/Column` 已支持 `START / CENTER / END` 主轴排布，demo 已补可视化验证 |
| 已完成 | `pixel-ui` 列表程序化定位首轮落地 | `ScrollController` 已支持基于运行时测量结果将指定项滚入视口，demo 已补跳转验证 |
| 已完成 | `pixel-ui` 单子节点滚动容器 | 已具备 `PixelSingleChildScrollView`，可承载单一长子树并复用现有纵向滚动链路 |
| 已完成 | `:pixel-demo` 宿主 | Demo 已可编译，并覆盖文本、调色板、文本输入、单子节点滚动、横纵分页、纵向列表、表单与列表组合、分页与列表组合、点击反馈、混合文本风格验证与权重布局展示 |
| 进行中 | retained/runtime 与 legacy bridge 收口 | 正在继续削弱 retained 主链对 bridge/legacy 默认装配细节的感知，并把 `BuildOwner` 进一步收成 owner/scheduler |
| 未开始 | Launcher 迁移 | 在 demo 自证前不启动 |

---

## 8. 接手规则

后续任何工程师接手时，必须遵守以下规则：

1. 不允许把 `LauncherState`、`Drawer`、`Idle`、`SMS` 等产品语义带进 `:pixel-core`
2. 不允许把 `Pager`、`List` 这类 UI 语义直接塞进 `:pixel-core`
3. 不允许让 `:app` 继续依赖半成品 `:pixel-core` 或 `:pixel-ui`
4. 不允许通过兼容别名把框架内部实现偷偷挂回 `:app`
5. 不允许把 `PixelBuffer` 直接暴露成业务搭页面的主路径
6. 每完成一个阶段，都必须更新本文档中的“当前进度基线”

---

## 9. 当前推荐起手顺序

如果从现在开始继续实现，推荐起手顺序固定为：

1. 固定 Flutter 式 `Widget -> Element -> RenderObject` 基础协议
2. 建最小 `RenderObject / PipelineOwner` 骨架
3. 打通 `Text + Surface` 首批新渲染链路
4. 保持 `bridge + legacy` 作为整树 fallback
5. 继续用 `pixel-demo` 做验收

这份顺序不是建议，而是当前阶段的执行顺序。

---

## 10. 当前阶段具体后续工作

当前阶段后续工作统一按下面四组推进，但由同一条主线持续推进，不再单独拆成并行执行文档。

### 10.1 pipeline bootstrap

目标：

- 落第一版最小新渲染管线
- 先证明 `pixel-ui` 已经具备脱离 `legacy renderer` 出图的真实能力

范围：

- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline`
- pipeline 相关测试与 demo 场景
- 与 pipeline 接入直接相关的 runtime glue

完成定义：

- 新增内部 `RenderObjectWidget / RenderObjectElement` 协议，先固定 widget 配置如何创建/更新 render object
- 新增内部 `SingleChildRenderObjectWidget / SingleChildRenderObjectElement` 协议，先固定单 child render object 的 element 复用和子节点挂接
- 新增内部 `MultiChildRenderObjectWidget / MultiChildRenderObjectElement` 协议，先固定多 child render object 的 element 复用和子节点挂接
- `RenderFlex` 改为继承 `MultiChildRenderObject`，不再私有手写 render child 生命周期
- 公开 `Text` 的首批受支持配置改为直接创建 `RenderText`，pipeline renderer 优先消费 retained tree 上的 direct render root
- 公开 `DecoratedBox` 改为直接创建 `RenderSurface`，首批 `DecoratedBox + Text` 子树不再依赖 bridge lowering
- 公开 `Padding / Align / Center` 改为直接创建 `RenderSurface`，首批单 child 布局壳开始共享 render object 主链
- 公开 `SizedBox / Container / GestureDetector / Row / Column / Stack / Positioned / Expanded / Flexible / Spacer / TextField / OutlinedButton` 改为 direct render object 或 direct widget 组合，不再保留这些 widget 的 bridge fallback
- `RenderFlex` 新增基础 flex 权重分配，`RenderStack / RenderPositioned` 新增基础叠放与定位能力，pipeline result 开始导出 direct text input target
- 新增最小内部协议：
  - `PipelineOwner`
  - `RenderObject`
  - `RenderBox`
  - `RenderConstraints`
  - `RenderSize`
  - `PaintContext`
  - `HitTestResult`
- 第一批具体 render object 已落地：
  - `RenderText`
  - `RenderSurface`
- `PaintContext` 直接基于 `PixelBuffer` 绘制，不复用 `legacy renderer` 的 render support

### 10.2 retained -> pipeline lowering

目标：

- 把 retained element tree 接入新 pipeline
- 第一版只做整树级分流，不做混合子树渲染

范围：

- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime`
- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge`
- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline`
- lowering 相关测试与文档引用

完成定义：

- 新增 `PipelineElementTreeRenderer`
- 新增显式 capability checker，集中维护“哪些兼容节点树可以整树走新 pipeline”
- capability checker 需要能提供明确回退原因，而不只是 `true / false`
- retained 主链改成：
  - 先尝试新 pipeline renderer
  - 任意不支持节点则整树回退到 `BridgeElementTreeRenderer`
- 第一批结构性支持 widget 固定为：
  - `Text`
  - `Container` / `DecoratedBox`
  - `Padding`
  - `Align` / `Center`
  - `SizedBox`
  - `Align`
  - `Center`
- 当前已额外支持最小 `Row / Column` 基础排布，并覆盖 `START / CENTER / END`、`SPACE_*` 与 `stretch`，但仍不支持 `Expanded / Flexible` 这类权重场景
- 允许这些节点通过 modifier 形式携带最小 clickable / size / padding / fill 语义
- 不做：
  - `PageView`
  - `ListView`
  - `SingleChildScrollView`
  - `TextField`
  - mixed subtree pipeline/legacy 渲染

### 10.3 bridge/legacy fallback 维持

目标：

- 维持当前 fallback 链稳定可用
- 不再把“继续拆 factory/assembly”当作主线进展

范围：

- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy`
- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy`
- `pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge`

完成定义：

- `legacy` 继续只作为内部 fallback 后端存在
- `bridge` 继续只做最薄兼容层
- 后续只在以下两种情况下修改这两层：
  - 修复 bug
  - 直接支撑新 pipeline 接入
- 冻结纯 `assembly/factory` 型重构，避免继续投入低收益整理

### 10.4 文档、测试、注释与验收

目标：

- 给新 pipeline 主线持续兜底
- 防止“代码已经转向，但文档还停在 legacy 收口”这种错位继续发生

范围：

- `docs/design/engine`
- `pixel-ui/src/test`
- `pixel-demo`

完成定义：

- 文档与目录结构同步
- 至少有一个 demo 场景完整走新 pipeline
- 注释覆盖不再只靠“碰到再补”
- pipeline 核心类和方法具备可读注释

---

## 11. 当前阶段合入门槛

每一轮主线改动都必须通过：

```bash
./gradlew :pixel-core:testDebugUnitTest :pixel-ui:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```

真机回归门槛：

```bash
adb -s <device> install -r /Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/build/outputs/apk/debug/pixel-demo-debug.apk
adb -s <device> shell am start -n com.purride.pixeldemo/.app.DemoMenuActivity
```

最低验收要求：

- demo 菜单可启动
- “新渲染管线”页面可打开
- 文本页可打开
- 输入页可打开
- 列表/分页页可打开
- 设备在线时，新渲染管线页面至少确认：
  - 文本可见
  - 容器背景与边框正确
  - 点击命中不回退

---

## 12. 下一阶段目标

当前阶段完成定义不再是“继续把 legacy wiring 拆得更细”，而是：

- 至少有一个真实 demo 场景不经过 `legacy renderer`
- retained 主链已经具备新 pipeline 与旧 fallback 的整树级分流能力
- pipeline 支持边界已经由显式 capability checker 维护，而不是散落在 lowering 分支里
- pipeline 回退原因已经可以被显式观测和测试覆盖

在这之后，下一阶段才进入：

- 让更多基础组件直接成为 `RenderObjectWidget`
- 扩展 `RenderObject / PipelineOwner`
- 补齐 `Row / Column / Align / Padding` 的长期 render object 形状
- 更完整的 lowering 能力
- 新 renderer 承接更多基础组件集

在这之前，不做：

- `:app` 页面迁移
- 删除整套 `legacy`
- 一次性重写 `PageView / ListView / TextField`
- 再把“继续拆 legacy factory/assembly”当成主要主线

一句话说，当前阶段的目标不是“把新 renderer 一口气写完”，而是：

> 用一条真实可见的最小新渲染链路，证明后端替换已经开始，而不再只是整理过渡层。
