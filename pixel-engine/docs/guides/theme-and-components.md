# 主题与组件

`PixelThemeTokens` 是 1.0 的主题事实源，包含颜色、排版、间距、尺寸、圆角、焦点、动画和 25 个
组件 token 族。标准组件按八状态集合解析 token：enabled、pressed、focused、hovered、selected、
checked、error、loading。显式组件参数优先于主题，旧 facade 只保留兼容语义。

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

组件参数、token 表和状态优先级见 [使用说明与 API 手册](../使用说明与API手册.md)，历史迁移见
[主题 Token 与组件状态](../migrations/1.0.0-theme-tokens-and-component-states.md)。
