# Pixel Engine 1.0 支持矩阵

本页区分“产物声明的硬下限”和“每次发布真实执行的支持组合”。只满足 AAR metadata
不等于进入支持范围；支持组合必须通过独立 Maven 消费者的 Kotlin、Java、R8 和公开 SPI 测试。

## Android 运行环境

| 项目 | 1.0 契约 |
|---|---|
| `minSdk` | 24 |
| 编译 API 硬下限 | Android 36 |
| 推荐编译 API | Android 36.1 |
| `targetSdk` 示例 | 36 |
| Java source/target | 11 |
| Gradle 运行 JDK | 21（发布矩阵实测） |

九个 AAR 都内嵌 `minCompileSdk=36` 与 `minAndroidGradlePluginVersion=8.10.0`。这两个字段让明显
不支持的工程在 `check*AarMetadata` 阶段失败，不会拖到 Kotlin 编译或 R8 才给出模糊错误。

## 构建工具组合

| 档位 | Kotlin | AGP | Gradle | compileSdk | 状态 |
|---|---:|---:|---:|---:|---|
| 最低支持 | 2.2.10 | 8.10.1 | 8.11.1 | 36 | 每次发布必测 |
| 推荐 | AGP 内置 2.2.10 | 9.1.1 | 9.3.1 | 36.1 | 每次发布必测 |
| Producer 当前组合 | AGP 内置 2.2.10 | 9.0.1 | 9.1.0 | 36 | SDK 自身门禁 |

“最低支持”是已验证组合，不把未实测的 AGP 8.10.0/Gradle 其他补丁版本自动视为支持。AGP 低于
8.10.0 或 compileSdk 低于 36 明确不支持。更高版本先进入 CI 推荐档验证；通过后才能更新本表。

## 消费者验收内容

最低与推荐组合都从隔离 HTTP Maven 仓库解析 `com.purride:pixel-engine`，并执行：

- Kotlin 与 Java 单元测试；
- 自定义 `PixelLeafRenderObjectWidget` / `PixelRenderBox` 的 layout、paint 与像素断言；
- Debug APK；
- 开启 R8 的 Release APK 与非空 mapping；
- 实际 Kotlin compiler 版本记录；
- 不支持的 AGP 与 compileSdk 负例。

命令为：

```bash
bash tools/pixel-supply-chain-check.sh
```

该命令默认强制校验 Apache-2.0 LICENSE、九个 POM 和 SBOM 的许可证声明；正式候选不得通过环境变量
关闭许可证门禁。
原始矩阵报告位于 `build/reports/compatibility/m8-2/matrix.json`。

## 版本升级规则

- 提高 `minSdk`、minCompileSdk、Kotlin、AGP、Gradle 或 JDK 下限属于显著兼容变更，必须写入
  CHANGELOG 和迁移指南。
- 1.x 中不得在 patch 版本提高硬下限；通常放入 minor，若导致大量现有消费者无法升级则按
  breaking change 进入 major。
- 修改支持表时必须同步 AAR metadata、消费者矩阵常量、POM/Gradle metadata 验证和文档。
- Android 设备行为矩阵与构建工具矩阵是两套证据；构建成功不能替代 API 24/29/36 模拟器测试。
