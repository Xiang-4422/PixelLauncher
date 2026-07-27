# 主题与组件

`PixelThemeTokens` 是唯一的主题模型，包含颜色、排版、间距、尺寸、圆角、焦点、动画和 25 个
组件 token 族。`PixelTheme(tokens = ..., child = ...)` 是唯一的提供入口，`PixelTheme.of(context)` /
`PixelTheme.maybeOf(context)` 是唯一的查询入口。标准组件按八状态集合解析 token：enabled、
pressed、focused、hovered、selected、checked、error、loading。显式组件参数优先于主题。

简洁组件 API（如 `OutlinedButton`、`Checkbox`、`Dialog`、`Toast`、`Snackbar`、`ProgressBar`、
`Badge`、`Divider`、`AppScaffold`、`Scrollbar`、`RefreshIndicator`、`TextField`、`Slider`）与
state-aware 重载共用同一实现：可选视觉参数默认 `null`，由 token 解析；非 null 时保持调用方精确值。
无论是否挂载 `PixelTheme`，同一输入都构建完全相同的 widget 树；缺少 provider 时统一解析
`PixelThemeTokens.Default`。

```kotlin
val engine = PixelEngine.Builder()
    .theme(PixelThemeTokens.HighContrastDark)
    .build()
```

使用原则：

- 业务颜色使用语义 role，不在组件中复制固定 ARGB。
- 状态通过受控参数/controller 表达；主题切换不得重建业务状态。
- RTL、textScale、HighContrast、ReducedMotion 和 Touch/TV profile 都由 Host 环境进入同一树。
- 自定义组件先组合公开 widget；只有布局/绘制无法表达时才进入 RenderObject SPI。

表单同步字段校验使用 `Validator<T>`，整表校验使用 `FormValidator`；异步版本必须返回取消函数，
防止字段更新或页面释放后旧结果覆盖新状态。

组件参数、token 表、表单和状态优先级见[使用说明与 API 手册](../使用说明与API手册.md)。
