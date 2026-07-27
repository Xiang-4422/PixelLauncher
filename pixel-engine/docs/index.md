# pixel-engine 1.0

pixel-engine 是 Android-first 的 retained-mode 像素 UI SDK。它以一个 AAR 提供像素缓冲、字体与资源、Widget/Element/RenderObject、标准组件、动画、路由、Android Host、测试 DSL 和诊断能力。

## 安装

```kotlin
dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
}
```

同仓库开发使用 `implementation(project(":pixel-engine"))`。

SDK 最低支持 Android API 24、compileSdk 36 和 AGP 8.10。当前不发布 Compose wrapper；Compose 应用可在应用侧通过 `AndroidView` 承载 Pixel Host。

## 源码层次

| 包 | 职责 |
|---|---|
| `com.purride.pixelcore` | 像素、几何、bitmap、字体、sprite 与资源缓存 |
| `com.purride.pixelui` | retained tree、布局、绘制、输入、主题和组件 |
| `com.purride.pixelui.host` | Android Host、帧时钟、生命周期与输入桥 |
| `com.purride.pixelui.widgets.navigation` | typed route、恢复、多返回栈与 deep link |
| `com.purride.pixelui.testing` | `PixelTester`、finder 与离屏断言 |
| `com.purride.pixelui.debug` | Inspector 与帧诊断 |

以上是 package 分层，全部属于同一个 `pixel-engine` 发布坐标。

## 下一步

- [快速开始](guides/quickstart.md)
- [Android Host](guides/host-integration.md)
- [主题与组件](guides/theme-and-components.md)
- [路由与恢复](guides/navigation.md)
- [测试](guides/testing.md)
- [完整 API 手册](使用说明与API手册.md)
- [发布与兼容](发布与兼容策略.md)
