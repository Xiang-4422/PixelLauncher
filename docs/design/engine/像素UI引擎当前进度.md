# 像素 UI 引擎当前进度与接手建议

这份文档只回答三个问题：

- 当前 `:pixel-engine` 和 `:pixel-demo` 到底做到哪一步
- 哪些能力已经可以当成可直接依赖的基础能力继续开发
- 后续工程师接手时，应该优先沿哪条路线继续推进

如果只看一句话，当前结论是：

> 像素引擎已经从“架构设想”进入“最小可运行框架”阶段；`pixel-core` 与 `pixel-ui` 已经合并为单一 Gradle 模块 `:pixel-engine`，模块内部继续保留 core/UI package 分层，`:app` 还没有开始迁移。

## 1. 当前模块状态

| 模块 | 当前状态 | 主要职责 |
| --- | --- | --- |
| `:app` | 稳定运行 | 现有 PixelLauncher 产品实现，本阶段不依赖新框架 |
| `:pixel-engine` | 可用 | 像素显示内核、字体底座、几何原语、组件体系、布局、分页、列表、输入、宿主桥接 |
| `:pixel-demo` | 可用 | 框架验证宿主，负责真实设备上的能力验收 |

当前依赖关系是：

- `:pixel-demo -> :pixel-engine`
- `:app` 暂时独立，不依赖 `:pixel-engine`

`pixel-engine` 内部保留两个 package 边界：

- `com.purride.pixelcore`：像素缓冲、调色板、显示几何、帧交换、字体底座、轴向位移与合成原语
- `com.purride.pixelui`：Widget/runtime、布局、输入、分页、列表、滚动、宿主桥接

这两个 package 是源码组织边界，不再是 Gradle 模块边界。

## 2. `pixel-engine` 已完成能力

### 2.1 Core package

当前 `com.purride.pixelcore` 已经具备以下可复用底座：

- 屏幕与几何：`ScreenProfile`、`ScreenProfileFactory`、`PixelGridGeometry`
- 像素缓冲与调色板：`PixelBuffer`、`PixelPalette`、`FrameSwapBuffer`
- 字体与字形：`PixelBitmapFont`、`PixelTextRasterizer`、`PixelGlyphPack`、`PixelGlyphPackAssetLoader`、`PixelFontEngine`
- 轴向运动与合成原语：`PixelAxis`、`AxisMotion`、`AxisBufferComposer`
- 显示契约与调试：`PixelFrameView`、`RenderPerfLogger`

核心路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelcore](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore)
- [pixel-engine/src/test/kotlin/com/purride/pixelcore](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-engine/src/test/kotlin/com/purride/pixelcore)

### 2.2 UI package

当前 `com.purride.pixelui` 已经具备最小可运行组件体系：

- Flutter 风格公开层：`Widget`、`BuildContext`、`Text`、`Container`、`Row`、`Column`、`Stack`、`PageView`、`ListView`、`SingleChildScrollView`、`TextField`、`OutlinedButton`
- retained build/runtime：`BuildOwner`、`Element`、`StatefulWidget`、`InheritedWidget`、`InheritedNotifier`、`Builder`、`StatefulBuilder`
- direct pipeline：`RenderObject`、`RenderObjectWidget`、`PipelineOwner`、`PipelineElementTreeRenderer`
- render objects：文本、surface、flex、stack、分页视口、滚动视口
- 控制器：`PageController`、`ScrollController`、`TextEditingController`
- 宿主桥接：`PixelHostView`、`PixelHostSetup`、`PixelHostBridge`、`PixelTextInputBridge`

核心路径：

- [pixel-engine/src/main/kotlin/com/purride/pixelui](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui)
- [pixel-engine/src/test/kotlin/com/purride/pixelui](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-engine/src/test/kotlin/com/purride/pixelui)

### 2.3 已验证场景

`pixel-demo` 当前已经能验证：

- 文本与混合字体
- 按钮点击
- 横向分页
- 纵向分页
- 纵向列表
- 单子节点滚动
- 文本输入聚焦与宿主输入桥接
- `Pager + List` 复合滚动仲裁
- `TextField + Button + List` 组合页面
- 权重布局、主轴排布、交叉轴对齐
- 多行文本换行与单行 ellipsis
- 列表可见区绘制与 target 裁剪
- 单行输入框 placeholder 裁剪、disabled/readOnly 状态
- Drawer-like 迁移前验收页

Demo 入口：

- [DemoMenuActivity.kt](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoMenuActivity.kt)
- [DemoSceneActivity.kt](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoSceneActivity.kt)
- [DemoScenes.kt](/Users/xiangyu/StudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoScenes.kt)

## 3. 当前限制

`pixel-engine` 仍然是第一版可运行框架，还不是完整产品级 UI 系统：

- `:app` 页面还没有迁移
- `ListView` 只有纵向单列，当前已限制绘制和 target 收集到可见区，但还不是真虚拟化列表
- 列表当前没有完整回弹和吸附
- `TextField` 目前只支持单行输入
- 文本已支持基础多行换行和最后一行 ellipsis，但还不支持富文本和段落级样式
- 主题系统还比较轻，当前主要靠 `PixelPalette` 和 `PixelTextStyle`
- 新渲染管线还没有达到完整 Flutter 级别的布局、手势、文本系统

旧渲染后端已经从生产源码删除；后续不要再把它作为 fallback 接回生产路径。

## 4. 总体进度总结

### 已完成

- `:app` 保持独立旧实现
- `:pixel-engine` 已整合原 core/UI 能力
- `:pixel-demo` 已能完成真实设备验收
- 中文字形链路已打通到 demo
- 核心组件和 runtime 已有单测
- 生产源码已经不再保留旧渲染后端
- `Text` 已支持字符级多行换行、`CLIP / ELLIPSIS` 和最后一行省略
- `ListView` 已在绘制与 target 导出阶段跳过不可见 item
- `TextField` 已补齐单行 placeholder ellipsis、disabled/readOnly 视觉状态和 selection 边界覆盖
- `pixel-demo` 已新增滚动压力、环境继承、Launcher-like 和 Drawer-like 验收场景

### 正在进行

- 补稳 direct pipeline 核心架构
- 补稳 `RenderObject / PipelineOwner / RenderObjectWidget` 的长期职责边界
- 继续扩展基础布局、输入和滚动视口的产品级边界
- 用 `pixel-demo` 持续证明真实场景不依赖旧后端

### 尚未开始

- `:app` 页面迁移
- 懒加载列表
- 列表虚拟化与更高级滚动物理
- 多行输入、富文本和段落级样式
- 更完整的主题 token 与环境默认值系统

## 5. 接手建议

如果下一位工程师现在接手，建议顺序如下：

1. 先读 [像素 UI 引擎架构与实施计划](./像素UI引擎架构与实施计划.md)
2. 再读 [像素 UI 引擎组件接入指南](./像素UI引擎组件接入指南.md)
3. 先在 `:pixel-demo` 上验证或新增组件，不要直接改 `:app`
4. 只有当 demo 能稳定覆盖新组件场景后，再讨论 `:app` 迁移

当前最值得继续推进的方向：

- 基于 Drawer-like demo 选择 `:app` 首个迁移页面
- 继续补列表虚拟化、滚动物理和更完整主题 token
- 在真实迁移前继续维持 `pixel-demo` gate 先行

## 6. 验证命令

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```
