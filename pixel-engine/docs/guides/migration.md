# 迁移到 1.0

不要直接跨版本替换 AAR。先在旧版本执行测试与 API dump，再升级到 1.0 artifact 图并完整 clean，
最后按失败领域逐项迁移。

1. 将新工程从聚合 `pixel-engine` 改为 `pixel-android` 加按需 testing/debug/Compose；旧导入别名仍保留。
2. 只 import 公开包，RenderObject 改用 `advanced.Pixel*` 真实 SPI。
3. Host 改用 `PixelEngine`/`PixelHostSetup`，owner 终态显式 dispose。
4. 主题改用语义 token 与八状态集合，删除复制的固定组件颜色。
5. 路由改用 typed destination/request/entry/outcome，并注册版本化 snapshot adapter。
6. 文本 offset 保持 UTF-16 API，但编辑、selection、caret 和 hit test 必须落在 Unicode 17 grapheme 边界。
7. clean Gradle/build cache，重新编译全部消费者，再执行 JVM、instrumentation、R8 和性能门禁。

细分迁移文档位于 [migrations 目录说明](../migrations/TEMPLATE.md)；破坏、弃用周期和 SemVer 规则见
[发布与兼容策略](../发布与兼容策略.md)。
