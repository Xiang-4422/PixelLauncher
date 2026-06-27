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
- `pixel-demo/src/main/kotlin/com/purride/pixeldemo`
  - 新引擎的真实设备验收宿主，后续 engine 能力先在这里 gate
  - 已包含 `ENGINE_STABILITY_GATE`，聚合验证布局、lazy list、富文本、多行输入、主题状态和嵌套滚动

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

注意：`:app:testDebugUnitTest` 覆盖 ViewModel 投影、状态机迁移与视口计算、Drawer 索引/搜索、设置模型、工具格式化等。`pixel-engine` SDK 发布门禁见 [tools/pixel-release-check.sh](tools/pixel-release-check.sh)，该门禁只覆盖 `:pixel-engine`、`:pixel-demo`、Maven local dry-run 和文档站构建；Launcher 应用仍按上面的 app 命令单独验收。

SDK 发布补充检查：

```bash
./tools/pixel-sdk-consumer-smoke.sh
./tools/pixel-perf-smoke.sh
./tools/pixel-soak-test.sh
```

`tools/pixel-device-smoke.sh` 需要已连接 Android 设备或设置 `ADB_SERIAL`，因此不放入默认无设备发布门禁。`0.x` SDK 兼容策略和破坏性变更记录见 [CHANGELOG.md](CHANGELOG.md)。

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
- 不追求每个方法都写注释
- 只给复杂、关键、非直观的方法补说明
- 注释应说明职责、调用时机、关键约束和回退行为，不重复代码表意

## 9. 文档入口

项目级文档只保留当前仍会指导开发的文档：

- [项目总览](docs/项目总览.md)：项目现状、模块架构、engine 原理、demo 职责、Launcher 核心模块和开发路径
- [设计文档](docs/设计文档.md)：PixelLauncher 产品目标、app 侧架构、页面职责和开发约束
- [PixelLauncher UI 规范](docs/PixelLauncher%20UI规范.md)：Launcher 页面、字体、间距、控件和真机问题反馈规则
- [pixel-engine 架构与技术实现](pixel-engine/docs/架构与技术实现.md)：engine 内部架构、渲染管线、runtime 和维护规则
- [pixel-engine 使用说明与 API 手册](pixel-engine/docs/使用说明与API手册.md)：engine 接入方式、常见用法、组件与 API 速查

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
