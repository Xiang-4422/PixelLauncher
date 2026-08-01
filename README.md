# PixelLauncher

PixelLauncher 是一个 Android 启动器应用，同时内置可复用的像素 UI 引擎。主工程维护七个 Gradle 模块：

<!-- architecture-contract:modules:start -->
- `:app`：Launcher 产品、页面状态和 Android 应用入口。
- `:pixel-engine`：像素渲染、组件、动画、路由、Android Host、测试 DSL 与诊断能力。
- `:showcase`：脱离 Launcher 独立运行的 Android Pixel Engine 与锁屏离线展示应用。
- `:showcase-desktop`：复用展示场景的 JVM/AWT 桌面宿主。
- `:lockscreen-module`：独立构建、默认惰性的像素锁屏 SystemUI 注入模块。
- `:pixel-design`：Launcher 与锁屏共享的产品主题目录。
- `:lockscreen-ui`：锁屏 APK 与离线预览共享的静态像素界面。
<!-- architecture-contract:modules:end -->

<!-- architecture-contract:dependencies:start -->
```text
:app -> :pixel-engine
:lockscreen-module -> :lockscreen-ui -> :pixel-design -> :pixel-engine
:app -> :pixel-design -> :pixel-engine
:showcase -> :pixel-engine
:showcase -> :lockscreen-ui -> :pixel-design -> :pixel-engine
:showcase-desktop --debug classes.jar--> :pixel-engine
:showcase-desktop --shared scene sources--> :showcase
```
<!-- architecture-contract:dependencies:end -->

三个 Android 应用模块通过 Gradle project 依赖消费引擎。`:showcase-desktop` 不声明 Android project
依赖：其 `compileKotlin` 先触发 `:pixel-engine:exportDebugClassesJar`（把 debug AAR 内的 classes.jar
解包到引擎自有的稳定路径 `pixel-engine/build/outputs/desktop-classes/classes.jar`），再消费该导出产物，
同时从 `:showcase` 共享 `DemoScene.kt` 与 `scenes/**`。
桌面入口由 `:showcase-desktop` 自有的
`showcase-desktop/src/main/kotlin/com/purride/pixelshowcase/desktop/DesktopShowcase.kt` 提供。SDK 对外仍只发布
`com.purride:pixel-engine:1.0.0` 一个坐标，避免消费者拼装多个内部产物。

## 环境

- JDK 21
- Android Studio / Android SDK 36
- 使用仓库自带的 Gradle Wrapper

```bash
./gradlew projects
./gradlew :app:assembleDebug
./gradlew :showcase:assembleDebug
./gradlew :showcase-desktop:classes
./gradlew :lockscreen-module:assembleDebug
./gradlew :pixel-engine:testDebugUnitTest
```

`./gradlew projects` 应只列出 `:app`、`:pixel-engine`、`:showcase`、`:showcase-desktop` 和
`:lockscreen-module`、`:pixel-design` 和 `:lockscreen-ui`。

## 运行

连接模拟器后执行：

```bash
./gradlew :app:installDebug
adb shell am start -n com.purride.pixellauncherv2.debug/com.purride.pixellauncherv2.app.MainActivity
```

Android Studio 直接选择 `app` 配置即可编译和运行。

如需运行独立的引擎展示应用，可选择 `showcase` 配置，或执行：

```bash
./gradlew :showcase:installDebug
adb shell am start -n com.purride.pixelshowcase/.ShowcaseActivity
```

Showcase 首页的 `LOCKSCREEN` 入口使用固定离线状态预览真实锁屏宿主，可切换八个主题、日夜模式、
电量/充电状态、横竖画布和四类透明叠加测试背景；当前阶段无需连接目标设备。

桌面展示宿主使用本机 JVM/AWT，可执行：

```bash
./gradlew :showcase-desktop:run
```

## SDK 接入

同仓库工程：

```kotlin
dependencies {
    implementation(project(":pixel-engine"))
}
```

发布消费者：

```kotlin
dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
}
```

当前不提供 Compose wrapper。引擎使用自己的 retained tree 和 Android View Host；这样可以避免 Compose 编译器向全部公开类注入 ABI 字段，也避免给非 Compose 消费者增加依赖。

## 常用验证

```bash
# 单元测试、API/ABI 和 Android Lint
./gradlew check

# 编译 instrumentation 测试
./gradlew :pixel-engine:assembleDebugAndroidTest

# 模拟器 instrumentation
./gradlew :pixel-engine:connectedDebugAndroidTest

# 单坐标发布物校验
bash tools/pixel-publication-validation.sh

# 完整非性能发布门禁
bash tools/pixel-release-check.sh
```

性能 benchmark 和长期 soak 不属于当前工程门禁，后续应在独立性能目标中重新设计，而不是恢复已经删除的 benchmark 模块。

## 目录

```text
app/                         Launcher 产品模块
pixel-engine/                像素引擎 SDK
  api/                       Kotlin/API 与 JVM ABI 基线
  config/                    产物预算和发布元数据
  docs/                      SDK 文档
  src/main/                  引擎实现
  src/test/                  JVM 测试
  src/androidTest/           Android Host 测试
showcase/                    Pixel Engine 独立展示应用
showcase-desktop/            复用 Showcase 场景的 JVM/AWT 桌面宿主
compatibility/               隔离 Maven 消费者与当前公开 API 验证工程
docs/                        项目级文档
tools/                       CI、发布、供应链与文档工具
```

## 文档入口

- [项目总览](docs/项目总览.md)
- [Pixel Engine 文档入口](pixel-engine/README.md)
- [SDK 首页](pixel-engine/docs/index.md)
- [SDK Changelog](pixel-engine/docs/CHANGELOG.md)
- [快速开始](pixel-engine/docs/guides/quickstart.md)
- [使用说明与 API 手册](pixel-engine/docs/使用说明与API手册.md)
- [发布与维护](pixel-engine/docs/发布与维护.md)

## 提交约定

提交信息沿用中文 Conventional Commits，例如：

```text
feat: 增加像素组件能力
fix: 修复路由状态恢复
refactor: 收敛引擎模块结构
docs: 更新 SDK 接入说明
```
