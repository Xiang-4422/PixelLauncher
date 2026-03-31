# 像素 UI 引擎架构与实施计划（第一版）

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

- `PixelScene`
- `PixelNode`
- `PixelModifier`
- 通用布局容器
- 通用文本与表面组件
- 通用点击、分页、焦点、命中测试
- `PixelPager(axis = ...)`
- `PixelPagerState`
- `PixelPagerController`
- `PixelPagerSnapshot`

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

### 4.2 `:pixel-ui` 第一批稳定接口

当前阶段要在 `:pixel-ui` 中稳定下来的分页接口如下：

```kotlin
data class PixelPagerState(...)

data class PixelPagerSnapshot(...)

class PixelPagerController(...)

fun PixelPager(
    axis: PixelAxis,
    state: PixelPagerState,
    ...
)
```

分页语义明确归 `:pixel-ui`：

- `pageCount`
- `currentPage`
- `targetPage`
- 分页阈值
- 分页边界
- 手势到分页语义的映射

### 4.3 `:pixel-ui` 第一版最小组件集

这一轮只实现最小必要组件，不扩散范围。

固定要做的组件：

- `PixelText`
- `PixelSurface`
- `PixelBox`
- `PixelRow`
- `PixelColumn`
- `PixelPager`

这一轮明确不做：

- `PixelList`
- `PixelTextField`
- 通用列表滚动 runtime
- 产品级组件库

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

- `pixel-ui` 具备最小可运行组件与分页 runtime
- `PixelPager(axis = ...)` 可以独立跑起来
- 横向分页和纵向分页都能由 `pixel-ui` 自己驱动

当前阶段优先级：

1. `PixelNode`
2. `PixelModifier`
3. `PixelText`
4. `PixelSurface`
5. `PixelBox / PixelRow / PixelColumn`
6. `PixelPagerState / PixelPagerController / PixelPagerSnapshot`
7. `PixelPager`

### Phase D. 新增 `:pixel-demo`

完成定义：

- `pixel-demo` 可编译、可安装、可运行
- `pixel-demo` 依赖链为 `:pixel-demo -> :pixel-ui -> :pixel-core`
- demo 页能验证基础组件和分页交互

固定 demo 页面：

- 文本与字体页
- 调色板与像素形状页
- 横向分页页
- 纵向分页页
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
| 已完成 | `:pixel-ui` 最小分页 runtime | `pixel-ui` 已具备最小布局组件、`PixelPager` 与 `PixelHostView` |
| 已完成 | `:pixel-demo` 宿主 | Demo 已可编译、安装、运行，并覆盖文本、调色板、横纵分页、点击反馈、混合文本风格验证 |
| 进行中 | `pixel-ui` 运行时补稳 | 正在继续补布局、命中、分页子页面交互和对应测试 |
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

1. 把 `pixel-core` 中旧的 `HorizontalPageView` 重构成轴向原语
2. 给 `pixel-core` 补齐轴向原语测试
3. 在 `pixel-ui` 落下 `PixelPagerState / PixelPagerController / PixelPagerSnapshot`
4. 在 `pixel-ui` 落下最小布局与 `PixelPager`
5. 新增 `:pixel-demo`
6. 用 demo 验证横向与纵向分页
7. 通过后才讨论 Launcher 迁移

这份顺序不是建议，而是当前阶段的执行顺序。
