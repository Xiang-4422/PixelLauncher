# Pixel Engine

Pixel Engine 是 Android-first 的 retained-mode 像素 UI SDK。它以一个 `pixel-engine` AAR 提供像素缓冲、
字体与资源、Widget/Element/RenderObject、标准组件、动画、路由、Android Host、测试 DSL 和诊断能力。

本文件是 Engine 全部现行文档的唯一入口。历史阶段验收、已完成 Goal 和未公开 0.x 迁移记录不再作为
使用或维护依据。

## 开始使用

- [SDK 文档首页](docs/index.md)：能力边界、环境要求和阅读顺序。
- [快速开始](docs/guides/quickstart.md)：依赖、最小 Activity 和第一个测试。
- [Android Host](docs/guides/host-integration.md)：生命周期、Back、Insets 和 Compose 应用承载方式。
- [使用说明与 API 手册](docs/使用说明与API手册.md)：完整公开 API、示例和行为约束。

## 设计与扩展

- [架构与设计](docs/架构与设计.md)：模块边界、retained tree、API 分层和 RenderObject SPI。
- [主题与组件](docs/guides/theme-and-components.md)：token、组件状态和受控组件。
- [路由与恢复](docs/guides/navigation.md)：typed route、返回栈、恢复和 Back。
- [资源与内存](docs/guides/resources.md)：bitmap、字体、sprite、cache 和资源所有权。
- [测试](docs/guides/testing.md)：`PixelTester`、instrumentation、golden 和隔离消费者。
- [性能](docs/guides/performance.md)：帧预算、诊断边界以及当前不包含的性能工作。
- [迁移](docs/guides/migration.md)：旧拆分 artifact 和未来版本升级的统一迁移规则。

## 维护与规划

- [发布与维护](docs/发布与维护.md)：SemVer、兼容、安全、工具链、供应链与发布清单。
- [Changelog](docs/CHANGELOG.md)：版本级变化。
- [长期规划](docs/长期规划.md)：未来能力和非当前范围。

## 常用命令

```bash
./gradlew :pixel-engine:testDebugUnitTest
./gradlew :pixel-engine:lintDebug
./gradlew :pixel-engine:checkBinaryApi :pixel-engine:checkMetalavaApi
./gradlew :pixel-engine:assembleRelease
bash tools/pixel-publication-validation.sh
```

主工程必须始终只有 `app` 和 `pixel-engine` 两个 Gradle 模块。性能 benchmark、真实远程发布和
Compose wrapper 不属于当前 Engine 工程边界。
