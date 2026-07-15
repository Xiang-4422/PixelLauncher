# M6-4 资源加载、校验与内存边界验收

- 日期：2026-07-14
- 结论：通过
- 设备：`Pixel_4` AVD，`sdk_gphone16k_arm64`，Android 17 / API 37，page size 16,384 bytes
- 性能证据口径：本页只证明资源正确性、主线程约束和 Android 解码行为；模拟器结果不进入 M6-2/M6-3 的真机性能 baseline。

## 实现闭环

### 有界缓存与不可变资源

- `PixelResourceCache` 使用单锁保护三类 access-order LRU、字节/条目计数、命中统计和在途表；loader 始终在锁外执行。
- bitmap、sprite sheet、glyph pack 有逐类预算，并共享全局总字节预算和访问序。淘汰原因区分单条过大、逐类字节、逐类条目和总字节预算。
- 同类型/同 key 并发未命中共享一次 loader；`remove` / `clear` 使此前开始的旧结果失去写回资格。eviction listener 在锁外调用，详细快照不持有资源对象。
- `PixelBitmap` 和 `PackedGlyphRecord` 的公开构造输入与数组 getter 均 defensive copy；引擎内部只读热路径使用 owned storage，避免逐帧复制。

### 格式、完整性和分配前校验

- manifest/catalog/sprite 改为统一严格有界递归下降 JSON：总字符、32 层深度、对象/数组、字符串、metadata 和资源/frame 数均有固定上限；拒绝重复 key、尾随垃圾、非法转义、浮点、整数溢出和不安全路径。
- bitmap 在像素分配前校验编码长度、PNG/JPEG/GIF/WebP/BMP magic、可选 SHA-256、bounds、单轴尺寸和总像素；resource 正式解码仍保留 Android density 语义。
- glyph 在集合/字节数组分配前校验总长、PGLY magic/version、cell height、glyph count、Unicode scalar、advance/width 和精确 packed length；拒绝重复 code point、截断和尾随数据。
- 有界流对批量 `read()` 零进度实现退化为单字节读取，避免不可信 `InputStream` 造成无限循环；超过预算立即失败。
- bitmap、sprite、manifest、glyph 的外部 SHA-256 均在解析或解码前校验。

### 正式加载 API

- `PixelResourceLoader` 接收调用方 `Executor`、共享 cache 和 `PixelResourceLoadingPolicy`，不创建或关闭全局线程。
- 同步 bitmap/sprite/glyph 入口默认拒绝 Android 主线程；异步入口在 executor 执行并按类型/key 去重。
- `PixelResourceLoadHandle.cancel()` 只取消当前订阅，不取消共享 IO、其他订阅或成功后的缓存写入。
- 三类资源均支持 prefetch；失败按单调时钟短期缓存，并支持按 key/全部清理。
- 未完成句柄拒绝主线程 `await()`，已经完成的结果允许无阻塞读取。

## 兼容与公开边界

- 三份 reviewed API baseline 已更新，`checkPublicApi`、`checkBinaryApi`、`checkMetalavaApi` 和 `checkStableApiBoundary` 全部通过。
- 更新基线前，以旧 binary baseline 和新生成 binary dump 做排序集合差，旧描述符删除数为 0。
- checksum 扩展没有删除旧资源定义的构造器、`componentN`、`copy` 或 `copy$default`；`PackedGlyphRecord` 和 `PixelBitmap` 也保留旧 JVM 入口。
- 新稳定签名没有暴露 internal package 类型。

API baseline SHA-256：

- public：`a6f1cb6225cbb310434529f347db954bd8a8f75180594ed6fa13f550d88573df`
- binary：`24d7499ce0511eceddc987c151414dc73ff473d3ea0369f583ec671227b8fd1e`
- Metalava：`1f649a2684283c85a81fdc658a71e9d2814f8682ec9b2c3d04336110cd37b765`

## JVM 验证

命令：

```bash
./gradlew :pixel-engine:testDebugUnitTest
```

结果：1,302/1,302，0 failure，0 error，0 skipped。资源定向集合为 54/54：

| 测试类 | 数量 | 结果 |
|---|---:|---|
| `PixelBitmapLoadersTest` | 2 | 通过 |
| `PixelResourceCacheTest` | 7 | 通过 |
| `PixelResourceCacheAdvancedTest` | 5 | 通过 |
| `PixelResourceLoaderTest` | 8 | 通过 |
| `PixelResourceManifestTest` | 9 | 通过 |
| `PixelSpriteSheetLoaderTest` | 9 | 通过 |
| `PixelResourceSecurityTest` | 12 | 通过 |
| `PixelGlyphPackParserTest` | 2 | 通过 |

资源定向测试覆盖：逐类/全局 LRU、过大拒绝、8 线程单飞、clear/在途竞态、订阅取消、预取、失败 TTL/清理、executor 拒绝、真实数组防御性复制、重复 JSON key、尾随/深层/长字符串、不安全路径、checksum、sprite 坐标溢出、glyph 超大/负 count、伪造长度、重复 scalar、截断/尾随数据，以及零进度流。

## API 37 模拟器验证

定向命令：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :pixel-engine:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.purride.pixelcore.PixelResourceLoadingInstrumentedTest
```

定向结果 4/4。随后使用相同显式 serial 执行完整 `:pixel-engine:connectedDebugAndroidTest`，结果 64/64，0 failure，0 error，0 skipped。

四条 M6-4 Android 路径证明：

1. 真实 Android 主 Looper 在 loader 执行前拒绝同步加载。
2. 主线程发起的异步请求在调用方后台 executor 解析。
3. Android `BitmapFactory` 对真实 2×2 PNG 完成 checksum、bounds、正式像素解码和不可变读取。
4. 损坏 magic、编码字节预算和解码尺寸预算均确定性失败。

机器结果：`pixel-engine/build/outputs/androidTest-results/connected/debug/TEST-Pixel_4(AVD) - 17-_pixel-engine-.xml`，SHA-256 `f2afcbaf988cbbdc7dc14d49086720d559a21bada009628e797395cf3d3b9cce`。同目录保留 device info、cpuinfo、meminfo、UTP 日志和每条测试 logcat。

## Release 与文档门禁

执行并通过：

```bash
./gradlew --parallel \
  :pixel-engine:testDebugUnitTest \
  :pixel-engine:checkPublicApi \
  :pixel-engine:checkBinaryApi \
  :pixel-engine:checkMetalavaApi \
  :pixel-engine:checkStableApiBoundary \
  :pixel-engine:checkKdocCoverage \
  :pixel-engine:lintRelease \
  :pixel-engine:checkReleaseArtifactBudget \
  :pixel-engine:testPixelTooling
python3 -m mkdocs build --strict
tools/pixel-release-check.sh
```

Python tooling 为 68/68。Release AAR 为 3,375,973/3,500,000 bytes、1,517/1,600 classes、15,289/16,000 methods；发布直接依赖 2/2、解析后 runtime artifact 17/17，预算报告无 violation。AAR SHA-256 为 `42e9a3a505e5da7696fd2dd7efaaa23825e3a1f827889d36947b460ab4d17faf`。

统一 release gate 最终完整通过，额外覆盖 worktree/artifact secret、两种 APK backup contract、RenderObject SPI、RouteEntry、旧消费者二进制运行时、隔离 Maven SDK consumer、六场景 JVM perf/trend、resource/lifecycle soak、两种 benchmark APK Baseline Profile 打包及完整 app/demo 构建。首次执行在与 M6-4 无关的 `:app:mergeReleaseNativeDebugMetadata` 使用默认 2 GiB Gradle 堆时 OOM；该精确任务以 4 GiB 临时堆成功重跑后，再次执行未修改、未跳项的统一脚本完整通过。预发布仓库尚无正式 released Metalava baseline，因此该单项按既有规则记录 `SKIPPED/NO_RELEASED_BASELINE`，不等同于测试跳过或兼容门禁放宽。

## 验收结论

- 恶意或损坏资源在大分配前受固定长度、数量、尺寸、深度、精确二进制长度和 checksum 约束；对应回归覆盖 OOM 放大、整数溢出、越界和无限循环入口。
- cache 超预算后的淘汰顺序、原因和字节结果可观测；clear 与在途加载竞态不会把旧资源重新写回或在 cache/snapshot 中保留引用。
- JVM 探针与 API 37 真实 Looper 双重证明大资源同步解析默认不能进入 UI 主线程，异步路径使用调用方 executor。

因此 M6-4 的实现、测试、文档和证据全部满足 Goal，可以标记完成。M6-2/M6-3 的代表性真机性能证据仍按原门禁保持进行中。
