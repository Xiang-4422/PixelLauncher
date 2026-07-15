# M8-2 外部消费者兼容矩阵验收

日期：2026-07-14
状态：通过
范围：真实 Maven 发布物、消费者构建版本、R8、Kotlin/Java/SPI、旧二进制和发布元数据

## 1. 验收边界

本工作包只接受发布到隔离 file-Maven 仓库的真实 POM/AAR。消费者工程位于一次性临时目录，
`settings.gradle.kts` 只把 `com.purride` 指向 `build/compatibility-repository`，没有
`project(":pixel-*")`、composite build 或 `mavenLocal()` 旁路。每轮默认先清空仓库，再由同一次 producer
构建发布全部九个正式坐标。

本包验证构建时兼容性，不新增设备运行行为。M8-1 已在 API 24/29/36 模拟器完成 Android 行为矩阵；
M8-2 没有操作实体手机，也不需要为了 AAR/POM/R8 验收启动模拟器。

## 2. 支持矩阵

| 档位 | Kotlin | AGP | Gradle | compileSdk | debug | minified release | Kotlin SPI | Java |
|---|---:|---:|---:|---:|---|---|---|---|
| 最低 | `2.2.10` | `8.10.1` | `8.11.1` | `36` | 通过 | 通过 | 通过 | 通过 |
| 推荐 | 内置 `2.2.10` | `9.1.1` | `9.3.1` | `36.1` | 通过 | 通过 | 通过 | 通过 |

两档都执行以下真实任务和断言：

- 从临时 Maven 仓库解析 `com.purride:pixel-engine:0.1.0-SNAPSHOT` 及其完整传递图；
- Kotlin 与 Java 各一项 JUnit 测试，脚本分别检查两份 XML 存在；
- 消费者自定义 `PixelLeafRenderObjectWidget` / `PixelRenderBox` 完成 layout、paint 和精确像素断言；
- Java 直接调用 `PixelFrameTimings` 的公开构造器和 getter；
- `assembleDebug` 产生 debug APK；
- `assembleRelease` 开启 R8，产生 release APK 与非空 `mapping.txt`；
- 日志读取实际 Kotlin compiler 版本，两档均为 `2.2.10`。

原始日志：

| 矩阵项 | 字节 | SHA-256 |
|---|---:|---|
| `minimum.log` | 4,491 | `938c1bafb028af141bf60f97efba63deb472e7385103244b508c4a52cfd49e1b` |
| `recommended.log` | 4,548 | `a3897ab161d1e9156542410fb0b74d47e5b2af2e6bc13d2f5199f36a7792c2c3` |

## 3. 不支持组合的提前失败

全部 AAR 在 `defaultConfig.aarMetadata` 中明确声明：

```text
minAndroidGradlePluginVersion=8.10.0
minCompileSdk=36
```

负例只运行 `checkDebugAarMetadata`，必须非零退出且命中精确边界提示：

| 负例 | 实际结果 | 原始日志 SHA-256 |
|---|---|---|
| AGP `8.10.1` / Gradle `8.11.1` / Kotlin `2.2.10` / compileSdk `35` | 八个解析到的 AAR 均提示 compile against version 36 or later | `31aaf4d7866d9f16746885f73dafad8874f60dfcb5a97e3cb3332a601c1e2c2b` |
| AGP `8.9.0` / Gradle `8.11.1` / Kotlin `2.2.10` / compileSdk `36` | 八个解析到的 AAR 均提示 requires Android Gradle plugin 8.10.0 or higher | `d2b4cc45cbc36900bab3a7d4ce7450b8160681703ccad2806ce25f1ff3feb047` |

因此不支持组合不会进入模糊的 Kotlin 编译或 R8 崩溃，也不依赖文档提醒才发现版本不兼容。

## 4. 发布物完整性

`tools/check_pixel_publication.py` 对九个坐标逐一验证：

- 唯一 AAR、POM、Gradle `.module`、sources JAR、Dokka Javadoc JAR；
- AAR 中非空 `classes.jar`、`proguard.txt` 和精确最低版本 metadata；
- sources 至少包含 Kotlin/Java 源码且不含 `.class`；
- Javadoc/KDoc 包包含 `index.html` 和类型页面；
- Gradle metadata 同时包含 `java-api`、`java-runtime`、`sources`、`javadoc` variant；
- POM 与 module metadata 的 `com.purride` 传递依赖精确等于 artifact ownership 图；
- module metadata 不得泄漏 Gradle project dependency；
- 报告记录每个主文件的大小和 SHA-256。

| 坐标 | AAR 字节 | sources 字节 | Javadoc 字节 | 内部传递依赖 |
|---|---:|---:|---:|---|
| `pixel-core` | 239,633 | 60,646 | 601,686 | 无 |
| `pixel-runtime` | 1,190,642 | 346,500 | 1,718,568 | core |
| `pixel-widgets` | 1,130,379 | 244,926 | 790,623 | core/runtime |
| `pixel-navigation` | 346,989 | 71,139 | 655,185 | core/runtime/widgets |
| `pixel-android` | 403,375 | 127,899 | 541,467 | core/runtime/widgets/navigation |
| `pixel-testing` | 89,923 | 18,152 | 318,389 | core/runtime/widgets/navigation |
| `pixel-debug` | 22,128 | 6,570 | 268,162 | core/runtime/widgets/navigation/android/testing |
| `pixel-compose` | 16,802 | 4,425 | 261,533 | core/runtime/widgets/navigation/android |
| `pixel-engine` | 41,440 | 1,003 | 260,486 | core/runtime/widgets/navigation/android/testing/debug |

发布物报告 `publication.json` 为 14,183 字节，SHA-256：
`b4e8b10d04c409aa863139b32dd1d948889b069094fff7ffe90c1e8d83a921ad`。

矩阵报告 `matrix.json` 为 1,306 字节，SHA-256：
`cdcbc64254e2e89106f3db4b79f9a8a4421310bdbf1e18e0d254e930edcb521f`。报告通过
`publicationReportSha256` 绑定本轮实际发布物。

## 5. 既有兼容门禁复核

完整 `tools/pixel-release-check.sh` 继续执行并通过：

- 外部 RenderObject SPI bytecode 边界；
- RouteEntry 正向消费者和三个预期编译失败类型负例；
- SHA 固定旧消费者 AAR 在当前拆分发布物上运行；
- 聚合 SDK 的 Kotlin、Java、Host、主题、字体、localization、frame diagnostics 消费；
- core/runtime/widgets/navigation/android/testing/debug/compose 九类隔离坐标的 debug、release、R8 和
  传递依赖检查；
- API/ABI/Metalava/KDoc/lint/单测、安全、性能、soak、Baseline Profile 和严格 MkDocs。

主 Gradle 批次为 1,060 tasks，`BUILD SUCCESSFUL`；随后全部 shell 门禁运行至最终严格 MkDocs，命令
退出码为 0。矩阵已由该 required CI 入口调用，因此每个支持组合都有明确机读结果，任一组合失败会
通过 `set -euo pipefail` 阻止发布候选。

## 6. 结论

M8-2 的五项任务和两项验收均满足：真实 Maven 边界、最低/推荐版本、debug/R8 release、
Kotlin/SPI/Java/旧二进制、完整发布 metadata 均有自动门禁和可复核原始证据。工作包范围内 P0/P1
遗留为零；下一工作包为 M8-3 CI 与质量门禁。
