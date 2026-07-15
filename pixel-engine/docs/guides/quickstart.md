# 快速开始

## 环境

- JDK 17。
- Android Gradle Plugin 8.10.1+；推荐 9.1.1。
- compileSdk 36+；推荐 36.1。minSdk 为 24。
- Kotlin 2.2.10。AGP 9 使用内置 Kotlin。

## 依赖

Android View Host 只需要最小生产坐标；`<pixelEngineVersion>` 在正式发布后替换为 `1.0.0`：

```kotlin
dependencies {
    implementation("com.purride:pixel-android:<pixelEngineVersion>")
    testImplementation("com.purride:pixel-testing:<pixelEngineVersion>")
}
```

需要 Compose 页面托管 Pixel tree 时额外增加 `pixel-compose`。旧工程可以继续依赖聚合
`pixel-engine`，但新工程应使用能力模块，避免把 testing/debug 带入生产图。

## 最小 Activity

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var pixelHost: PixelHostSetup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pixelHost = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = { Text("HELLO PIXEL") },
            ),
        )
        setContentView(pixelHost.rootView)
    }

    override fun onDestroy() {
        pixelHost.dispose()
        super.onDestroy()
    }
}
```

`PixelHostSetup` 已装配 `PixelHostView`、隐藏文本输入桥、Back dispatcher 和根 `FrameLayout`。
Fragment 应在 `onDestroyView` 调用 `dispose()`；普通暂时 detach 只暂停，不代表 owner 终态。

## 第一个 JVM 测试

```kotlin
@Test
fun buttonCanBeTappedOffscreen() {
    var clicked = false
    val tester = PixelTester()
    tester.pumpWidget(
        widget = OutlinedButton(text = "OK", onPressed = { clicked = true }),
        logicalWidth = 48,
        logicalHeight = 20,
    )
    tester.tap(find.byText("OK"))
    assertTrue(clicked)
    tester.dispose()
}
```

下一步阅读 [Host 接入](host-integration.md)、[主题与组件](theme-and-components.md) 和
[测试指南](testing.md)。完整 Gradle/Activity/Compose 示例见
[使用说明与 API 手册](../使用说明与API手册.md)。
