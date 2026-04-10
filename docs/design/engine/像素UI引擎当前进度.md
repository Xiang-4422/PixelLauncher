# 像素 UI 引擎当前进度与接手建议

这份文档只回答三个问题：

- 当前 `:pixel-core`、`:pixel-ui`、`:pixel-demo` 到底已经做到哪一步
- 哪些能力已经可以当成“可直接依赖的基础能力”继续开发
- 后续工程师接手时，应该优先沿哪条路线继续推进

如果只看一句话，当前结论是：

> 像素引擎已经从“架构设想”进入“最小可运行框架”阶段，`pixel-core`、`pixel-ui`、`pixel-demo` 已经形成闭环，并且到了可以开始新渲染管线的节点，但 `:app` 还没有开始迁移。

---

## 1. 当前模块状态

| 模块 | 当前状态 | 主要职责 |
| --- | --- | --- |
| `:app` | 稳定运行 | 现有 PixelLauncher 产品实现，本阶段不依赖新框架 |
| `:pixel-core` | 可用 | 像素显示内核、字体底座、几何与轴向原语 |
| `:pixel-ui` | 可用 | 最小通用组件、布局、分页、列表、输入、宿主桥接 |
| `:pixel-engine` | 可用 | 面向业务接入的聚合模块，统一导出 `core + ui` |
| `:pixel-demo` | 可用 | 框架验证宿主，负责真实设备上的能力验收 |

当前依赖关系是：

- `:pixel-demo -> :pixel-ui -> :pixel-core`
- `:pixel-engine -> :pixel-ui -> :pixel-core`
- `:app` 暂时独立，不依赖 `:pixel-core`
- `:app` 暂时独立，不依赖 `:pixel-ui`

这条边界当前已经落地，后续不要再把过渡兼容层塞回 `:app`。

当前推荐业务侧优先依赖 `:pixel-engine`，而不是分别手动引入 `:pixel-core` 与 `:pixel-ui`。  
保留 `core/ui` 两个源码模块，是为了维持编译期边界；`pixel-engine` 只负责简化接入。

---

## 2. `pixel-core` 当前完成度

### 2.1 已完成能力

当前 `:pixel-core` 已经具备以下可复用底座：

- 屏幕与几何
  - [ScreenProfile.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/screen/ScreenProfile.kt)
  - [ScreenProfileFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/screen/ScreenProfileFactory.kt)
  - [PixelGridGeometry.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/graphics/PixelGridGeometry.kt)
- 像素缓冲与调色板
  - [PixelBuffer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/graphics/PixelBuffer.kt)
  - [PixelPalette.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/theme/PixelPalette.kt)
  - [FrameSwapBuffer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/animation/FrameSwapBuffer.kt)
- 字体与字形
  - [PixelBitmapFont.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/font/PixelBitmapFont.kt)
  - [PixelTextRasterizer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/font/PixelTextRasterizer.kt)
  - [PixelGlyphPack.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/font/PixelGlyphPack.kt)
  - [PixelGlyphPackAssetLoader.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/font/PixelGlyphPackAssetLoader.kt)
  - [PixelFontEngine.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/font/PixelFontEngine.kt)
- 轴向运动与合成原语
  - [PixelAxis.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/animation/PixelAxis.kt)
  - [AxisMotion.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/animation/AxisMotion.kt)
  - [AxisBufferComposer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/animation/AxisBufferComposer.kt)
- 显示契约与调试
  - [PixelFrameView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/graphics/PixelFrameView.kt)
  - [RenderPerfLogger.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/main/kotlin/com/purride/pixelcore/runtime/RenderPerfLogger.kt)

### 2.2 当前测试覆盖

`pixel-core` 已经有独立单测覆盖以下方向：

- 屏幕与几何
  - [ScreenProfileFactoryTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/screen/ScreenProfileFactoryTest.kt)
  - [PixelGridGeometryTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/graphics/PixelGridGeometryTest.kt)
  - [PixelGridGeometryResolverTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/graphics/PixelGridGeometryResolverTest.kt)
- 帧与缓冲
  - [FrameSwapBufferTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/animation/FrameSwapBufferTest.kt)
- 字体
  - [PixelBitmapFontTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/font/PixelBitmapFontTest.kt)
  - [PixelGlyphPackParserTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/font/PixelGlyphPackParserTest.kt)
  - [PixelFontEngineTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/font/PixelFontEngineTest.kt)
  - [PixelTextRasterizerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/font/PixelTextRasterizerTest.kt)
- 运动与合成
  - [AxisMotionControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/animation/AxisMotionControllerTest.kt)
  - [AxisBufferComposerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/animation/AxisBufferComposerTest.kt)
  - pixel-core 测试目录当前已经按 `animation / font / graphics / screen` 四组收拢

### 2.3 当前边界判断

`pixel-core` 现在的边界是正确的：

- 它知道“像素怎么表示、怎么合成、怎么测量、怎么按轴移动”
- 它不知道“页面是什么、列表是什么、抽屉是什么、短信是什么”

后续如果一个类型需要知道 `pageCount`、`selectedIndex`、`drawer query`、`idle` 之类业务或 UI 语义，它就不应该进入 `pixel-core`。

---

## 3. `pixel-ui` 当前完成度

### 3.1 已完成能力

当前 `:pixel-ui` 已经具备最小可运行组件体系：

- Flutter 风格公开层
  - [Widget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/foundation/Widget.kt)
  - [BuildContext.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/foundation/BuildContext.kt)
  - [FrameworkEnvironment.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/foundation/FrameworkEnvironment.kt)
  - [Listenable.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/foundation/Listenable.kt)
  - [FlutterLayoutPrimitives.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/FlutterLayoutPrimitives.kt)
  - [FlutterWidgetAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/FlutterWidgetAliases.kt)
  - [FlutterControllerAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/controllers/FlutterControllerAliases.kt)
- retained build/runtime 入口
  - [RetainedBuildRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/runtime/RetainedBuildRuntime.kt)
  - [BuildOwner.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/runtime/BuildOwner.kt)
  - [Element.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/elements/Element.kt)
  - [StatefulElements.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/elements/StatefulElements.kt)
  - [InheritedElements.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/elements/InheritedElements.kt)
  - [ElementInflater.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/support/ElementInflater.kt)
  - [ElementChildUpdater.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/support/ElementChildUpdater.kt)
  - [DirtyElementScheduler.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/support/DirtyElementScheduler.kt)
  - [ListenableDependencyRegistry.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/support/ListenableDependencyRegistry.kt)
  - [RootElementSlot.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/support/RootElementSlot.kt)
  - [InheritedLookupBinding.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/retained/support/InheritedLookupBinding.kt)
  - retained 目录当前已经按 `runtime / elements / support` 三组收拢
  - [RetainedWidgetRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/RetainedWidgetRenderRuntime.kt)
  - [RetainedWidgetRenderAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/assembly/RetainedWidgetRenderAssemblyFactory.kt)
  - [RetainedWidgetRuntimeFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/RetainedWidgetRuntimeFactory.kt)
  - [RetainedWidgetRuntimeAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/assembly/RetainedWidgetRuntimeAssemblyFactory.kt)
  - [WidgetRuntimeAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/assembly/WidgetRuntimeAssemblyFactory.kt)
  - [PixelUiRuntimeAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/assembly/PixelUiRuntimeAssemblyFactory.kt)
  - [WidgetRenderRequestFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/request/WidgetRenderRequestFactory.kt)
  - runtime 目录当前已经按 `runtime / request / assembly / support / host` 五组收拢
  - 新渲染管线骨架已经启动：
    - pipeline 目录当前已经按 `core / runtime / renderobjects / lowering` 四组收拢
    - [PipelineOwner.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/runtime/PipelineOwner.kt)
    - [PipelineElementTreeRenderer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/runtime/PipelineElementTreeRenderer.kt)
    - [RenderObject.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/RenderObject.kt)
    - [RenderObjectWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/RenderObjectWidget.kt)
    - [PipelinePrimitives.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/PipelinePrimitives.kt)
    - [RenderFlex.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderFlex.kt)
    - [RenderPagerViewport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderPagerViewport.kt)
    - [RenderScrollViewport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderScrollViewport.kt)
    - [RenderSurface.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderSurface.kt)
    - [RenderText.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderText.kt)
    - [PipelineTreeCapabilityChecker.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/lowering/PipelineTreeCapabilityChecker.kt)
    - [PipelineBridgeTreeLowering.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/lowering/PipelineBridgeTreeLowering.kt)
  - 当前 pipeline 支持边界已经收口到显式 capability checker，并且能给出整树回退原因；首批支持能力已从 `Text + Surface` 扩到 `Align / Center / Padding / SizedBox / Container / Row / Column / Stack / Positioned / TextField / OutlinedButton / PageView / ListView / SingleChildScrollView` 的 direct render object 主链，当前覆盖 `START / CENTER / END / SPACE_*`、`stretch`、基础 flex 权重、垂直滚动视口和分页视口
  - Flutter 式 `Widget -> Element -> RenderObject` 地基已经开始落地：`RenderObjectWidget` 负责创建/更新 render object，`RenderObjectElement` 负责持有并暴露 render object，`SingleChildRenderObjectWidget / MultiChildRenderObjectWidget` 已经承接 child render object 挂接；公开 `Text / DecoratedBox / Padding / Align / Center / SizedBox / Container / Row / Column / Stack / Positioned / TextField / OutlinedButton / PageView / ListView / SingleChildScrollView` 已改为 direct pipeline，默认 retained 运行时已经改成 pipeline-only，不再自动启用 bridge/legacy fallback
  - [BridgeRenderNode.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/BridgeRenderNode.kt)
  - [DefaultBridgeTreeResolver.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/DefaultBridgeTreeResolver.kt)
  - [BridgeAdapterElement.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/elements/BridgeAdapterElement.kt)
  - [BridgeWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/BridgeWidget.kt)
  - [StaticBridgeNodeWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/StaticBridgeNodeWidget.kt)
  - [BridgeNodeBinding.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/elements/BridgeNodeBinding.kt)
  - [BridgeTreeResolveRequest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/BridgeTreeResolveRequest.kt)
  - bridge 目录当前只剩 `resolve / elements / widgets` 三组；默认运行时已经不再需要旧 `bridge/runtime` 中转壳
  - 已经 direct pipeline 化的 widget 已经移出 legacy 目录：
  - [TextWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/content/TextWidgets.kt)
  - [InputWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/content/InputWidgets.kt)
  - [ContainerWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/layout/ContainerWidgets.kt)
  - [AlignmentWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/layout/AlignmentWidgets.kt)
  - [DecorationWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/layout/DecorationWidgets.kt)
  - [FlexLayoutWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/layout/FlexLayoutWidgets.kt)
  - [FlexWrapperWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/layout/FlexWrapperWidget.kt)
  - [ListWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/scroll/ListWidgets.kt)
  - [PagerWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/scroll/PagerWidgets.kt)
  - [SingleChildScrollWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/widgets/scroll/SingleChildScrollWidgets.kt)
  - legacy widget 目录已经清空；已替换的 direct widget 统一收在 `internal/widgets/content`、`internal/widgets/layout` 与 `internal/widgets/scroll`
- 兼容层基础节点与场景
  - 这一层当前只作为 retained runtime 过渡桥接使用，已经开始收为模块内部实现
- 当前 retained 主链已经只面对 bridge 语义，不再直接从 `RetainedBuildRuntime` 输出 bridge tree；bridge 解析已经收敛到 [DefaultBridgeTreeResolver.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/DefaultBridgeTreeResolver.kt) 和 [RetainedWidgetRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/RetainedWidgetRenderRuntime.kt)
  - 公开 Flutter 风格组件的旧节点适配逻辑，也已经从 [FlutterWidgetAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/FlutterWidgetAliases.kt) 分离到 bridge/legacy support 文件，公开文件开始只保留 API 入口
  - [PixelNode.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/node/PixelNode.kt)
  - [PixelModifier.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/modifier/PixelModifier.kt)
  - [CustomDraw.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/drawing/CustomDraw.kt)
- 基础布局与内容组件
  - [LegacyLayoutValues.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/layout/LegacyLayoutValues.kt)
  - [LegacyCoreNodes.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/node/LegacyCoreNodes.kt)
  - [PixelTextOverflow.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/PixelTextOverflow.kt)
  - [PixelButton.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/PixelButton.kt)
  - [PixelTextStyle.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/PixelTextStyle.kt)
- 分页
  - [PixelPagerState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerState.kt)
  - [PixelPagerSnapshot.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerSnapshot.kt)
  - [PixelPagerController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerController.kt)
- 列表与滚动
  - [PixelList.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/node/PixelList.kt)
  - [PixelListState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListState.kt)
  - [PixelListController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListController.kt)
  - [PixelSingleChildScrollView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/node/PixelSingleChildScrollView.kt)
- 文本输入
  - [PixelTextField.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/PixelTextField.kt)
  - [PixelTextFieldState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldState.kt)
  - [PixelTextFieldController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldController.kt)
- 宿主桥接
  - [PixelHostBridge.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/host/PixelHostBridge.kt)
  - [PixelHostView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/host/PixelHostView.kt)
- 运行时与手势
  - legacy runtime 目录当前已经按 `core / root / assembly / factory` 四组收拢
  - [PixelRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/root/PixelRenderRuntime.kt)
  - [LegacyRenderSupportBundle.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/assembly/LegacyRenderSupportBundle.kt)
  - [LegacyNodeRuntimeSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/core/LegacyNodeRuntimeSupport.kt)
  - [LegacyRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/core/LegacyRenderSupport.kt)
  - [LegacyRenderCallbacksFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/factory/LegacyRenderCallbacksFactory.kt)
  - [LegacyRenderCallbacks.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/core/LegacyRenderCallbacks.kt)
  - [LegacyRenderSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/factory/LegacyRenderSupportFactory.kt)
  - [LegacyRenderSupportAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/factory/LegacyRenderSupportAssemblyFactory.kt)
  - [LegacyLayoutSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/factory/LegacyLayoutSupportFactory.kt)
  - [LegacyLayoutSupportAssembly.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/assembly/LegacyLayoutSupportAssembly.kt)
  - [LegacyViewportSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/factory/LegacyViewportSupportFactory.kt)
  - [LegacyViewportSupportAssembly.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/assembly/LegacyViewportSupportAssembly.kt)
  - [LegacyNodeSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/factory/LegacyNodeSupportFactory.kt)
  - [LegacyNodeSupportAssembly.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/assembly/LegacyNodeSupportAssembly.kt)
  - [LegacyStructureSupportAssembly.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/assembly/LegacyStructureSupportAssembly.kt)
  - [LegacyTreeRenderer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/root/LegacyTreeRenderer.kt)
  - [LegacyTreeRendererFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/root/LegacyTreeRendererFactory.kt)
  - [RetainedRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/RetainedRenderSupport.kt)
  - [RetainedRenderSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/RetainedRenderSupportFactory.kt)
  - [RetainedRenderSupportAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/assembly/RetainedRenderSupportAssemblyFactory.kt)
  - [WidgetRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/WidgetRenderRuntime.kt)
  - [WidgetRenderRuntimeFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/WidgetRenderRuntimeFactory.kt)
  - bridge runtime 中转壳已经删除，legacy 测试夹具直接通过 `DefaultBridgeTreeResolver + LegacyTreeRendererFactory` 显式 opt-in 旧链路
  - [PixelRootRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/root/PixelRootRenderSupport.kt)
  - [PixelRootLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/root/PixelRootLayoutSupport.kt)
  - legacy node 目录当前已经按 `core / dispatch / modifier / target` 四组收拢
  - [PixelNodeRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/core/PixelNodeRenderSupport.kt)
  - [PixelNodeRenderDispatch.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/dispatch/PixelNodeRenderDispatch.kt)
  - [PixelNodeSpecialRenderDispatch.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/dispatch/PixelNodeSpecialRenderDispatch.kt)
  - [PixelNodeModifierContext.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/modifier/PixelNodeModifierContext.kt)
  - legacy layout 目录当前已经按 `core / flex / stack / surface` 四组收拢
  - [PixelLayoutRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/core/PixelLayoutRenderSupport.kt)
  - [PixelLayoutMeasureSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/core/PixelLayoutMeasureSupport.kt)
  - [PixelRowRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/flex/PixelRowRenderSupport.kt)
  - [PixelColumnRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/flex/PixelColumnRenderSupport.kt)
  - [PixelSurfaceRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/surface/PixelSurfaceRenderSupport.kt)
  - [PixelStackRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/stack/PixelStackRenderSupport.kt)
  - [PixelPositionedRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/stack/PixelPositionedRenderSupport.kt)
  - [PixelFlexLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/flex/PixelFlexLayoutSupport.kt)
  - [PixelPositionedLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/stack/PixelPositionedLayoutSupport.kt)
  - [PixelAlignmentLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/core/PixelAlignmentLayoutSupport.kt)
  - legacy viewport 目录当前已经按 `gesture / scroll / support` 三组收拢
  - [PixelViewportRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/support/PixelViewportRenderSupport.kt)
  - [PixelViewportSessionSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/support/PixelViewportSessionSupport.kt)
  - [PixelViewportResultSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/support/PixelViewportResultSupport.kt)
  - [PixelTargetTranslateSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/target/PixelTargetTranslateSupport.kt)
  - legacy text 目录当前已经按 `core / field` 两组收拢
  - [PixelTextRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/core/PixelTextRenderSupport.kt)
  - [PixelTextLayoutAssembler.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/core/PixelTextLayoutAssembler.kt)
  - [PixelTextRasterizerResolver.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/core/PixelTextRasterizerResolver.kt)
  - [PixelTextFieldVisualSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/field/PixelTextFieldVisualSupport.kt)
  - [PixelTextFieldTargetExport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/field/PixelTextFieldTargetExport.kt)
  - legacy measure 目录当前已经收进 `core`
  - [PixelMeasureResultSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/measure/core/PixelMeasureResultSupport.kt)
  - [PixelRenderSessionFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/core/PixelRenderSessionFactory.kt)
  - [PixelTextAlignmentSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/core/PixelTextAlignmentSupport.kt)
  - [PixelTextLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/core/PixelTextLayoutSupport.kt)
  - [PixelTextFieldRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/field/PixelTextFieldRenderSupport.kt)
  - [PixelTextFieldLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/field/PixelTextFieldLayoutSupport.kt)
  - [PixelMeasureSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/measure/core/PixelMeasureSupport.kt)
  - [PixelMeasureDispatch.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/measure/core/PixelMeasureDispatch.kt)
  - [PixelModifierSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/modifier/PixelModifierSupport.kt)
  - [PixelRenderPrimitives.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/core/PixelRenderPrimitives.kt)
  - [PixelPagerRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/scroll/PixelPagerRenderSupport.kt)
  - [PixelVerticalScrollRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/scroll/PixelVerticalScrollRenderSupport.kt)
  - [PixelListRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/scroll/PixelListRenderSupport.kt)
  - [PixelSingleChildScrollRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/scroll/PixelSingleChildScrollRenderSupport.kt)
  - [PagerGesturePolicy.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/gesture/PagerGesturePolicy.kt)
  - [NestedScrollGesturePolicy.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/gesture/NestedScrollGesturePolicy.kt)

### 3.2 当前已验证场景

`pixel-ui` 当前已经能稳定承载这些真实交互：

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

这些都已经在 [DemoScenes.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoScenes.kt) 里有真机场景，不是只停留在单测层。

另外，当前 demo 已经开始真实使用 retained 状态机制，而不再主要依赖手动 `requestRender()`：

- `ListenableBuilder / ValueListenableBuilder`
- `StatefulWidget + State.setState`
- `Builder / StatefulBuilder`
- `InheritedNotifier`
- `Theme / Directionality / MediaQuery` 这类环境传播

其中 `Theme / Directionality / MediaQuery` 已经不只是在测试里可用，文本页现在已经有真实的环境信息展示和局部覆盖场景。

当前 retained 主链的真实状态是：

- `Widget -> BuildOwner / Element tree` 已经成立
- `StatefulWidget / InheritedWidget / InheritedNotifier / Builder / StatefulBuilder` 已经在真实 demo 页面里使用
- retained 核心目前已经从 `BuildOwner` 中继续拆出：
  - `ElementInflater`
  - `ElementChildUpdater`
  - `DirtyElementScheduler`
  - `ListenableDependencyRegistry`
- `RetainedBuildRuntime` 当前只负责 retained element tree，本身不再直接产出 bridge tree
- 默认绘制链路当前已经改成 `RetainedWidgetRenderRuntime -> RetainedRenderSupport -> PipelineElementTreeRenderer -> PipelineOwner`，不再自动接入 bridge/legacy fallback
- 当前重构主线不是再补更多组件名字，而是继续把 retained 主链对 legacy 中间表示的剩余依赖删掉

当前 `pixel-demo` 主路径已经统一转到 Flutter 风格公开 API：

- `Text`
- `OutlinedButton`
- `Row`
- `Column`
- `DecoratedBox`
- `PageView`
- `ListView`
- `SingleChildScrollView`
- `TextField`
- `PageController`
- `ScrollController`
- `TextEditingController`

公开布局这条线也已经开始往更像 Flutter 的写法收：

- `CrossAxisAlignment.STRETCH`
- `SizedBox(height = ...)`
- `Expanded`

它们现在已经可以替掉大部分页面层公开“手动拉伸和尺寸兼容参数”写法。

旧的 `PixelText`、`PixelButton`、`PixelList`、`PixelPager` 等名称当前只作为 `pixel-ui` 模块内部兼容桥接保留，已经不再是建议依赖的公开页面 API。

### 3.3 当前测试覆盖

`pixel-ui` 已经有独立测试覆盖：

- [PixelPagerControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/state/PixelPagerControllerTest.kt)
- [PixelListControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/state/PixelListControllerTest.kt)
- [PixelTextFieldControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/state/PixelTextFieldControllerTest.kt)
- [PixelRenderRuntimeTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/legacy/runtime/PixelRenderRuntimeTest.kt)
- [RetainedWidgetRuntimeTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/retained/RetainedWidgetRuntimeTest.kt)
- [PipelineElementTreeRendererTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/pipeline/PipelineElementTreeRendererTest.kt)
- [PagerGesturePolicyTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/legacy/viewport/gesture/PagerGesturePolicyTest.kt)
- [NestedScrollGesturePolicyTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/legacy/viewport/gesture/NestedScrollGesturePolicyTest.kt)
- pixel-ui 测试目录当前已经按 `state / internal/render / internal/retained` 三组收拢

### 3.4 当前限制

当前 `pixel-ui` 仍然是“第一版可运行框架”，还不是完整产品级 UI 系统。当前限制包括：

- retained build tree 默认已经能直接通过 pipeline render object tree 出图
- retained build tree 已经拆分出 `BuildOwner / Element` 层级，并开始拥有自己的 retained render object 树
- `ListView` 只有纵向单列，不是虚拟化列表
- 列表当前没有回弹和吸附
- `TextField` 目前只支持单行输入
- 文本当前还不支持富文本和段落级样式
- 主题系统还比较轻，当前主要靠 `PixelPalette` 和 `PixelTextStyle`
- 公开层主组件已经开始移除 `PixelModifier` 参数，页面层应优先使用 `Container / Padding / SizedBox / Expanded / Align / Stack` 这套 Flutter 风格布局入口；`PixelModifier` 当前主要只留在模块内部的底层节点和兼容运行时里
- `legacy` 渲染后端已经从默认运行时路径移出；后续只作为待删除的历史后端处理
- 新渲染管线已经启动最小骨架，但当前覆盖仍只适合 `Text + Surface` 这类首批场景
- 还没有开始把 `:app` 页面迁进来

---

## 4. `pixel-demo` 当前作用

`pixel-demo` 现在已经不是“演示玩具”，而是框架验收宿主。

关键文件如下：

- [DemoMenuActivity.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoMenuActivity.kt)
- [DemoSceneActivity.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoSceneActivity.kt)
- [DemoSceneKind.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoSceneKind.kt)
- [DemoScenes.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoScenes.kt)
- [DemoTextRasterizers.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-demo/src/main/kotlin/com/purride/pixeldemo/app/DemoTextRasterizers.kt)

当前 demo 已经验证了两件非常关键的事：

1. 新框架可以在真实设备上承载中文与中英混排  
2. 像素显示链路已经能按宿主尺寸全屏铺开，而不是只在中间显示一个小方块

---

## 5. 当前项目总体进度总结

### 5.1 已完成

- `:app` 已恢复为独立旧实现
- `:pixel-core` 已具备可复用的像素显示底座
- `:pixel-ui` 已具备最小可用组件体系
- `:pixel-demo` 已能完成真实设备验收
- 中文字形链路已打通到 demo
- 核心组件已有单测

### 5.2 正在进行

- 启动最小新渲染管线骨架
- 让 retained 主链默认直接进入新 pipeline，未接入组件不再静默走 bridge/legacy fallback
- 用 `pixel-demo` 新增验证页证明至少有一个真实场景已经不再依赖 `legacy renderer`
- 持续同步注释、测试和实施计划，防止主线和文档再错位

### 5.3 尚未开始

- `:app` 页面迁移
- `Row / Column` 等更完整的新布局协议
- 更完整的主题与环境默认值系统
- 懒加载列表
- 列表虚拟化与更高级滚动物理

---

## 6. 接手建议

如果下一位工程师现在接手，建议顺序如下：

1. 先读 [像素 UI 引擎架构与实施计划](./像素UI引擎架构与实施计划.md)
2. 再读 [像素 UI 引擎组件接入指南](./像素UI引擎组件接入指南.md)
3. 先在 `:pixel-demo` 上验证或新增组件，不要直接改 `:app`
4. 只有当 demo 能稳定覆盖新组件场景后，再讨论 `:app` 迁移

当前最值得继续推进的方向有三条：

- 启动并稳定最小 `RenderObject / PipelineOwner` 骨架
- 打通 `Text + Surface` 首批新渲染链路，并保持整树 fallback 稳定
- 在 `pixel-demo` 上持续验收新 pipeline 页面与现有 retained/bridge/legacy 兼容页面，不提前启动 `:app` 迁移

---

## 7. 当前结论

当前像素引擎项目已经不再是“纯规划阶段”。

更准确的说法是：

> `pixel-core` 和 `pixel-ui` 已经形成了可运行、可测试、可继续演进的第一版基础框架；最近一轮 `legacy` 内部整理已经把过渡后端收到了足够清楚的位置，接下来主线不再是继续拆 `factory/assembly`，而是开始落最小新渲染管线。

更具体一点说，当前工程已经进入：

> “新渲染管线启动期”，短期主线是用 `Text + Surface` 先证明真实端到端替换已经开始，而不是启动业务页迁移或继续深挖 `legacy` 内部整理。
