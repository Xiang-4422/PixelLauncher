# 像素 UI 引擎组件接入指南

这份文档的目标很直接：

> 让后续业务模块可以把 `pixel-engine` 当成一个可直接依赖的库来接入，而不是必须先读完全部源码。

当前指南基于仓库里的真实实现编写，适用于：

- `:pixel-engine`
- `:pixel-demo`

不适用于当前的 `:app`，因为 Launcher 还没有开始迁移到新框架。

## 1. 当前推荐依赖方式

如果新建一个业务模块，并希望直接搭建像素页面，当前只依赖 `:pixel-engine`：

```kotlin
dependencies {
    implementation(project(":pixel-engine"))
}
```

`pixel-engine` 内部已经包含：

- `com.purride.pixelcore`：底层像素能力
- `com.purride.pixelui`：UI runtime 与组件
- `com.purride.pixelengine`：模块 marker

页面层通常不应该直接长期操作 `PixelBuffer`。大多数业务页面只需要 `PixelHostView + Widget + 公开组件 + 状态控制器`。

源码物理目录按职责组织，但公开 Kotlin package 保持稳定。后续接入方不应该依赖源码文件名或 internal 目录结构。

### 当前模块基础要求

- `minSdk = 24`
- Java / Kotlin 目标版本为 11
- Android `compileSdk` 当前为 36

参考文件：

- [pixel-engine/build.gradle.kts](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/build.gradle.kts)
- [settings.gradle.kts](/Users/even/AndroidStudioProjects/PixelLauncher/settings.gradle.kts)

## 2. 最小接入链路

一个最小可运行页面，当前需要这几步：

1. 创建 `PixelHostView`
2. 准备 `PixelHostSetupConfig`
3. 调用 `createPixelHostSetup(...)`
4. 把 `rootView` 设给 Activity

### 最小示例

```kotlin
val hostView = PixelHostView(this)
val counter = ValueNotifier(0)
val hostSetup = createPixelHostSetup(
    context = this,
    hostView = hostView,
    config = PixelHostSetupConfig(
        profilePreference = PixelHostProfilePreference(
            dotSizePx = 8,
        ),
        textDirection = TextDirection.LTR,
        palette = PixelPalette.terminalGreen(),
        themeData = ThemeData(
            textStyle = TextStyle.Accent,
            buttonStyle = ButtonStyle.Accent,
        ),
        content = {
            ValueListenableBuilder(counter) { _, value ->
                Container(
                    padding = EdgeInsets.all(4),
                    child = Column(
                        spacing = 4,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        children = listOf(
                            Text("COUNT $value"),
                            SizedBox(
                                height = 14,
                                child = OutlinedButton(
                                    text = "CLICK",
                                    onPressed = { counter.value = value + 1 },
                                ),
                            ),
                        ),
                    ),
                )
            }
        },
    ),
)

setContentView(hostSetup.rootView)
```

关键类型：

- [PixelHostView.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui/host/PixelHostView.kt)
- [PixelHostSetup.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui/host/PixelHostSetup.kt)
- [PixelHostProfilePreference.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui/host/PixelHostProfilePreference.kt)
- [PixelThemeData.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelui/theme/PixelThemeData.kt)
- [ScreenProfileFactory.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore/screen/ScreenProfileFactory.kt)
- [PixelPalette.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore/theme/PixelPalette.kt)

## 3. 页面组织方式

当前推荐的页面组织方式：

- 页面状态放在宿主或页面层
- 组件树通过 `setContent(RootWidgetProvider)` 或 `PixelHostSetupConfig.content` 返回
- 交互优先通过 `State.setState`、`ValueNotifier`、控制器回调修改状态
- 需要局部上下文或局部短状态时，优先用 `Builder` / `StatefulBuilder`
- 需要把一个 `Listenable` 作为子树环境向下广播时，优先用 `InheritedNotifier`
- 重建应由 retained runtime 自动触发，不再把 `hostView.requestRender()` 当成页面主路径
- 线性布局里需要子项横向铺满时，优先用 `crossAxisAlignment = CrossAxisAlignment.STRETCH + SizedBox(height = ...)`

## 4. 常用公开 API

当前公开组件主路径：

- 内容：`Text`
- 容器与布局：`Container`、`Padding`、`SizedBox`、`Align`、`Center`、`Row`、`Column`、`Expanded`、`Stack`、`Positioned`
- 滚动：`PageView`、`PageViewBuilder`、`ListView`、`ListViewBuilder`、`ListViewSeparated`、`GridView`、`GridViewBuilder`、`CustomScrollView`、`SliverList`、`SliverPinnedHeader`、`SliverAppBar`、`SingleChildScrollView`、`Scrollbar`、`RefreshIndicator`
- 输入、表单与按钮：`TextField`、`Form`、`FormField`、`Validator`、`OutlinedButton`
- 状态：`StatefulWidget`、`StatefulBuilder`、`Builder`、`ValueNotifier`、`ValueListenableBuilder`、`InheritedNotifier`
- 控制器：`PageController`、`ScrollController`、`TextEditingController`、`PixelRefreshIndicatorController`

旧的 `PixelText`、`PixelButton`、`PixelList`、`PixelPager` 等节点式名称不再作为推荐页面 API。

当前 `Text` 已支持：

- 单行 `CLIP / ELLIPSIS`
- `softWrap = true` 时的字符级多行换行
- `maxLines` 限制
- 超出 `maxLines` 时最后一行 ellipsis

当前 `TextField` 已覆盖：

- placeholder ellipsis
- disabled/readOnly 视觉状态
- selection clamp
- 多行 caret / selection / composition 分行绘制
- 光标闪烁与最小 selection handle 拖拽

当前 `Form` 已覆盖：

- `FormController.validate()` / `reset()`
- `FormFieldState<T>.setValue()` / `errorText`
- `Validator<T>` 返回错误文本
- demo 和单测中的 TextField 表单校验组合

当前滚动能力已经覆盖 eager list、固定高度 lazy list、变高估算 lazy list、separated list、fixed-cell lazy grid、`CustomScrollView` + `SliverList` + pinned header + collapsible `SliverAppBar` v1、scrollbar 和最小 pull-to-refresh。`RefreshIndicator` 使用独立的 `PixelRefreshIndicatorState` / `PixelRefreshIndicatorController`，只暴露状态与回调，不绑定网络或异步任务。

## 5. 自定义底层能力

只有在明确做这些事情时，才建议直接触碰 `com.purride.pixelcore`：

- 自定义字形包或文本栅格器
- 自己写更底层的像素显示宿主
- 做不经过 UI 组件树的纯底层实验

关键类型：

- [PixelTextRasterizer.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore/font/PixelTextRasterizer.kt)
- [PixelGlyphPackAssetLoader.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore/font/PixelGlyphPackAssetLoader.kt)
- [PixelFontEngine.kt](/Users/even/AndroidStudioProjects/PixelLauncher/pixel-engine/src/main/kotlin/com/purride/pixelcore/font/PixelFontEngine.kt)

## 6. 新组件接入流程

新增组件时建议按这个顺序推进：

1. 先在 `pixel-engine/src/main/kotlin/com/purride/pixelui` 实现组件或 render object
2. 在 `pixel-engine/src/test/kotlin` 补覆盖
3. 在 `pixel-demo` 增加可视化验收场景
4. Demo 稳定后再讨论是否迁移 `:app`

如果要准备迁移 Launcher 的 Drawer，先在 `pixel-demo` 里打开 Drawer-like 验收页，确认搜索输入、长列表、空结果、滚动定位和点击反馈都符合预期，再开始碰 `:app`。

## 7. 验证命令

```bash
./gradlew :pixel-engine:testDebugUnitTest :pixel-engine:assembleDebug :pixel-demo:assembleDebug --no-daemon
```
