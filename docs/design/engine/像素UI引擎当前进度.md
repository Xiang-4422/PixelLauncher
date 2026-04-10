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
    - pipeline 目录当前已经按 `core / gesture / runtime / renderobjects` 四组收拢，旧 lowering 目录已删除
    - [PipelineOwner.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/runtime/PipelineOwner.kt)
    - [PipelineElementTreeRenderer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/runtime/PipelineElementTreeRenderer.kt)
    - [RenderObject.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/RenderObject.kt)
    - [RenderObjectWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/RenderObjectWidget.kt)
    - [PipelinePrimitives.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/PipelinePrimitives.kt)
    - [PixelRenderPrimitives.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/core/PixelRenderPrimitives.kt)
    - [PixelRenderSessionFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/runtime/PixelRenderSessionFactory.kt)
    - [PixelLayoutValues.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/model/PixelLayoutValues.kt)
    - [PixelLayoutMappings.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/model/PixelLayoutMappings.kt)
    - [PixelModifier.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/model/PixelModifier.kt)
    - [PagerGesturePolicy.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/gesture/PagerGesturePolicy.kt)
    - [NestedScrollGesturePolicy.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/gesture/NestedScrollGesturePolicy.kt)
    - [RenderFlex.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderFlex.kt)
    - [RenderPagerViewport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderPagerViewport.kt)
    - [RenderScrollViewport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderScrollViewport.kt)
    - [RenderSurface.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderSurface.kt)
    - [RenderText.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/pipeline/renderobjects/RenderText.kt)
  - 旧 `PipelineTreeCapabilityChecker / PipelineBridgeTreeLowering` 已删除，生产 pipeline renderer 只接受 direct `RenderObject` tree，不再维护中间树 lowering
  - pipeline 需要复用的 `PixelAlignment / PixelTextAlign / PixelModifier / PixelFlexFit` 等基础模型已经收进 `internal/model`
  - 当前 pipeline 支持边界已经收口到 direct render object 主链；首批支持能力已从 `Text + Surface` 扩到 `Align / Center / Padding / SizedBox / Container / Row / Column / Stack / Positioned / TextField / OutlinedButton / PageView / ListView / SingleChildScrollView`，当前覆盖 `START / CENTER / END / SPACE_*`、`stretch`、基础 flex 权重、垂直滚动视口和分页视口
  - Flutter 式 `Widget -> Element -> RenderObject` 地基已经开始落地：`RenderObjectWidget` 负责创建/更新 render object，`RenderObjectElement` 负责持有并暴露 render object，`SingleChildRenderObjectWidget / MultiChildRenderObjectWidget` 已经承接 child render object 挂接；公开 `Text / DecoratedBox / Padding / Align / Center / SizedBox / Container / Row / Column / Stack / Positioned / TextField / OutlinedButton / PageView / ListView / SingleChildScrollView` 已改为 direct pipeline
  - `internal/legacy`、`internal/render/legacy` 和测试侧旧 bridge 夹具已经删除，生产源码不再保留 legacy renderer 后端
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
  - 已替换的 direct widget 统一收在 `internal/widgets/content`、`internal/widgets/layout` 与 `internal/widgets/scroll`
- 当前 retained 主链已经直接面对 direct render object tree，不再通过旧中间表示
  - 公开 Flutter 风格组件文件现在只装配 direct widget，不再保留旧节点适配逻辑
  - [PixelModifier.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/model/PixelModifier.kt)
- 基础布局与内容组件
  - [PixelLayoutValues.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/model/PixelLayoutValues.kt)
  - [PixelTextOverflow.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/PixelTextOverflow.kt)
  - [PixelButton.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/PixelButton.kt)
  - [PixelTextStyle.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/PixelTextStyle.kt)
- 分页
  - [PixelPagerState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerState.kt)
  - [PixelPagerSnapshot.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerSnapshot.kt)
  - [PixelPagerController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerController.kt)
- 列表与滚动
  - [PixelListState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListState.kt)
  - [PixelListController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListController.kt)
- 文本输入
  - [PixelTextField.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/PixelTextField.kt)
  - [PixelTextFieldState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldState.kt)
  - [PixelTextFieldController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldController.kt)
- 宿主桥接
  - [PixelHostBridge.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/host/PixelHostBridge.kt)
  - [PixelHostView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/host/PixelHostView.kt)
- 运行时与手势
  - 旧 runtime 与旧测试夹具已经删除
  - [RetainedRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/RetainedRenderSupport.kt)
  - [RetainedRenderSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/RetainedRenderSupportFactory.kt)
  - [RetainedRenderSupportAssemblyFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/assembly/RetainedRenderSupportAssemblyFactory.kt)
  - [WidgetRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/WidgetRenderRuntime.kt)
  - [WidgetRenderRuntimeFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/WidgetRenderRuntimeFactory.kt)
  - 新增运行时和手势能力后，应优先落在 `internal/render/pipeline`、`internal/widgets` 或 `state`，不再新增 legacy 后端文件

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
- `RetainedBuildRuntime` 当前只负责 retained element tree，本身不再直接产出渲染中间树
- 默认绘制链路当前已经改成 `RetainedWidgetRenderRuntime -> RetainedRenderSupport -> PipelineElementTreeRenderer -> PipelineOwner`
- 当前重构主线不是再补更多组件名字，而是继续把 pipeline render object 的长期形态补稳，并防止旧后端重新进入生产源码

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

旧的 `PixelText`、`PixelButton`、`PixelList`、`PixelPager` 等节点式名称已经从生产源码删除，不再作为建议依赖的页面 API。

### 3.3 当前测试覆盖

`pixel-ui` 已经有独立测试覆盖：

- [PixelPagerControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/state/PixelPagerControllerTest.kt)
- [PixelListControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/state/PixelListControllerTest.kt)
- [PixelTextFieldControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/state/PixelTextFieldControllerTest.kt)
- [RetainedWidgetRuntimeTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/retained/RetainedWidgetRuntimeTest.kt)
- [PipelineElementTreeRendererTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/pipeline/PipelineElementTreeRendererTest.kt)
- [PipelineOwnerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/pipeline/PipelineOwnerTest.kt)
- [PagerGesturePolicyTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/pipeline/gesture/PagerGesturePolicyTest.kt)
- [NestedScrollGesturePolicyTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/render/pipeline/gesture/NestedScrollGesturePolicyTest.kt)
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
- 公开层主组件已经开始移除 `PixelModifier` 参数，页面层应优先使用 `Container / Padding / SizedBox / Expanded / Align / Stack` 这套 Flutter 风格布局入口；`PixelModifier` 当前主要只留在模块内部 pipeline 模型里
- `legacy` 渲染后端已经从生产源码删除；后续不能再把它作为 fallback 重新接入
- 新渲染管线已经从最小 `Text + Surface` 骨架推进到基础布局、输入与滚动视口，但还没有达到完整 Flutter 级别的布局/手势/文本系统
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

- 补稳 direct pipeline 核心架构
- `PipelineOwnerTest` 已补 attach/detach、脏 layout、挂载后 child adoption 的基础生命周期覆盖
- 继续补稳 retained 主链直接进入新 pipeline 后的布局、输入与滚动长期形态
- 用 `pixel-demo` 持续证明真实场景不依赖旧后端
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

- 补稳 `RenderObject / PipelineOwner` 的长期职责边界
- 继续扩展 direct pipeline 的基础布局、输入和滚动视口
- 在 `pixel-demo` 上持续验收新 pipeline 页面，不提前启动 `:app` 迁移

---

## 7. 当前结论

当前像素引擎项目已经不再是“纯规划阶段”。

更准确的说法是：

> `pixel-core` 和 `pixel-ui` 已经形成了可运行、可测试、可继续演进的第一版基础框架；legacy 后端已经从生产源码删除，接下来主线不再是继续拆 `factory/assembly`，而是把 direct pipeline 的核心架构补成长期形态。

更具体一点说，当前工程已经进入：

> “新渲染管线成长期”，短期主线是补稳 `RenderObject / PipelineOwner / RenderObjectWidget` 的长期设计，而不是启动业务页迁移或继续深挖 legacy 内部整理。
