# Pixel Engine 1.0 支持矩阵

## Android 与构建环境

| 项目 | 1.0 契约 |
|---|---|
| `minSdk` | 24 |
| compileSdk 硬下限 | 36 |
| 推荐 compileSdk | 36.1 |
| AAR 最低 AGP | 8.10.0 |
| Java source/target | 11 |
| Producer JDK | 21 |

统一 AAR 内嵌 `minCompileSdk=36` 与 `minAndroidGradlePluginVersion=8.10.0`。

| 档位 | Kotlin | AGP | Gradle | compileSdk |
|---|---:|---:|---:|---:|
| 最低支持 | 2.2.10 | 8.10.1 | 8.11.1 | 36 |
| 推荐 | AGP 内置 2.2.10 | 9.1.1 | 9.3.1 | 36.1 |
| Producer | AGP 内置 2.2.10 | 9.0.1 | 9.1.0 | 36 |

消费者矩阵从隔离 HTTP Maven 仓库解析 `com.purride:pixel-engine`，执行 Kotlin/Java 测试、自定义 RenderObject SPI、Debug APK、R8 Release APK，以及低 AGP/compileSdk 负例。

```bash
bash tools/pixel-supply-chain-check.sh
```

提高 minSdk、compileSdk、Kotlin、AGP、Gradle 或 JDK 下限必须同步 CHANGELOG、迁移指南、AAR metadata 和消费者矩阵。
