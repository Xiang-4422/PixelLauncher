# 像素 UI 引擎架构与实施计划

这份文档是当前像素 UI 引擎的执行口径。历史上引擎曾拆成 `pixel-core` 和 `pixel-ui` 两个 Gradle 模块；当前已经收敛为单一模块 `:pixel-engine`，内部继续用 package 保留低层 core 与 UI runtime 的边界。

## 1. 当前架构

当前工程模块：

- `:app`：现有 PixelLauncher 产品实现，当前已有实验性 engine 接入；本轮不继续扩大页面迁移范围
- `:pixel-engine`：像素显示内核、UI runtime、组件、布局、输入、滚动、宿主桥接
- `:pixel-demo`：引擎验收宿主

`settings.gradle.kts` 只包含：

```kotlin
include(":app")
include(":pixel-engine")
include(":pixel-demo")
```

当前依赖方向：

```text
:pixel-demo -> :pixel-engine
:app        -> :pixel-engine（已有实验性接入，后续主线暂不继续迁移）
```

## 2. `pixel-engine` 内部分层

当前 `:pixel-engine` 是单一 Gradle 模块，但源码按 package 保持三类目录：

- `com.purride.pixelcore`：底层像素能力层
- `com.purride.pixelui`：Widget/runtime/render/host 层
- `com.purride.pixelengine`：模块 marker 层

这些目录只用于源码组织和边界表达；公开 Kotlin package 继续保持稳定，不作为再次拆分 Gradle 模块的信号。

### 2.1 Core package

路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelcore](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore)

职责：

- 像素缓冲
- 调色板
- 显示几何
- 帧交换
- 字体底座
- 字形包解析与加载
- 轴向位移与合成原语

禁止放入：

- Launcher 产品语义
- 页面模式
- Drawer、Home、Idle、SMS 等业务概念
- 分页、列表、按钮、输入框这类 UI 语义

### 2.2 UI package

路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelui](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui)

职责：

- Widget 公开 API
- retained build/runtime
- direct render pipeline
- render object 树
- 基础布局
- 文本、按钮、输入
- 分页、列表、滚动
- 手势仲裁
- 宿主桥接

禁止放入：

- Launcher 产品状态
- 真实系统服务读取
- app 页面专属逻辑
- 为迁移临时保留的旧渲染 fallback

### 2.3 Engine marker package

路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelengine](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelengine)

职责：

- 标记 `:pixel-engine` Gradle 模块统一承载 core 与 UI runtime
- 给后续模块级文档、诊断或装配入口预留稳定命名空间

禁止放入：

- 具体像素缓冲、字体、几何等 core 实现
- Widget、render object、宿主桥接等 UI 实现
- Launcher 产品语义或页面逻辑

## 3. 当前公开主路径

页面层优先使用 Flutter 风格 API：

- `Widget`
- `BuildContext`
- `Text`
- `Container`
- `Padding`
- `SizedBox`
- `Align`
- `Center`
- `Row`
- `Column`
- `Expanded`
- `Stack`
- `Positioned`
- `PageView`
- `ListView`
- `SingleChildScrollView`
- `TextField`
- `OutlinedButton`
- `PageController`
- `ScrollController`
- `TextEditingController`

旧的节点式公开 API 不再作为新页面推荐入口。

## 4. 渲染主链路

当前默认链路：

```text
Widget
-> retained Element tree
-> RenderObject tree
-> PipelineOwner
-> PixelBuffer
-> PixelHostView
```

旧 bridge lowering 和 legacy render backend 已经从生产源码删除。后续不要重新引入“旧后端 fallback”作为主路径兜底。

## 5. 当前阶段目标

短期主线不是继续推进 `:app` 页面迁移，而是把 `pixel-engine` 本身补稳：

- 补稳 `RenderObject / PipelineOwner / RenderObjectWidget` 长期职责
- 扩展 direct pipeline 的基础布局能力
- 补强输入、滚动和手势边界
- 用 `pixel-demo` 验证真实场景
- 暂停扩大 `:app` 迁移面，等 engine/demo gate 更稳定后再迁移业务页

## 6. 已完成

- 单模块 `:pixel-engine` 已承接原 core/UI 能力
- `:pixel-demo` 依赖 `:pixel-engine`
- `:app` 已有实验性 `:pixel-engine` 依赖，但当前不继续扩大迁移
- core package 已具备像素显示底座
- UI package 已具备最小组件体系
- retained runtime 已能进入 direct pipeline
- Pipeline / retained runtime 已具备 internal diagnostics，覆盖 render tree、element tree、dirty queue、layout/paint 计数和 target 数量
- 旧渲染后端已从生产源码删除
- 核心状态、pipeline、字体、几何、滚动控制器已有单测
- `Text` 已支持基础多行换行、`maxLines` 和最后一行 ellipsis
- `Text / RichText` 已通过内部 paragraph helper 统一基础换行、对齐和 ellipsis 规则
- `ListViewBuilder(itemExtent)` 已支持固定高度 lazy viewport
- `PixelScrollPhysics` 已提供 clamp/fling/bounce 参数基础
- `TextField` 已补齐 placeholder、disabled/readOnly、多行 line config 与 selection 边界
- `RichText`、`PixelTextSpan` 已支持基础富文本 span 样式切换
- `PixelThemeTokens` 已支持 selected/pressed/focused/disabled/readOnly 等基础状态默认值
- `pixel-demo` 已有 Launcher-like、Drawer-like、Virtual List、Rich Text、Theme States、Engine Stability Gate 和 Drawer Gate V2 gate

## 7. 尚未完成

- 变高 item 虚拟化
- 更完整的滚动物理、snap 和 nested scroll 策略
- 段落级文本、字号/字距 token 和文本选择体验
- 更完整的主题 token、组件默认值和局部覆盖系统
- 更完整的布局协议、手势边界和性能策略

## 8. 开发规则

新增能力时按这个顺序推进：

1. 判断它属于 core package、UI package 还是 demo 验收
2. 在 `pixel-engine` 内实现
3. 补 `pixel-engine` 单测
4. 在 `pixel-demo` 加真实场景
5. 只有 demo 稳定后，才讨论继续扩大 `:app` 迁移

硬约束：

- 不继续扩大 `:app` 对半成品引擎页面的依赖
- 不把 Launcher 业务语义塞进 `com.purride.pixelcore`
- 不把系统服务读取塞进 `com.purride.pixelui`
- 不恢复旧 legacy 渲染后端
- 不新增 `pixel-core` / `pixel-ui` Gradle 模块

## 9. 验证命令

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```
