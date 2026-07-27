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
