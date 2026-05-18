# Extending pixel-engine

This document shows how to add new capabilities to pixel-engine without modifying SDK internals. All extension points are in the `com.purride.pixelui.advanced` and `com.purride.pixelui.gesture` packages.

---

## 1. Custom RenderObject

Use `PixelLeafRenderObjectWidget` when you want to own the full layout + paint cycle for a self-contained piece of content.

### Full example — `SpinningSquareWidget`

From `pixel-demo/.../customrender/SpinningSquareWidget.kt`:

```kotlin
import com.purride.pixelui.advanced.*
import com.purride.pixelcore.PixelTone

/**
 * Widget: immutable configuration.
 * Create/update the render object from here.
 */
class SpinningSquareWidget(
    val side: Int,
    val tone: PixelTone,
    override val key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {

    override fun createRenderObject(context: BuildContext): PixelRenderObject =
        SpinningSquareRender(side = side, tone = tone)

    override fun updateRenderObject(context: BuildContext, renderObject: PixelRenderObject) {
        // Cast to the concrete type, then update fields.
        (renderObject as SpinningSquareRender).update(side = side, tone = tone)
    }
}

/**
 * RenderObject: owns the pixels.
 */
internal class SpinningSquareRender(
    private var side: Int,
    private var tone: PixelTone,
) : PixelRenderBox() {

    override fun layout(constraints: PixelRenderConstraints) {
        // Clamp to constraints; record our size.
        size = PixelRenderSize(
            width  = constraints.constrainWidth(side),
            height = constraints.constrainHeight(side),
        )
    }

    override fun paint(context: PixelPaintContext, offsetX: Int, offsetY: Int) {
        // Draw a hollow border rectangle.
        for (y in 0 until size.height) {
            for (x in 0 until size.width) {
                val edge = x == 0 || y == 0 || x == size.width - 1 || y == size.height - 1
                if (edge) context.buffer.setPixel(offsetX + x, offsetY + y, tone.value)
            }
        }
    }

    /** Called by updateRenderObject. Marks dirty only when something actually changed. */
    fun update(side: Int, tone: PixelTone) {
        val sizeChanged = this.side != side
        val toneChanged = this.tone != tone
        if (!sizeChanged && !toneChanged) return     // equality short-circuit — avoid redundant passes
        this.side = side
        this.tone = tone
        if (sizeChanged) markNeedsLayout()           // layout changed → paint will follow automatically
        markNeedsPaint()
    }
}
```

### Key rules

| Rule | Why |
|---|---|
| Always equality-check before `markNeedsLayout` / `markNeedsPaint` | Skips redundant passes. Forgetting this means every rebuild triggers a full repaint even when nothing changed. |
| `markNeedsLayout()` implies repaint | Calling it is enough; do not call both unless you only changed paint state. |
| Keep `paint()` side-effect-free | It may be skipped or replayed. Write only to `context.buffer`. |
| Do not allocate in `paint()` | Use `PixelBufferPool` if you need a scratch buffer; `context.buffer` is already pooled. |

### Available base classes

| Type alias (in `pixelui.advanced`) | Use when |
|---|---|
| `PixelLeafRenderObjectWidget` | Widget with no children, owns its own paint |
| `PixelSingleChildRenderObjectWidget` | Widget that wraps one child (e.g., `Padding`) |
| `PixelMultiChildRenderObjectWidget` | Widget that manages multiple children (e.g., `Row`) |
| `PixelRenderBox` | RenderObject with a 2-D size; most widgets use this |
| `PixelPaintContext` | Passed to `paint()`; gives access to the `PixelBuffer` |
| `PixelRenderConstraints` | Incoming tight/loose constraints from the parent |
| `PixelRenderSize` | The size your `layout()` commits to |

---

## 2. Custom Gesture Policy

Override `PagerGesturePolicy` or `NestedScrollGesturePolicy` to tune swipe recognition thresholds. Then inject your instance via `PixelHostSetupConfig`.

### `PagerGesturePolicy` — example from GESTURE_TUNING demo

```kotlin
import com.purride.pixelui.gesture.PagerGesturePolicy
import com.purride.pixelui.gesture.ScrollAxis

/**
 * A policy that reads axisBias from a live provider, so the user
 * can tune it at runtime with a slider.
 */
class TunablePagerGesturePolicy(
    private val axisBiasProvider: () -> Float,
) : PagerGesturePolicy() {

    override fun shouldStartDrag(
        axis: ScrollAxis,
        deltaX: Float,
        deltaY: Float,
        touchSlopPx: Float,
        axisBias: Float,
    ): Boolean = super.shouldStartDrag(
        axis, deltaX, deltaY, touchSlopPx,
        axisBias = axisBiasProvider(),   // swap in the live value
    )
}
```

### Injection

```kotlin
val policy = TunablePagerGesturePolicy { axisBiasState.value }

pixelHostView.setup(
    PixelHostSetupConfig(
        pagerGesturePolicy = policy,
        // nestedScrollPolicy = ...,
        // scrollPhysics = ...,
    )
)
```

`NestedScrollGesturePolicy` follows the same pattern — extend it and override `shouldChildScrollBeforeParent(...)`.

---

## 3. Custom Text Rasterizer

`PixelTextRasterizer` is the interface that converts a Unicode code point + style into a glyph bitmap.

```kotlin
interface PixelTextRasterizer {
    fun rasterize(codePoint: Int, style: PixelGlyphStyle): PixelGlyph
}
```

### Using a built-in font

```kotlin
// Load a glyph-pack asset (pixel-engine ships several)
val rasterizer = PixelGlyphPackAssetLoader(assets).load("fonts/mono5x7.pgp")
pixelHostView.textRasterizer = rasterizer
```

### Using PixelBitmapFont

For a minimal ASCII-only font embedded in code:

```kotlin
val compact = PixelBitmapFont(
    glyphWidth  = 4,
    glyphHeight = 5,
    charSet     = PixelBitmapFont.ASCII_PRINTABLE,
    bitmapData  = MY_BITMAP_BYTES,
)
pixelHostView.textRasterizer = compact
```

Switching `hostView.textRasterizer` at runtime rebuilds the font engine and re-renders the current frame. The `PixelBufferPool` ensures the transition does not cause extra allocations.

---

## 4. Custom FrameScheduler

`PixelFrameScheduler` decouples frame pacing from `Choreographer`. Useful for:
- Test harnesses that drive frames manually
- Record-and-replay tooling
- Custom VSync sources

```kotlin
interface PixelFrameScheduler {
    fun scheduleFrame(callback: (frameTimeNanos: Long) -> Unit)
}
```

The SDK ships `PixelFrameScheduler.Default` (backed by `Choreographer`) and `ManualFrameScheduler` for tests:

```kotlin
// Production — already the default; shown for clarity
PixelHostSetupConfig(frameScheduler = PixelFrameScheduler.Default)

// Tests
val manual = ManualFrameScheduler()
val config = PixelHostSetupConfig(frameScheduler = manual)
// ...
manual.tick(frameTimeNanos = 16_666_666L)  // advance one frame
```

---

## 5. explicitApi Strict — what it means for extensions

The module is compiled with `kotlin { explicitApi = ExplicitApiMode.Strict }`. This means:

- Every `class`, `fun`, and `val` you write in a file within the `pixel-engine` module must have an explicit visibility modifier (`public`, `internal`, or `private`).
- If you are writing extension code **outside** the module (in your own app module), this rule does not apply to your code — only to SDK source files.
- When subclassing `PagerGesturePolicy` or `PixelRenderBox` from your app module, you do not need to follow strict mode, but it is good practice to mark overrides `override` and top-level declarations with their intended visibility.

### Common pitfall — `markNeedsLayout` timing

Call `markNeedsLayout()` / `markNeedsPaint()` **only from the main thread** (the same thread that drives `PixelUiRuntime.render()`). Calling them from a background thread without synchronization will corrupt the dirty-flag state.
