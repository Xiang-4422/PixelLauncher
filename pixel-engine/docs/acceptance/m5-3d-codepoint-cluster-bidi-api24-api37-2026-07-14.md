# M5-3D Code-point 字体、cluster 段落、Bidi 几何与 API 24/37 验收

## 结论

2026-07-14，M5-3D 已完成。字体 source/provider/cache 和字形包主链路使用完整 Unicode
code point `Int`；既有 `Char` source/provider 的 JVM 描述符继续保留。段落、RichText、TextField
统一以 Unicode 17 extended grapheme cluster 作为不可拆分 layout unit，并通过固定 Unicode
17.0.0、UAX #9 revision 51 的 engine-owned resolver 建立逻辑 cluster 与视觉 cluster 映射。

wrap、ellipsis、letter spacing、RichText 跨 span、caret affinity、pointer hit test、selection 多段
矩形/handle 与 Accessibility character bounds 均消费同一份段落几何。API 24 与 API 37 在同一最终
集成态分别通过完整 46/46 和 51/51 instrumentation 套件；完整 JVM、API/ABI、Lint、Release
AAR、旧二进制与隔离消费者、性能、soak 和严格文档门禁通过。M5-3D 范围内没有 P0/P1 代码、
测试、文档或兼容遗留。

1.0 保证 grapheme-safe 编辑、确定性的 UBA 视觉重排和 unsupported cluster fallback，但不承诺
SDK 内置 Arabic/Indic contextual shaping、彩色 emoji、全部 ZWJ ligature 资产或脚本级断词。这些
边界已进入用户手册和迁移文档，本报告不以安全的 cluster 几何冒充完整字体资产覆盖。

## Code-point 字体与旧 ABI

`GlyphSource`、`GlyphProvider`、`PixelGlyphPack`、`PixelFontEngine` 和两级 cache 的内部 key 均使用
完整 Unicode scalar `Int`。supplementary glyph 不再被拆成两个 surrogate：真实字形包记录可以被
查找、测量、缓存和绘制，缓存身份包含完整 code point 与样式。

既有 `Char` source/provider 入口仍保留原 JVM 描述符。新 `Int` 默认方法对 BMP 转发旧 `Char`
实现；supplementary 值不会伪造 surrogate 调用。隔离 Kotlin consumer 实现新的 `Int`
`GlyphProvider`，验证 supplementary scalar 只查询一次；隔离 Java consumer 实现旧 `Char`
`GlyphSource`，验证 BMP 转发且 supplementary 返回 `null`。冻结旧 AAR consumer 也在当前 runtime
真实加载通过，旧基线 SHA-256 为
`a88dd712c4934581a644356780e828b4a1fd1166dccdc9ef7ab54ec5b298bfca`。

新增 `PixelClusterTextRasterizer` 是 additive capability。声明支持时，measure/draw 收到完全相同的
多 code-point cluster；不支持时，段落只提交一个 U+FFFD fallback。Java 可以直接消费
`PixelStyledTextRasterizer` 与 cluster capability；由于冻结的 `PixelTextRasterizer.drawText` 含
Kotlin value class `PixelColor`，Java 不适合直接实现该旧接口，Java 自定义 rasterizer 应由一层
Kotlin adapter 暴露。该边界未通过破坏旧 ABI 来隐藏。

## Cluster 段落与共享几何

`PixelParagraphLayout` 先按 Unicode 17 grapheme boundary 构建 styled cluster，再进行硬换行、
wrap、ellipsis、Bidi level 解析、视觉重排和几何生成。稳定契约如下：

- UTF-16 source range 始终回指原始 backing `String`，不会被 normalization 或 fallback 改写；
- LF、CR、CRLF、NEL、LS、PS、连续空行和首尾空行保留 source range 与合法 caret；
- RichText 的样式边界不能把一个 grapheme 拆成多个测量或绘制单元；
- letter spacing 只出现在相邻可绘制 layout unit 之间，不能插入 cluster 内部；
- ellipsis 只替换完整 cluster，输出范围和 caret 不落入 cluster 内部；
- default-ignorable-only、ZWJ、variation selector 与 Bidi control 保留在 source/Bidi 输入中，
  但零宽且不单独绘制 tofu；
- consumer 未声明 atomic cluster 能力时，一个 unsupported cluster 恰好产生一个 U+FFFD 单元，
  不污染相邻测量。

`RenderText`、`RenderRichText`、`RenderTextField`、selection overlay、pointer router 与
`PixelHostAccessibilityNodeProvider` 共用 paragraph 的 logical/visual boundary、caret affinity、
selection rect 和 character rect。mixed Bidi 的一个逻辑范围可以产生多个不相邻视觉矩形；handle
按端点 affinity 选择正确视觉边。Accessibility API 26 character-location extras 返回相同的屏幕
坐标矩形，既不按 UTF-16 code unit 平均切格，也不重算另一套 Bidi 几何。

## Unicode Bidi 权威与完整 conformance

生产实现固定 Unicode 17.0.0、UAX #9 revision 51，不调用桌面 JDK/Android `java.text.Bidi` 或设备
ICU 决定 level 和视觉顺序。生成表包含 1,267 个 Bidi_Class range、128 个 bracket 条目和 428 个
character mirroring 映射。

完整官方 corpus 的精确执行规模为：

| corpus | 执行数 | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `BidiTest.txt`，包含每行请求的 auto/LTR/RTL paragraph level | 770,241 | 0 | 0 | 0 |
| `BidiCharacterTest.txt` | 91,707 | 0 | 0 | 0 |

输入、生成器和许可证均固定版本与摘要；生成器在 `--check` 下验证输入摘要并拒绝陈旧输出：

| 文件 | SHA-256 |
|---|---|
| `BidiTest.txt` | `888bdfc8090652272d1f859cdb00ae659e2dc6c26740be61ef1d03998a687620` |
| `BidiCharacterTest.txt` | `a3e6e905ab5afbe318a96df5401d0372a04cd73ef139ab5e3cf0ae241c255488` |
| `BidiBrackets.txt` | `dadbaf38a0d0246e5b805bf8725cb81b7c621f93d030595635f5ba2c2f179428` |
| `BidiMirroring.txt` | `a2f16fb873ab4fcdf3221cb1a8a85a134ddd6ed03603181823ff5206af3741ce` |
| 生成后的 `UnicodeBidiData.kt` | `9d967ea5f819b7884929adaef48386237967c2047cc3798249c9f316e0e1698a` |

Unicode License v3 与官方输入一起跟踪，并进入 Release artifact。Grapheme 数据生成器也已加入
Gradle `check`/release gate：它在线下载 checksum-pinned Unicode 17 输入并以 `--check` 比对生成
结果；Bidi 生成器使用仓库内完整官方输入做离线 `--check`。

## 可区分字形与行为矩阵

定向 JVM 测试不使用“所有字符都画成相同 tofu”的弱断言，而为每个测试 glyph 分配可区分像素。
覆盖纯 RTL、`ABC אבג 123`、RTL 中嵌 LTR/数字、括号/镜像标点、组合字符、family ZWJ、硬换行、
ellipsis 和 RichText，并同时断言：

- 实际 paint 顺序与 UBA 视觉顺序一致，数字内部保持 LTR；
- code-point/cluster 的 measure、draw、cache key 和 fallback 次数一致；
- caret、hit test 与 pointer selection 返回原始 UTF-16 logical offset；
- mixed Bidi selection 产生预期的多段 rect，start/end handle 使用正确 affinity；
- TextField composition 和 Accessibility bounds 与普通 Text 使用同一几何。

主要定向套件包括 `PixelCodePointFontEngineTest` 5/5、
`PixelParagraphUnicodeLayoutTest` 10/10、`RenderSurfaceSelectionTest` 14/14、
`PixelHostAccessibilityNodeProviderTest` 15/15、`UnicodeBidiDataTest` 3/3，以及完整 Bidi
conformance 2/2，均为零失败、零错误、零跳过。

## API 24 与 API 37 设备证据

两个 emulator 分别运行 `:pixel-engine:connectedDebugAndroidTest` 的完整套件，而不是只运行新增
方法：

| 设备 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| API 24 / Android 7.0，`google/sdk_google_phone_arm64/generic_arm64:7.0/NYC/8695085:userdebug/dev-keys` | 46 | 0 | 0 | 0 |
| API 37 / Android 17，`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys` | 51 | 0 | 0 | 0 |

API 37 套件包含两个 API 26+ character-location 用例，分别验证 TextField mixed-Bidi/cluster
geometry 与普通 Text paragraph geometry。API 24 套件验证旧 Accessibility 兼容投影和全部通用
文本路径。

API 24 首次全量运行暴露一个测试自身的版本错误：测试直接调用 API 30 才存在的
`AccessibilityNodeInfo.getStateDescription()`，因此 45/46 通过、1 个 `NoSuchMethodError`。生产
实现没有调用该不可用方法。测试随后改为 API 30+ 检查 `stateDescription`、API 24–29 检查生产
兼容路径的 `text` 投影，并重新运行完整套件达到 46/46；没有通过 skip 或降低断言接受失败。

完整 UTP、XML、HTML、device-info 和日志保存在：

- `pixel-engine/build/reports/device/m5-3d/api24-2026-07-14/`
- `pixel-engine/build/reports/device/m5-3d/api37-2026-07-14/`

## API、ABI、文档与发布门禁

三份 current baseline 已更新并通过精确检查：

| baseline | 当前 SHA-256 |
|---|---|
| public API | `5981f9331f1b864e32984f2b65d929c5d217a336ea2c234ec7f13e97bc0b03da` |
| JVM binary API | `c514065daec838c0b81a18099615e837aac32b307ef88f907af0c91601ed6639` |
| Metalava API | `f173859ff290db233352592a6ccd18568ffd4d386e74f7c9ecf5a3e8eb01c5a1` |

`checkPublicApi`、`checkBinaryApi`、`checkMetalavaApi`、stable boundary、RenderObject SPI、
RouteEntry、冻结旧 AAR 和当前隔离 Kotlin/Java consumer 均通过。当前 SDK consumer 共 40/40
测试并成功组装独立 APK。项目还没有正式 released Metalava signature，因此 released
compatibility 按预发布规则记录 `SKIPPED/NO_RELEASED_BASELINE`；这不是 skipped test，正式
1.0 signature 由 M9-3 冻结。

README、使用手册、架构、CHANGELOG、Unicode editing migration 与新增
`docs/migrations/1.0.0-codepoint-cluster-bidi-text.md` 已同步索引层级、cluster contract、fallback、
Bidi/Accessibility 几何、Java 边界和 shaping 排除项。M5-3D 新增/修改的类、变量和方法均补充
职责、不变量或兼容边界说明。

最终 `tools/pixel-release-check.sh` 成功，覆盖 worktree/artifact secret scan、backup contract、
Unicode 生成器、三份 API/ABI、stable boundary、25/25 component token、10/10 foundation token、
KDoc、Python tooling、engine/Demo/App JVM、Lint、Release AAR/POM/sources/Javadoc、外部 consumer、
六场景 perf、lifecycle/resource soak 和严格 MkDocs。随后又单独运行
`:pixel-engine:lintDebug :pixel-engine:check`，确认最后的 API 24 测试兼容修正没有引入回归。

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` 完整 JVM | 1,237 | 0 | 0 | 0 |
| `pixel-demo` JVM | 26 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| Python tooling | 40 | 0 | 0 | 0 |
| API 24 完整 instrumentation | 46 | 0 | 0 | 0 |
| API 37 完整 instrumentation | 51 | 0 | 0 | 0 |

KDoc 为 1,178/2,123（55.49%，当前阶段门槛 35%）；全仓 public/protected 100% 仍由 M9-1
收口。性能报告的 list scroll、text input、animation、graphics primitives、page transition 和
overlay 六个场景全部通过，`overallPass=true`。

最终 Release AAR 为 3,138,791 bytes，SHA-256：
`190b720ff3405bfcf10bafec4fef92c0147d56499b61c8c187256d73d094df3d`。

## 后续边界

- M5-3E 负责 Android capability 自动采集、viewport、density/textScale、inset/display feature、
  RTL Flex 和自适应布局；
- M5-3F 汇总 Adaptive & Localization Demo、LTR/RTL/textScale/high-contrast/small-window golden、
  差异设备矩阵与 M5-3 最终集成门禁；
- M8 最终收口 API 24/29/30/37 和更多真实设备兼容矩阵；
- M9-1/M9-3 分别收口全仓 KDoc 100% 和正式 released signature。
