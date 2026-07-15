# PixelLauncher

PixelLauncher 是一个面向 Android 手机的像素风桌面启动器。  
这个仓库的目标不是做传统图标桌面，而是通过统一的像素 UI、极简信息呈现和状态驱动页面，减少用户为了“确认状态”而频繁打开 App。

这份 `README` 作为项目入口和运行指南。需要完整了解项目事实、模块职责、运行链路和开发边界时，先读 [项目总览](docs/项目总览.md)。

## 1. 项目定位

当前产品方向是：

- 用像素风格建立统一、克制、精致的设备体验
- 优先减少手机使用时间，而不是增加信息消费
- `Home` 负责显示必须看的信息
- `Drawer` 负责快速定位并启动 App
- `Idle` 负责待机展示，不是系统锁屏替代
- `Settings` 是单页完整设置系统

当前优先适配对象：

- Android 手机
- 竖屏
- 优先考虑 `1:1` 比例设备

## 2. 技术架构结论

这个项目不是 Compose，也不是多 Activity / 多 Fragment 页面架构。  
当前真实主干是：

- 单 `Activity`
- 单一 `LauncherState`（由 `LauncherViewModel` 持有为唯一状态源，渲染时投影为 `LauncherUiState`）
- pixel-engine widget 渲染链路（`LauncherRootHost` + `PixelHostView`）
- 状态机驱动页面模式与输入处理

主运行时链路是：

1. `MainActivity` 初始化仓库、字体、`LauncherViewModel` 和 `LauncherRootHost`
2. `data` 层从系统服务、权限能力或网络中读取真实数据
3. `LauncherStateTransitions`（纯函数）把输入和数据收敛成新的 `LauncherState`，由 `LauncherViewModel` 持有为唯一状态源（`MainActivity` 的 `state` 委托至此）
4. `MainActivity` 从 `LauncherViewModel` 读取最新状态，投影成 `LauncherUiState` 后推送给 `LauncherRootHost`
5. `LauncherRootHost` 按页面模式构建 pixel-engine 的 widget 树，经 `PixelHostView` 绘制到屏幕

这意味着：

- 当前 UI 不是 Android 控件树，而是 pixel-engine 的 Widget 树
- 页面由 `ui/screen` 下的 widget 构建函数声明式描述
- 后续开发优先遵循“数据 → ViewModel → UiState → widget”单向数据流，而不是直接堆逻辑到 `MainActivity`

## 3. 代码分层

主代码目录：

- `app/src/main/kotlin/com/purride/pixellauncherv2/app`
  - 运行时编排入口
  - 核心文件：[MainActivity.kt](app/src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt)
- `app/src/main/kotlin/com/purride/pixellauncherv2/launcher`
  - 状态机、纯函数状态迁移、抽屉与设置页模型、设置枚举（主题 / 字体 / 充电特效）、统一渲染宿主
  - 核心文件：[LauncherState.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt)
  - 核心文件：[LauncherStateTransitions.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt)
  - 核心文件：[LauncherRootHost.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt)（按页面模式构建 pixel-engine widget 树）
- `app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel`
  - 持有唯一 `LauncherState` 状态源（`MainActivity` 的 `state` 委托至此）；`toLauncherUiState()` 在渲染时投影出 `LauncherUiState`
  - 核心文件：[LauncherViewModel.kt](app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherViewModel.kt)、[LauncherUiState.kt](app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiState.kt)
- `app/src/main/kotlin/com/purride/pixellauncherv2/ui`
  - 基于 pixel-engine 的页面与组件：`screen`（每个页面一个 widget 构建函数）、`widget`（Header / BatteryDivider / 设置控件）、`theme`（`LauncherTheme` 颜色方案）、`text`（字形光栅化）
  - 核心文件：[HomeScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt)、[SettingsScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SettingsScreen.kt)、[DrawerScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/DrawerScreen.kt)
- `app/src/main/kotlin/com/purride/pixellauncherv2/render`
  - 显示 / 文本 / 动画原语：屏幕分辨率档位、像素字形度量、充电动画节拍
  - 核心文件：[ScreenProfile.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/ScreenProfile.kt)、[PixelFontEngine.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/PixelFontEngine.kt)（承载 `GlyphStyle` 度量）
- `app/src/main/kotlin/com/purride/pixellauncherv2/data`
  - 应用列表、设备状态、Usage Access、通信、定位、降雨预测、设置持久化
- `app/src/main/kotlin/com/purride/pixellauncherv2/system`
  - 启动 App、窗口模式、重力映射等系统封装
- `app/src/main/kotlin/com/purride/pixellauncherv2/util`
  - 时间文本、标签格式化、节流等轻量工具
- `pixel-engine/src/main/kotlin/com/purride`
  - 新像素 UI 引擎，当前作为单一 Gradle 模块维护
  - `pixelcore` package 承接像素缓冲、字体、几何、调色板和轴向原语
  - `pixelui` package 承接 Widget/runtime、布局、输入、分页、列表、滚动和宿主桥接
  - 完整 `PixelThemeTokens` 覆盖 light/dark、高对比度、自定义主题、25 个组件族和统一八状态契约
  - TextField 使用固定 Unicode 17 grapheme 边界，并在 API 24+ 默认 InputConnection 上保护 selection、composition、删除和补充平面文本
  - 字体以完整 Unicode scalar `Int` 查找，Text/RichText/TextField 使用 grapheme cluster 与固定 Unicode 17 UBA 共享 wrap、ellipsis、caret、hit-test、selection 和 Accessibility 几何
- `pixel-demo/src/main/kotlin/com/purride/pixeldemo`
  - 新引擎的真实设备验收宿主，后续 engine 能力先在这里 gate
  - 已包含 `ENGINE_STABILITY_GATE`，聚合验证布局、lazy list、富文本、多行输入、主题状态和嵌套滚动
  - `Theme Showcase` 可视化 94 个基础 token 与 25×8（200 格）生产组件状态矩阵，并支持键盘切换五套主题
  - `Adaptive & Localization` 综合场景可实时切换 locale、RTL、textScale、高对比度、reduce motion、density/refresh 和 hinge，并验证真实 IME、Unicode 输入与双独立导航栈状态保持

## 4. 当前页面与实现入口

### Home

- 模式：`LauncherMode.HOME`
- 页面：[HomeScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/HomeScreen.kt)
- 宿主：[LauncherRootHost.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt)

当前 Home 会显示：

- 日期
- 闹钟
- 动态信息行：`CALL / SMS / RAIN`
- 屏幕使用时间和打开次数
- 终端状态文案
- 上下文卡片

### Drawer

- 模式：`LauncherMode.APP_DRAWER`
- 页面：[DrawerScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/DrawerScreen.kt)（pixel-engine 渲染，由 `LauncherRootHost` 装配，搜索框在共享状态栏）
- 搜索与排序：[DrawerSearchSupport.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/DrawerSearchSupport.kt)
- 视口行数：[AppListLayout.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/AppListLayout.kt) + [DrawerListGeometry.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/DrawerListGeometry.kt)

当前 Drawer 的实现特征：

- 顶部对齐文本列表
- 状态栏承担搜索入口
- ASCII 搜索输入
- 拼音/英文检索支持
- 右侧隐藏快速定位区
- 点击应用名直接启动

### Settings

- 模式：`LauncherMode.SETTINGS`
- 模型：[SettingsMenuModel.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/SettingsMenuModel.kt)
- 页面：[SettingsScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/SettingsScreen.kt)

当前 Settings 的实现特征：

- 单页设置
- 无可见选中高亮
- 点击直接生效
- 超出视口后使用和抽屉一致的文本列表基座滚动浏览

### Idle

- 模式：`LauncherMode.IDLE`
- 页面：[IdleScreen.kt](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen/IdleScreen.kt)

当前 Idle 是简化待机页，不是系统锁屏替代。

## 5. 当前关键数据源

当前已经接入的关键真实数据包括：

- 应用列表：
  - [PackageManagerAppRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/PackageManagerAppRepository.kt)
- 电池与充电状态：
  - [DeviceStatusRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/DeviceStatusRepository.kt)
- 下一次闹钟：
  - [NextAlarmRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/NextAlarmRepository.kt)
- 屏幕使用时间与打开次数：
  - [ScreenUsageRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/ScreenUsageRepository.kt)
- 未接来电与未读短信：
  - [CommunicationStatusRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/CommunicationStatusRepository.kt)
- 定位与降雨提醒：
  - [DeviceLocationRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/DeviceLocationRepository.kt)
  - [RainForecastRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/RainForecastRepository.kt)
- 外观与交互偏好持久化：
  - [FontSettingsRepository.kt](app/src/main/kotlin/com/purride/pixellauncherv2/data/FontSettingsRepository.kt)

## 6. 权限与系统能力

当前 `AndroidManifest.xml` 中已经声明并使用的关键能力包括：

- `INTERNET`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`
- `READ_CALL_LOG`
- `READ_SMS`
- `PACKAGE_USAGE_STATS`

需要注意：

- Usage Access 不是普通运行时权限，必须跳到系统设置页开启
- 定位用于未来 `6` 小时降雨提醒
- 通话记录和短信权限用于 Home 的动态信息行

Manifest 入口：

- [AndroidManifest.xml](app/src/main/AndroidManifest.xml)

## 7. 开发与运行

### 环境要求

这个仓库提交了 Gradle Wrapper，clone 后不需要本机预装 Gradle。  
但 Android 工程仍然需要本机具备 JDK 和 Android SDK，不能把这些机器级路径提交进仓库。

推荐环境：

- Android Studio，或独立安装的 Android SDK
- JDK 21，Android Studio 自带的 JBR 21 也可以
- Android SDK Platform `android-36`
- Android SDK Platform `android-36.1`
- 已接受 Android SDK licenses

命令行环境需要能找到 Java：

```bash
java -version
```

如果系统提示找不到 Java，可以临时使用 Android Studio 自带 JBR：

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
```

`local.properties` 不应提交到 Git。首次用 Android Studio 打开项目时它会自动生成；命令行构建时也可以通过 `ANDROID_HOME` 或 `ANDROID_SDK_ROOT` 指向本机 SDK。

如果需要在命令行执行 `adb` / `installDebug`，还需要把 Android SDK 的 `platform-tools` 加入 `PATH`。macOS 默认 SDK 位置通常是：

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

如果 SDK 平台缺失，可以用 Android Studio 的 SDK Manager 安装，也可以在已有 `sdkmanager` 的环境里执行：

```bash
sdkmanager "platforms;android-36" "platforms;android-36.1"
sdkmanager --licenses
```

构建配置摘要：

- `minSdk = 24`
- `targetSdk = 36`
- Java source/target compatibility = `11`
- Gradle runtime JDK = `21` 推荐
- release `applicationId = com.purride.pixellauncherv2`
- debug `applicationId = com.purride.pixellauncherv2.debug`

Pixel Engine SDK 的最低/推荐 AGP、Gradle、Kotlin、compileSdk 与 JDK 组合以
[支持矩阵](pixel-engine/docs/support-matrix.md)为准；发布签名、SBOM、依赖锁定和 OSV 流程见
[发布元数据与供应链](pixel-engine/docs/supply-chain.md)。

配置入口：

- [app/build.gradle.kts](app/build.gradle.kts)
- [settings.gradle.kts](settings.gradle.kts)

常用命令：

```bash
./gradlew assembleDebug
./gradlew :app:compileDebugKotlin
./gradlew installDebug
adb shell am start -W -n com.purride.pixellauncherv2/.app.MainActivity
```

测试命令：

```bash
./gradlew testDebugUnitTest
```

注意：`:app:testDebugUnitTest` 覆盖 ViewModel 投影、状态机迁移与视口计算、Drawer 索引/搜索、设置模型、工具格式化等。完整 SDK 发布门禁见 [tools/pixel-release-check.sh](tools/pixel-release-check.sh)；它同时覆盖 `:pixel-engine`、`:pixel-demo`、Launcher app、API/安全/性能门禁、隔离 file-Maven 消费者（含 RouteEntry 类型负例与旧二进制运行）以及文档站构建，不使用 `mavenLocal()` 作为兼容证据。

SDK 发布补充检查：

```bash
./tools/pixel-sdk-consumer-smoke.sh
./tools/pixel-docs-consumer-smoke.sh
./tools/pixel-perf-smoke.sh
./tools/pixel-soak-test.sh
```

`pixel-soak-test.sh` 是 PR/本地发布门禁使用的精确 10,000 次快速资源压力测试。30–60 分钟真实
Android Host 长跑使用专用模拟器入口，必须显式绑定设备；默认执行 30 分钟并每分钟采集一次 PSS：

```bash
PIXEL_BENCHMARK_SERIAL=emulator-5554 bash tools/pixel-device-soak.sh
```

接线调试可以同时设置 `PIXEL_SOAK_ALLOW_SHORT=1` 与较短的
`PIXEL_SOAK_DURATION_SECONDS`，但机器报告会保持 `qualifiesForGoal=false`，不能作为发布证据。

### SDK CI 与干净环境复现

GitHub Actions 使用 JDK 21、Python 3.12、`requirements-docs.txt` 中固定的 MkDocs 1.6.1，
并显式安装 `platforms;android-36` 与 `platforms;android-36.1`。本地首次复现前需要安装相同平台和
文档依赖：

```bash
sdkmanager "platform-tools" "platforms;android-36" "platforms;android-36.1"
python3 -m pip install --user -r requirements-docs.txt
python3 tools/prepare_mkdocs_docs.py
python3 -m mkdocs build --strict
```

PR/主分支工作流 `.github/workflows/pixel-engine.yml` 把门禁拆为七个相互独立的 required job；对应
本地入口如下。多个会清理 `build` 的入口应在同一 worktree 中顺序执行，不要并行：

```bash
bash tools/pixel-ci-fast.sh
bash tools/pixel-ci-api.sh
bash tools/pixel-ci-lint.sh
bash tools/pixel-publication-validation.sh
bash tools/pixel-ci-consumer.sh
bash tools/pixel-ci-performance.sh
```

设备 job 使用 API 29 独立模拟器。启动 AVD 后必须显式指定 emulator serial，避免连接了实体手机时误选
设备：

```bash
rm -rf pixel-engine/build/outputs/androidTest-results \
  pixel-engine/build/reports/androidTests \
  pixel-engine/build/outputs/connected_android_test_additional_output
ANDROID_SERIAL=emulator-5556 ./gradlew \
  :pixel-engine:connectedDebugAndroidTest \
  --no-build-cache \
  --no-daemon
```

`Required pixel-engine gate` 聚合 fast、API/ABI/documentation、Lint、API 29 instrumentation、
consumer、publication 和 performance；任一上游结果为 `failure`、`cancelled` 或 `skipped` 都会失败。
仓库分支保护应只把这个聚合 job 设为必需状态检查，避免矩阵 job 名称变化造成配置漂移。

定时工作流 `.github/workflows/pixel-engine-nightly.yml` 每天执行 API 24/29/36 instrumentation、API 36
Macrobenchmark、精确 10,000 次生命周期压力和 API 36 模拟器 30 分钟设备 soak。长跑逐轮执行真实
启动、列表、文本、动画、页面转场和 Overlay，并要求 callback/listener/ticker/retained tree 终态归零、
PSS 首尾趋势有界。两个工作流不配置自动重试或 `continue-on-error`；Gradle dependency
cache 只缓存下载与 Gradle User Home，关键任务使用 `clean`、`--no-build-cache`、隔离临时工程、稳定
runId 或主动删除旧报告，不能用旧构建产物产生假绿。测试 XML/HTML、API diff、golden diff、trace、
benchmark、publication JSON 和隔离 Maven 仓库在成功或失败时都会作为 Actions artifact 上传。

发布前的一站式无凭据复核仍是：

```bash
bash tools/pixel-release-check.sh
```

该入口聚合安全/备份、单测、API/ABI/Metalava/KDoc、Lint、Release、SPI/RouteEntry/旧二进制、真实
Maven 消费者矩阵、只依据文档完成 Host/路由/SPI/测试的全新消费者、性能、soak、Baseline Profile 和
严格 MkDocs。它不包含真实中央仓库发布、签名密钥、
GitHub 分支保护写入等需要外部凭据或管理员权限的操作。

`tools/pixel-device-smoke.sh` 需要已连接 Android 设备或设置 `ADB_SERIAL`，因此不放入默认无设备发布门禁。`0.x` SDK 兼容策略和破坏性变更记录见 [CHANGELOG.md](CHANGELOG.md)，当前候选核对见 [pixel-engine/docs/1.0.0发布清单.md](pixel-engine/docs/1.0.0发布清单.md)，长期 SemVer / 弃用 / migration guide 规则见 [pixel-engine/docs/发布与兼容策略.md](pixel-engine/docs/发布与兼容策略.md)。

### Android Studio 运行

用 Android Studio 打开仓库根目录，等待 Gradle Sync 完成后，Run 配置下拉框应出现：

- `app`：来自 [.run/app.run.xml](.run/app.run.xml)，会构建 `:app` 并直接启动 `com.purride.pixellauncherv2.app.MainActivity`
- `pixel-demo`：来自 [.run/pixel-demo.run.xml](.run/pixel-demo.run.xml)，会构建 `:pixel-demo` 并启动 `com.purride.pixeldemo.DemoActivity`

debug 包使用 `applicationIdSuffix = ".debug"`，因此会安装为 `com.purride.pixellauncherv2.debug`。这样设备上即使已有不同签名的正式包 `com.purride.pixellauncherv2`，Android Studio 也可以直接安装并运行 debug 包。

如果 Run 按钮不可用，优先检查：

- Project SDK / Gradle JDK 是否是 JDK 21 或 Android Studio 自带 JBR 21
- SDK Manager 是否已经安装 `Android API 36` 和 `Android API 36.1`
- 是否打开的是仓库根目录，而不是 `app` 子目录
- 是否已经完成 Gradle Sync

如果 Android Studio 提示 `Please select Android SDK`：

1. 打开 `File > Project Structure > Project`
2. 将 `SDK` 选择为已安装的 Android SDK，例如 `Android API 36.1, extension level 20 Platform`
3. `Gradle JDK` 仍然选择 JDK 21 或 Android Studio 自带 JBR 21
4. Apply 后重新执行 Gradle Sync

## 8. 工程规范与修改原则

接手这个项目时，建议遵循这些硬约束：

- 不要把主界面改写成 Compose
- 不要让渲染器直接读取系统服务
- 不要绕过 `LauncherState` 直接在 UI 层塞临时状态
- 不要把大型页面逻辑继续堆到 `MainActivity`
- 不要把 Android 原生控件树直接混进像素主界面
- 新的数据能力优先进入 `data`
- 新的页面语义优先进入 `LauncherState` 与 `LauncherStateTransitions`
- 新的布局规则优先进入 `launcher/*Layout`
- 新的页面与绘制优先进入 `ui/screen` 与 `ui/widget`（pixel-engine widget）

注释约定：

- 优先写高质量 `KDoc`
- 所有类、变量和方法都需要有与职责相称的必要注释；简单声明可以使用一句精确说明
- public / protected API 必须使用 `KDoc` 说明适用的参数、返回值、生命周期和兼容约束
- 内部状态、缓存、所有权转移和非直观局部变量必须说明用途与不变量
- 注释应说明职责、调用时机、关键约束和回退行为，不重复代码表意

## 9. 文档入口

项目级文档只保留当前仍会指导开发的文档：

- [项目总览](docs/项目总览.md)：项目现状、模块架构、engine 原理、demo 职责、Launcher 核心模块和开发路径
- [设计文档](docs/设计文档.md)：PixelLauncher 产品目标、app 侧架构、页面职责和开发约束
- [PixelLauncher UI 规范](docs/PixelLauncher%20UI规范.md)：Launcher 页面、字体、间距、控件和真机问题反馈规则
- [Pixel Engine 1.0 SDK 首页](pixel-engine/docs/index.md)：模块选择、质量承诺和 Quickstart/Host/主题/路由/资源/SPI/测试/性能/迁移入口
- [pixel-engine 架构与技术实现](pixel-engine/docs/架构与技术实现.md)：engine 内部架构、渲染管线、runtime 和维护规则
- [pixel-engine 使用说明与 API 手册](pixel-engine/docs/使用说明与API手册.md)：engine 接入方式、常见用法、组件与 API 速查
- [pixel-engine API 分层与高级 SPI](pixel-engine/docs/API分层与高级SPI.md)：stable / experimental / testing / debug / internal 边界、兼容承诺与自定义 RenderObject 示例
- [pixel-engine 1.0 高价值组件迁移指南](pixel-engine/docs/migrations/1.0.0-high-value-components.md)：Radio、IconButton、表单装饰、单选集合、Slidable 与多栈导航组件的迁移契约
- [pixel-engine 1.0 Unicode 文本编辑迁移指南](pixel-engine/docs/migrations/1.0.0-unicode-text-editing.md)：UTF-16 offset、grapheme 编辑、InputConnection、自定义 Host 与补充平面输入契约
- [pixel-engine 1.0 字体、cluster 与 Bidi 迁移指南](pixel-engine/docs/migrations/1.0.0-codepoint-cluster-bidi-text.md)：code-point 字体 SPI、整 cluster rasterizer、固定 Unicode 17 Bidi、fallback 与共享几何契约
- [pixel-engine 1.0 自适应 Host 与 Viewport 迁移指南](pixel-engine/docs/migrations/1.0.0-adaptive-host-viewport.md)：正交 viewport、profile policy、Android capability、raw inset、textScale、RTL 与 AdaptiveBuilder 契约
- [pixel-engine 1.0 Engine Services 迁移指南](pixel-engine/docs/migrations/1.0.0-engine-services.md)：实例 Builder、服务注入、多 Host 隔离、聚焦 capability、类型安全 action 与结构化错误契约
- [pixel-engine 1.0 Compose Host 互操作迁移指南](pixel-engine/docs/migrations/1.0.0-compose-host.md)：可选 wrapper、生命周期/Insets/输入/无障碍传递、saved state 与非 Compose 依赖隔离
- [pixel-engine 1.0 完整帧诊断迁移指南](pixel-engine/docs/migrations/1.0.0-frame-diagnostics.md)：build/layout/paint/Canvas submit、allocation/GC、cache、丢帧归因和默认关闭成本边界
- [pixel-engine 1.0 SDK Goal](pixel-engine/docs/1.0-GOAL.md)：从当前 0.x 状态推进到可对外发布 1.0 SDK 的工作包、依赖顺序和验收标准

根目录 `README.md` 只作为项目入口和运行指南。详细 SDK 文档集中在 `pixel-engine/docs`。

## 10. 建议的接手顺序

如果你是第一次接手这个项目，建议按下面顺序进入代码：

1. 先读本 `README`
2. 再读 [项目总览](docs/项目总览.md)
3. 读 [设计文档](docs/设计文档.md)
4. 涉及 UI 时读 [PixelLauncher UI 规范](docs/PixelLauncher%20UI规范.md)
5. 看 [MainActivity.kt](app/src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt)，建立运行时主链路认知
6. 看 [LauncherViewModel.kt](app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherViewModel.kt) / [LauncherUiState.kt](app/src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiState.kt) 和 [LauncherStateTransitions.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt)
7. 看 [LauncherRootHost.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherRootHost.kt) 和 [ui/screen](app/src/main/kotlin/com/purride/pixellauncherv2/ui/screen) 下的页面构建函数
8. 如果涉及 engine，再读 [pixel-engine 架构与技术实现](pixel-engine/docs/架构与技术实现.md) 和 [pixel-engine 使用说明与 API 手册](pixel-engine/docs/使用说明与API手册.md)

## 11. 当前状态一句话总结

这个项目目前具备完整的像素 launcher 技术底盘：主界面由 pixel-engine 渲染，`LauncherViewModel` 持有唯一 `LauncherState` 状态源，经 `LauncherRootHost` → `PixelHostView` 绘制；SMS 编排（`SmsController`）与 Drawer 页面（`DrawerScreen`）各自独立，`:app` 接入 CI 门禁（编译 / lint / 单测）。当前主线是 `:app` 收尾与发布准备（行高一致性、lint 清理、配置变更 / insets / 暗色 / 签名）；`pixel-engine` SDK 路线作为并行长期线。
