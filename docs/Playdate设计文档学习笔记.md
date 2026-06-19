# Playdate 设计文档学习笔记

日期：2026-06-20

原文：[Designing for Playdate](https://help.play.date/developer/designing-for-playdate/)

状态：已融合进 `docs/PixelLauncher UI规范.md`，本文仅作为来源索引和讨论记录，不作为独立 UI 规范。

说明：官方文档没有标注可全文转载的许可，因此本文不镜像原文内容，只保存章节结构、设计要点转译和 PixelLauncher 可借鉴方向。讨论时以官方原文链接为准。

## 1. 文档结构

官方文档主要包含这些章节：

- Screen
  - Sprites
  - Tiles
  - Text
  - 2x Graphics
  - Transforming bitmap images
  - Dither patterns
  - Dither flashing
  - Refresh rate
  - Screen accessibility
- Sound
  - Sound accessibility
- Crank
  - Using buttons and the crank together
  - Crank accessibility
- Buttons
  - Button accessibility
- Accelerometer
- User Interface
  - System font
  - Onscreen keyboard
  - Crank indicator
  - QR codes
- Launcher
  - Launcher card
  - Wrapping paper pattern
  - Menu
  - Menu Image
- Performance
- Playdate Simulator vs. Playdate hardware
  - Simulator performance
  - Simulator appearance

## 2. 最值得学习的原则

### 2.1 低分辨率不等于小

Playdate 的关键判断是：低分辨率 UI 仍然要优先保证可读性。它不是把所有东西缩小，而是在有限像素里选择合适的文本尺寸、图形尺寸和信息密度。

对 PixelLauncher 的启发：

- Home 只放重要信息，不把多个状态硬塞在一屏。
- Drawer 列表只显示 App 名，别名、命中原因等信息不能挤占主标题空间。
- Settings 行只显示标题和当前值，长说明放详情页。

### 2.2 真实设备比模拟器重要

官方文档强调需要在真实硬件上判断文本、图形和性能。Playdate 的模拟器外观和实际设备观感并不完全一致。

对 PixelLauncher 的启发：

- 任何 UI 修改都要真机截图验收。
- 文字裁切、padding、状态栏高度、两栏压缩不能只靠桌面预览判断。
- connected device 截图应成为 UI 修改流程的一部分。

### 2.3 字体要按可读性设计

Playdate 文档用 cap height、stroke thickness、hinting 等概念说明字体可读性。核心不是字体风格，而是文字在真实尺寸下是否清楚。

对 PixelLauncher 的启发：

- 中文字体不能只看字形是否像素化，还要看 7x7 / 8x8 cell 下是否能读。
- UI 布局必须按 font metrics 和 cell 尺寸测量，不手动猜文字高度。
- 文本边框内 padding 不能依赖字形自身留白。

### 2.4 少色和抖动要克制

Playdate 是 1-bit 屏幕，文档讨论了 dither patterns 和 flashing。重点是：像素纹理不是越多越好，错误的抖动会带来噪声和闪烁。

对 PixelLauncher 的启发：

- 不用大面积噪声纹理做背景。
- 不做复杂渐变或密集装饰。
- selected / pressed 状态使用稳定的反色、边框或亮度变化，不做闪烁纹理。

### 2.5 UI 要清晰、快速、适度 playful

Playdate 系统 UI 的目标是清晰、可读、快速使用，并在合适位置有一点趣味。它没有提供大量通用 widget，而是鼓励开发者做贴合内容的 UI。

对 PixelLauncher 的启发：

- 我们不需要复杂组件库，先做少量高质量基础组件。
- 组件要服务 Launcher 场景，而不是复刻游戏 UI。
- Pixel 风格可以有个性，但不能牺牲短信、设置、搜索这些工具型流程的效率。

## 3. 可直接转化为 PixelLauncher 规则

### Screen -> Android 屏幕

- Playdate 关注实际屏幕尺寸和像素密度。
- PixelLauncher 要关注真实 Android 状态栏高度、字体像素尺寸和触摸区域。
- 不要用固定屏幕比例猜 UI 尺寸。

### Text -> 字体和文本布局

- 文本是 PixelLauncher 的核心信息载体。
- 中文和英文必须分别定义 cell、baseline、行高和 fallback 策略。
- 文本容器必须可测量，不能硬写高度。

### Buttons -> 触控和反馈

- Playdate 的 A/B 按钮有确认/返回语义。
- PixelLauncher 的触控按钮也要有稳定语义：进入、返回、保存、取消。
- 按钮 selected / pressed 状态不能改变尺寸。

### User Interface -> 系统工具感

- Playdate UI 少，但清晰。
- PixelLauncher 也应该少组件、高一致性。
- Settings、Drawer、SMS 的列表行应共享同一套 spacing、selected、disabled、loading 规则。

### System menu -> 轻量浮层菜单

Playdate 系统菜单值得重点学习：

- 它像系统层浮出来，不打断当前页面上下文。
- 选中项使用整行反色，状态非常明确。
- 每行只显示短标题，不放解释文字。
- 行高、padding、文字位置稳定，没有贴边和裁切。
- 同一菜单里可以混合普通动作和行内状态，例如 volume 右侧的像素音量条。
- 菜单底部可以保留时间、电量等系统信息，但不喧宾夺主。

对 PixelLauncher 的启发：

- Drawer 长按 App 菜单可以采用这种形式，用于重命名和编辑别名。
- Settings 的短选项可以用这种菜单，而不是进入多级页面。
- SMS 消息长按可以用这种菜单承载复制、验证码复制、重发。
- 菜单应是轻量浮层，不做厚边框、复杂阴影或游戏化装饰。

### Launcher -> 第一印象

- Playdate 的 Launcher card 是游戏的第一印象。
- PixelLauncher 的 Home 是手机亮屏后的第一印象。
- Home 不应展示所有功能，只展示当前最重要的信息。

### Performance -> 少重绘

- Playdate 强调减少不必要 redraw。
- PixelLauncher 的 pixel-engine UI 也应避免无意义重建和动画泛滥。
- Idle、Home、Drawer 搜索要优先稳定、低功耗。

## 4. 不适合照搬的部分

- Crank 相关交互不适用于 Android Launcher。
- Playdate 的 1-bit 视觉不应完整复制，我们可以保留少色但不必只用黑白。
- Playdate 字体不解决中文问题。
- Playdate 游戏 Launcher card 不等价于 Android App Drawer。
- Playdate 的系统菜单结构不适合直接搬到 Settings。
- Playdate 菜单的视觉原则可以学习，但不能把掌机按钮/曲柄交互照搬到 Android 触控。

## 5. 对现有 UI 规范的补充建议

建议同步补进 `docs/PixelLauncher UI规范.md`：

- “低分辨率不等于小”：优先可读性。
- “真实设备优先”：UI 修改必须真机截图。
- “文字优先”：所有组件先通过最长中文文案验收。
- “状态稳定”：selected / pressed / loading 不改变布局尺寸。
- “少组件”：基础组件先覆盖 TextButton、ListRow、Switch、Input、Dialog、TwoColumn。

## 6. 讨论问题

后续可以围绕这些问题讨论：

1. PixelLauncher 是否采用接近 Playdate 的少色系统，还是保留当前多主题能力？
2. 中文字体是否继续 8x8 cell，还是为可读性扩展到更大的 cell？
3. Settings 是否统一成 Playdate 式简洁列表，而不是多装饰面板？
4. Drawer 搜索结果是否只显示标题，命中原因隐藏到长按或详情？
5. Idle 是否采用更强的 Playdate 风格：大字、低信息、少动画？
6. pixel-engine 是否需要提供官方 `PixelListRow`、`PixelTextButton`、`PixelSwitch`、`PixelDialog` 组件？

## 7. 参考链接

- [Designing for Playdate](https://help.play.date/developer/designing-for-playdate/)
- [Playdate SDK License](https://play.date/dev/sdk-license/)
- [Inside Playdate](https://sdk.play.date/inside-playdate)
