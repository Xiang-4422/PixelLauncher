# 主题与样式

pixel-engine 的主题系统遵循一条核心原则：

> **tokens 是唯一真相来源**

设计上：一个 `ThemeData` 实例由两部分组成——一组扁平的 `tokens`（语义化颜色 / 间距等小单位），和一组可选的具体 `style` 覆盖。绝大多数场景下你只需要改 tokens，所有 widget 自动跟随；只有在"某一类组件视觉显著不同"时才显式传 style。

## 调色板与色阶（PixelTone）

像素 UI 的视觉表达基于三个色阶（`PixelTone`）：

| Tone | 含义 | 默认在终端绿调色板下 |
|---|---|---|
| `OFF` | 关 / 背景 | 深绿 |
| `ON` | 开 / 主前景 | 中绿 |
| `ACCENT` | 强调 | 亮绿 |

调色板（`PixelPalette`）把这三个 tone 映射到具体 RGB。SDK 提供若干预设：

```kotlin
// 5 个内置主题预设：
PixelPalette.fromTheme(PixelTheme.GREEN_PHOSPHOR)   // 默认 CRT 绿
PixelPalette.fromTheme(PixelTheme.AMBER_CRT)        // 复古琥珀
PixelPalette.fromTheme(PixelTheme.ICE_LCD)          // 冰蓝 LCD
PixelPalette.fromTheme(PixelTheme.MONO_LCD)         // 纯单色 LCD
PixelPalette.fromTheme(PixelTheme.NIGHT_MONO)       // 夜间单色

// 便利别名：
PixelPalette.terminalGreen()                        // = fromTheme(GREEN_PHOSPHOR)

// 完全自定义：
PixelPalette(
    backgroundColor = 0xFF000000.toInt(),
    pixelOffColor   = 0xFF222222.toInt(),
    pixelOnColor    = 0xFF66CC33.toInt(),
    accentColor     = 0xFFFFD000.toInt(),
)
```

`fromTheme` 还接受 `isLowBattery: Boolean` 入参，会自动降低 accent 亮度以省电。

调色板在宿主层注入：

```kotlin
createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(palette = PixelPalette.amberCrt()),
)
```

## ThemeData 与 Tokens

```kotlin
data class PixelThemeTokens(
    val textTone: PixelTone = PixelTone.ON,
    val accentTone: PixelTone = PixelTone.ACCENT,
    val mutedTone: PixelTone = PixelTone.OFF,
    val surfaceTone: PixelTone = PixelTone.OFF,
    val borderTone: PixelTone? = PixelTone.ON,
    val accentBorderTone: PixelTone? = PixelTone.ACCENT,
    val selectedBorderTone: PixelTone? = PixelTone.ACCENT,
    val pressedBorderTone: PixelTone? = PixelTone.ACCENT,
    val focusedBorderTone: PixelTone? = PixelTone.ACCENT,
    val disabledBorderTone: PixelTone? = PixelTone.ON,
    val readOnlyBorderTone: PixelTone? = PixelTone.ACCENT,
    val inputPadding: Int = 2,
)
```

每个 token 对应一个语义。比如 `accentBorderTone` 决定 `OutlinedButton(style = Accent)` 的边框 tone，`disabledBorderTone` 决定禁用按钮 / 输入框的边框 tone。

## 三种使用模式

### 模式 1：全局换肤（推荐）

只改 tokens，全部 widget 跟着变：

```kotlin
val darkTheme = ThemeData().withTokens {
    copy(
        textTone = PixelTone.ACCENT,
        borderTone = PixelTone.ACCENT,
        disabledBorderTone = PixelTone.OFF,
    )
}

createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        themeData = darkTheme,
        content = { /* ... */ },
    ),
)
```

`withTokens` 是 `PixelThemeData` 的扩展函数，等价于 `theme.copy(tokens = theme.tokens.copy(...))`，但更直观。

### 模式 2：单 widget 显式覆盖

当某个 widget 必须与全局视觉对比时，直接传 style：

```kotlin
OutlinedButton(
    text = "DANGER",
    onPressed = { /* ... */ },
    style = ButtonStyle(
        fillTone = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        textStyle = TextStyle(tone = PixelTone.ACCENT),
    ),
)
```

这个按钮使用传入的 style，**不再走 tokens 推导**。其它按钮仍然按当前主题 token 派生样式。

### 模式 3：混合

主题 token 设置基础色，单点 widget 覆盖特殊样式。这是大多数生产 App 的做法。

```kotlin
// 全局：text 跟着 accent token
val theme = ThemeData().withTokens { copy(textTone = PixelTone.ACCENT) }

// 某一行需要静音色
Row(children = listOf(
    Text("USERNAME"),
    Text("(optional)", style = TextStyle(tone = PixelTone.OFF)),
))
```

## ThemeData 完整字段

`ThemeData` 实例除了 `tokens`，还可携带可选的具体 style：

```kotlin
data class PixelThemeData(
    val tokens: PixelThemeTokens = PixelThemeTokens.Default,

    // 文字
    val textStyle: PixelTextStyle = PixelTextStyle.Default,
    val accentTextStyle: PixelTextStyle = PixelTextStyle.Accent,

    // 按钮
    val buttonStyle: PixelButtonStyle = PixelButtonStyle.Default,
    val accentButtonStyle: PixelButtonStyle = PixelButtonStyle.Accent,
    val disabledButtonStyle: PixelButtonStyle = PixelButtonStyle.Disabled,

    // 输入框
    val textFieldStyle: PixelTextFieldStyle = PixelTextFieldStyle.Default,
    val readOnlyTextFieldStyle: PixelTextFieldStyle = PixelTextFieldStyle.ReadOnly,

    // 容器
    val containerStyle: PixelContainerStyle = PixelContainerStyle.Default,
)
```

只要某字段保持默认值，SDK 就会从 `tokens` 派生它；任何字段被显式覆盖（赋为非默认对象），就直接使用覆盖值。

## 局部覆盖 vs 全局

要让"子树以下"用一组不同主题，用 `Theme` widget 包裹：

```kotlin
Theme(
    data = darkTheme,
    child = Column(/* 这里以下都用 darkTheme */),
)

// 兄弟 widget 仍然用宿主层注入的全局主题
```

`Theme.of(context)` 取当前生效的主题（从最近的 `Theme` 祖先取，或宿主默认）。

## resolveXxxStyle 优先级表

SDK 在每个 widget 内部用 `resolveXxxStyle()` 计算实际样式。规则：

| 情况 | 返回 |
|---|---|
| widget 调用方显式传入了 style 参数 | 直接使用 widget 参数 |
| 主题中显式覆盖了对应 style 字段 | 直接返回覆盖值，tokens 不参与 |
| 都没覆盖 | 从当前主题的 `tokens` 派生 |

优先级（高→低）：`widget 参数 > 主题 style 字段 > 主题 tokens 派生`。

## 完整示例：自定义主题

```kotlin
// 1. 基于 tokens 全局换琥珀色调
val amberTheme = ThemeData().withTokens {
    copy(
        textTone = PixelTone.ON,
        accentTone = PixelTone.ACCENT,
        borderTone = PixelTone.ON,
        accentBorderTone = PixelTone.ACCENT,
    )
}

// 2. 个别按钮风格不一致：实心填充
val filledButtonTheme = amberTheme.copy(
    accentButtonStyle = PixelButtonStyle(
        fillTone = PixelTone.ACCENT,
        borderTone = null,
        textStyle = PixelTextStyle(tone = PixelTone.OFF),
    ),
)

createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        palette = PixelPalette.amberCrt(),
        themeData = filledButtonTheme,
        content = { /* ... */ },
    ),
)
```

## 与调色板的关系

- **palette** —— 设备级 RGB 映射，决定每个 tone 在屏幕上具体长啥颜色
- **theme tokens** —— 应用级 tone 选择，决定"文字用哪个 tone"等语义
- 两者解耦：换 palette 不需要改 theme，反之亦然

```
ThemeData.tokens.textTone = ON
         ↓
RenderText 知道用 ON tone
         ↓
PixelPalette.amberCrt().onColor = 琥珀色
         ↓
屏幕显示琥珀色像素
```

## 接下来

- 不同 widget 怎么用样式参数 → [WIDGETS.md](WIDGETS.md)
- 内置 `PixelPalette` 都有哪些预设 → 看 `PixelPalette` 类的 companion object
- 自定义字体（点阵样式） → [EXTENDING.md](EXTENDING.md)
