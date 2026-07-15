# Pixel Engine 1.0 SDK

Pixel Engine 是 Android-first 的像素 UI 引擎 SDK，提供 retained widget tree、整数像素布局与绘制、
输入/焦点/无障碍、动画、类型化路由、资源缓存、离屏测试和诊断能力。SDK 不依赖 Material 或
Cupertino，也不以复刻 Flutter 全 API 为目标。

首次接入从 [快速开始](guides/quickstart.md) 开始；已有 0.x 消费者先读
[迁移指南](guides/migration.md)。完整接口与行为见 [使用说明与 API 手册](使用说明与API手册.md)，
内部设计见 [架构与技术实现](架构与技术实现.md)。

## 发布模块

| 坐标 | 职责 |
|---|---|
| `pixel-core` | 像素、几何、bitmap、字体、资源缓存基础类型 |
| `pixel-runtime` | retained tree、布局、渲染、输入、语义、动画 runtime |
| `pixel-widgets` | 主题、标准组件、Overlay、滚动与动画组件 |
| `pixel-navigation` | typed route、恢复、多返回栈、deep link |
| `pixel-android` | View Host、IME、无障碍、Lifecycle、Insets、Back |
| `pixel-testing` | `PixelTester`、finder、离屏交互和像素断言 |
| `pixel-debug` | Inspector 与帧诊断 UI |
| `pixel-compose` | 可选的 Compose `AndroidView` Host 适配 |
| `pixel-engine` | 兼容旧消费者的聚合坐标 |

新 Android View 消费者通常从 `pixel-android` 开始，只按需增加 testing/debug/Compose；不要为了
方便把调试或测试 artifact 带进生产最小依赖图。

## 质量承诺

- API、Metalava 与 JVM ABI baseline 阻止未审阅破坏。
- 所有显式 public/protected Kotlin 声明必须具备有效 KDoc，门槛为 100%。
- API 24/29/36 instrumentation、确定性 golden、属性测试、独立 Maven/R8 consumer 和性能门禁
  共同保护发布候选。
- 每个 Host/Engine 实例隔离焦点、输入、资源与生命周期；只有调用方显式共享服务时才共享状态。
- 版本、弃用和迁移承诺以 [发布与兼容策略](发布与兼容策略.md) 为准。

## 指南索引

- [Android Host 接入](guides/host-integration.md)
- [主题与组件](guides/theme-and-components.md)
- [路由与恢复](guides/navigation.md)
- [资源与内存](guides/resources.md)
- [自定义 RenderObject SPI](guides/custom-render-spi.md)
- [测试](guides/testing.md)
- [性能](guides/performance.md)
- [迁移](guides/migration.md)
