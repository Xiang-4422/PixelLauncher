# M5-3F Adaptive & Localization Demo、golden 与 M5-3 最终验收

## 结论

2026-07-14，M5-3F 与 M5-3 总工作包已完成。`pixel-demo` 新增真实
`Adaptive & Localization` 场景，直接使用 SDK 的 Android Host override、AdaptiveDp profile、
Localization provider、AdaptiveBuilder、TextField/IME、SafeArea、高对比度 theme helper 和
controller-bound multi-stack navigation；没有建立一套只能在 Demo 中工作的平行环境模型。

七个确定性环境已经形成只读 semantic snapshot 与 ASCII pixel golden：English/Chinese LTR 1×、
Arabic RTL 1×、LTR 2× textScale、高对比度、compact hinge、small-window IME。测试没有写源码
基线或 `REGEN` 自动接受入口，只把可丢弃候选写到 `build/reports/acceptance/` 供人工审阅。

最终完整 JVM、API 24/37 instrumentation、API 29/30 WindowInsets 差异分支、三份 API/ABI、
stable boundary、旧二进制、独立 Kotlin/Java consumer、Lint、Release AAR/POM/sources/Javadoc、
性能、soak 和严格 MkDocs 全部通过。M5-3 范围内没有 P0/P1 代码、测试、文档或兼容遗留。

## 综合 Demo

场景注册在 `布局 → 系统边界 → Adaptive & Localization`，并可通过 `AdaptiveBuilder`、
`localization`、`multi-stack` 等 catalog 关键词搜索。场景的实时指标包括：

- active locale preference 与 resolved provider locale；
- layout direction、text scale、高对比度和 reduce motion；
- density、refresh rate、physical/logical size、orientation 和 width/height window class；
- SafeArea padding、IME viewInsets 与 fold/hinge/cutout display features。

可交互 preset 为 System、中文、RTL、Text 2×、High contrast、Reduce motion、2× density/120Hz
和 Hinge。每次 preset 都从进入场景前捕获的 immutable snapshot 派生，不会把前一个 preset 的
属性意外累积到下一个。场景进入时使用 `PixelHostProfilePolicy.AdaptiveDp(4f)`，退出时恢复原
capability override 和 profile policy。

Unicode 展示同时包含中文、decomposed `Cafe\u0301`、family/skin-tone/flag/keycap emoji cluster、
真实 CRLF、纯 RTL、`ABC אבג 123` mixed Bidi 与 U+10FFFF unsupported fallback。真实 TextField
连接 Android InputConnection，并公开显示当前 UTF-16 selection 和 focus 状态。

场景底部使用两个始终 mounted 的 `PixelMultiStackNavigator` 栈和 controller-bound
`NavigationBar`。Home/Settings 各自可以 push detail；切换 stack 不清空 inactive history，Back
先 pop 当前栈，再从 secondary root 返回 Home。

## API 37 真实交互证据

API 37 Pixel_4 AVD 安装并启动最终 `pixel-demo-debug.apk` 后完成以下真实操作：

1. 从三栏 Demo browser 进入 `Adaptive & Localization`；
2. 切到 RTL，确认 footer、metric Row 和视觉 child 顺序反转，语义 label 保持原身份；
3. 滚动到 TextField，点击后得到 `focus=true`、IME logical bottom `75`；
4. 通过真实 InputConnection 输入 `SDK`，selection 变为合法 UTF-16 boundary；
5. 隐藏 IME、切到 High contrast，再滚回输入区，文本仍为
   `Café · SDK👨‍👩‍👧‍👦 · ABC אבג 123`，selection 与滚动位置保留；
6. Home push 到 Home Detail，切到 Settings，再返回 Home，仍显示 Home Detail route entry。

最终截图位于
`pixel-engine/build/reports/device/m5-3f-api37-adaptive-localization-home-detail.png`，大小
39,620 bytes，SHA-256：
`c8e0e38e226ba78f34bdd7f9b96149b55dcb75d3617b3c46b66936c06047b03e`。

截图同时显示 mixed Bidi、unsupported fallback、保留的输入文本/selection、Home Detail 和
controller-bound NavigationBar。Android layout tree 另外确认输入节点 `focused`、IME inset、
selected destination 与 route 页面语义；没有使用截图像素代替交互断言。

## 只读 snapshot 与 pixel golden

`M53AdaptiveLocalizationGoldenTest` 在七个独立 `PixelTester` runtime 中按 Host 根真实顺序注入
`HostCapabilities`、`Directionality`、`MediaQuery`、`PixelAdaptiveEnvironment`、
`PixelLocalizationProvider` 与 capability-aware `PixelTheme`。

两个 source-controlled artifact 为：

- `src/test/resources/element-snapshots/m5-3-adaptive-localization.txt`；
- `src/test/resources/golden/m5-3-adaptive-localization.txt`。

semantic snapshot 固定 locale/provider、direction、textScale、contrast、window class、IME、feature
count、本地化 Checkbox label、TextField 原文/selection/actions 与逻辑 geometry。pixel golden 固定
RTL 三色 child 视觉反转、2× text 的尺寸增长、高对比度 surfaceVariant、compact 裁切和
SafeArea/IME 位移。

每次运行会把实际输出写到 `build/reports/acceptance/*-candidate.txt`，但断言只读取已存在的源码
基线。基线缺失时测试直接失败；代码中没有创建源码目录、写 baseline 或检查 `REGEN` 环境变量
的分支。最终两项 2/2 通过。

## 最终测试与设备矩阵

| 范围 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` 完整 JVM | 1,261 | 0 | 0 | 0 |
| `pixel-demo` JVM | 27 | 0 | 0 | 0 |
| Launcher app JVM | 328 | 0 | 0 | 0 |
| Python tooling / SDK consumer | 40 | 0 | 0 | 0 |
| API 24 完整 instrumentation | 53 | 0 | 0 | 0 |
| API 29 WindowInsets 差异分支 | 1 | 0 | 0 | 0 |
| API 30 typed WindowInsets 差异分支 | 1 | 0 | 0 | 0 |
| API 37 完整 instrumentation | 58 | 0 | 0 | 0 |

API 24、29、30、37 fingerprint 与 platform inset 输入细节见 M5-3E 验收报告。M5-3F 在新增
Demo/golden 的最终工作树上重新执行 API 24 完整 53/53、API 29 差异 1/1 和 API 37 完整
58/58；API 30 typed 分支不受 Demo/test-only 改动影响，并沿用 M5-3E 同一最终引擎二进制的
1/1 结果。完整四 API 设备矩阵仍由 M8 收口。

## API、文档与发布门禁

M5-3F 只新增 Demo、测试、golden 和文档，没有新增 SDK public surface，三份 baseline 与 M5-3E
保持一致：

| baseline | SHA-256 |
|---|---|
| public API | `d5dbc19297af15af6e04e9584c8251a6df3d34612492143c5a74af75cc9ff74a` |
| JVM binary API | `495925d4f0810497bae404dff21f5b7f55cf887f8d696c25614c6236be947806` |
| Metalava API | `2ba16f4419b76f936c9e8f2c7d6055b6a9997e9b68700dd9308ed9efb06716a0` |

README、使用手册、架构、CHANGELOG、Localization/Unicode/code-point/Bidi/adaptive migration 与
M5-3A–F 设备验收报告已交叉复核。文档继续区分三件事：逻辑 grapheme-safe 编辑、引擎内置
glyph asset 覆盖、consumer rasterizer 的复杂 shaping 能力；没有把 cluster-safe geometry 写成
Arabic/Indic contextual shaping、彩色 emoji 或全部 ZWJ ligature 承诺。

最终 `tools/pixel-release-check.sh` 成功，覆盖 secret scan、backup contract、Unicode generators、
API/ABI/stable boundary、25/25 component token、10/10 foundation group、KDoc、完整 JVM/tooling、
Lint、Release AAR/POM/sources/Javadoc、Render SPI、RouteEntry、冻结旧 AAR、隔离 consumer、六场景
perf、lifecycle/resource soak 与严格 MkDocs，性能 `overallPass=true`。

KDoc 为 1,216/2,176（55.88%，当前阶段门槛 35%）；M5-3 新增/修改的类、变量和方法已具备职责、
不变量或兼容边界说明。全仓 public/protected KDoc 100% 由 M9-1 收口。

最终 Release AAR 为 3,205,125 bytes，SHA-256：
`38abc3685fe8cb9c2d5640e66971c96659e6cf9d80206718e62d4da05857ca23`。
released Metalava 仍按预发布契约记录 `SKIPPED/NO_RELEASED_BASELINE`，不是 skipped test；正式
1.0 signature 由 M9-3 冻结。

## 后续边界

- M6 继续收口性能、内存与资源系统；
- M7 继续收口模块化、扩展点和发布工程；
- M8 完成完整 API/设备/输入兼容矩阵；
- M9 完成全仓 KDoc 100%、正式签名冻结、发布演练和 1.0.0 最终发布证据。
