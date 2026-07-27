# 迁移到统一 pixel-engine

从旧拆分 artifact 迁移时按以下顺序处理：

1. 删除 `pixel-core`、`pixel-runtime`、`pixel-widgets`、`pixel-navigation`、`pixel-android`、`pixel-testing`、`pixel-debug` 和 `pixel-compose` 依赖。
2. 生产与测试源码统一依赖 `com.purride:pixel-engine:1.0.0`。
3. 保留现有公开包 import；源码包名没有因产物合并而改变。
4. 删除 Compose `PixelHost` wrapper 调用；需要 Compose 时在应用侧用 `AndroidView` 承载 `PixelHostSetup.rootView`。
5. clean 后重新执行 JVM 测试、instrumentation 和消费者侧 R8。

```bash
./gradlew clean test lint assemble
```

细分 API 迁移文档位于 [migrations 目录说明](../migrations/TEMPLATE.md)，兼容规则见 [发布与兼容策略](../发布与兼容策略.md)。
