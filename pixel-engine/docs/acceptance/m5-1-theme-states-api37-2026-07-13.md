# M5-1 完整设计 token 与组件状态验收记录

## 结论

状态：**PASS**

M5-1 的 foundation、完整主题图、组件状态/capability、21 个标准组件 token、legacy facade
兼容、21×8 状态矩阵、Theme Showcase、文档与 API 已收口。API 37 真实 Android Host 验证了
五套完整主题、精确状态像素、焦点与 Android 语义、交互状态跨主题保持，以及无显式主题的旧 facade
像素兼容；最终发布门禁和全部测试套件均无 failure、error 或 skipped test。

## 环境

- 日期：2026-07-13，时区 `Asia/Shanghai`
- 分支：`main`
- 工作区基线提交：`a4f74b169c4a2b8dee7ca8ba36ba3ebfe343145d`
- 远端基线：`origin/main` 同为 `a4f74b169c4a2b8dee7ca8ba36ba3ebfe343145d`
- 设备：`Pixel_4(AVD) - 17`，序列号 `emulator-5554`
- SDK：37；型号：`sdk_gphone16k_arm64`
- 分辨率/密度：`1080×2280`、`440 dpi`，验收结束锁定 portrait
- 系统指纹：`google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys`
- AndroMeld Helper：`com.catchingnow.andfiles.helper` `1.10.194`，经用户授权保留安装
- 验收结束设置：`accessibility_enabled=0`、`enabled_accessibility_services=null`

AndroMeld Helper 不是 SDK 依赖或通过条件。正式设备结论来自真实 `ActivityScenario`、
`PixelHostView.draw(Canvas)`、`UiAutomation`、Android virtual accessibility node 和完整 connected 套件；
Theme Showcase 截图用于补充人工视觉复核。

## 规格与证据

| 规格 | 实现与验证证据 | 结果 |
|---|---|---|
| 完整 foundation/theme 图 | `PixelThemeTokens` 覆盖 color、typography、spacing、size、radius、border、elevation/layer、motion、labels 和 component tokens；Light、Dark、HighContrastDark、HighContrastLight、Custom 均以完整图运行。 | PASS |
| 状态与组合优先级 | `PixelControlState`、capability normalization 和 21×8 生产状态矩阵覆盖 normal、hovered、pressed、focused、selected、disabled、error、loading；输入、焦点、语义与绘制消费同一规范化状态。 | PASS |
| 21 个真实生产组件 | `ProductionComponentStateMatrixTest` 直接构建 21 个公开工厂；三通道独立哨兵验证 container/content/border，token scanner 覆盖 21 component families 与 10 foundation groups。 | PASS |
| 旧 facade 兼容 | `PixelHostLegacyFacadeInstrumentedTest` 在真实 Host 上证明无主题旧 `ProgressBar` 与独立历史 fixture 整张位图相等，并精确验证历史颜色、白色边框和 `48×5` 几何；显式主题精确消费自定义颜色与 `63×11` 几何。 | PASS |
| 状态跨主题保持 | Demo 测试通过公开语义动作修改 Checkbox、Switch、Slider、Tabs、Dropdown 后切换 Dark，五项状态全部保留；真实 Host 另验证 Checkbox 的 checked、input focus 和 click capability 在 Light→HighContrastDark 完整主题替换后保持。 | PASS |
| 焦点、语义与高对比度 | API 37 的 `ThemeStateInstrumentedTest` 验证五套主题精确 danger/primary/warning/focus 像素，Button/Checkbox/Progress Android class、bounds、range 和主题尺寸；Loading/Disabled capability 与焦点变化亦由真实 virtual node 验证。 | PASS |
| Demo、文档与 API | Theme Showcase 注册精确 21 个生产组件、94 个 foundation token 和 21×8 矩阵；API 手册、架构文档、migration guide、三份 API baseline 与 KDoc 门禁通过。 | PASS |

## API 37 设备结果

最终设备命令：

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --no-daemon --no-parallel
```

结果：`28 tests / 0 failures / 0 errors / 0 skipped`。

M5-1 新增的两个真实 Host 场景分别证明：

1. Checkbox 先由 Android `ACTION_FOCUS`、`ACTION_CLICK` 进入 focused+checked；公开主题按钮把完整
   token 图从 Light 替换为 HighContrastDark 后，active-theme 语义、精确 primary 像素、checked、
   input focus 和 click action 同时保持，且切换后可再次点击回 unchecked。
2. 无显式主题的旧 `ProgressBar` 真实 Android 位图与不调用 facade 的历史 Stack fixture 全量相等；
   包入显式自定义 `PixelTheme` 后，颜色与尺寸均按 token 改变，排除“兼容分支绕过主题”的假通过。

完整 connected 回归还覆盖 Host lifecycle、双 Host 焦点/IME、Android 键盘与手势、Accessibility、
Production Overlay、predictive back 和 multi-stack Bundle。

## Theme Showcase 复核

API 37 Demo 实际进入 `主题 → 主题与 Token → Theme Showcase`，逐一切换 Light、Dark、HC Dark、
HC Light、Custom；accessibility tree 的 `Active theme` 随预设更新，页面保留 `PRODUCTION COMPONENTS
(21)`、五个可点击/可聚焦预设及真实生产组件语义。

自动化 Demo 回归额外确认：

- 精确五个 preset 与完整 94 项 foundation token；
- 精确 21 个公开生产工厂、稳定 key 和语义标签；
- 21 个组件族 × 8 个状态的完整矩阵；
- Tab/Enter 主题切换与 focused/selected/disabled/error/loading 结构化语义；
- Checkbox `false→true`、Switch `true→false`、Slider `0.42→0.78`、Tabs `A→B`、Dropdown
  `A→B` 在切换 Dark 后全部保持。

人工复核截图：

```text
pixel-engine/build/reports/device/m5-1/theme-showcase-light.png
pixel-engine/build/reports/device/m5-1/theme-showcase-hc-dark.png
pixel-engine/build/reports/device/m5-1/theme-showcase-production-tabs-segmented-light.png
```

Light 与 HighContrastDark 截图均保持整数像素边界；HighContrastDark 下白色正文、青色 focus、绿色
selected 与普通描边表面在黑色画布上可直接区分。Custom 的精确色彩与扩展 Checkbox 几何由真实 Host
五主题 instrumentation 断言，不以截图主观判断代替。

## 最终自动化与发布门禁

最终执行：

```bash
./gradlew :pixel-engine:testDebugUnitTest --rerun-tasks --no-daemon
ANDROID_SERIAL=emulator-5554 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --no-daemon --no-parallel
./tools/pixel-release-check.sh
```

| 套件 | 测试数 | failure | error | skipped |
|---|---:|---:|---:|---:|
| `pixel-engine` JVM | 1086 | 0 | 0 | 0 |
| `pixel-demo` JVM | 25 | 0 | 0 | 0 |
| `app` JVM | 328 | 0 | 0 | 0 |
| API 37 connected | 28 | 0 | 0 | 0 |
| Python tooling | 33 | 0 | 0 | 0 |

`pixel-release-check.sh` 完整通过，覆盖 secret/产物扫描、backup contract、Public/Metalava/Binary API、
stable boundary、21/10 token coverage、KDoc、Lint、Release AAR、Demo/App Debug 与 Release、隔离
SDK/SPI/RouteEntry/旧二进制消费者、六场景性能、soak 和 `mkdocs build --strict`。六个性能场景
`overallPass=true`。

## API、文档与产物

- Public、Binary、Metalava 三份生成报告与已审阅 baseline 一致。
- KDoc：1042/1962，覆盖率 53.11%，高于 35% 门禁；M5-1 新增/修改声明均有必要说明。
- Release AAR：2,760,098 bytes。
- Release AAR SHA-256：
  `752539830860d0c61a1b20dabec6fffcbc8d5c40827afbb4c673a5f44c5113a6`。
- 外部 SDK consumer 与上一版本二进制 consumer 均通过当前 AAR 运行验证。

主要报告与产物：

```text
pixel-engine/build/reports/androidTests/connected/debug/
pixel-engine/build/outputs/androidTest-results/connected/debug/
pixel-engine/build/reports/api/
pixel-engine/build/reports/theme/
pixel-engine/build/reports/kdoc/kdoc-coverage.txt
pixel-engine/build/reports/compatibility/stable-api-boundary.json
pixel-engine/build/reports/perf/pixel-engine-render-smoke.txt
pixel-engine/build/outputs/aar/pixel-engine-release.aar
build/reports/compatibility/
build/reports/security/
```

## 遗留与边界

- M5-1 的 P0/P1 代码、测试、文档与验收遗留为零。
- `checkMetalavaReleasedCompatibility` 按既有 M1/M9 策略报告
  `SKIPPED/NO_RELEASED_BASELINE`：仓库尚无正式外部版本，因此这不是 skipped test 或未审阅 API
  差异；当前/旧二进制消费者已通过。首次正式发布前仍必须冻结 released signature。
- Android SDK tools 的 XML v3/v4 版本提示不影响任何编译、测试或产物验证。
- API 24/29/36、更多真实设备、RTL/字体缩放/窗口组合和持续性能趋势属于全局 M8/M6 矩阵，
  不以本次单一 API 37 结果替代。
