# Pixel Engine 迁移指南

## 从旧拆分 artifact 迁移

旧工程如果依赖 `pixel-core`、`pixel-runtime`、`pixel-widgets`、`pixel-navigation`、`pixel-android`、
`pixel-testing`、`pixel-debug` 或 `pixel-compose`，统一改为：

```kotlin
dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
}
```

公开导航声明统一位于根包 `com.purride.pixelui`，旧的 `com.purride.pixelui.widgets.navigation.*`
import 需要改写为根包 import。其余公开 package import 保持不变。删除 Compose `PixelHost` wrapper 调用；Compose 页面需要在应用侧
用 `AndroidView` 承载 `PixelHostSetup.rootView`，并在 owner 销毁时调用 `dispose()`。

## 从 0.x 源码迁移

- 自定义渲染只使用 `com.purride.pixelui.advanced` 的真实 RenderObject SPI，不 import `internal.*`。
- Host 统一使用 `PixelEngine`、`PixelHostSetup` 和显式 owner 生命周期。
- 主题只有 `PixelThemeTokens` 一个模型：`PixelTheme(tokens = ...)` 提供，`PixelTheme.of` /
  `PixelTheme.maybeOf` 查询；不再存在 `PixelThemeData` / `PixelThemeColors` 与
  `fromLegacy` / `toLegacyThemeData` 投影，也不再有 `tokensOf` / `maybeTokensOf` 双入口。
- 简洁组件 API 全部保留，但可选视觉参数改为 nullable 且默认 `null`（由 token 解析）；不再有
  “无 `PixelTheme` 时保持旧像素”的 scope-less 分支，简洁入口与 state-aware 实现渲染一致。
  依赖旧默认具体值（如 Slider 橙色填充、LoadingBar 以 `color` 作为 `trackColor`、Slidable 无表面
  装饰）的调用方需要显式传入对应参数，或复制相应 component token。
- 可选 `semanticLabel` 同样改为 nullable 且默认 `null`。`NavigationBar` / `NavigationRail` 删除了
  `"Navigation bar"` / `"Navigation rail"` 默认字符串 sentinel：显式传入该文本现在优先于本地化
  provider；需要 provider 解析的调用方应改为省略该参数。显式空白集合名称仍在构建时被拒绝。
- 宿主能力只有 typed capability set 一个模型。`PixelHostBridge`、`PixelTextEditingHostBridge`、
  `PixelSystemAction`、`PixelHostCapabilitySet.fromLegacyBridge` 和 `PixelHostView.hostBridge`
  全部删除：改为组装 `PixelHostCapabilitySet` 并通过 `PixelEngine.Builder.hostServices(...)` 注入，
  `PixelHostView` 只从所绑定 Engine 读取 `services.hostServices`。帧调度不再是 bridge 方法
  （`requestFrame` 删除），由 `PixelFrameScheduler` 单独负责。
- `PixelImeCapability` 的三个方法统一接收 `PixelTextEditingSession`（`id` + `request` + `value`），
  不再有只带 `PixelTextInputRequest` 的简化重载。`PixelTextInputBridge` 的隐藏编辑器固定为
  引擎自有实现，不再接受外部 `EditText`，普通 `EditText` 的弱兼容 `TextWatcher` 写回路径删除。
- viewport 只有 `PixelViewportPolicy` 一套表示。`ScaleMode`、`ScreenProfile.scaleMode`、
  `PixelViewportPolicy.LegacyFitCenter`、`fromLegacyScaleMode` 以及
  `PixelGridGeometryResolver.resolve` / `mapSurfaceToLogical` 的无策略重载全部删除；
  `PixelHostView.viewportPolicy` 改为非空，默认值 `PixelViewportPolicy()` 就是 canonical 默认策略
  （Contain + Integer + Center），行为与旧 `FIT_CENTER` 一致。
- profile 只有 `PixelHostProfilePolicy` 一个入口。`PixelHostProfilePreference`、
  `PixelHostView.profilePreference` 和 `PixelHostSetupConfig.profilePreference` 删除，
  改用 `PixelHostProfilePolicy.AdaptivePixels(dotSizePx, pixelShape)`；
  `PixelHostView.screenProfile` 变为只读派生值，原先的直接赋值改为
  `profilePolicy = PixelHostProfilePolicy.Fixed(profile)`。
- 焦点只有 runtime-local owner 一个模型。`PixelFocusManager` 及其 `rootScope`、`primaryFocus`、
  `setPrimaryFocus`、`clearFocus`、`dispatchKeyEvent`、`dispatchTextInputEvent` 全部删除：
  改用 `PixelHostView.dispatchPixelKeyEvent` / `dispatchPixelTextInput` 或 `PixelTester.pressKey`
  / `pressText`。detached legacy focus tree、root scope sentinel 重绑定和跨 runtime 回落一并移除；
  未挂载的 `FocusNode.requestFocus()` 返回 `false`，`unfocus()` 为空操作。
- `Focus(...)` 只有一个 canonical 声明，`onTextInput` 是可选参数（原先的双 overload 合并）。
  参数顺序为 `child`、`node`、`autofocus`、`canRequestFocus`、`onKeyEvent`、`onTextInput`、
  `scrollTarget`、`key`；使用具名实参的调用点无需改动。
- 输入事件按“文本 / 非文本”彻底二分。`PixelKey.CHARACTER` 与 `PixelKeyEvent.character` 删除，
  `PixelTester.pressKey` 不再接受 `character` 参数。所有可打印文本（BMP、supplementary、
  组合簇、多 code-point IME 提交）只走 `PixelTextInputEvent`；未被 `onTextInput` 消费的文本
  不再回落到 `onKeyEvent`。Android mapper 对无非文本语义的 key code 返回 `PixelKey.UNKNOWN`。
- 字形 SPI 只保留 Unicode scalar 入口。`GlyphSource.findGlyph(Char, ...)` 和
  `GlyphProvider.rasterizeGlyph(Char, ...)` 删除，`rasterizeGlyph(Int, ...)` 不再有默认实现，
  supplementary → U+FFFD 的兼容投影也一并移除；自定义 source/provider 改为实现
  `findGlyph(codePoint: Int, style)` / `rasterizeGlyph(codePoint: Int, style)`。孤立 surrogate 输入
  仍按健壮性契约映射为确定的 U+FFFD 查询。
- 帧调度只有 `PixelFrameScheduler` 一个契约。`PixelCancellableFrameScheduler` 接口和
  `PixelFrameScheduler.scheduleCancellableFrame` 扩展（含仅为旧实现保留的逻辑取消回落）删除；
  `scheduleFrame` 现在返回 `PixelFrameCallbackRegistration`，实现方必须支持真实取消。
- `MediaQuery` 只承载逻辑视口。`MediaQuery.capabilitiesOf` / `maybeCapabilitiesOf` 这两个
  为避免扩展 `MediaQueryData` ABI 而添加的投影删除，改用 `HostCapabilities.of` / `maybeOf`。
- 主题使用语义 token 与组件状态集合，不复制旧固定颜色。
- 路由使用 typed destination/request/entry/outcome 和版本化 snapshot adapter。
- 文本 offset 对外保持 UTF-16，但 selection、caret、编辑和 hit test 必须落在 grapheme 边界。
- Overlay、焦点、Back、Insets、IME 和 Accessibility 通过 Host 统一桥接。
- 资源输入使用有界 loader/cache，并明确 executor、取消和释放所有权。

这些变化发生在首个公开稳定版本之前，不再保留逐里程碑迁移文档。详细现行行为以
[API 手册](../使用说明与API手册.md)和[架构与设计](../架构与设计.md)为准。

## 未来版本升级流程

1. 阅读目标版本 [Changelog](../CHANGELOG.md)。
2. 在旧版本保存测试、API dump 和必要状态样本。
3. 单独升级 Engine 坐标并执行 clean build。
4. 按编译错误、状态恢复、视觉/语义变化逐项迁移。
5. 执行 JVM、Lint、instrumentation、R8 和隔离消费者验证。

```bash
./gradlew clean :pixel-engine:testDebugUnitTest :app:testDebugUnitTest
./gradlew :pixel-engine:lintDebug :app:lintDebug
./gradlew :pixel-engine:assembleDebugAndroidTest :app:assembleRelease
```

稳定 API 的弃用周期和 breaking change 规则见[发布与维护](../发布与维护.md)。
