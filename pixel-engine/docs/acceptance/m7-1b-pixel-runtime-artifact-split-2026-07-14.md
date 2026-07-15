# M7-1b `pixel-runtime` Artifact 拆分阶段验收

- 日期：2026-07-14
- 结论：阶段通过；M7-1 总工作包继续进行中
- Android 行为设备：`Pixel_4` AVD，`sdk_gphone16k_arm64`，Android 17 / API 37

## Artifact 与源码所有权

- 新增独立 `:pixel-runtime` Android Library 与 `com.purride:pixel-runtime:0.1.0-SNAPSHOT` 发布坐标。
- runtime 从兼容源码树按受审 ownership 清单确定性同步 130 个 Kotlin 文件，并编译两个固定 Unicode Bidi Java 参考实现，共 132 个生产文件。
- ownership 门禁已扩展到 Kotlin/Java 多 source root，支持 Java 分号 package/import、精确 owner、失效 override 和跨 source root 重名拒绝；当前 265 个生产文件、9 个目标 artifact、33 条声明依赖均通过，finding 为 0。
- 生产 `pixel-engine` 不使用 Kotlin friend path：它只编译未归属 core/runtime 的剩余源码，并以 `api(project(":pixel-runtime"))` 聚合。历史白盒单测仅在 test compile 使用 core/runtime debug JAR friend path。
- 跨 artifact 实现契约以 `com.purride.pixelui.internal.PixelArtifactInternalApi` 标记；独立与聚合 Metalava/public/JVM ABI 门禁按 classfile 注解排除这些 sibling SPI，不把实现可见性升级误报为第三方稳定 API。

`pixel-runtime` AAR 中没有 testing/debug class；POM 只发布：

- `com.purride:pixel-core:0.1.0-SNAPSHOT`
- `org.jetbrains.kotlin:kotlin-stdlib:2.2.10`

Gradle Release runtime 实际解析集合为上述两项与 `org.jetbrains:annotations:13.0`，不含 Lifecycle、Compose、widgets、navigation、android、testing、debug 或聚合 engine。

## API、ABI、POM 与预算

独立 runtime 门禁：

- Metalava baseline：`pixel-runtime/api/pixel-runtime.metalava-api`，SHA-256 `25fc147f4c00e7cb193a445755db1450154cffd5a16060b2575dd729b931d83a`
- JVM ABI baseline：`pixel-runtime/api/pixel-runtime.binary-api`，SHA-256 `5f475c0143d5225ed3702d0da49679abd0e6baf7ffb1e7c1174c3b5508d2c11b`
- Release AAR：1,136,399 / 1,250,000 bytes，569 / 650 classes，5,015 / 5,600 methods
- AAR SHA-256：`e77cfe7bb0ade22c30b2653a12341220e727a3ca49d3306444516373931b692d`
- 独立 API 共 1,790 条结构化声明，全部是聚合 Metalava API 的子集；`internal` package 和 artifact SPI 标记没有泄漏。

旧聚合坐标按 engine/core/runtime 三个 AAR 并集计数：3,386,279 / 3,500,000 bytes、1,525 / 1,600 classes、15,341 / 16,000 methods，重复 class 为 0。engine AAR 本体为 2,010,252 bytes，SHA-256 `512b06ecd9a4676e69c644df490f825926921cf6d82f76da8c3d02266f80a03d`；4 个发布依赖和 19 个解析 artifact 与精确白名单一致。

机器报告：

- `pixel-runtime/build/reports/artifact-budget/release-artifact-budget.json`
- `pixel-engine/build/reports/artifact-budget/release-artifact-budget.json`
- `pixel-engine/build/reports/architecture/artifact-boundaries.json`
- `build/reports/compatibility/runtime-classpath.json`

## 行为、消费者与兼容验证

JVM 回归：

- `:pixel-runtime:testDebugUnitTest`：2/2，覆盖纯 runtime retained leaf 绘制与第二帧完整 render cache 命中
- `:pixel-engine:testDebugUnitTest`：1,178/1,178
- 两组均为零失败、零错误、零跳过

隔离 `pixel-runtime` 消费者只声明真实临时 Maven 坐标，通过公开 `PixelLeafRenderObjectWidget`、`PixelRenderBox` 和 `PixelPaintContext` 完成布局与 4×3 像素绘制；依赖报告拒绝 engine/widgets/navigation/android/testing/debug/compose/Lifecycle，debug、JVM 行为测试和启用 R8 的 release APK 均成功。

以下发布边界全部通过：

```bash
./tools/pixel-runtime-consumer-smoke.sh
./tools/pixel-sdk-consumer-smoke.sh
./tools/pixel-render-spi-compatibility.sh
./tools/pixel-route-entry-compatibility.sh
./tools/pixel-previous-binary-compatibility.sh
```

旧二进制 runner 明确校验当前 engine/core/runtime 三个生产 AAR 的 SHA-256，冻结旧 engine AAR 不在 runtime classpath，旧消费者没有内嵌 SDK class；当前报告记录 20 个 runner artifact。

更新后的统一 `tools/pixel-release-check.sh` 最终完整通过，覆盖 secret/backup、core/runtime/engine API 与 ABI、75 项 Python tooling、全部 JVM/Lint/Release 构建、五条隔离消费者/兼容链、六场景 JVM perf 趋势、soak、消费者 Baseline Profile 打包和 `mkdocs build --strict`。

## API 37 模拟器验证

命令明确指定模拟器序列号，不向同时连接的实体设备安装或启动应用：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-engine:connectedDebugAndroidTest --stacktrace
```

结果：66/66，零失败、零错误、零跳过。机器结果为 `pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`，SHA-256 `1093d961163fc0b5df5be89e9910e298f9861e4ae268f6e79c48915857845817`。

## 阶段结论与遗留

`pixel-runtime` 已满足独立 artifact 的实现、生产隔离编译、测试、API、ABI、POM、consumer rules、预算、最小消费者、历史兼容和模拟器行为回归要求，可以作为 M7-1 的第二个完成阶段。它不传递 Android UI/AndroidX UI 能力，也不包含 testing/debug class。

M7-1 尚不能整体完成：widgets/navigation/android/testing/debug/compose 仍需按 ownership graph 拆分，当前聚合 engine 仍包含这些实现；最小 Host 与聚合 debug 泄漏总验收保持未勾选。下一阶段先拆 `pixel-widgets`，M7-1 与 Codex Goal 继续 active。
