# 像素 UI 引擎架构与实施计划（第一版）

本文档是 PixelLauncher 当前“像素 UI 引擎”方向的单一交接文档。  
它同时承担四个职责：

- 说明当前仓库的真实基线，避免接手者误判现状
- 明确像素引擎的目标、边界和不做的事情
- 给出模块设计、公开接口、运行时设计和迁移策略
- 提供阶段步骤、当前进度、验收标准和下一位工程师的起手顺序

如果只看一句话，这个方向的目标是：

> 在 Android 内容区内建立一套声明式、自绘、可复用的像素 UI runtime，让业务 App 能基于通用组件快速搭建像素化界面，而不是继续为每个页面单独写专用 renderer。

注意：

- 这不是产品 PRD，也不是纯路线图
- 这不是“完整替代 Android framework”的方案
- 若与旧规划文档冲突，以当前代码和本文档为准

---

## 1. 文档定位

### 1.1 目标定义

本项目要构建的是：

- 面向 Android 的像素 UI runtime
- 运行在 Android `Activity` 内容区中的自绘 UI 系统
- 由业务状态驱动、由组件树描述页面、由像素引擎统一负责布局与绘制

本项目不定义为：

- 完整替代 Android UI framework
- 跨平台框架
- 第一阶段就具备 Compose 编译器能力的系统
- 第一阶段就重做 IME、权限、Window、无障碍、导航栈的系统

### 1.2 参考对象

这个引擎应主动借鉴两类成熟系统，但只借鉴适合当前阶段的部分。

参考 Compose 的部分：

- 声明式页面 API
- 状态提升与单向数据流
- `Modifier` 风格的布局与交互装饰
- `Measure/Layout` 分离的布局思路

参考 Flutter 的部分：

- 自绘 Surface
- retained runtime
- 输入命中测试
- 滚动、分页、手势和动画由统一运行时管理

明确不借鉴或暂不实现的部分：

- Compose 编译器插件
- `@Composable`
- slot table / compiler-assisted recomposition
- Flutter 全量 Widget / Element / RenderObject 复杂分层
- 完整平台语义树与无障碍系统

### 1.3 第一阶段目标

第一阶段的固定目标如下：

- 同仓多模块演进，不新开独立仓库
- 采用纯 Kotlin DSL，不做编译器插件
- Android `Activity` 继续作为宿主壳
- 用 Launcher 的 `Home / Drawer / Settings` 三页迁移，验证工具包成立

---

## 2. 当前仓库真实基线

### 2.1 当前模块情况

当前仓库只有一个业务模块：

- `:app`

`settings.gradle.kts` 当前真实内容仅为：

```kotlin
rootProject.name = "PixelLauncherV2"
include(":app")
```

这意味着：

- 还没有 `:pixel-core`
- 还没有 `:pixel-ui`
- 还没有真正的引擎级边界

### 2.2 当前已经存在、可直接复用的底座

以下能力已经具备明显的通用底座价值：

- `PixelBuffer`
- `PixelFontEngine`
- `PixelFrameView`
- `PixelDisplayView`
- `PixelGlDisplayView`
- `ScreenProfile`
- `HorizontalPageController`

这些代码已经接近“像素显示内核”，主要集中在：

- `app/src/main/kotlin/com/purride/pixellauncherv2/render`

### 2.3 当前不能直接抽成通用模块的核心原因

当前引擎方向最大的结构问题，不是“没有绘制能力”，而是“绘制能力和 Launcher 页面业务深绑”。

核心原因如下：

1. `PixelRenderer.kt` 直接依赖 launcher 专属类型
   - 直接依赖 `LauncherState`
   - 直接依赖 `HomeLayout`
   - 直接依赖 `AppListLayout`
   - 直接依赖 `SettingsMenuModel`
   - 直接依赖 `TextListSupport`

2. `MainActivity.kt` 当前仍承担大量运行时编排职责
   - 输入分发
   - 页面切换
   - 搜索输入代理
   - 列表滚动与分页编排
   - 系统副作用与权限桥接

3. 有通用潜力的代码仍然挂在 Launcher 包内
   - `TextListSupport`
   - 各类 layout metrics 与 hit-test 逻辑
   - 页面模式切换逻辑的一部分

### 2.4 当前 UI 主链路

当前真实主链路如下：

1. `MainActivity`
2. `LauncherStateTransitions`
3. `PixelRenderer`
4. `PixelFrameView`

也就是：

`MainActivity -> LauncherStateTransitions -> PixelRenderer -> PixelFrameView`

当前不是 Android 控件树驱动 UI，而是：

- 单 `Activity`
- 单状态源 `LauncherState`
- 自定义像素渲染链路

### 2.5 当前目录角色

当前核心目录角色如下：

- `app`
  - 宿主 Activity 和运行时编排
- `launcher`
  - 业务状态、页面模式、页面布局、列表模型、搜索模型
- `render`
  - 像素显示、字体、缓冲、页面绘制、显示后端
- `data`
  - 系统服务、网络与持久化接线层
- `system`
  - 启动 App、窗口模式等系统桥接

---

## 3. 目标架构

### 3.1 三层模块结构

目标模块结构固定为：

#### `:pixel-core`

职责：

- 纯像素内核
- 与具体业务页面无关
- 只提供像素显示与低层运行时能力

包含内容：

- `PixelBuffer`
- 字体与字形系统
- 调色板
- 屏幕参数
- `PixelFrameView`
- `PixelDisplayView`
- `PixelGlDisplayView`
- 基础动画和几何工具
- 分页控制器等通用低层控制器

禁止事项：

- 不依赖 `LauncherState`
- 不依赖 `launcher` 包
- 不依赖任何业务页面模型

#### `:pixel-ui`

职责：

- 通用像素 UI runtime
- 承载组件树、布局树、绘制树、命中测试、事件分发、焦点、滚动、分页、文本输入桥接协议

包含内容：

- `PixelNode`
- `PixelModifier`
- `PixelScene`
- retained tree runtime
- 通用布局与组件
- 列表、分页、输入组件
- 宿主桥接接口

禁止事项：

- 不依赖 `:app`
- 不依赖 `LauncherState`
- 不携带 `Home / Drawer / Settings` 这类 Launcher 专属概念

#### `:app`

职责：

- Launcher 宿主应用
- 只保留业务状态、数据仓库、权限、系统服务、副作用和场景映射

保留内容：

- `MainActivity`
- `LauncherState`
- `LauncherStateTransitions`
- `data`
- `system`
- Launcher 场景构建逻辑

### 3.2 总体数据与渲染流

目标链路为：

1. 业务层持有 `State`
2. `Scene(State)` 构建 `PixelNode` 树
3. runtime 对节点树做 diff
4. 维护 retained layout/render tree
5. 执行 measure/layout
6. paint 到 `PixelBuffer`
7. `PixelHostView` 把 buffer 交给 `PixelFrameView`
8. 输入通过命中测试和手势系统回流为业务 `Action`

即：

`State -> Scene(State) -> PixelNode tree -> diff -> layout/render tree -> PixelBuffer -> PixelFrameView`

### 3.3 宿主职责边界

Android 宿主继续负责：

- `Activity`
- `Window`
- `Intent`
- 权限申请
- IME
- 生命周期
- 系统服务

像素引擎负责：

- 页面内容区布局
- 页面内容区绘制
- 命中测试
- 手势分发
- 焦点
- 滚动
- 分页

明确不在第一阶段接管：

- Android 导航栈
- 权限模型
- Window 管理
- 多进程 UI 协调
- 完整无障碍语义系统

---

## 4. 公开接口与类型

以下接口是第一版需要稳定下来的引擎级接口。

### 4.1 `PixelScene<State, Action>`

职责：

- 业务页面入口
- 输入业务状态
- 输出组件树

建议形态：

```kotlin
fun interface PixelScene<State, Action> {
    fun render(state: State, handlers: PixelActionSink<Action>): PixelNode
}
```

说明：

- 第一版使用纯 Kotlin DSL
- 不使用 `@Composable`

### 4.2 `PixelNode`

职责：

- 所有组件节点的统一抽象
- 是组件树最小公共语义

应承载的信息：

- 组件类型
- `Modifier`
- children
- key
- 组件参数

### 4.3 `PixelModifier`

职责：

- 统一表达布局、交互和语义装饰

第一版必须覆盖：

- 尺寸
- padding
- 对齐
- 背景/边框
- clickable
- scrollable
- focusable
- 语义 id / test tag

### 4.4 `PixelHostBridge`

职责：

- 宿主与引擎之间的桥接接口

第一版至少包含：

- 显示输入法
- 隐藏输入法
- 更新输入焦点
- 触发宿主动作回调
- 动画调度
- haptic

### 4.5 runtime 状态对象

第一版要求所有持续 UI 状态显式提升，不做隐式 `remember`。

必须定义：

- `PixelUiState`
- `PixelListState`
- `PixelPagerState`
- `PixelTextFieldState`

原则：

- 业务显式持有状态对象
- Scene 只消费状态对象，不隐式创建长期状态

### 4.6 `CustomDraw`

职责：

- 提供受控的自定义像素绘制逃生口

用途：

- 图表
- 特殊粒子
- 波形
- 复杂像素图形

约束：

- 不能替代通用组件体系
- 只能作为补充能力

### 4.7 第一版明确不用的能力

第一版明确不做：

- 编译器插件
- `@Composable`
- 隐式 `remember`
- Android View 级组件树
- 无障碍语义系统
- 复杂嵌套滚动协调

---

## 5. 第一版组件集

第一版组件范围固定如下。

### 5.1 布局组件

- `PixelBox`
- `PixelRow`
- `PixelColumn`
- `PixelSpacer`

### 5.2 内容组件

- `PixelText`
- `PixelIcon`
- `PixelSurface`

### 5.3 交互组件

- `PixelClickable`
- `PixelButton`

### 5.4 容器组件

- `PixelScrollColumn`
- `PixelList`
- `PixelPager`

### 5.5 输入组件

- `PixelTextField`

### 5.6 高级组件

- `PixelHeader`
- `PixelMenuRow`
- `PixelIndexedRail`

### 5.7 组件层约束

必须写清楚以下约束：

- 第一版不允许业务方长期依赖页面专属 renderer 分支
- 默认通过组件树搭页面
- `CustomDraw` 只是补充能力，不是主路径

---

## 6. 运行时设计

### 6.1 组件树与 retained runtime

第一版采用“声明式组件树 + retained runtime”的混合设计。

基本原则：

- 每次业务状态变化时，可以重新执行 scene builder
- 允许重新生成新的 `PixelNode` 树
- 但不允许每一帧都重建整棵 layout/render tree

runtime 需要做的事：

- 对新旧组件树做 diff
- 复用稳定节点
- 更新 retained layout tree
- 更新 retained draw tree

节点复用规则第一版固定为：

- `type + key + position`

### 6.2 布局系统

布局系统参考 Compose 的 `MeasurePolicy` 思路。

基本要求：

- 父节点向子节点传约束
- 子节点返回测量结果
- 测量和放置分离
- 所有坐标都使用逻辑像素

第一版必须支持：

- 固定尺寸
- 包裹内容
- 填满父容器
- 对齐
- padding / spacing
- 单轴滚动
- 列表虚拟窗口
- 横向分页

### 6.3 绘制系统

绘制系统负责：

- 把 retained draw tree 输出为 `PixelBuffer`
- 由宿主 `PixelFrameView` 最终显示

要求：

- 文本绘制只通过统一字体引擎
- 通用组件使用统一调色板与主题
- 绘制顺序与 hit-test 顺序保持一致

### 6.4 输入与手势

输入系统参考 Flutter，但不做完整 Flutter 手势竞技场。

第一版必须支持：

- tap
- press
- horizontal drag / pager
- vertical drag / list
- fling settle
- key focus move
- text input focus

默认约束：

- 单次只允许一个主拖动目标
- 不支持复杂嵌套滚动竞争
- 运行时统一处理手势仲裁

### 6.5 文本输入

文本输入不在引擎内部重做 IME。

第一版方案固定为：

- `PixelTextField` 只表达文本输入语义
- 宿主通过 `PixelHostBridge` 使用原生输入法桥接
- 输入结果回流到 `PixelTextFieldState`

---

## 7. 实施步骤

### Phase 0. 文档落地与边界冻结

目标：

- 建立单一事实来源
- 冻结第一阶段范围和不做项

执行项：

1. 新建本交接文档
2. 写清楚目标、边界、模块结构和当前基线
3. 写清楚第一版 API 与组件集
4. 写清楚接手顺序、验收标准和进度表

完成定义：

- 接手者不需要阅读多份零散文档才能知道该怎么开工

### Phase 1. 抽出 `:pixel-core`

目标：

- 把纯像素内核从 `:app` 中独立出来

执行项：

1. 修改 `settings.gradle.kts`
2. 新增 `:pixel-core`
3. 迁移纯渲染底座代码
4. 调整依赖边界
5. 跑现有字体、缓冲、分页控制器和显示相关测试

完成定义：

- `:pixel-core` 可独立编译
- `:pixel-core` 不依赖 `launcher` 或 `app`

### Phase 2. 建立 `:pixel-ui` runtime

目标：

- 把“通用 UI runtime”从 Launcher 页面逻辑中独立出来

执行项：

1. 新增 `:pixel-ui`
2. 定义 `PixelNode / PixelModifier / PixelHostBridge / PixelUiState`
3. 建 retained tree
4. 实现 diff、measure/layout、paint、hit test
5. 升级分页、列表滚动和输入分发为 runtime 能力
6. 实现 `PixelHostView`

完成定义：

- 可用最小组件树渲染一页纯像素页面
- 可处理 tap、drag、pager、list scroll

### Phase 3. 实现第一版基础组件

目标：

- 让业务层不依赖 Launcher 类型也能搭完整页面

执行项：

1. 完成布局组件
2. 完成文本、surface、clickable
3. 完成 list、pager、text field
4. 完成 header、menu row、indexed rail
5. 补充 `CustomDraw`

完成定义：

- 可以写出完整像素页面
- 有组件级测试和渲染快照测试

### Phase 4. 迁移 Launcher 三页

目标：

- 用真实业务页面证明工具包成立

执行项：

1. `Home` 改为 `LauncherState -> PixelNode`
2. `Drawer` 改为 `LauncherState -> PixelNode`
3. `Settings` 改为 `LauncherState -> PixelNode`
4. `MainActivity` 只保留状态编排、桥接和副作用
5. 逐步删除 `PixelRenderer` 中对应页面的专属分支

完成定义：

- 三页完全跑在新组件体系上
- 现有交互行为不回退

### Phase 5. 独立 Demo 与复用验证

目标：

- 证明这不是 Launcher 私有框架

执行项：

1. 新增一个不依赖 `LauncherState` 的 demo scene 或 demo app
2. 用 demo 验证工具包可复用
3. 补齐接入说明和最小示例

完成定义：

- 第三方页面不接触 `PixelBuffer` 也能搭建像素界面
- 文档中附有最小接入示例

---

## 8. 当前进度基线

以下进度以当前仓库真实状态为准。

| 状态 | 里程碑 | 完成定义 |
| --- | --- | --- |
| 已完成 | 单 `Activity` + 单状态源 + 自绘像素链路 | `MainActivity -> LauncherStateTransitions -> PixelRenderer -> PixelFrameView` 已成立 |
| 已完成 | 纯像素显示底座初步存在 | `PixelBuffer / PixelFontEngine / PixelFrameView / PixelDisplayView / PixelGlDisplayView / ScreenProfile / HorizontalPageController` 已存在 |
| 已完成 | Launcher 关键交互底盘已存在 | 抽屉、设置、分页、输入、滚动等已有参考实现 |
| 已完成 | 测试基础已建立 | 渲染、字体、分页控制器已有单测基础 |
| 进行中 | 引擎边界已明确 | 已确认可抽底座、不可直接抽的业务绑定点，以及目标模块边界 |
| 进行中 | 架构方向已决策 | 同仓多模块、纯 Kotlin DSL、Activity 宿主壳、迁移三页验证 |
| 未开始 | `:pixel-core` 拆分 | 尚未修改 Gradle 模块结构 |
| 未开始 | `:pixel-ui` runtime 实现 | 尚未建立 `PixelNode`、retained tree 和运行时 |
| 未开始 | 通用组件树 | 尚未建立可复用组件集 |
| 未开始 | Launcher 三页迁移 | 仍依赖 `PixelRenderer` 页面分支 |
| 未开始 | 独立 demo | 尚无脱离 Launcher 类型的示例场景 |
| 未开始 | 正式对外接入文档 | 尚无独立接入说明和最小示例 |

---

## 9. 接手规则

所有后续接手工程师必须遵守以下规则：

1. 不允许把 `LauncherState` 带进 `:pixel-core` 或 `:pixel-ui`
2. 不允许继续在 `MainActivity` 中增加页面专属绘制逻辑
3. 不允许让组件系统重新退化成业务方直接写 `PixelBuffer`
4. 第一版所有持续 UI 状态必须显式提升
5. 第一版不做隐式 `remember`
6. 第一版保留 Android 宿主壳，不重做 IME、权限和 `Activity`
7. 每完成一个 phase，必须更新本文档中的进度和验收结果

---

## 10. 测试与验收标准

### 10.1 固定验收标准

后续工作必须满足以下硬性验收标准：

- `:pixel-core` 无业务依赖
- `:pixel-ui` 不依赖 `:app`
- Launcher 的 `Home / Drawer / Settings` 迁移完成
- 独立 demo scene 可运行
- `PixelTextField` 可通过宿主桥接正常输入
- `PixelList / PixelPager` 的滚动和分页行为有单测
- 组件树渲染有 golden tests
- 新页面接入时不需要新增页面专属 renderer 分支

### 10.2 各阶段最低测试要求

`Phase 1`：

- 字体、缓冲、几何、分页控制器测试全部可继续运行

`Phase 2`：

- diff、layout、hit-test、scroll、pager 基础测试

`Phase 3`：

- 组件级单测
- 组件级渲染快照测试

`Phase 4`：

- Launcher 三页迁移后的渲染和交互回归测试

`Phase 5`：

- demo page 基础渲染与输入回归测试

---

## 11. 下一位工程师的起手顺序

如果由下一位工程师立刻接手，建议按以下顺序启动：

1. 阅读根目录 `README.md`
2. 阅读《技术实现总览（第一版）》
3. 阅读《渲染实现原理（第一版）》
4. 阅读本文档
5. 先做 `Phase 1` 的目录和模块边界设计
6. 列出 `render/` 中可迁入 `:pixel-core` 的具体文件清单
7. 明确 `PixelRenderer` 中必须滞留在 `:app` 的部分
8. 再进入模块拆分实施

开工前的第一件事不是写组件，而是：

- 先把 `:pixel-core / :pixel-ui / :app` 边界钉死

如果边界不先锁定，后续实现极容易退化回“继续在 `:app` 里长大”。

---

## 12. 默认假设

除非后续文档明确修订，默认假设如下：

- 文档语言为中文
- 第一版只覆盖 Android
- 第一版采用纯 Kotlin DSL
- 第一版不做编译器插件
- 第一版不做完整无障碍
- 第一版不做完整 Android UI 替代
- 本交接文档默认作为单文档承载，不再拆成路线图、技术方案和任务表三份独立文档
- 后续所有引擎相关实施，都应先以本文档为基线更新

