# Pixel Engine 文档

Pixel Engine 是 Android-first 的 retained-mode 像素 UI SDK，以一个 AAR 提供完整能力。仓库开发者应从
模块根 `pixel-engine/README.md` 查看全部文档分类；本站提供同一组现行文档的可浏览版本。

## 使用

- [快速开始](guides/quickstart.md)
- [Android Host](guides/host-integration.md)
- [主题与组件](guides/theme-and-components.md)
- [路由与恢复](guides/navigation.md)
- [资源与内存](guides/resources.md)
- [测试](guides/testing.md)
- [性能](guides/performance.md)
- [迁移](guides/migration.md)
- [完整 API 手册](使用说明与API手册.md)

## 维护

- [架构与设计](架构与设计.md)
- [发布与维护](发布与维护.md)
- [Changelog](CHANGELOG.md)
- [长期规划](长期规划.md)

当前最低 `minSdk` 为 24、compileSdk 为 36、AAR 最低 AGP 为 8.10.0。Compose wrapper、性能 benchmark
和真实公共发布不属于当前主工程范围。
