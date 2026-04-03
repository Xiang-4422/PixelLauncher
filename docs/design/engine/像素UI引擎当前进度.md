# 像素 UI 引擎当前进度与接手建议

这份文档只回答三个问题：

- 当前 `:pixel-core`、`:pixel-ui`、`:pixel-demo` 到底已经做到哪一步
- 哪些能力已经可以当成“可直接依赖的基础能力”继续开发
- 后续工程师接手时，应该优先沿哪条路线继续推进

如果只看一句话，当前结论是：

> 像素引擎已经从“架构设想”进入“最小可运行框架”阶段，`pixel-core`、`pixel-ui`、`pixel-demo` 已经形成闭环，但 `:app` 还没有开始迁移。

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
  - [ScreenProfileFactoryTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/ScreenProfileFactoryTest.kt)
  - [PixelGridGeometryTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/PixelGridGeometryTest.kt)
  - [PixelGridGeometryResolverTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/PixelGridGeometryResolverTest.kt)
- 帧与缓冲
  - [FrameSwapBufferTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/FrameSwapBufferTest.kt)
- 字体
  - [PixelBitmapFontTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/PixelBitmapFontTest.kt)
  - [PixelGlyphPackParserTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/PixelGlyphPackParserTest.kt)
  - [PixelFontEngineTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/PixelFontEngineTest.kt)
  - [PixelTextRasterizerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/PixelTextRasterizerTest.kt)
- 运动与合成
  - [AxisMotionControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/AxisMotionControllerTest.kt)
  - [AxisBufferComposerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-core/src/test/kotlin/com/purride/pixelcore/AxisBufferComposerTest.kt)

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
  - [RetainedWidgetRuntimeFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/RetainedWidgetRuntimeFactory.kt)
  - runtime 目录当前已经按 `runtime / request / assembly / support / host` 五组收拢
  - [BridgeRenderNode.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/BridgeRenderNode.kt)
  - [DefaultBridgeTreeResolver.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/DefaultBridgeTreeResolver.kt)
  - [BridgeWidgetAdapter.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/BridgeWidgetAdapter.kt)
  - [BridgeAdapterElement.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/elements/BridgeAdapterElement.kt)
  - [BridgeWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/BridgeWidget.kt)
  - [LegacyLeafWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/LegacyLeafWidget.kt)
  - [LegacySingleChildWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/LegacySingleChildWidget.kt)
  - [LegacyMultiChildWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/LegacyMultiChildWidget.kt)
  - [LegacyFlexBridgeWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/LegacyFlexBridgeWidgets.kt)
  - [StaticBridgeNodeWidget.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/StaticBridgeNodeWidget.kt)
  - [BridgeWidgetAdapterSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/widgets/BridgeWidgetAdapterSupport.kt)
  - [LegacyModifierMergeSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/modifier/LegacyModifierMergeSupport.kt)
  - [LegacyModifierApplier.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/modifier/LegacyModifierApplier.kt)
  - [BridgeNodeBinding.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/elements/BridgeNodeBinding.kt)
  - [BridgeTreeResolveRequest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/BridgeTreeResolveRequest.kt)
  - [BridgeRenderSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/runtime/BridgeRenderSupportFactory.kt)
  - bridge 目录当前已经按 `runtime / resolve / elements / widgets / modifier` 五组收拢
  - [LegacyContainerWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/layout/LegacyContainerWidgets.kt)
  - [LegacyAlignmentWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/layout/LegacyAlignmentWidgets.kt)
  - [LegacyDecorationWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/layout/LegacyDecorationWidgets.kt)
  - [LegacyFlexLayoutWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/layout/LegacyFlexLayoutWidgets.kt)
  - [LegacyTextWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/content/LegacyTextWidgets.kt)
  - [LegacyInputWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/content/LegacyInputWidgets.kt)
  - [LegacyPagerWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/scroll/LegacyPagerWidgets.kt)
  - [LegacyListWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/scroll/LegacyListWidgets.kt)
  - [LegacySingleChildScrollWidgets.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/widgets/scroll/LegacySingleChildScrollWidgets.kt)
  - legacy widget 目录当前已经按 `layout / content / scroll` 三组收拢
- 兼容层基础节点与场景
  - 这一层当前只作为 retained runtime 过渡桥接使用，已经开始收为模块内部实现
- 当前 retained 主链已经只面对 bridge 语义，不再直接从 `RetainedBuildRuntime` 输出 bridge tree；bridge 解析已经收敛到 [DefaultBridgeTreeResolver.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/resolve/DefaultBridgeTreeResolver.kt) 和 [RetainedWidgetRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/RetainedWidgetRenderRuntime.kt)
  - 公开 Flutter 风格组件的旧节点适配逻辑，也已经从 [FlutterWidgetAliases.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/FlutterWidgetAliases.kt) 分离到 bridge/legacy support 文件，公开文件开始只保留 API 入口
  - [PixelNode.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/PixelNode.kt)
  - [PixelModifier.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/PixelModifier.kt)
  - [CustomDraw.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/CustomDraw.kt)
- 基础布局与内容组件
  - [LegacyLayoutValues.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/LegacyLayoutValues.kt)
  - [LegacyCoreNodes.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/LegacyCoreNodes.kt)
  - [PixelTextOverflow.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/PixelTextOverflow.kt)
  - [PixelButton.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/PixelButton.kt)
  - [PixelTextStyle.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/theme/PixelTextStyle.kt)
- 分页
  - [PixelPagerState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerState.kt)
  - [PixelPagerSnapshot.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerSnapshot.kt)
  - [PixelPagerController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelPagerController.kt)
- 列表与滚动
  - [PixelList.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/PixelList.kt)
  - [PixelListState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListState.kt)
  - [PixelListController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelListController.kt)
  - [PixelSingleChildScrollView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/legacy/PixelSingleChildScrollView.kt)
- 文本输入
  - [PixelTextField.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/widgets/PixelTextField.kt)
  - [PixelTextFieldState.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldState.kt)
  - [PixelTextFieldController.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/state/PixelTextFieldController.kt)
- 宿主桥接
  - [PixelHostBridge.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/host/PixelHostBridge.kt)
  - [PixelHostView.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/host/PixelHostView.kt)
- 运行时与手势
  - [PixelRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/PixelRenderRuntime.kt)
  - [LegacyRenderSupportBundle.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/LegacyRenderSupportBundle.kt)
  - [LegacyRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/LegacyRenderSupport.kt)
  - [LegacyRenderCallbacks.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/LegacyRenderCallbacks.kt)
  - [LegacyRenderSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/LegacyRenderSupportFactory.kt)
  - [LegacyTreeRenderer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/LegacyTreeRenderer.kt)
  - [LegacyTreeRendererFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/LegacyTreeRendererFactory.kt)
  - [RetainedRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/RetainedRenderSupport.kt)
  - [RetainedRenderSupportFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/RetainedRenderSupportFactory.kt)
  - [WidgetRenderRuntime.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/support/WidgetRenderRuntime.kt)
  - [WidgetRenderRuntimeFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/runtime/runtime/WidgetRenderRuntimeFactory.kt)
  - [BridgeTreeRenderer.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/bridge/runtime/BridgeTreeRenderer.kt)
  - [PixelRootRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/PixelRootRenderSupport.kt)
  - [PixelNodeRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/PixelNodeRenderSupport.kt)
  - [PixelNodeRenderDispatch.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/PixelNodeRenderDispatch.kt)
  - [PixelNodeSpecialRenderDispatch.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/PixelNodeSpecialRenderDispatch.kt)
  - [PixelNodeModifierContext.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/PixelNodeModifierContext.kt)
  - [PixelLayoutRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelLayoutRenderSupport.kt)
  - [PixelRowRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelRowRenderSupport.kt)
  - [PixelColumnRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelColumnRenderSupport.kt)
  - [PixelSurfaceRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelSurfaceRenderSupport.kt)
  - [PixelStackRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelStackRenderSupport.kt)
  - [PixelFlexLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelFlexLayoutSupport.kt)
  - [PixelPositionedLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelPositionedLayoutSupport.kt)
  - [PixelAlignmentLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/layout/PixelAlignmentLayoutSupport.kt)
  - [PixelViewportRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PixelViewportRenderSupport.kt)
  - [PixelViewportSessionSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PixelViewportSessionSupport.kt)
  - [PixelTargetTranslateSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/PixelTargetTranslateSupport.kt)
  - [PixelTextRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextRenderSupport.kt)
  - [PixelTextLayoutAssembler.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextLayoutAssembler.kt)
  - [PixelTextRasterizerResolver.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextRasterizerResolver.kt)
  - [PixelTextFieldVisualSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextFieldVisualSupport.kt)
  - [PixelTextFieldTargetExport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextFieldTargetExport.kt)
  - [PixelMeasureResultSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/measure/PixelMeasureResultSupport.kt)
  - [PixelRenderSessionFactory.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/PixelRenderSessionFactory.kt)
  - [PixelTextAlignmentSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextAlignmentSupport.kt)
  - [PixelTextLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextLayoutSupport.kt)
  - [PixelTextFieldRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextFieldRenderSupport.kt)
  - [PixelTextFieldLayoutSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/text/PixelTextFieldLayoutSupport.kt)
  - [PixelMeasureSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/measure/PixelMeasureSupport.kt)
  - [PixelMeasureDispatch.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/measure/PixelMeasureDispatch.kt)
  - [PixelModifierSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/node/PixelModifierSupport.kt)
  - [PixelRenderPrimitives.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/runtime/PixelRenderPrimitives.kt)
  - [PixelPagerRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PixelPagerRenderSupport.kt)
  - [PixelVerticalScrollRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PixelVerticalScrollRenderSupport.kt)
  - [PixelListRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PixelListRenderSupport.kt)
  - [PixelSingleChildScrollRenderSupport.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PixelSingleChildScrollRenderSupport.kt)
  - [PagerGesturePolicy.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/PagerGesturePolicy.kt)
  - [NestedScrollGesturePolicy.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/main/kotlin/com/purride/pixelui/internal/render/legacy/viewport/NestedScrollGesturePolicy.kt)

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
- 最终绘制当前通过 `RetainedWidgetRenderRuntime -> RetainedRenderSupport -> BridgeElementTreeRenderer -> DefaultBridgeTreeResolver -> LegacyTreeRenderer -> PixelRenderRuntime` 这条链路落到 legacy 渲染器
- 当前重构主线不是再补更多组件名字，而是继续把 retained 主链和 legacy render bridge 切得更干净

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

- [PixelPagerControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/PixelPagerControllerTest.kt)
- [PixelListControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/PixelListControllerTest.kt)
- [PixelTextFieldControllerTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/PixelTextFieldControllerTest.kt)
- [PixelRenderRuntimeTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/PixelRenderRuntimeTest.kt)
- [RetainedWidgetRuntimeTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/RetainedWidgetRuntimeTest.kt)
- [PagerGesturePolicyTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/PagerGesturePolicyTest.kt)
- [NestedScrollGesturePolicyTest.kt](/Users/jiuzhou/AndroidStudioProjects/PixelLauncher/pixel-ui/src/test/kotlin/com/purride/pixelui/internal/NestedScrollGesturePolicyTest.kt)

### 3.4 当前限制

当前 `pixel-ui` 仍然是“第一版可运行框架”，还不是完整产品级 UI 系统。当前限制包括：

- 已经有 retained build tree，但最终绘制仍然落到 legacy `PixelNode + PixelRenderRuntime`
- retained build tree 已经拆分出 `BuildOwner / Element` 层级，但还没有自己的 retained render object 树
- `ListView` 只有纵向单列，不是虚拟化列表
- 列表当前没有回弹和吸附
- `TextField` 目前只支持单行输入
- 文本当前还不支持富文本和段落级样式
- 主题系统还比较轻，当前主要靠 `PixelPalette` 和 `PixelTextStyle`
- 公开层主组件已经开始移除 `PixelModifier` 参数，页面层应优先使用 `Container / Padding / SizedBox / Expanded / Align / Stack` 这套 Flutter 风格布局入口；`PixelModifier` 当前主要只留在模块内部的底层节点和兼容运行时里
- legacy 渲染主链已经被拆成 support graph，但 retained runtime 和 legacy bridge 还没有彻底解耦
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

- 把 `pixel-ui` 从“能跑”继续推进到“适合直接依赖开发”
- 补齐组件说明、接入文档、约束说明
- 稳定宿主接入方式和组件使用习惯

### 5.3 尚未开始

- `:app` 页面迁移
- retained render object 树
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

- 继续切 retained 主链和 legacy render bridge：让 retained runtime 尽量只面对 bridge，而不再感知 legacy 细节
- 继续压薄 legacy renderer：把 `PixelRenderRuntime` 收成更纯的 façade，让 support graph 成为唯一装配入口
- 在 `pixel-demo` 上持续验收 retained 状态、环境、分页、列表、输入和方向性，不提前启动 `:app` 迁移

---

## 7. 当前结论

当前像素引擎项目已经不再是“纯规划阶段”。

更准确的说法是：

> `pixel-core` 和 `pixel-ui` 已经形成了可运行、可测试、可继续演进的第一版基础框架；下一阶段的重点不是再讨论边界，而是把这套框架补成真正适合业务直接依赖的库。

更具体一点说，当前工程已经进入：

> “retained runtime 重构中段”，短期主线是继续做架构收口，而不是启动业务页迁移。
