# 自定义 RenderObject SPI

先组合 `pixel-widgets`。只有需要自定义 layout、paint、hit test 或 semantics 时，才 opt-in
`com.purride.pixelui.advanced` 的真实公共 SPI；禁止 import `internal.*`。

实现清单：

1. Widget 的 `createRenderObject` 创建一次 RenderObject。
2. `updateRenderObject` 比较字段；尺寸变化只标 `markNeedsLayout`，颜色等绘制变化只标
   `markNeedsPaint`，无变化不标脏。
3. layout 必须通过 `PixelRenderConstraints` 约束 `PixelRenderSize`。
4. paint 使用 `PixelPaintContext` 和传入 offset/clip，热路径避免持久分配。
5. 可交互对象同时实现命中、键盘/焦点和 semantics；dispose 释放 listener、ticker 与 child owner。
6. 用独立 consumer、R8 release 和 `PixelTester` 验证，不依赖 friend path。

完整可编译 HollowSquare 示例见 [API 手册自定义 RenderObject](../使用说明与API手册.md)，
从旧 typealias 迁移见 [真实 SPI 迁移指南](../migrations/0.1.0-SNAPSHOT-real-render-spi.md)。
