# PixelLauncher

PixelLauncher 是一个 Android 启动器应用，同时内置可复用的像素 UI 引擎。主工程只保留两个 Gradle 模块：

- `app`：Launcher 产品、页面状态和 Android 应用入口。
- `pixel-engine`：像素渲染、组件、动画、路由、Android Host、测试 DSL 与诊断能力。

模块间只有一条依赖：`app -> pixel-engine`。SDK 对外也只发布
`com.purride:pixel-engine:1.0.0` 一个坐标，避免消费者拼装多个内部产物。

## 环境

- JDK 21
- Android Studio / Android SDK 36
- 使用仓库自带的 Gradle Wrapper

```bash
./gradlew projects
./gradlew :app:assembleDebug
./gradlew :pixel-engine:testDebugUnitTest
```

`./gradlew projects` 应只列出 `:app` 和 `:pixel-engine`。

## 运行

连接模拟器后执行：

```bash
./gradlew :app:installDebug
adb shell am start -n com.purride.pixellauncherv2/.MainActivity
```

Android Studio 直接选择 `app` 配置即可编译和运行。

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
compatibility/               隔离 Maven 消费者与旧二进制兼容夹具
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
