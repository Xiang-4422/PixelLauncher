# PixelLauncher

PixelLauncher 是一个面向 Android 手机的像素风桌面启动器。  
这个仓库的目标不是做传统图标桌面，而是通过统一的像素 UI、极简信息呈现和状态驱动页面，减少用户为了“确认状态”而频繁打开 App。

这份 `README` 作为项目的文档中枢，帮助新接手的工程师快速建立完整心智模型：项目是什么、架构怎么组织、代码入口在哪、如何运行、应该先读哪些文档，以及哪些边界不要轻易打破。

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
- 单一 `LauncherState`
- 自定义像素渲染链路
- 状态机驱动页面模式与输入处理

主运行时链路是：

1. `MainActivity` 初始化仓库、字体、渲染器、显示视图和输入代理
2. `data` 层从系统服务、权限能力或网络中读取真实数据
3. `LauncherStateTransitions` 把输入和数据收敛成新的 `LauncherState`
4. `PixelRenderer` 根据状态和屏幕参数生成 `PixelBuffer`
5. `PixelDisplayView` 把逻辑像素缓冲绘制到屏幕

这意味着：

- 当前 UI 不是 Android 控件树
- 页面布局主要靠布局指标和像素绘制完成
- 后续开发优先遵循“状态 + 数据 + 渲染”分层，而不是直接堆逻辑到 `MainActivity`

## 3. 代码分层

主代码目录：

- `app/src/main/kotlin/com/purride/pixellauncherv2/app`
  - 运行时编排入口
  - 核心文件：[MainActivity.kt](app/src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt)
- `app/src/main/kotlin/com/purride/pixellauncherv2/launcher`
  - 状态机、页面布局、抽屉与设置页模型、搜索与列表交互
  - 核心文件：[LauncherState.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt)
  - 核心文件：[LauncherStateTransitions.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt)
- `app/src/main/kotlin/com/purride/pixellauncherv2/render`
  - 像素字体、像素缓冲、页面绘制、主题、分辨率、动画
  - 核心文件：[PixelRenderer.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/PixelRenderer.kt)
  - 核心文件：[PixelDisplayView.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/PixelDisplayView.kt)
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
  - 新引擎的真实设备验收宿主

## 4. 当前页面与实现入口

### Home

- 模式：`LauncherMode.HOME`
- 布局：[HomeLayout.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/HomeLayout.kt)
- 绘制：[PixelRenderer.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/PixelRenderer.kt)

当前 Home 会显示：

- 日期
- 闹钟
- 动态信息行：`CALL / SMS / RAIN`
- 屏幕使用时间和打开次数
- 终端状态文案
- 上下文卡片

### Drawer

- 模式：`LauncherMode.APP_DRAWER`
- 布局：[AppListLayout.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/AppListLayout.kt)
- 搜索与排序：[DrawerSearchSupport.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/DrawerSearchSupport.kt)
- 列表基座：[TextListSupport.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/TextListSupport.kt)
- 滚动物理：[DrawerVerticalScrollController.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/DrawerVerticalScrollController.kt)

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
- 布局：[SettingsMenuLayout.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/SettingsMenuLayout.kt)

当前 Settings 的实现特征：

- 单页设置
- 无可见选中高亮
- 点击直接生效
- 超出视口后使用和抽屉一致的文本列表基座滚动浏览

### Idle

- 模式：`LauncherMode.IDLE`
- 待机物理：[IdleFluidEngine.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/IdleFluidEngine.kt)

当前 Idle 主要仍是待机页和动效页，不是系统锁屏替代。

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

注意：当前 `testDebugUnitTest` 仍有若干断言失败，不能作为 clone 后的绿色验收线；基础编译验收以 `./gradlew assembleDebug` 为准。

### Android Studio 运行

用 Android Studio 打开仓库根目录，等待 Gradle Sync 完成后，Run 配置下拉框应出现 `app`。  
这个配置来自 [.run/app.run.xml](.run/app.run.xml)，会构建 `:app` 并直接启动 `com.purride.pixellauncherv2.app.MainActivity`。

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
- 新的绘制优先进入 `PixelRenderer`

注释约定：

- 优先写高质量 `KDoc`
- 不追求每个方法都写注释
- 只给复杂、关键、非直观的方法补说明
- 注释应说明职责、调用时机、关键约束和回退行为，不重复代码表意

## 9. 文档入口

设计与技术文档都在 `docs/design` 下。

当前文档已经按目录分类：

- 索引入口：
  - [docs/design/README.md](docs/design/README.md)
- 当前真实实现：
  - [docs/design/architecture/技术实现总览.md](docs/design/architecture/技术实现总览.md)
  - [docs/design/architecture/渲染实现原理.md](docs/design/architecture/渲染实现原理.md)
- 像素引擎：
  - [docs/design/engine/像素UI引擎架构与实施计划.md](docs/design/engine/像素UI引擎架构与实施计划.md)
  - [docs/design/engine/像素UI引擎当前进度.md](docs/design/engine/像素UI引擎当前进度.md)
  - [docs/design/engine/像素UI引擎组件接入指南.md](docs/design/engine/像素UI引擎组件接入指南.md)
- 产品目标与模块设计：
  - [docs/design/product/产品总规约.md](docs/design/product/产品总规约.md)
  - [docs/design/product/主页设计.md](docs/design/product/主页设计.md)
  - [docs/design/product/应用抽屉设计.md](docs/design/product/应用抽屉设计.md)
  - [docs/design/product/待机页设计.md](docs/design/product/待机页设计.md)
  - [docs/design/product/设置信息架构.md](docs/design/product/设置信息架构.md)

推荐阅读顺序：

1. [docs/design/README.md](docs/design/README.md)
2. [docs/design/architecture/技术实现总览.md](docs/design/architecture/技术实现总览.md)
3. 如果涉及渲染、性能或 Idle 动画，读 [docs/design/architecture/渲染实现原理.md](docs/design/architecture/渲染实现原理.md)
4. 如果涉及像素引擎重构或新页面接入，读 [docs/design/engine/像素UI引擎架构与实施计划.md](docs/design/engine/像素UI引擎架构与实施计划.md)
5. 再读 [docs/design/product/产品总规约.md](docs/design/product/产品总规约.md)

模块文档：

- [主页设计.md](docs/design/product/主页设计.md)
- [应用抽屉设计.md](docs/design/product/应用抽屉设计.md)
- [待机页设计.md](docs/design/product/待机页设计.md)
- [设置信息架构.md](docs/design/product/设置信息架构.md)

## 10. 建议的接手顺序

如果你是第一次接手这个项目，建议按下面顺序进入代码：

1. 先读本 `README`
2. 再读技术总览
3. 看 [MainActivity.kt](app/src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt)，建立运行时主链路认知
4. 看 [LauncherState.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt) 和 [LauncherStateTransitions.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherStateTransitions.kt)
5. 看 [TextListSupport.kt](app/src/main/kotlin/com/purride/pixellauncherv2/launcher/TextListSupport.kt) 和 [PixelRenderer.kt](app/src/main/kotlin/com/purride/pixellauncherv2/render/PixelRenderer.kt)
6. 最后按需求进入具体 `Repository`、`Layout` 和模块文档

## 11. 当前状态一句话总结

这个项目目前已经具备比较完整的像素 launcher 技术底盘：  
状态机、像素渲染、文本列表基座、Home 真实数据接入和单页设置系统都已经建立，但 `MainActivity` 仍然承担较多编排职责，后续开发要继续往“状态 + 数据 + 渲染”分层收敛。
