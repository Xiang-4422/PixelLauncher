# Pixel Engine 接入与升级指南

Pixel Engine 尚未正式发布，仓库只维护一套当前 API，不保留任何历史版本的兼容层或逐里程碑迁移记录。
本文说明当前唯一的接入形态，以及首个正式版本之后的升级流程。

## 依赖坐标

SDK 只发布一个坐标，全部公开声明都在它内部：

```kotlin
dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
}
```

## 当前 API 形态

接入时按以下唯一模型编写代码；每一项都只有一个入口，没有并存的替代写法：

- **Host 装配**：`PixelEngine.Builder` 构造 Engine，`createPixelHostSetup` 返回 `PixelHostSetup`，
  owner 终态时调用 `dispose()`。Compose 页面用 `AndroidView` 承载 `PixelHostSetup.rootView`。
- **宿主环境**：`HostCapabilitiesData` 快照是唯一环境模型。`PixelHostView.hostCapabilities` 读取
  当前生效快照，`PixelHostView.capabilitiesOverride` 是唯一覆盖入口；只想改动个别字段时从
  `hostCapabilities.copy(...)` 派生。`null` 表示完全跟随 Android 平台配置。
- **宿主能力**：组装 `PixelHostCapabilitySet` 并通过 `PixelEngine.Builder.hostServices(...)` 注入；
  `PixelHostView` 只从所绑定 Engine 读取 `services.hostServices`。帧调度由 `PixelFrameScheduler`
  单独负责，`scheduleFrame` 返回可真实取消的 `PixelFrameCallbackRegistration`。
- **视口与 profile**：`PixelViewportPolicy`（Contain + Integer + Center 为默认）与
  `PixelHostProfilePolicy`（`Fixed` / `AdaptivePixels`）各只有一个模型，`screenProfile` 是只读派生值。
- **主题**：只有 `PixelThemeTokens`。`PixelTheme(tokens = ...)` 提供，`PixelTheme.of` /
  `PixelTheme.maybeOf` 查询。可选视觉参数为 nullable 且默认 `null`，由 component token 解析。
- **本地化**：文本按 `explicit → PixelLocalizations provider → theme label token → 内置英文` 解析。
  未安装 provider 时组件回落到 theme label token，显式传入的文本始终优先。
- **焦点与输入**：焦点是 runtime-local owner 模型，使用 `PixelHostView.dispatchPixelKeyEvent` /
  `dispatchPixelTextInput` 或 `PixelTester.pressKey` / `pressText`。输入按“文本 / 非文本”二分：
  可打印文本只走 `PixelTextInputEvent`，非文本走 `PixelKeyEvent`。
- **文本边界**：对外 offset 保持 UTF-16，但 selection、caret、编辑与 hit test 必须落在 grapheme
  边界；反向 selection 与非法 UTF-16 有确定性的归一化契约。
- **字形 SPI**：只有 Unicode scalar 入口 `findGlyph(codePoint: Int, style)` /
  `rasterizeGlyph(codePoint: Int, style)`；孤立 surrogate 确定性映射为 U+FFFD 查询。
- **路由与持久化**：typed destination / request / entry / outcome，配合
  `PixelRouteSnapshotAdapter` 注册表。外层 snapshot 只有一个 schema
  （`PixelNavigatorPersistentSnapshotSchemaVersion`），其他版本一律被明确拒绝；destination 自有的
  argument / route-state payload 版本由各自 adapter 负责解码。
- **资源格式**：manifest / catalog 与 sprite sheet / atlas 各只有一个协议版本
  （`PixelResourceManifestVersion`、`PixelSpriteSheetVersion`），未声明 `version` 按当前版本处理，
  其他值直接拒绝。加载走有界 loader/cache，并明确 executor、取消与释放所有权。
- **自定义渲染**：只使用 `com.purride.pixelui.advanced` 的公开 RenderObject SPI，不 import
  `internal.*`。该扩展能力由隔离 consumer 工程持续验证。
- **Overlay**：`PixelOverlayHandle.dismiss(reason)` 是唯一关闭入口，必须显式给出
  `PixelOverlayDismissReason`。Overlay、焦点、Back、Insets、IME 和 Accessibility 统一经 Host 桥接。

## 首个正式版本之后的升级流程

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

详细现行行为以 [API 手册](../使用说明与API手册.md)和[架构与设计](../架构与设计.md)为准；
稳定 API 的弃用周期、breaking change 规则与 released baseline 启用时机见
[发布与维护](../发布与维护.md)。
