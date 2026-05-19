# 快速入门

5 分钟从空 Activity 接入 pixel-engine，覆盖文字、按钮、列表、主题、状态。

## 前置

- Android Studio
- 最低 minSdk 24（Android 7.0）
- Kotlin 项目

## 步骤 1：引入模块

```kotlin
// settings.gradle.kts
include(":pixel-engine")
```

```kotlin
// 你的 app 模块 build.gradle.kts
dependencies {
    implementation(project(":pixel-engine"))
}
```

## 步骤 2：第一个像素界面

新建一个 Activity，用 `createPixelHostSetup` 创建宿主，把 widget 树通过 `content` lambda 传入：

```kotlin
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.purride.pixelui.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = {
                    Center(
                        child = Text("HELLO PIXEL"),
                    )
                },
            ),
        )
        setContentView(setup.rootView)
    }
}
```

跑起来。屏幕中央会显示像素点阵 `HELLO PIXEL`，背景使用默认调色板（绿色 CRT 风）。

## 步骤 3：组合布局

把单一 `Text` 换成竖向排列 + 按钮：

```kotlin
content = {
    Column(
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        children = listOf(
            Text("PIXEL UI"),
            SizedBox(height = 4),
            OutlinedButton(
                text = "TAP ME",
                onPressed = {
                    // 按钮点击回调
                },
            ),
        ),
    )
}
```

关键点：
- `Column` 主轴是垂直方向，`mainAxisAlignment = CENTER` 让 children 在垂直方向居中
- `SizedBox(height = 4)` 是 4 像素高的占位空白
- `OutlinedButton` 直接接受 `String`（不是 Widget），SDK 内部会用主题样式包装

## 步骤 4：状态与重建

`StatefulBuilder` 或 `ValueNotifier` + `ValueListenableBuilder` 都可以维护状态。下面用 `ValueNotifier`：

```kotlin
content = {
    val counter = ValueNotifier(0)

    Column(
        mainAxisAlignment = MainAxisAlignment.CENTER,
        crossAxisAlignment = CrossAxisAlignment.CENTER,
        children = listOf(
            ValueListenableBuilder(listenable = counter) { _, value ->
                Text("COUNT: $value")
            },
            SizedBox(height = 4),
            OutlinedButton(
                text = "+1",
                onPressed = { counter.value = counter.value + 1 },
            ),
        ),
    )
}
```

注意：`content` lambda 在每次重绘时都会被调用，因此 `ValueNotifier(0)` 应该提到 lambda **外面**，否则每次都重置：

```kotlin
class MainActivity : AppCompatActivity() {
    private val counter = ValueNotifier(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = {
                    Column(
                        // ...
                        children = listOf(
                            ValueListenableBuilder(listenable = counter) { _, value ->
                                Text("COUNT: $value")
                            },
                            OutlinedButton(
                                text = "+1",
                                onPressed = { counter.value = counter.value + 1 },
                            ),
                        ),
                    )
                },
            ),
        )
        setContentView(setup.rootView)
    }
}
```

## 步骤 5：可滚动列表

`ListViewBuilder` 提供按需构造的滚动列表。需要把 `controller`（行为）和 `state`（状态）一同传入：

```kotlin
private val listController = PixelListController()
private val listState = listController.create()

content = {
    ListViewBuilder(
        itemCount = 50,
        itemBuilder = { index ->
            Padding(
                padding = EdgeInsets.symmetric(horizontal = 4, vertical = 1),
                child = Text("ITEM #$index"),
            )
        },
        state = listState,
        controller = listController,
        itemExtent = 8,    // 长列表必传：单 item 像素高度，启用真正的 lazy 渲染
    )
}
```

> ⚠️ **关于 lazy 渲染**：`ListViewBuilder` **只有传 `itemExtent`（每 item 固定高度）时才走 lazy 路径**，仅构造可见区 + cacheExtent 范围内的 item。如果不传 `itemExtent`，会一次性构造全部 itemCount 个 widget，长列表（> 几十条）会卡顿。详见 [WIDGETS.md](WIDGETS.md#listviewbuilder)。

`PixelListController` 让你能编程式滚动（如 `controller.scrollItemIntoView(state, index)`）。详见 [STATE.md](STATE.md)。

## 步骤 6：自定义主题

默认主题用默认 token。要换肤，传入 `themeData`：

```kotlin
import com.purride.pixelcore.PixelTone

val customTheme = ThemeData().withTokens {
    copy(
        textTone = PixelTone.ACCENT,        // 文字用强调色
        accentBorderTone = PixelTone.ACCENT, // 按钮边框用强调色
    )
}

val setup = createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        themeData = customTheme,
        content = { /* ... */ },
    ),
)
```

详细的主题机制见 [THEME.md](THEME.md)。

## 步骤 7：调色板

换调色板（背景色 + ON / OFF / ACCENT 三色映射）：

```kotlin
import com.purride.pixelcore.PixelPalette
import com.purride.pixelcore.PixelTheme

val setup = createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        palette = PixelPalette.fromTheme(PixelTheme.AMBER_CRT),   // 复古琥珀
        // 还可选：GREEN_PHOSPHOR / ICE_LCD / MONO_LCD / NIGHT_MONO，或自定义构造
        content = { /* ... */ },
    ),
)
```

## 下一步

到这里你已经能搭出一个基本可交互的像素界面。要继续深入：

- 想看 SDK 提供的全部 widget → [WIDGETS.md](WIDGETS.md)
- 想搞清主题与样式覆盖的优先级 → [THEME.md](THEME.md)
- 想做翻页、文本输入、滚动到指定项 → [STATE.md](STATE.md)
- 想画一个自定义的 RenderObject → [EXTENDING.md](EXTENDING.md)

## 常见问题

**Q：为什么字体是固定的位图？能用 ttf 吗？**
A：默认走内置位图字体（5×7 像素），保证完全像素化。你可以通过自定义 `PixelTextRasterizer` 把 ttf 离散化到像素网格里——见 [EXTENDING.md](EXTENDING.md) 的「自定义字体栅格器」一节。

**Q：能嵌到普通 ViewGroup / Compose 里吗？**
A：`PixelHostView` 是 `android.view.View` 子类，可以放在任何 ViewGroup 里。Compose 中用 `AndroidView` 包装即可。

**Q：状态在 Activity 重建时怎么保留？**
A：把 `ValueNotifier` / Controller 放进 `ViewModel`，或用 `onSaveInstanceState` 序列化关键值。SDK 自身不持有跨配置存活状态。

**Q：能在子线程构建 widget 吗？**
A：不能。`setState` / `markNeedsXxx` 必须在主线程调用，与 Android UI 线程同一根。
