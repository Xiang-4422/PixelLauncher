# 快速开始

## 环境

- minSdk 24，compileSdk 36+
- AGP 8.10.1+；推荐 AGP 9.1.1
- JDK 17+；仓库开发使用 JDK 21

## 依赖

```kotlin
dependencies {
    implementation("com.purride:pixel-engine:1.0.0")
}
```

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

## 第一个测试

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

下一步阅读 [Host 接入](host-integration.md)、[主题与组件](theme-and-components.md) 和 [测试指南](testing.md)。
