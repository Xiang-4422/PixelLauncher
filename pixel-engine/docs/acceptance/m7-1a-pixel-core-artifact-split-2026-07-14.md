# M7-1a `pixel-core` Artifact 拆分阶段验收

- 日期：2026-07-14
- 结论：阶段通过；M7-1 总工作包继续进行中
- Android 行为设备：`Pixel_4` AVD，`sdk_gphone16k_arm64`，Android 17 / API 37

## Artifact 与依赖边界

- 新增独立 `:pixel-core` Android Library 与 `com.purride:pixel-core:0.1.0-SNAPSHOT` 发布坐标。
- core 独占 `com/purride/pixelcore` 生产源码；四个冻结包名的 Android loader/View 兼容实现迁入 `pixelandroid/compat/core`，由聚合模块编译，两个 AAR 没有重复 class。
- core 发布 POM 只有 `org.jetbrains.kotlin:kotlin-stdlib:2.2.10`，解析后只有 stdlib 和 annotations；不包含 Lifecycle、Compose、testing、debug 或 `pixel-engine`。
- `com.purride:pixel-engine` 通过 POM `api` 传递 `pixel-core`，保持旧消费者单依赖体验。旧聚合 API、Metalava API 和 JVM ABI 由两个 artifact 的并集生成，冻结描述符没有删除。
- 跨 artifact 热路径使用明确的 `com.purride.pixelcore.internal.PixelCoreArtifactAccess` sibling bridge；该 internal package 被独立和聚合稳定 API 工具排除，不承诺第三方兼容。

受审所有权清单 `pixel-engine/config/artifact-ownership.json` 当前覆盖 258 个生产 Kotlin 文件、9 个目标 artifact 和 33 条声明依赖。`checkArtifactBoundaries` 同时检查未归属/失效文件、声明环、实际项目 import、平台 import、最小图禁用依赖和 split package 白名单。

## API、POM 与产物预算

独立 core 门禁：

- Metalava baseline：`pixel-core/api/pixel-core.metalava-api`，SHA-256 `ab1401aee3604a44dae1ef2dda9708c54e69b70772dd1c720f198e3c19114f96`
- JVM ABI baseline：`pixel-core/api/pixel-core.binary-api`，SHA-256 `37972f8a63ae299ffcac8203ecae648f9d65d8461299b17771e7878f540a2108`
- Release AAR：239,628 / 300,000 bytes，123 / 140 classes，1,134 / 1,300 methods
- AAR SHA-256：`b82be8c887d1468ebb6e08051ba213c59bb102213710997a761e4de6591a56e2`

旧聚合坐标预算按两个 AAR 的并集计数：3,381,207 / 3,500,000 bytes、1,519 / 1,600 classes、15,302 / 16,000 methods，重复 class 为 0；发布直接依赖 3 项、解析后 artifact 18 项，均与精确白名单一致。聚合 engine AAR 本体为 3,141,579 bytes，SHA-256 `c98bad179115703a961e6969c2c22808df986644e1f0a9a1d4cd5666276ab51e`。

机器报告：

- `pixel-core/build/reports/artifact-budget/release-artifact-budget.json`
- `pixel-engine/build/reports/artifact-budget/release-artifact-budget.json`
- `build/reports/compatibility/runtime-classpath.json`

## 行为与兼容验证

JVM 聚合回归：

- `:pixel-core:testDebugUnitTest`：125/125
- `:pixel-engine:testDebugUnitTest`：1,178/1,178
- 合计：1,303/1,303，零失败、零跳过

拆分后的首轮外部聚合消费者暴露了 `PixelBitmapFont` 相邻文本内部方法跨模块不可见、同名扩展递归导致的 `StackOverflowError`。实现改为由 core internal bridge 执行无拼接测量，并新增 `scaledCoreBitmapFontMeasuresAdjacentTextWithoutRecursion` 回归；修复后隔离消费者 11 项 Kotlin/Java 行为测试与启用 R8 的 Release 构建通过。

以下隔离发布边界全部通过：

```bash
./tools/pixel-core-consumer-smoke.sh
./tools/pixel-sdk-consumer-smoke.sh
./tools/pixel-render-spi-compatibility.sh
./tools/pixel-route-entry-compatibility.sh
./tools/pixel-previous-binary-compatibility.sh
```

旧二进制 runner 明确校验当前 engine/core AAR 摘要、冻结旧 engine AAR 不在运行时 classpath、旧消费者没有内嵌 SDK class。运行时报告记录 19 个 runner artifact，当前 engine/core 摘要与生产 AAR 完全一致。

## API 37 模拟器验证

命令：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-engine:connectedDebugAndroidTest --no-daemon
```

结果：66/66，零失败、零错误、零跳过。覆盖真实 `Choreographer` 默认调度器、Android Host、IME、accessibility、lifecycle、资源加载和旧兼容入口。机器结果为 `pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`，SHA-256 `3b5381205f26e253e618d938e817066d9e59d10db93b1f40a60406d7848248f3`。

## 聚合质量门禁

以下范围同批通过：core/engine Metalava 与 JVM ABI、聚合 public API、stable API boundary、artifact ownership、独立与聚合预算、KDoc、Lint、Release AAR、73 项 Python tooling、三条历史兼容链、两个隔离消费者和 API 37 instrumentation。消费者 Release 开启 R8，`PixelFrameScheduler.Default` 的反射 Android 实现由 consumer rules 保留。

## 阶段结论与遗留

`pixel-core` 已满足独立 artifact 的实现、测试、API、ABI、POM、consumer rules、预算、消费者和 Android 回归要求，可以作为 M7-1 的第一个完成阶段。M7-1 尚不能整体完成：runtime/widgets/navigation/android/testing/debug/compose 仍需按 ownership graph 拆分，当前聚合 AAR 仍包含 testing/debug；因此 Goal 继续 active，且 M7-1 的最小 Host、纯 core/runtime 和 debug 泄漏总验收仍保持未勾选。
