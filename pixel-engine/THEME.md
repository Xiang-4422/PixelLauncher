# pixel-engine Theme System

## tokens Are the Single Source of Truth

`PixelThemeData` holds one field that drives all styling:

```kotlin
data class PixelThemeData(
    val tokens: PixelThemeTokens = PixelThemeTokens.Default,
    // explicit style overrides — use sparingly (see below)
    val textStyle: PixelTextStyle? = null,
    val buttonStyle: PixelButtonStyle? = null,
    ...
)
```

`PixelThemeTokens` is a flat data class of scalar design tokens — colors (expressed as `PixelTone` palette indices), border widths, font size categories, and so on. Every built-in widget calls `theme.resolveXxxStyle()` internally, which derives a concrete style from the active tokens. If you only change tokens, every widget in the tree follows automatically.

---

## Three Usage Modes

### 1. Global re-skin — only touch tokens

```kotlin
val darkTheme = PixelThemeData().withTokens {
    copy(
        textTone          = PixelTone.ACCENT,
        accentBorderTone  = PixelTone.ACCENT,
        disabledBorderTone = PixelTone.OFF,
    )
}

PixelThemeProvider(themeData = darkTheme) {
    MyScreen()
}
```

All widgets in `MyScreen` pick up the new tones. No per-widget changes required.

### 2. Single-widget override — pass an explicit style

When one widget needs to look different from the global theme, pass an explicit style directly to the widget:

```kotlin
PixelButton(
    text = "Danger",
    buttonStyle = PixelButtonStyle(
        fillTone   = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        textStyle  = PixelTextStyle(tone = PixelTone.ACCENT),
    ),
    onPressed = { ... },
)
```

The widget uses the passed style as-is and does not call `resolveButtonStyle()`. Token changes do not affect it.

### 3. Mixed — tokens for the base, explicit for exceptions

Set global tokens for the brand colors, then override specific widgets where the brand rule does not apply. This is the most common production pattern.

---

## Walking Through `accentUiTheme()`

`pixel-demo` contains a reference theme implementation in `DemoScenes.accentUiTheme()`:

```kotlin
ThemeData(
    textStyle = TextStyle.Accent,                    // all text → ACCENT tone
    buttonStyle = ButtonStyle.Accent,                 // buttons → ACCENT preset
    disabledButtonStyle = ButtonStyle(               // disabled state: custom
        fillTone   = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        textStyle  = TextStyle(tone = PixelTone.OFF),
    ),
    textFieldStyle = TextFieldStyle(                 // inputs → ACCENT border
        borderTone        = PixelTone.ACCENT,
        focusedBorderTone = PixelTone.ACCENT,
        textStyle         = TextStyle.Accent,
        placeholderStyle  = TextStyle.Default,
    ),
    readOnlyTextFieldStyle = TextFieldStyle(...),    // read-only inherits same
    containerStyle = ContainerStyle(                 // containers: OFF fill, ACCENT border
        fillTone  = PixelTone.OFF,
        borderTone = PixelTone.ACCENT,
        alignment = Alignment.CENTER,
    ),
)
```

Notice that this demo theme uses **explicit style objects** on every field rather than tokens. That is intentional for a demo that illustrates the override mechanism. In a production app you would encode the ACCENT brand color in tokens and let `resolveXxxStyle()` derive all of these automatically.

---

## `withTokens` Helper

`withTokens` is an extension function that returns a copy of `PixelThemeData` with a modified token block. It leaves all explicit style overrides untouched:

```kotlin
// Change one token, keep everything else
val theme = baseTheme.withTokens { copy(textTone = PixelTone.ACCENT) }

// Chain multiple token changes
val theme = baseTheme
    .withTokens { copy(textTone = PixelTone.ACCENT) }
    .withTokens { copy(accentBorderTone = PixelTone.ON) }
```

Use `withTokens` when inheriting from a base theme; use `copy(tokens = ...)` when replacing the entire token set.

---

## `resolveXxxStyle` Priority Table

| Situation | What `resolveXxxStyle()` returns |
|---|---|
| No explicit style field set (`null`) | Style derived from `tokens` |
| Explicit style field set (non-`null`) | The explicit style, **tokens ignored** |
| `withTokens { ... }` applied after explicit style | Explicit style still wins; only new calls that don't have an explicit override use the new tokens |

In code:

```kotlin
internal fun resolveTextStyle(): PixelTextStyle =
    textStyle ?: PixelTextStyle(tone = tokens.textTone, ...)
```

The explicit field is checked first (`?: ` short-circuit). If it is non-null, derivation is bypassed entirely.
