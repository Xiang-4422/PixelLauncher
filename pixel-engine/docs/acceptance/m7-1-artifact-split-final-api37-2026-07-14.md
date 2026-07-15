# M7-1 Artifact 拆分最终验收

- 日期：2026-07-14
- 结论：M7-1 完成
- Android 行为设备：`emulator-5554`，`Pixel_4` AVD，Android 17 / API 37，16KB page size

## 最终模块边界

受审 ownership 清单覆盖 266 个生产 Kotlin/Java 文件、9 个 artifact 与 32 条声明依赖，
finding 为 0。最终文件所有权为：core 25、runtime 132、widgets 60、navigation 10、Android
33、testing 1、debug 3、Compose 边界 1、legacy engine marker 1。

依赖图无环，最小 Host 路径为：

```text
pixel-runtime    -> pixel-core
pixel-widgets    -> pixel-runtime + pixel-core
pixel-navigation -> pixel-widgets + pixel-runtime + pixel-core
pixel-android    -> pixel-navigation + pixel-widgets + pixel-runtime + pixel-core
pixel-testing    -> pixel-navigation + pixel-widgets + pixel-runtime + pixel-core
pixel-debug      -> pixel-android + pixel-testing + 最小 SDK 图
pixel-compose    -> pixel-android + 最小 SDK 图（可选边界）
pixel-engine     -> 除 Compose 外的 legacy 聚合图
```

`pixel-testing`、`pixel-debug` 和 `pixel-compose` 都不是 `pixel-android` 的传递依赖。Compose
artifact 当前只冻结可选坐标、POM 和内部边界锚点；真正的 Compose Host API 依赖 M7-2，归 M7-3
实现，当前任何非 Compose 图均不解析 `androidx.compose`。

## 独立 Artifact 产物

| Artifact | Release AAR bytes | Classes | Methods | SHA-256 |
|---|---:|---:|---:|---|
| `pixel-core` | 239,628 | 123 | 1,134 | `b82be8c887d1468ebb6e08051ba213c59bb102213710997a761e4de6591a56e2` |
| `pixel-runtime` | 1,136,399 | 569 | 5,015 | `e77cfe7bb0ade22c30b2653a12341220e727a3ca49d3306444516373931b692d` |
| `pixel-widgets` | 1,130,374 | 451 | 5,509 | `66084e94285da74b9eb2a9f47a8c9b69c549a142570ebc53a442f05439b58b99` |
| `pixel-navigation` | 346,984 | 170 | 1,522 | `c704c5128893341ea41d5ca9272fd116e847d85dddb62fb83e2f9035710af5a4` |
| `pixel-android` | 385,410 | 174 | 1,743 | `c0c1c182b053c853906399c47c4b1f50be00367f05f915de7e79bec92c63cb7d` |
| `pixel-testing` | 89,918 | 29 | 372 | `6db193be43dbde4c7676b5db481696b2ca00e445d386d0ddee3be823d8671482` |
| `pixel-debug` | 22,123 | 8 | 44 | `018fc338f0254acee858c6572e11d9b3096ef684c70b79b0cf5b3a3f14849d20` |
| `pixel-compose` | 1,719 | 1 | 2 | `e6dd186dfc16fba1c407891bf141b0ed3aa23e94933d67636714216bc8a8a3a4` |
| `pixel-engine` AAR 本体 | 41,530 | 1 | 2 | `70b40c9b3148f6fc33d94be9afc03b6417c21a84f0ca19b34c13a9e9e14f33d0` |

所有独立预算及其精确 POM/runtime 依赖白名单均通过。legacy `pixel-engine` 的 8-AAR 兼容并集
（engine 本体加 core/runtime/widgets/navigation/android/testing/debug）为 3,392,366 / 3,500,000
bytes、1,525 / 1,600 classes、15,341 / 16,000 methods，重复 class 为 0。可选 Compose 不进入
该并集，也不进入 legacy engine POM。

legacy engine AAR 本体只包含 `PixelEngine` marker，没有 testing/debug 实现。为保持旧聚合坐标已经
发布的 API/ABI，legacy POM 仍显式传递独立 testing/debug 坐标；需要最小 release Host 图的消费者
应直接依赖 `pixel-android`，其图不含 testing/debug/compose。这里区分“物理 AAR 不重复/不内嵌
调试实现”与“旧聚合 POM 保留历史 API 可达性”，不能通过删除旧 API 伪造更小依赖图。

## API 与 JVM ABI

| Artifact | Metalava 行数 / SHA-256 | JVM ABI 行数 / SHA-256 |
|---|---|---|
| `pixel-navigation` | 1,238 / `289d9c230c75a24b2ad4c35302b214c00d9c011686838e1e9a7649fe96aa3076` | 1,038 / `faa56d692854dc7bf2660707af753dcd2ca34ff6ff880499436f013dbe1724a0` |
| `pixel-android` | 692 / `4a1807f10d03c5df45e08a47ef650fd6b6199d12d96ecf18827f4bc98cd7c5ae` | 643 / `d954c96846e3f3f8733e7cbc9ca5a5e1443f3be7c120fb0176f30e0203d032af` |
| `pixel-testing` | 124 / `31f660d3ed8e4b898d146a6cbf7e47d076048e630638579f8920bb1267ae5c7a` | 130 / `bf53dd6cd60dc2ff8bae5bef8c57023cd94b77c2c393add53a2ceeb874b5906d` |
| `pixel-debug` | 16 / `40d9bb22d3b5a96844950c77b9c5d42e2717b46cefb22017dc36e91907026d8a` | 13 / `456c97726744a5bbb2c86262373809b9b6ba037d89c80c5e47b0f8f18fae4c6d` |
| `pixel-compose` | 1 / `ca574f6632497f059d3f68b387fc3f8c7fa0a23eee1f4d3d259fe12d818429bb` | 1 / `44110795cb247c03a724304b917f59ae84758f083cd13958641955d4b41eab87` |

Compose 的一行 baseline 只有格式/header，不冻结公共类型。所有非空独立 API/ABI 都是聚合 baseline
的受审子集；production compile 不使用 friend path，只有 legacy 白盒测试通过 test-only friend JAR
访问兄弟 artifact 内部实现。

## 行为、消费者与发布兼容

独立行为测试全部通过：core 125/125、runtime 2/2、widgets 1/1、navigation 2/2、testing 1/1、
debug 1/1、Compose boundary 1/1。Android artifact 还在 API 37 上以真实默认 Host setup 完成 1/1
独立 instrumentation。Python tooling 为 81/81。

以下真实临时 Maven POM/AAR 消费者全部完成 JVM 测试、Debug APK 和启用 R8 的 Release APK：

```bash
./tools/pixel-core-consumer-smoke.sh
./tools/pixel-runtime-consumer-smoke.sh
./tools/pixel-widgets-consumer-smoke.sh
./tools/pixel-navigation-consumer-smoke.sh
./tools/pixel-android-consumer-smoke.sh
./tools/pixel-testing-consumer-smoke.sh
./tools/pixel-debug-consumer-smoke.sh
./tools/pixel-compose-consumer-smoke.sh
./tools/pixel-sdk-consumer-smoke.sh
```

RenderObject SPI、RouteEntry 正/负编译、旧消费者二进制运行时也全部通过。旧二进制 runner 精确
校验 engine/core/runtime/widgets/navigation/android/testing/debug 八个生产 AAR 的 SHA-256；冻结旧
engine AAR 不进入 runtime classpath，旧消费者没有内嵌 SDK class。

## API 37 模拟器与统一发布门禁

设备命令显式锁定 `emulator-5554`，没有向实体设备安装或启动应用：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-engine:connectedDebugAndroidTest --no-daemon
```

结果：66/66，0 failure、0 error、0 skipped。机器结果：

- `pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`
- SHA-256 `cadb48e5ff86b27cabeebe319ab625dda532a6c8c373a7e0917bab6514a86704`

最终未删项 `tools/pixel-release-check.sh` 完整通过，覆盖 9 artifact API/ABI/预算/单测/lint、聚合
API/ABI、ownership、secret/backup、Demo/app、全部隔离兼容消费者、JVM perf 趋势、soak、两份
Baseline Profile APK 和 `mkdocs build --strict`。

## 结论

M7-1 的实现、独立编译、API/ABI、POM、consumer rules、预算、最小消费者、旧坐标兼容、无重复
class、最小 Host 隔离与模拟器行为证据均已闭环。M7-1 可以标记完成；下一工作包是 M7-2 的
`PixelEngine` 实例与 Services。Compose 的实际互操作 API 明确保留在 M7-3，不把边界占位误报为
功能完成。
