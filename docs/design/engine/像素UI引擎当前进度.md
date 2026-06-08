# 像素 UI 引擎当前进度与接手建议

这份文档只回答三个问题：

- 当前 `:pixel-engine` 和 `:pixel-demo` 到底做到哪一步
- 哪些能力已经可以当成可直接依赖的基础能力继续开发
- 后续工程师接手时，应该优先沿哪条路线继续推进

如果只看一句话，当前结论是：

> 像素引擎已经从“架构设想”进入“可运行框架补稳”阶段；`pixel-core` 与 `pixel-ui` 已经合并为单一 Gradle 模块 `:pixel-engine`，模块内部继续保留 core/UI package 分层。当前工作重心是继续完善 engine 底层与 `pixel-demo` gate，暂不继续推进新的 `:app` 页面迁移。

## 1. 当前模块状态

| 模块 | 当前状态 | 主要职责 |
| --- | --- | --- |
| `:app` | 稳定运行 | 现有 PixelLauncher 产品实现，当前已有实验性 engine 接入，但本阶段不继续扩展迁移 |
| `:pixel-engine` | 可用 | 像素显示内核、字体底座、几何原语、组件体系、布局、分页、列表、输入、宿主桥接 |
| `:pixel-demo` | 可用 | 框架验证宿主，负责真实设备上的能力验收 |

当前依赖关系是：

- `:pixel-demo -> :pixel-engine`
- `:app -> :pixel-engine` 已存在，但后续主线暂时回到 engine/framework 补稳

`pixel-engine` 内部保留三个 package 边界：

- `com.purride.pixelcore`：像素缓冲、调色板、显示几何、帧交换、字体底座、轴向位移与合成原语
- `com.purride.pixelui`：Widget/runtime、布局、输入、分页、列表、滚动、宿主桥接
- `com.purride.pixelengine`：模块 marker，标记单一 Gradle 模块统一承载 core 与 UI runtime

这些 package 是源码组织边界，不再是 Gradle 模块边界；物理目录可以继续按职责整理，但公开 Kotlin package 需要保持稳定。

## 2. `pixel-engine` 已完成能力

### 2.1 Core package

当前 `com.purride.pixelcore` 已经具备以下可复用底座：

- 屏幕与几何：`ScreenProfile`、`ScreenProfileFactory`、`PixelGridGeometry`
- 像素缓冲与调色板：`PixelBuffer`、`PixelPalette`、`FrameSwapBuffer`
- 字体与字形：`PixelBitmapFont`、`PixelTextRasterizer`、`PixelGlyphPack`、`PixelGlyphPackAssetLoader`、`PixelFontEngine`
- 轴向运动与合成原语：`PixelAxis`、`AxisMotion`、`AxisBufferComposer`
- 显示契约与调试：`PixelFrameView`、`RenderPerfLogger`

核心路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelcore](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore)
- [pixel-engine/src/test/kotlin/com/purride/pixelcore](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/test/kotlin/com/purride/pixelcore)

### 2.2 UI package

当前 `com.purride.pixelui` 已经具备最小可运行组件体系：

- Flutter 风格公开层：`Widget`、`BuildContext`、`Text`、`Container`、`Row`、`Column`、`Stack`、`PageView`、`ListView`、`GridView`、`CustomScrollView`、`SliverList`、`SliverPinnedHeader`、`SingleChildScrollView`、`Scrollbar`、`RefreshIndicator`、`TextField`、`OutlinedButton`
- retained build/runtime：`BuildOwner`、`Element`、`StatefulWidget`、`InheritedWidget`、`InheritedNotifier`、`Builder`、`StatefulBuilder`
- direct pipeline：`RenderObject`、`RenderObjectWidget`、`PipelineOwner`、`PipelineElementTreeRenderer`
- render objects：文本、surface、flex、stack、分页视口、滚动视口
- 控制器：`PageController`、`ScrollController`、`TextEditingController`、`PixelRefreshIndicatorController`
- 宿主桥接：`PixelHostView`、`PixelHostSetup`、`PixelHostBridge`、`PixelTextInputBridge`

核心路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelui](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui)
- [pixel-engine/src/test/kotlin/com/purride/pixelui](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/test/kotlin/com/purride/pixelui)

### 2.3 已验证场景

`pixel-demo` 当前已经能验证：

- 文本与混合字体
- 按钮点击
- 横向分页
- 纵向分页
- 纵向列表
- 固定 item 高度 lazy 列表
- fixed-cell lazy grid
- `CustomScrollView` + `SliverList` + pinned header
- Scrollbar 与 pull-to-refresh
- 单子节点滚动
- 文本输入聚焦与宿主输入桥接
- 多行文本输入 line config
- `Pager + List` 复合滚动仲裁
- `TextField + Button + List` 组合页面
- 权重布局、主轴排布、交叉轴对齐
- 多行文本换行与单行 ellipsis
- 富文本 span tone 切换
- 列表可见区绘制与 target 裁剪
- 单行输入框 placeholder 裁剪、disabled/readOnly 状态
- 主题 token 与 selected/pressed/focused/disabled/readOnly 状态
- Drawer-like 迁移前验收页
- Drawer Gate V2

Demo 入口：

- [DemoMenuActivity.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoMenuActivity.kt)
- [DemoSceneActivity.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoSceneActivity.kt)
- [DemoScenes.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoScenes.kt)
- [.run/pixel-demo.run.xml](/Users/even/AndroidStudioProjects/PixelLauncher/.run/pixel-demo.run.xml)

## 3. 当前限制

`pixel-engine` 仍然是第一版可运行框架，还不是完整产品级 UI 系统：

- `CustomScrollView` 已有 `SliverList` 和 pinned header v1，但还没有 `SliverAppBar`、lazy sliver builder 或多个 pinned header 的推挤规则
- 列表已有 clamp/fling/bounce 参数基础，但还没有完整平台级滚动物理、snap 策略和 route-aware scroll restoration
- `TextField` 已支持多行 caret / selection / composition、光标节拍和最小 selection handle，但还没有完整长按菜单或平台级选择浮层
- `RichText` 已支持 span tone 切换，但还不是完整段落/字号/字距系统
- 主题 token 已接入基础状态，但还需要继续收敛组件级默认值和局部覆盖语义
- 新渲染管线已有内部 diagnostics 和基础 target 裁剪回归，但还没有达到完整 Flutter 级别的布局、手势、文本系统

旧渲染后端已经从生产源码删除；后续不要再把它作为 fallback 接回生产路径。

## 4. 总体进度总结

### 已完成

- `:pixel-engine` 已整合原 core/UI 能力
- `:pixel-demo` 已能完成真实设备验收
- 中文字形链路已打通到 demo
- 核心组件和 runtime 已有单测
- 生产源码已经不再保留旧渲染后端
- `Text` 已支持字符级多行换行、`CLIP / ELLIPSIS` 和最后一行省略
- `Text / RichText` 已收敛到内部 paragraph helper，统一基础换行、对齐和 ellipsis 规则
- `ListViewBuilder(itemExtent)` 已支持固定高度 lazy viewport
- `TextField` 已补齐 placeholder ellipsis、disabled/readOnly、多行 line config 与 selection 边界覆盖
- Pipeline / retained runtime 已有 internal diagnostics，覆盖 render tree、element tree、dirty queue、layout/paint 计数和 target 数量
- `RichText`、`PixelTextSpan`、`PixelScrollPhysics`、`PixelThemeTokens` 已作为向后兼容扩展加入
- `pixel-demo` 已新增滚动压力、环境继承、Launcher-like、Drawer-like、Virtual List、Rich Text、Theme States、Engine Stability Gate 和 Drawer Gate V2 验收场景

### 正在进行

- 继续补稳 direct pipeline 核心架构、布局协议和更复杂 target 裁剪边界
- 继续扩展 diagnostics 到更完整的宿主调试和性能定位场景
- 继续扩展输入、滚动和手势的产品级边界
- 用 `pixel-demo` 持续证明真实场景不依赖旧后端

### 尚未开始

- 变高 item 虚拟化
- 更完整的滚动物理、snap 和 nested scroll 策略
- 段落级文本、字号/字距 token 和文本选择体验
- 更完整的主题 token、组件默认值和局部覆盖系统
- 更完整的布局协议、手势边界和性能策略

## 5. 接手建议

如果下一位工程师现在接手，建议顺序如下：

1. 先读 [像素 UI 引擎架构与实施计划](./像素UI引擎架构与实施计划.md)
2. 再读 [像素 UI 引擎组件接入指南](./像素UI引擎组件接入指南.md)
3. 先在 `:pixel-demo` 上验证或新增组件，不要继续扩大 `:app` 迁移面
4. 只有当 engine/demo gate 长期稳定后，再讨论新的 `:app` 页面迁移

当前最值得继续推进的方向：

- 补强复杂布局裁剪、嵌套手势和 diagnostics 的宿主可视化入口
- 继续补变高列表虚拟化、滚动物理、文本系统和更完整主题 token
- 继续维持 `pixel-demo` gate 先行，暂不推进新的 `:app` 迁移

## 6. 验证命令

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```
