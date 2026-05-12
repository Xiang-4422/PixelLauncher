# 像素 UI 引擎架构与实施计划

这份文档是当前像素 UI 引擎的执行口径。历史上引擎曾拆成 `pixel-core` 和 `pixel-ui` 两个 Gradle 模块；当前已经收敛为单一模块 `:pixel-engine`，内部继续用 package 保留低层 core 与 UI runtime 的边界。

## 1. 当前架构

当前工程模块：

- `:app`：现有 PixelLauncher 产品实现，暂时不依赖新引擎
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
:app         -> 独立旧实现
```

## 2. `pixel-engine` 内部分层

### 2.1 Core package

路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelcore](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore)

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

- [pixel-engine/src/main/kotlin/com/purride/pixelui](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui)

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

短期主线不是启动 `:app` 页面迁移，而是把 `pixel-engine` 本身补稳：

- 补稳 `RenderObject / PipelineOwner / RenderObjectWidget` 长期职责
- 扩展 direct pipeline 的基础布局能力
- 补强输入、滚动和手势边界
- 用 `pixel-demo` 验证真实场景
- 保持 `:app` 独立，等 demo 稳定后再迁移业务页

## 6. 已完成

- 单模块 `:pixel-engine` 已承接原 core/UI 能力
- `:pixel-demo` 依赖 `:pixel-engine`
- `:app` 不依赖 `:pixel-engine`
- core package 已具备像素显示底座
- UI package 已具备最小组件体系
- retained runtime 已能进入 direct pipeline
- 旧渲染后端已从生产源码删除
- 核心状态、pipeline、字体、几何、滚动控制器已有单测

## 7. 尚未完成

- `:app` 页面迁移
- 懒加载列表和虚拟化
- 更完整的滚动物理
- 多行输入和富文本
- 更完整的主题与环境默认值系统
- 更完整的布局协议与文本系统

## 8. 开发规则

新增能力时按这个顺序推进：

1. 判断它属于 core package、UI package 还是 demo 验收
2. 在 `pixel-engine` 内实现
3. 补 `pixel-engine` 单测
4. 在 `pixel-demo` 加真实场景
5. 只有 demo 稳定后，才讨论 `:app` 迁移

硬约束：

- 不让 `:app` 直接依赖半成品引擎页面
- 不把 Launcher 业务语义塞进 `com.purride.pixelcore`
- 不把系统服务读取塞进 `com.purride.pixelui`
- 不恢复旧 legacy 渲染后端
- 不新增 `pixel-core` / `pixel-ui` Gradle 模块

## 9. 验证命令

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```
