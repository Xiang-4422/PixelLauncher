package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.internal.AlignDirectionalWidget
import com.purride.pixelui.internal.AlignWidget
import com.purride.pixelui.internal.AutomaticFocusAction
import com.purride.pixelui.internal.AspectRatioWidget
import com.purride.pixelui.internal.ColumnWidget
import com.purride.pixelui.internal.ConstrainedBoxWidget
import com.purride.pixelui.internal.ContainerDirectionalWidget
import com.purride.pixelui.internal.ContainerWidget
import com.purride.pixelui.internal.CustomPaintWidget
import com.purride.pixelui.internal.CustomScrollViewWidget
import com.purride.pixelui.internal.DecoratedBoxWidget
import com.purride.pixelui.internal.FlexWrapperWidget
import com.purride.pixelui.internal.FittedBoxWidget
import com.purride.pixelui.internal.CircleWidget
import com.purride.pixelui.internal.ClipRectWidget
import com.purride.pixelui.internal.FormFieldDecorationWidget
import com.purride.pixelui.internal.GestureDetectorWidget
import com.purride.pixelui.internal.GridViewWidget
import com.purride.pixelui.internal.ImageWidget
import com.purride.pixelui.internal.LazyGridViewWidget
import com.purride.pixelui.internal.LazyListViewWidget
import com.purride.pixelui.internal.LineWidget
import com.purride.pixelui.internal.LazySeparatedListViewWidget
import com.purride.pixelui.internal.ListViewWidget
import com.purride.pixelui.internal.OutlinedButtonWidget
import com.purride.pixelui.internal.OpacityWidget
import com.purride.pixelui.internal.SliderWidget
import com.purride.pixelui.internal.PaddingDirectionalWidget
import com.purride.pixelui.internal.PaddingWidget
import com.purride.pixelui.internal.PageViewWidget
import com.purride.pixelui.internal.PathWidget
import com.purride.pixelui.internal.PositionedDirectionalWidget
import com.purride.pixelui.internal.PositionedWidget
import com.purride.pixelui.internal.PolygonWidget
import com.purride.pixelui.internal.RefreshIndicatorWidget
import com.purride.pixelui.internal.RowWidget
import com.purride.pixelui.internal.ScrollbarWidget
import com.purride.pixelui.internal.SingleChildScrollViewWidget
import com.purride.pixelui.internal.SizedBoxWidget
import com.purride.pixelui.internal.StackWidget
import com.purride.pixelui.internal.SpriteWidget
import com.purride.pixelui.internal.TextFieldWidget
import com.purride.pixelui.internal.TextButtonWidget
import com.purride.pixelui.internal.RichTextWidget
import com.purride.pixelui.internal.TextWidget
import com.purride.pixelui.internal.TransformTranslateWidget
import com.purride.pixelui.internal.VariableLazyListViewWidget
import com.purride.pixelui.internal.WrapWidget
import com.purride.pixelui.internal.activationKeyHandler
import com.purride.pixelui.internal.resolveForTextField
import com.purride.pixelui.animation.IntOffset
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelRefreshIndicatorController
import com.purride.pixelui.state.PixelRefreshIndicatorState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * Flutter 风格公开别名层。
 *
 * 公开组件先形成 retained build tree，再由 direct render object pipeline 输出像素结果。
 */
public typealias TextStyle = PixelTextStyle
/** 保留 `PixelWidgets` 对 `ButtonStyle` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias ButtonStyle = PixelButtonStyle
/** 保留 `PixelWidgets` 对 `TextButtonStyle` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias TextButtonStyle = PixelTextButtonStyle
/** 保留 `PixelWidgets` 对 `TextFieldStyle` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias TextFieldStyle = PixelTextFieldStyle
/** 保留 `PixelWidgets` 对 `TextOverflow` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias TextOverflow = PixelTextOverflow
/** 保留 `PixelWidgets` 对 `TextInputAction` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias TextInputAction = PixelTextInputAction

/** 创建 `Padding` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Padding(
    child: Widget,
    all: Int,
    key: Any? = null,
): Widget {
    return Padding(
        child = child,
        padding = EdgeInsets.all(all),
        key = key,
    )
}

/** 创建 `Padding` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Padding(
    child: Widget,
    padding: EdgeInsets,
    key: Any? = null,
): Widget {
    return PaddingWidget(child = child, padding = padding, key = key)
}

/** 创建 `Padding` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Padding(
    child: Widget,
    horizontal: Int = 0,
    vertical: Int = 0,
    key: Any? = null,
): Widget {
    return Padding(
        child = child,
        padding = EdgeInsets.symmetric(
            horizontal = horizontal,
            vertical = vertical,
        ),
        key = key,
    )
}

/** 创建 `PaddingDirectional` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun PaddingDirectional(
    child: Widget,
    padding: EdgeInsetsDirectional,
    key: Any? = null,
): Widget {
    return PaddingDirectionalWidget(child = child, padding = padding, key = key)
}

/** 创建 `Align` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Align(
    child: Widget,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return AlignWidget(child = child, alignment = alignment, key = key)
}

/** 创建 `Center` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Center(
    child: Widget,
    key: Any? = null,
): Widget {
    return Align(
        child = child,
        alignment = Alignment.CENTER,
        key = key,
    )
}

/** 创建 `AlignDirectional` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun AlignDirectional(
    child: Widget,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    key: Any? = null,
): Widget {
    return AlignDirectionalWidget(child = child, alignment = alignment, key = key)
}

/** 创建 `SizedBox` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun SizedBox(
    width: Int? = null,
    height: Int? = null,
    child: Widget? = null,
    key: Any? = null,
): Widget {
    return SizedBoxWidget(width = width, height = height, child = child, key = key)
}

/** 创建 `Expanded` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Expanded(
    child: Widget,
    flex: Int = 1,
    key: Any? = null,
): Widget {
    return FlexWrapperWidget(
        key = key,
        child = child,
        flex = flex,
        fit = FlexFit.TIGHT,
    )
}

/** 创建 `Flexible` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Flexible(
    child: Widget,
    flex: Int = 1,
    fit: FlexFit = FlexFit.LOOSE,
    key: Any? = null,
): Widget {
    return FlexWrapperWidget(
        key = key,
        child = child,
        flex = flex,
        fit = fit,
    )
}

/** 创建 `Spacer` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Spacer(
    flex: Int = 1,
    key: Any? = null,
): Widget {
    return FlexWrapperWidget(
        key = key,
        child = SizedBox(key = "${key ?: "spacer"}-box"),
        flex = flex,
        fit = FlexFit.TIGHT,
    )
}

/** 创建 `GestureDetector` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun GestureDetector(
    child: Widget,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onSwipeStart: (() -> Unit)? = null,
    onSwipeUpdate: ((Int) -> Unit)? = null,
    onSwipeEnd: ((Int) -> Unit)? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    key: Any? = null,
): Widget {
    return GestureDetectorWidget(
        child = child,
        onTap = onTap,
        onLongPress = onLongPress,
        onDoubleTap = onDoubleTap,
        onSwipeStart = onSwipeStart,
        onSwipeUpdate = onSwipeUpdate,
        onSwipeEnd = onSwipeEnd,
        onSwipeLeft = onSwipeLeft,
        onSwipeRight = onSwipeRight,
        key = key,
    )
}

/**
 * 直线 widget（Bresenham 算法）。
 *
 * 端点坐标 ([startX], [startY]) → ([endX], [endY]) 相对 widget 左上角。
 * Layout intrinsic 尺寸 = `(max(startX, endX) + 1, max(startY, endY) + 1)`，
 * 父约束更紧时被 clamp。
 */
public fun Line(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    color: PixelColor,
    key: Any? = null,
): Widget {
    return LineWidget(
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        color = color,
        key = key,
    )
}

/**
 * 直线 widget，使用统一 shape style 控制颜色、线宽和 blend mode。
 */
public fun Line(
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    style: PixelShapeStyle,
    key: Any? = null,
): Widget {
    return LineWidget(
        startX = startX,
        startY = startY,
        endX = endX,
        endY = endY,
        color = style.color,
        style = style,
        key = key,
    )
}

/**
 * 圆形 widget。
 *
 * Layout intrinsic 尺寸 = `(2 * radius + 1, 2 * radius + 1)`，父约束更紧时
 * 被 clamp。圆心 = 当前 layout box 中点。
 *
 * - [filled] = true：逐 scanline 填充（默认）。
 * - [filled] = false：中点圆轮廓算法，单像素线宽。
 *
 * 非矩形容器场景（如 radio dot、状态指示）适用；矩形容器请直接用 [Container]。
 */
public fun Circle(
    radius: Int,
    color: PixelColor,
    filled: Boolean = true,
    key: Any? = null,
): Widget {
    return CircleWidget(radius = radius, color = color, filled = filled, key = key)
}

/**
 * 圆形 widget，使用统一 shape style 控制颜色、填充、线宽和 blend mode。
 */
public fun Circle(
    radius: Int,
    style: PixelShapeStyle,
    key: Any? = null,
): Widget {
    return CircleWidget(radius = radius, color = style.color, filled = style.filled, style = style, key = key)
}

/**
 * 执行 `PixelWidgets` 的 `Polygon` 公开行为；具体参数、返回和副作用见下文。
 *
 * Polygon widget backed by direct pixel drawing.
 *
 * [filled] uses scanline fill; outline mode draws single-pixel line segments.
 */
public fun Polygon(
    points: List<PixelPoint>,
    color: PixelColor,
    filled: Boolean = true,
    key: Any? = null,
): Widget {
    return PolygonWidget(points = points, color = color, filled = filled, key = key)
}

/**
 * 执行 `PixelWidgets` 的 `Polygon` 公开行为；具体参数、返回和副作用见下文。
 *
 * Polygon widget backed by direct pixel drawing, styled with [PixelShapeStyle].
 */
public fun Polygon(
    points: List<PixelPoint>,
    style: PixelShapeStyle,
    key: Any? = null,
): Widget {
    return PolygonWidget(points = points, color = style.color, filled = style.filled, style = style, key = key)
}

/**
 * 执行 `PixelWidgets` 的 `Path` 公开行为；具体参数、返回和副作用见下文。
 *
 * Path widget supporting line, quadratic, cubic, and close commands.
 */
public fun Path(
    path: PixelPath,
    color: PixelColor,
    closed: Boolean = false,
    strokeWidth: Int = 1,
    key: Any? = null,
): Widget {
    return PathWidget(path = path, color = color, closed = closed, strokeWidth = strokeWidth, key = key)
}

/**
 * 执行 `PixelWidgets` 的 `Path` 公开行为；具体参数、返回和副作用见下文。
 *
 * Path widget supporting line, quadratic, cubic, and close commands, styled with [PixelShapeStyle].
 *
 * [PixelShapeStyle.filled] is ignored because Path currently only strokes segments.
 */
public fun Path(
    path: PixelPath,
    style: PixelShapeStyle,
    closed: Boolean = false,
    key: Any? = null,
): Widget {
    return PathWidget(path = path, color = style.color, closed = closed, strokeWidth = style.strokeWidth, style = style, key = key)
}

/**
 * 执行 `PixelWidgets` 的 `CustomPaint` 公开行为；具体参数、返回和副作用见下文。
 *
 * Canvas-style pixel painter for batching multiple low-level drawing commands
 * inside one render object.
 */
public fun CustomPaint(
    width: Int,
    height: Int,
    key: Any? = null,
    painter: PixelCanvas.() -> Unit,
): Widget {
    return CustomPaintWidget(width = width, height = height, painter = painter, key = key)
}

/**
 * 把不可变 [PixelBitmap] 1:1 blit 到目标 buffer。
 *
 * Layout：intrinsic 尺寸 = bitmap (width, height)，再按父约束 clamp；
 * Paint：行级 [System.arraycopy]，超出 layout size 自动裁剪，不缩放。
 *
 * 缩放需求由调用方在构造 [PixelBitmap] 前完成（如
 * [android.graphics.Bitmap.createScaledBitmap]）。
 */
public fun Image(
    bitmap: PixelBitmap,
    key: Any? = null,
): Widget {
    return ImageWidget(bitmap = bitmap, key = key)
}

/**
 * 执行 `PixelWidgets` 的 `Sprite` 公开行为；具体参数、返回和副作用见下文。
 *
 * Draws one frame from [PixelSpriteSheet] without scaling.
 */
public fun Sprite(
    sheet: PixelSpriteSheet,
    frameIndex: Int,
    key: Any? = null,
): Widget {
    return SpriteWidget(sheet = sheet, frameIndex = frameIndex, key = key)
}

/** 创建 `Text` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Text(
    data: String,
    style: TextStyle = TextStyle.Default,
    color: PixelColor? = null,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    textAlign: TextAlign = TextAlign.START,
    key: Any? = null,
): Widget {
    return TextWidget(
        data = data,
        style = style,
        color = color,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        key = key,
    )
}

/** 创建 `RichText` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun RichText(
    spans: List<PixelTextSpan>,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    textAlign: TextAlign = TextAlign.START,
    key: Any? = null,
): Widget {
    return RichTextWidget(
        spans = spans,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        key = key,
    )
}

/** 创建 `DecoratedBox` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun DecoratedBox(
    child: Widget? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    padding: Int = 2,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return DecoratedBoxWidget(
        child = child,
        fillColor = fillColor,
        borderColor = borderColor,
        padding = padding,
        alignment = alignment,
        key = key,
    )
}

/** 创建 `Container` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Container(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return ContainerWidget(
        child = child,
        width = width,
        height = height,
        padding = padding,
        margin = margin,
        fillColor = fillColor,
        borderColor = borderColor,
        alignment = alignment,
        key = key,
    )
}

/** 创建 `ContainerDirectional` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ContainerDirectional(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    paddingDirectional: EdgeInsetsDirectional? = null,
    margin: EdgeInsets? = null,
    marginDirectional: EdgeInsetsDirectional? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    key: Any? = null,
): Widget {
    return ContainerDirectionalWidget(
        child = child,
        width = width,
        height = height,
        padding = padding,
        paddingDirectional = paddingDirectional,
        margin = margin,
        marginDirectional = marginDirectional,
        fillColor = fillColor,
        borderColor = borderColor,
        alignment = alignment,
        key = key,
    )
}

/** 创建 `Stack` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Stack(
    children: List<Widget>,
    alignment: Alignment = Alignment.TOP_START,
    key: Any? = null,
): Widget {
    return StackWidget(children = children, alignment = alignment, key = key)
}

/** 创建 `Positioned` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Positioned(
    child: Widget,
    left: Int? = null,
    top: Int? = null,
    right: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    key: Any? = null,
): Widget {
    return PositionedWidget(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        width = width,
        height = height,
        key = key,
    )
}

/** 创建 `PositionedDirectional` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun PositionedDirectional(
    child: Widget,
    start: Int? = null,
    top: Int? = null,
    end: Int? = null,
    bottom: Int? = null,
    width: Int? = null,
    height: Int? = null,
    key: Any? = null,
): Widget {
    return PositionedDirectionalWidget(
        child = child,
        start = start,
        top = top,
        end = end,
        bottom = bottom,
        width = width,
        height = height,
        key = key,
    )
}

/** 创建 `PositionedFill` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun PositionedFill(
    child: Widget,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    key: Any? = null,
): Widget {
    return Positioned(
        child = child,
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        key = key,
    )
}

/**
 * 执行 `PixelWidgets` 的 `Opacity` 公开行为；具体参数、返回和副作用见下文。
 *
 * Applies group [opacity] while retaining the child's layout and State.
 *
 * Values are clamped to `0f..1f`; non-finite values are treated as zero. At exactly zero the
 * child does not paint, receive hit/interaction targets, or expose semantics. Positive opacity
 * keeps hit testing and semantics active while scaling only painted alpha.
 */
public fun Opacity(
    opacity: Float,
    child: Widget,
    key: Any? = null,
): Widget {
    return OpacityWidget(opacity = opacity, child = child, key = key)
}

/** 创建 `ClipRect` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ClipRect(
    child: Widget,
    key: Any? = null,
): Widget {
    return ClipRectWidget(child = child, key = key)
}

/** 集中提供 `PixelWidgets` 共享的工厂、常量或无状态辅助入口。 */
public object Transform {
    /** 依据 `PixelWidgets` 的公开契约执行 `translate`，并返回或提交经过边界校验的结果。 */
    public fun translate(
        offset: IntOffset,
        child: Widget,
        key: Any? = null,
    ): Widget {
        return TransformTranslateWidget(offset = offset, child = child, key = key)
    }
}

/** 创建 `Row` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Row(
    children: List<Widget>,
    spacing: Int = 0,
    mainAxisSize: MainAxisSize = MainAxisSize.MIN,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget {
    return RowWidget(
        children = children,
        spacing = spacing,
        mainAxisSize = mainAxisSize,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        key = key,
    )
}

/** 创建 `Wrap` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Wrap(
    children: List<Widget>,
    spacing: Int = 0,
    runSpacing: Int = 0,
    key: Any? = null,
): Widget {
    return WrapWidget(children = children, spacing = spacing, runSpacing = runSpacing, key = key)
}

/** 创建 `AspectRatio` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun AspectRatio(
    aspectRatio: Float,
    child: Widget,
    key: Any? = null,
): Widget {
    return AspectRatioWidget(aspectRatio = aspectRatio, child = child, key = key)
}

/** 创建 `ConstrainedBox` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ConstrainedBox(
    constraints: PixelBoxConstraints,
    child: Widget,
    key: Any? = null,
): Widget {
    return ConstrainedBoxWidget(constraints = constraints, child = child, key = key)
}

/** 创建 `FittedBox` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun FittedBox(
    child: Widget,
    key: Any? = null,
): Widget {
    return FittedBoxWidget(child = child, key = key)
}

/** 创建 `Column` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun Column(
    children: List<Widget>,
    spacing: Int = 0,
    mainAxisSize: MainAxisSize = MainAxisSize.MIN,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.START,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.START,
    key: Any? = null,
): Widget {
    return ColumnWidget(
        children = children,
        spacing = spacing,
        mainAxisSize = mainAxisSize,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        key = key,
    )
}

/** 创建 `PageView` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun PageView(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    pages: List<Widget>,
    onPageChanged: ((Int) -> Unit)? = null,
    onPageDragStart: (() -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PagerSemanticsWidget(
        axis = axis,
        controller = controller,
        state = state,
        pageCount = pages.size,
        onPageChanged = onPageChanged,
        child = PageViewWidget(
            axis = axis,
            controller = controller,
            state = state,
            pages = pages,
            onPageChanged = onPageChanged,
            onPageDragStart = onPageDragStart,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "pager") },
    )
}

/** 创建 `PageViewBuilder` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun PageViewBuilder(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    onPageChanged: ((Int) -> Unit)? = null,
    onPageDragStart: (() -> Unit)? = null,
    key: Any? = null,
): Widget {
    /** 本次声明构建的页面列表，同时供渲染视口与分页语义边界使用。 */
    val pages = List(itemCount) { index -> itemBuilder(index) }
    return PagerSemanticsWidget(
        axis = axis,
        controller = controller,
        state = state,
        pageCount = itemCount,
        onPageChanged = onPageChanged,
        child = PageViewWidget(
            axis = axis,
            controller = controller,
            state = state,
            pages = pages,
            key = key,
            onPageChanged = onPageChanged,
            onPageDragStart = onPageDragStart,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "pager") },
    )
}

/** 创建 `ListView` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ListView(
    items: List<Widget>,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.LIST,
        rowCount = items.size,
        columnCount = 1,
        child = ListViewWidget(
            items = items,
            state = state,
            controller = controller,
            spacing = spacing,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "list") },
    )
}

/** 创建 `ListViewBuilder` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ListViewBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    itemExtent: Int? = null,
    estimatedItemExtent: Int? = null,
    cacheExtent: Int = 1,
    key: Any? = null,
): Widget {
    val fixedItemExtent = itemExtent
    if (fixedItemExtent != null) {
        return ScrollableSemanticsWidget(
            state = state,
            controller = controller,
            role = PixelSemanticRole.LIST,
            rowCount = itemCount,
            columnCount = 1,
            child = LazyListViewWidget(
                itemCount = itemCount,
                itemBuilder = itemBuilder,
                itemExtent = fixedItemExtent,
                state = state,
                controller = controller,
                spacing = spacing,
                cacheExtent = cacheExtent,
                key = key,
            ),
            key = key?.let { PixelViewportSemanticsKey(it, "lazy-list") },
        )
    }
    val estimatedExtent = estimatedItemExtent
    if (estimatedExtent != null && estimatedExtent > 0) {
        return ScrollableSemanticsWidget(
            state = state,
            controller = controller,
            role = PixelSemanticRole.LIST,
            rowCount = itemCount,
            columnCount = 1,
            child = VariableLazyListViewWidget(
                itemCount = itemCount,
                itemBuilder = itemBuilder,
                estimatedItemExtent = estimatedExtent,
                state = state,
                controller = controller,
                spacing = spacing,
                cacheExtent = cacheExtent,
                key = key,
            ),
            key = key?.let { PixelViewportSemanticsKey(it, "variable-list") },
        )
    }
    return ListView(
        items = List(itemCount) { index -> itemBuilder(index) },
        state = state,
        controller = controller,
        spacing = spacing,
        key = key,
    )
}

/** 创建 `ListViewSeparated` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ListViewSeparated(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    separatorBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    val separatedItems = buildList {
        repeat(itemCount) { index ->
            add(itemBuilder(index))
            if (index < itemCount - 1) {
                add(separatorBuilder(index))
            }
        }
    }
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.LIST,
        rowCount = itemCount,
        columnCount = 1,
        child = ListViewWidget(
            items = separatedItems,
            state = state,
            controller = controller,
            spacing = 0,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "separated-list") },
    )
}

/** 创建 `ListViewSeparatedBuilder` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun ListViewSeparatedBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    separatorBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    itemExtent: Int? = null,
    separatorExtent: Int? = null,
    estimatedItemExtent: Int? = null,
    estimatedSeparatorExtent: Int? = null,
    cacheExtent: Int = 1,
    key: Any? = null,
): Widget {
    require(itemCount >= 0) { "itemCount must be >= 0" }
    require(itemExtent == null || itemExtent > 0) { "itemExtent must be > 0" }
    require(separatorExtent == null || separatorExtent > 0) { "separatorExtent must be > 0" }
    require(estimatedItemExtent == null || estimatedItemExtent > 0) { "estimatedItemExtent must be > 0" }
    require(estimatedSeparatorExtent == null || estimatedSeparatorExtent > 0) {
        "estimatedSeparatorExtent must be > 0"
    }
    require(itemExtent != null || estimatedItemExtent != null) {
        "itemExtent or estimatedItemExtent must be provided"
    }
    require(separatorExtent != null || estimatedSeparatorExtent != null || itemCount <= 1) {
        "separatorExtent or estimatedSeparatorExtent must be provided"
    }
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.LIST,
        rowCount = itemCount,
        columnCount = 1,
        child = LazySeparatedListViewWidget(
            itemCount = itemCount,
            itemBuilder = itemBuilder,
            separatorBuilder = separatorBuilder,
            itemExtent = itemExtent,
            separatorExtent = separatorExtent,
            estimatedItemExtent = estimatedItemExtent,
            estimatedSeparatorExtent = estimatedSeparatorExtent,
            state = state,
            controller = controller,
            cacheExtent = cacheExtent,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "lazy-separated-list") },
    )
}

/** 创建 `SliverList` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun SliverList(
    items: List<Widget>,
    spacing: Int = 0,
    key: Any? = null,
): PixelSliver {
    return PixelSliverList(
        items = items,
        spacing = spacing,
        key = key,
    )
}

/** 创建 `SliverListBuilder` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun SliverListBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    itemExtent: Int? = null,
    estimatedItemExtent: Int? = null,
    spacing: Int = 0,
    cacheExtent: Int = 1,
    key: Any? = null,
): PixelSliver {
    require(itemCount >= 0) { "itemCount must be >= 0" }
    require(itemExtent == null || itemExtent > 0) { "itemExtent must be > 0" }
    require(estimatedItemExtent == null || estimatedItemExtent > 0) { "estimatedItemExtent must be > 0" }
    require(itemExtent != null || estimatedItemExtent != null) { "itemExtent or estimatedItemExtent must be provided" }
    return PixelSliverListBuilder(
        itemCount = itemCount,
        itemBuilder = itemBuilder,
        itemExtent = itemExtent,
        estimatedItemExtent = estimatedItemExtent,
        spacing = spacing,
        cacheExtent = cacheExtent,
        key = key,
    )
}

/** 创建 `SliverPinnedHeader` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun SliverPinnedHeader(
    child: Widget,
    key: Any? = null,
): PixelSliver {
    return PixelSliverPinnedHeader(child = child, key = key)
}

/** 创建 `SliverAppBar` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun SliverAppBar(
    child: Widget,
    expandedHeight: Int,
    collapsedHeight: Int,
    floating: Boolean = false,
    snap: Boolean = false,
    stretch: Boolean = false,
    stretchLimit: Int = expandedHeight,
    key: Any? = null,
): PixelSliver {
    require(expandedHeight >= 0) { "expandedHeight must be >= 0" }
    require(collapsedHeight in 0..expandedHeight) { "collapsedHeight must be in 0..expandedHeight" }
    require(!snap || floating) { "snap requires floating = true" }
    require(stretchLimit >= 0) { "stretchLimit must be >= 0" }
    return PixelSliverAppBar(
        child = child,
        expandedHeight = expandedHeight,
        collapsedHeight = collapsedHeight,
        floating = floating,
        snap = snap,
        stretch = stretch,
        stretchLimit = stretchLimit,
        key = key,
    )
}

/** 创建 `CustomScrollView` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun CustomScrollView(
    slivers: List<PixelSliver>,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    /** 所有 sliver 对无障碍服务呈现的逻辑条目总数。 */
    val semanticItemCount = slivers.sumOf { sliver ->
        when (sliver) {
            is PixelSliverList -> sliver.items.size
            is PixelSliverListBuilder -> sliver.itemCount
            is PixelSliverPinnedHeader,
            is PixelSliverAppBar,
            -> 1
        }
    }
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.LIST,
        rowCount = semanticItemCount,
        columnCount = 1,
        child = CustomScrollViewWidget(
            slivers = slivers,
            state = state,
            controller = controller,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "custom-scroll") },
    )
}

/** 创建 `GridView` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun GridView(
    items: List<Widget>,
    cellWidth: Int,
    cellHeight: Int,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    runSpacing: Int = spacing,
    key: Any? = null,
): Widget {
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.LIST,
        rowCount = items.size,
        columnCount = -1,
        gridCellWidth = cellWidth,
        gridSpacing = spacing,
        child = GridViewWidget(
            items = items,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            state = state,
            controller = controller,
            spacing = spacing,
            runSpacing = runSpacing,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "grid") },
    )
}

/** 创建 `GridViewBuilder` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun GridViewBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    cellWidth: Int,
    cellHeight: Int,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    runSpacing: Int = spacing,
    cacheExtent: Int = 1,
    key: Any? = null,
): Widget {
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.LIST,
        rowCount = itemCount,
        columnCount = -1,
        gridCellWidth = cellWidth,
        gridSpacing = spacing,
        child = LazyGridViewWidget(
            itemCount = itemCount,
            itemBuilder = itemBuilder,
            cellWidth = cellWidth,
            cellHeight = cellHeight,
            state = state,
            controller = controller,
            spacing = spacing,
            runSpacing = runSpacing,
            cacheExtent = cacheExtent,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "lazy-grid") },
    )
}

/**
 * 执行 `PixelWidgets` 的 `Scrollbar` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds the binary-compatible scrollbar facade around [child].
 *
 * Historical concrete defaults act as omission sentinels inside [PixelTheme], while explicit
 * non-default colors and width retain caller precedence.
 */
public fun Scrollbar(
    child: Widget,
    state: PixelListState,
    thumbColor: PixelColor = PixelColor.White,
    trackColor: PixelColor? = null,
    width: Int = 1,
    key: Any? = null,
): Widget {
    return buildScrollbar(
        child = child,
        state = state,
        states = PixelControlStateSet.Normal,
        // The historical default is an omission sentinel so theme roles can propagate.
        thumbColor = thumbColor.takeUnless { color -> color == PixelColor.White },
        trackColor = trackColor,
        // The historical one-pixel default is resolved through the active size token.
        width = width.takeUnless { value -> value == 1 },
        enabled = true,
        key = key,
        semanticLabel = null,
        legacyFacade = true,
        legacyThumbColor = thumbColor,
        legacyTrackColor = trackColor,
        legacyWidth = width,
    )
}

/**
 * 执行 `PixelWidgets` 的 `Scrollbar` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds a themed, state-aware scrollbar around [child].
 *
 * [states] is required so this overload remains source-distinct from the compatibility facade;
 * its stable JVM name also prevents future Kotlin default-argument changes from moving the Java
 * entry point. Scrollbar does not add an independent focus stop because its public contract has no
 * controller-backed key action; the wrapped scrollable continues to own keyboard and accessible
 * scrolling.
 *
 * @param child Scrollable viewport whose list target supplies geometry and mutation ownership.
 * @param state List state shared with the wrapped viewport.
 * @param states Caller-owned semantic states merged with retained pointer hover/press feedback.
 * @param thumbColor Optional concrete thumb override; null resolves the component foreground role.
 * @param trackColor Optional concrete track override; null resolves the component container role.
 * @param width Optional logical width override; null resolves the component minimum-width token.
 * @param enabled Whether drag mutation is available before state normalization.
 * @param key Stable identity shared by the semantics and render boundaries.
 * @param semanticLabel Optional spoken label; null or blank resolves the theme label token.
 */
@JvmName("ScrollbarWithControlStates")
public fun Scrollbar(
    child: Widget,
    state: PixelListState,
    states: PixelControlStateSet,
    thumbColor: PixelColor? = null,
    trackColor: PixelColor? = null,
    width: Int? = null,
    enabled: Boolean = true,
    key: Any? = null,
    semanticLabel: String? = null,
): Widget {
    return buildScrollbar(
        child = child,
        state = state,
        states = states,
        thumbColor = thumbColor,
        trackColor = trackColor,
        width = width,
        enabled = enabled,
        key = key,
        semanticLabel = semanticLabel,
        legacyFacade = false,
        legacyThumbColor = PixelColor.White,
        legacyTrackColor = null,
        legacyWidth = 1,
    )
}

/** Builds one Scrollbar while retaining whether historical defaults require scope-less fallback. */
@Suppress("LongParameterList")
private fun buildScrollbar(
    child: Widget,
    state: PixelListState,
    states: PixelControlStateSet,
    thumbColor: PixelColor?,
    trackColor: PixelColor?,
    width: Int?,
    enabled: Boolean,
    key: Any?,
    semanticLabel: String?,
    /** Whether mounting without an explicit PixelTheme must preserve old paint defaults. */
    legacyFacade: Boolean,
    /** Exact historical thumb value before omission-sentinel conversion. */
    legacyThumbColor: PixelColor,
    /** Exact historical optional track value before theme fallback. */
    legacyTrackColor: PixelColor?,
    /** Exact historical logical width before omission-sentinel conversion. */
    legacyWidth: Int,
): Widget {
    /** Disabled is terminal for drag mutation even when other states are also present. */
    val disabled = !enabled || PixelControlState.Disabled in states
    /** Loading removes pointer mutation and semantic availability. */
    val loading = PixelControlState.Loading in states
    /** Drag targets exist only while neither terminal state blocks mutation. */
    val interactive = !disabled && !loading
    /** Disabled normalized into the state set when it originates from the explicit enabled flag. */
    val normalizedStates = if (disabled) states + PixelControlState.Disabled else states
    return ScrollbarWidget(
        child = child,
        state = state,
        states = normalizedStates,
        thumbColor = thumbColor,
        trackColor = trackColor,
        width = width,
        enabled = interactive,
        semanticLabel = semanticLabel,
        legacyFacade = legacyFacade,
        legacyThumbColor = legacyThumbColor,
        legacyTrackColor = legacyTrackColor,
        legacyWidth = legacyWidth,
        key = key,
    )
}

/**
 * 在子内容外建立下拉、键盘和无障碍共用的刷新生命周期。
 *
 * Adds pull, keyboard, and accessibility refresh activation around [child].
 *
 * Enter/Space and the semantic click action enter the same [state] lifecycle as a completed pull;
 * [semanticLabel] names that composite action without hiding the child's semantic descendants.
 *
 * @param child 接收下拉位移并保留自身语义子树的内容。
 * @param state 调用方持有的刷新阶段与下拉进度。
 * @param controller 驱动 [state] 刷新生命周期的控制器。
 * @param onRefresh 成功进入刷新阶段时调用一次的业务回调。
 * @param thresholdPx 从拉动进入 armed 状态所需的像素距离。
 * @param enabled 是否接受下拉、键盘与无障碍刷新动作。
 * @param indicatorColor 普通拉动阶段的指示器颜色。
 * @param armedColor 达到触发阈值后的指示器颜色。
 * @param refreshingColor 正在刷新阶段的指示器颜色。
 * @param key 刷新边界及其语义节点的稳定 identity。
 * @param semanticLabel 与子内容分离的刷新动作无障碍名称。
 */
public fun RefreshIndicator(
    child: Widget,
    state: PixelRefreshIndicatorState,
    controller: PixelRefreshIndicatorController,
    onRefresh: () -> Unit,
    thresholdPx: Int = 12,
    enabled: Boolean = true,
    indicatorColor: PixelColor = PixelColor.White,
    armedColor: PixelColor = PixelColor.fromRgb(200, 100, 0),
    refreshingColor: PixelColor = PixelColor.fromRgb(255, 255, 0),
    key: Any? = null,
    semanticLabel: String = "Refresh",
): Widget {
    return buildRefreshIndicator(
        child = child,
        state = state,
        controller = controller,
        states = PixelControlStateSet.Normal,
        onRefresh = onRefresh,
        // Historical defaults become omission sentinels for theme resolution.
        thresholdPx = thresholdPx.takeUnless { value -> value == 12 },
        enabled = enabled,
        indicatorColor = indicatorColor.takeUnless { color -> color == PixelColor.White },
        armedColor = armedColor.takeUnless { color -> color == LegacyRefreshArmedColor },
        refreshingColor = refreshingColor.takeUnless { color -> color == LegacyRefreshingColor },
        key = key,
        semanticLabel = semanticLabel.takeUnless { label -> label == "Refresh" },
        legacyFacade = true,
        legacyThresholdPx = thresholdPx,
        legacyIndicatorColor = indicatorColor,
        legacyArmedColor = armedColor,
        legacyRefreshingColor = refreshingColor,
    )
}

/**
 * 执行 `PixelWidgets` 的 `RefreshIndicator` 公开行为；具体参数、返回和副作用见下文。
 *
 * Adds state-aware pull, keyboard, and accessibility refresh activation around [child].
 *
 * The required [states] parameter and stable JVM name keep this overload separate from the legacy
 * binary facade. Armed contributes Selected, refreshing contributes Loading, and both merge with
 * retained hover, press, and focus before resolving `components.refresh`.
 *
 * @param child Content receiving pull gestures while retaining semantic descendants.
 * @param state Caller-owned refresh phase and pull distance.
 * @param controller Controller driving the shared pull/refresh lifecycle.
 * @param states Caller-owned states merged with controlled and pointer micro-states.
 * @param onRefresh Callback invoked once after a successful refresh transition; null disables it.
 * @param thresholdPx Optional trigger distance; null resolves the foundation size scale.
 * @param enabled Whether mutation is available before state normalization.
 * @param indicatorColor Optional ordinary-pull foreground override.
 * @param armedColor Optional armed/Selected foreground override.
 * @param refreshingColor Optional refreshing/Loading foreground override.
 * @param key Stable identity shared by theme, focus, semantics, and render boundaries.
 * @param semanticLabel Optional spoken label; null or blank resolves the theme label token.
 */
@JvmName("RefreshIndicatorWithControlStates")
public fun RefreshIndicator(
    child: Widget,
    state: PixelRefreshIndicatorState,
    controller: PixelRefreshIndicatorController,
    states: PixelControlStateSet,
    onRefresh: (() -> Unit)?,
    thresholdPx: Int? = null,
    enabled: Boolean = true,
    indicatorColor: PixelColor? = null,
    armedColor: PixelColor? = null,
    refreshingColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
): Widget {
    return buildRefreshIndicator(
        child = child,
        state = state,
        controller = controller,
        states = states,
        onRefresh = onRefresh,
        thresholdPx = thresholdPx,
        enabled = enabled,
        indicatorColor = indicatorColor,
        armedColor = armedColor,
        refreshingColor = refreshingColor,
        key = key,
        semanticLabel = semanticLabel,
        legacyFacade = false,
        legacyThresholdPx = 12,
        legacyIndicatorColor = PixelColor.White,
        legacyArmedColor = LegacyRefreshArmedColor,
        legacyRefreshingColor = LegacyRefreshingColor,
    )
}

/** Builds one retained RefreshIndicator while keeping facade provenance internal to the SDK. */
@Suppress("LongParameterList")
private fun buildRefreshIndicator(
    child: Widget,
    state: PixelRefreshIndicatorState,
    controller: PixelRefreshIndicatorController,
    states: PixelControlStateSet,
    onRefresh: (() -> Unit)?,
    thresholdPx: Int?,
    enabled: Boolean,
    indicatorColor: PixelColor?,
    armedColor: PixelColor?,
    refreshingColor: PixelColor?,
    key: Any?,
    semanticLabel: String?,
    legacyFacade: Boolean,
    legacyThresholdPx: Int,
    legacyIndicatorColor: PixelColor,
    legacyArmedColor: PixelColor,
    legacyRefreshingColor: PixelColor,
): Widget {
    return Builder(key = key?.let(::RefreshThemeResolutionKey)) { themedContext ->
        /** Complete token graph resolved before sharing one threshold with every input path. */
        val themeTokens = PixelTheme.tokensOf(themedContext)
        /** Optional application bundle overrides only provider-aware text and formatting. */
        val localizedLabels = PixelLocalizations.maybeOf(themedContext)?.labels
        /** Scope-less legacy provenance used only for historical trigger and paint behavior. */
        val scopeLessLegacy = legacyFacade && PixelTheme.maybeTokensOf(themedContext) == null
        /** Refresh-specific geometry used as a floor for the foundation compact-control scale. */
        val componentHeight = themeTokens.components.refresh
            .resolveMinimumHeight(themeTokens.sizes)
        /** Omitted historical threshold resolved through live foundation size tokens. */
        val resolvedThreshold = if (scopeLessLegacy) {
            legacyThresholdPx.coerceAtLeast(1)
        } else {
            (thresholdPx ?: maxOf(componentHeight, themeTokens.sizes.compactControlHeight))
                .coerceAtLeast(1)
        }
        /** Localizable spoken label shared by focus diagnostics and semantics. */
        val resolvedLabel = semanticLabel?.takeIf { label -> label.isNotBlank() }
            ?: localizedLabels?.refresh
            ?: themeTokens.labels.refresh
        /** Disabled is terminal and removes the automatic focus owner. */
        val disabled = !enabled || onRefresh == null || PixelControlState.Disabled in states
        /** Explicit Loading remains focusable but rejects every mutation path. */
        val persistentLoading = PixelControlState.Loading in states
        /** Shared keyboard and semantic action entering the same lifecycle as a completed pull. */
        val activate: () -> Boolean = {
            if (disabled || persistentLoading || state.isRefreshing) {
                false
            } else {
                controller.startPull(state)
                controller.updatePull(state, resolvedThreshold.toFloat(), resolvedThreshold)
                /** Whether this action became the unique active refresh lifecycle. */
                val started = controller.endPull(state, resolvedThreshold)
                if (started) onRefresh?.invoke()
                started
            }
        }
        AutomaticFocusAction(
            // Loading retains focus; only the terminal Disabled state removes traversal.
            enabled = !disabled,
            debugLabel = resolvedLabel,
            onKeyEvent = activationKeyHandler(activate),
            key = key,
        ) { _, _ ->
            RefreshIndicatorWidget(
                child = child,
                state = state,
                controller = controller,
                states = states,
                thresholdPx = resolvedThreshold,
                enabled = !disabled && !persistentLoading,
                focusable = !disabled,
                indicatorColor = indicatorColor,
                armedColor = armedColor,
                refreshingColor = refreshingColor,
                legacyFacade = legacyFacade,
                legacyIndicatorColor = legacyIndicatorColor,
                legacyArmedColor = legacyArmedColor,
                legacyRefreshingColor = legacyRefreshingColor,
                semanticAction = activate,
                onRefresh = onRefresh ?: {},
                semanticLabel = resolvedLabel,
                key = key,
            )
        }
    }
}

/** Stable identity for the theme-resolution builder above one refresh focus boundary. */
private data class RefreshThemeResolutionKey(
    /** Original caller-owned component key. */
    val componentKey: Any,
)

/** Historical armed-color sentinel retained only by the binary compatibility facade. */
private val LegacyRefreshArmedColor: PixelColor = PixelColor.fromRgb(200, 100, 0)

/** Historical refreshing-color sentinel retained only by the binary compatibility facade. */
private val LegacyRefreshingColor: PixelColor = PixelColor.fromRgb(255, 255, 0)

/** 创建 `SingleChildScrollView` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun SingleChildScrollView(
    child: Widget,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    return ScrollableSemanticsWidget(
        state = state,
        controller = controller,
        role = PixelSemanticRole.SCROLL_VIEW,
        rowCount = null,
        columnCount = null,
        child = SingleChildScrollViewWidget(
            child = child,
            state = state,
            controller = controller,
            key = key,
        ),
        key = key?.let { PixelViewportSemanticsKey(it, "single-scroll") },
    )
}

/** Value key that keeps one generated viewport semantics boundary stable across rebuilds. */
private data class PixelViewportSemanticsKey(
    /** Caller-owned viewport identity. */
    val owner: Any,
    /** Distinguishes semantic wrappers when one caller key is reused by different viewport kinds. */
    val kind: String,
)

/**
 * Adds collection metadata and page-sized accessibility scrolling without changing viewport layout.
 */
private data class ScrollableSemanticsWidget(
    /** Mutable scroll geometry controlled by [controller]. */
    val state: PixelListState,
    /** Controller that performs semantic scroll actions through the normal state contract. */
    val controller: PixelListController,
    /** Platform role announced for the viewport. */
    val role: PixelSemanticRole,
    /** Logical row count, or null for a non-collection scroll view. */
    val rowCount: Int?,
    /** Logical column count, or null for a non-collection scroll view. */
    val columnCount: Int?,
    /** Grid cell width used to resolve dynamic columns after layout. */
    val gridCellWidth: Int? = null,
    /** Horizontal gap paired with [gridCellWidth]. */
    val gridSpacing: Int = 0,
    /** Original viewport whose retained children keep their own semantic identities. */
    val child: Widget,
    /** Stable identity for the generated semantics boundary. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Rebuilds capabilities when controller geometry or offset changes. */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        /** 初次布局前尚不可判断真实滚动范围的几何状态。 */
        val geometryPending = state.viewportHeightPx <= 0 && state.contentHeightPx <= 0
        /** 当前偏移是否允许向逻辑起点滚动一页。 */
        val canScrollBackward = state.scrollOffsetPx > 0f
        /** 当前几何或待布局内容是否允许向逻辑终点滚动一页。 */
        val canScrollForward = state.scrollOffsetPx < state.maxScrollOffsetPx ||
            (geometryPending && (rowCount == null || rowCount > 0))
        /** 布局后从视口宽度推导出的网格列数，非网格沿用声明值。 */
        val resolvedColumnCount = if (columnCount == -1 && gridCellWidth != null) {
            /** 避免非法单元宽度造成除零的安全宽度。 */
            val safeCellWidth = gridCellWidth.coerceAtLeast(1)
            /** 一个网格列占用的单元宽度与水平间距。 */
            val stride = safeCellWidth + gridSpacing.coerceAtLeast(0)
            ((state.viewportWidthPx + gridSpacing.coerceAtLeast(0)) / stride).coerceAtLeast(1)
        } else {
            columnCount
        }
        /** 根据逻辑条目数与最终列数生成的集合元数据。 */
        val collection = rowCount?.let { logicalItemCount ->
            /** 无障碍集合最终声明的逻辑行数。 */
            val rows = if (columnCount == -1 && resolvedColumnCount != null) {
                (logicalItemCount.coerceAtLeast(0) + resolvedColumnCount - 1) / resolvedColumnCount
            } else {
                logicalItemCount.coerceAtLeast(0)
            }
            PixelSemanticsCollectionInfo(
                rowCount = rows,
                columnCount = resolvedColumnCount ?: -1,
            )
        }
        return Semantics(
            label = "",
            role = role,
            collectionInfo = collection,
            mergeDescendants = false,
            actions = PixelSemanticsActions(
                onScrollForward = if (canScrollForward) {
                    { performSemanticScroll(forward = true) }
                } else {
                    null
                },
                onScrollBackward = if (canScrollBackward) {
                    { performSemanticScroll(forward = false) }
                } else {
                    null
                },
            ),
            child = child,
            key = key?.let { "$it-boundary" },
        )
    }

    /** Moves exactly one viewport and reports whether the controlled offset changed. */
    private fun performSemanticScroll(forward: Boolean): Boolean {
        /** 执行动作前的受控偏移，用于判断动作是否生效。 */
        val previousOffset = state.scrollOffsetPx
        /** 单次无障碍滚动使用的最小一像素视口跨度。 */
        val viewportDelta = state.viewportHeightPx.coerceAtLeast(1).toFloat()
        /** 根据动作方向计算、随后交给 controller 钳位的目标偏移。 */
        val requestedOffset = if (forward) previousOffset + viewportDelta else previousOffset - viewportDelta
        controller.scrollTo(
            state = state,
            targetOffsetPx = requestedOffset,
            viewportHeightPx = state.viewportHeightPx,
            contentHeightPx = state.contentHeightPx,
        )
        return state.scrollOffsetPx != previousOffset
    }
}

/** Adds collection state and discrete accessibility paging to a PageView. */
private data class PagerSemanticsWidget(
    /** Logical paging direction used to expose rows versus columns. */
    val axis: PixelAxis,
    /** Controller that owns the current page. */
    val controller: PixelPagerController,
    /** Mutable pager state observed for action availability and selected page. */
    val state: PixelPagerState,
    /** Declarative page count before controller clamping. */
    val pageCount: Int,
    /** Existing page-changed callback also used by semantic actions. */
    val onPageChanged: ((Int) -> Unit)?,
    /** Rendered pager viewport. */
    val child: Widget,
    /** Stable generated semantics identity. */
    override val key: Any? = null,
) : StatelessWidget(key = key) {
    /** Rebuilds selected value and available directions after every controller notification. */
    override fun build(context: BuildContext): Widget {
        context.watch(controller)
        /** 保证分页语义始终拥有至少一个有效分母。 */
        val safePageCount = pageCount.coerceAtLeast(1)
        /** 钳位到当前声明页面范围内的选中页。 */
        val currentPage = state.currentPage.coerceIn(0, safePageCount - 1)
        /** 按分页方向映射成一行或一列的集合元数据。 */
        val collection = if (axis == PixelAxis.VERTICAL) {
            PixelSemanticsCollectionInfo(rowCount = safePageCount, columnCount = 1)
        } else {
            PixelSemanticsCollectionInfo(rowCount = 1, columnCount = safePageCount)
        }
        return Semantics(
            label = "",
            role = PixelSemanticRole.SCROLL_VIEW,
            value = "${currentPage + 1}/$safePageCount",
            collectionInfo = collection,
            actions = PixelSemanticsActions(
                onScrollForward = if (currentPage < safePageCount - 1) {
                    { moveToPage(currentPage + 1) }
                } else {
                    null
                },
                onScrollBackward = if (currentPage > 0) {
                    { moveToPage(currentPage - 1) }
                } else {
                    null
                },
            ),
            child = child,
            key = key?.let { "$it-boundary" },
        )
    }

    /** Performs one discrete page move and dispatches the same public callback as pointer paging. */
    private fun moveToPage(targetPage: Int): Boolean {
        /** 分页动作前的页码，用于抑制边界上的无效回调。 */
        val previousPage = state.currentPage
        controller.syncToPage(state, targetPage)
        /** controller 钳位后是否确实进入了另一页。 */
        val changed = state.currentPage != previousPage
        if (changed) onPageChanged?.invoke(state.currentPage)
        return changed
    }
}

/**
 * 渲染受控像素文本输入，并桥接 Android IME 与虚拟无障碍编辑动作。
 *
 * Controlled pixel text input with Android IME and virtual accessibility editing support.
 *
 * [semanticLabel] names the field independently from its value. [semanticHint] is an optional
 * instruction, while [semanticError] is announced as validation state. The generated semantic
 * node owns set-text and set-selection actions only while the field is editable.
 *
 * @param semanticLabel 与编辑值分离的无障碍字段名称。
 * @param semanticHint 可选的输入说明。
 * @param semanticError 可选的校验错误说明。
 */
public fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    inputType: PixelInputType = PixelInputType.TEXT,
    textAlign: TextAlign = TextAlign.START,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    focusNode: FocusNode? = null,
    key: Any? = null,
    semanticLabel: String = placeholder,
    semanticHint: String? = null,
    semanticError: String? = null,
): Widget {
    return buildTextField(
        state = state,
        controller = controller,
        states = PixelControlStateSet.Normal,
        placeholder = placeholder,
        style = style,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        minLines = minLines,
        maxLines = maxLines,
        inputType = inputType,
        textAlign = textAlign,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        fillColor = fillColor,
        borderColor = borderColor,
        focusNode = focusNode,
        key = key,
        // A non-blank placeholder is the historical spoken label, not an omission sentinel.
        semanticLabel = semanticLabel.takeIf { candidate -> candidate.isNotBlank() },
        semanticHint = semanticHint,
        semanticError = semanticError,
        decoration = null,
        legacyFacade = true,
    )
}

/**
 * 执行 `PixelWidgets` 的 `TextField` 公开行为；具体参数、返回和副作用见下文。
 *
 * Controlled pixel text input with visible [decoration] and one merged accessibility node.
 *
 * [decoration] is explicit so this additive overload cannot change the JVM descriptor or default
 * bridge of the original TextField facade. A decoration error replaces its helper while preserving
 * its caller-formatted counter. Required state adds only a label marker and never validates input.
 *
 * @param state Caller-owned editable text, selection, composition, and focus state.
 * @param controller Controller that mutates and observes [state].
 * @param decoration Explicit label, supporting, validation, required, and counter presentation.
 * @param placeholder Text painted while [state] is empty.
 * @param style Optional legacy style override above theme component tokens.
 * @param enabled Whether the field can receive focus and edit actions.
 * @param readOnly Whether focus is allowed while text mutation is suppressed.
 * @param autofocus Whether the field requests focus after mounting.
 * @param minLines Minimum visible line count, coerced to at least one.
 * @param maxLines Maximum visible line count, coerced to at least [minLines].
 * @param inputType Platform input type requested from the active IME.
 * @param textAlign Direction-aware alignment of editable text.
 * @param textInputAction Platform IME action dispatched by this field.
 * @param onChanged Callback invoked after controlled text mutation.
 * @param onSubmitted Callback invoked by the configured IME action.
 * @param fillColor Optional concrete fill override.
 * @param borderColor Optional concrete outline override.
 * @param focusNode Optional caller-owned focus node.
 * @param key Stable retained identity for input, decoration, focus, and semantics.
 * @param semanticLabel Optional accessibility name overriding [FormFieldDecoration.label].
 * @param semanticHint Optional accessibility instruction merged with visible supporting text.
 * @param semanticError Optional legacy validation fallback used when decoration error is blank.
 */
@kotlin.jvm.JvmName("TextFieldWithDecoration")
public fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    decoration: FormFieldDecoration,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    inputType: PixelInputType = PixelInputType.TEXT,
    textAlign: TextAlign = TextAlign.START,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    focusNode: FocusNode? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    semanticHint: String? = null,
    semanticError: String? = null,
): Widget {
    return buildTextField(
        state = state,
        controller = controller,
        states = PixelControlStateSet.Normal,
        placeholder = placeholder,
        style = style,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        minLines = minLines,
        maxLines = maxLines,
        inputType = inputType,
        textAlign = textAlign,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        fillColor = fillColor,
        borderColor = borderColor,
        focusNode = focusNode,
        key = key,
        semanticLabel = semanticLabel,
        semanticHint = semanticHint,
        semanticError = semanticError,
        decoration = decoration,
        legacyFacade = false,
    )
}

/**
 * 执行 `PixelWidgets` 的 `TextField` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware text input whose persistent visual states are supplied through [states].
 *
 * Transient focus is merged by the retained input widget. A non-blank [semanticError] always adds
 * [PixelControlState.Error], while Loading keeps the field focusable but suppresses editing.
 * [style], [fillColor], and [borderColor] remain explicit compatibility overrides above component
 * and foundation theme tokens.
 */
@kotlin.jvm.JvmName("TextFieldWithControlStates")
public fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    states: PixelControlStateSet,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    inputType: PixelInputType = PixelInputType.TEXT,
    textAlign: TextAlign = TextAlign.START,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    focusNode: FocusNode? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    semanticHint: String? = null,
    semanticError: String? = null,
): Widget {
    return buildTextField(
        state = state,
        controller = controller,
        states = states,
        placeholder = placeholder,
        style = style,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        minLines = minLines,
        maxLines = maxLines,
        inputType = inputType,
        textAlign = textAlign,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        fillColor = fillColor,
        borderColor = borderColor,
        focusNode = focusNode,
        key = key,
        semanticLabel = semanticLabel,
        semanticHint = semanticHint,
        semanticError = semanticError,
        decoration = null,
        legacyFacade = false,
    )
}

/**
 * 执行 `PixelWidgets` 的 `TextField` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware controlled text input with explicit [decoration].
 *
 * Persistent [states] follow the existing TextField capability priority. Decoration consumes the
 * same `components.textField` family, and focus remains an additive input outline. This overload
 * has a dedicated JVM name and does not replace either existing TextField descriptor.
 *
 * @param state Caller-owned editable text, selection, composition, and focus state.
 * @param controller Controller that mutates and observes [state].
 * @param states Persistent visual and capability states supplied by the caller.
 * @param decoration Explicit label, supporting, validation, required, and counter presentation.
 * @param placeholder Text painted while [state] is empty.
 * @param style Optional legacy style override above theme component tokens.
 * @param enabled Whether the field can receive focus and edit actions.
 * @param readOnly Whether focus is allowed while text mutation is suppressed.
 * @param autofocus Whether the field requests focus after mounting.
 * @param minLines Minimum visible line count, coerced to at least one.
 * @param maxLines Maximum visible line count, coerced to at least [minLines].
 * @param inputType Platform input type requested from the active IME.
 * @param textAlign Direction-aware alignment of editable text.
 * @param textInputAction Platform IME action dispatched by this field.
 * @param onChanged Callback invoked after controlled text mutation.
 * @param onSubmitted Callback invoked by the configured IME action.
 * @param fillColor Optional concrete fill override.
 * @param borderColor Optional concrete outline override.
 * @param focusNode Optional caller-owned focus node.
 * @param key Stable retained identity for input, decoration, focus, and semantics.
 * @param semanticLabel Optional accessibility name overriding [FormFieldDecoration.label].
 * @param semanticHint Optional accessibility instruction merged with visible supporting text.
 * @param semanticError Optional legacy validation fallback used when decoration error is blank.
 */
@kotlin.jvm.JvmName("TextFieldWithControlStatesAndDecoration")
public fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    states: PixelControlStateSet,
    decoration: FormFieldDecoration,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1,
    inputType: PixelInputType = PixelInputType.TEXT,
    textAlign: TextAlign = TextAlign.START,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    focusNode: FocusNode? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    semanticHint: String? = null,
    semanticError: String? = null,
): Widget {
    return buildTextField(
        state = state,
        controller = controller,
        states = states,
        placeholder = placeholder,
        style = style,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        minLines = minLines,
        maxLines = maxLines,
        inputType = inputType,
        textAlign = textAlign,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        fillColor = fillColor,
        borderColor = borderColor,
        focusNode = focusNode,
        key = key,
        semanticLabel = semanticLabel,
        semanticHint = semanticHint,
        semanticError = semanticError,
        decoration = decoration,
        legacyFacade = false,
    )
}

/** Builds either the legacy TextField facade or the state-aware overload without changing public ABI. */
private fun buildTextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    states: PixelControlStateSet,
    placeholder: String,
    style: TextFieldStyle,
    enabled: Boolean,
    readOnly: Boolean,
    autofocus: Boolean,
    minLines: Int,
    maxLines: Int,
    inputType: PixelInputType,
    textAlign: TextAlign,
    textInputAction: TextInputAction,
    onChanged: ((String) -> Unit)?,
    onSubmitted: ((String) -> Unit)?,
    fillColor: PixelColor?,
    borderColor: PixelColor?,
    focusNode: FocusNode?,
    key: Any?,
    semanticLabel: String?,
    semanticHint: String?,
    semanticError: String?,
    /** Optional explicit decoration; null preserves the exact legacy widget tree. */
    decoration: FormFieldDecoration?,
    /** Whether scope-less mounting must retain the pre-token visual contract. */
    legacyFacade: Boolean,
): Widget {
    /** Decoration normalized once so visual and semantic error precedence cannot diverge. */
    val resolvedDecoration = decoration?.resolveForTextField(
        semanticLabel = semanticLabel,
        semanticHint = semanticHint,
        semanticError = semanticError,
    )
    /** Final field name supplied to the input semantic boundary. */
    val effectiveSemanticLabel = resolvedDecoration?.semanticLabel ?: semanticLabel
    /** Final field hint containing every currently visible non-error decoration string. */
    val effectiveSemanticHint = resolvedDecoration?.semanticHint ?: semanticHint
    /** Final validation message shared by visible error text and platform error state. */
    val effectiveSemanticError = resolvedDecoration?.semanticError ?: semanticError
    /** Persistent states after public enabled and validation inputs are normalized. */
    var effectiveStates = states
    if (!enabled) effectiveStates += PixelControlState.Disabled
    if (!effectiveSemanticError.isNullOrBlank()) effectiveStates += PixelControlState.Error
    /** Disabled fields leave traversal, while Loading fields deliberately retain their focus node. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Stable diagnostic label; rendered semantics resolve omitted labels from theme tokens. */
    val focusDebugLabel = effectiveSemanticLabel?.takeIf { label -> label.isNotBlank() }
        ?: placeholder.ifBlank { LEGACY_TEXT_FIELD_DEBUG_LABEL }
    return AutomaticFocusAction(
        enabled = focusable,
        autofocus = autofocus,
        focusNode = focusNode,
        debugLabel = focusDebugLabel,
        key = key,
    ) { _, effectiveFocusNode ->
        /** Stable input widget whose retained position is preserved inside decorated fields. */
        val field = TextFieldWidget(
            state = state,
            controller = controller,
            states = effectiveStates,
            placeholder = placeholder,
            style = style,
            enabled = enabled,
            readOnly = readOnly,
            autofocus = autofocus,
            minLines = minLines,
            maxLines = maxLines,
            inputType = inputType,
            textAlign = textAlign,
            textInputAction = textInputAction,
            onChanged = onChanged,
            onSubmitted = onSubmitted,
            fillColor = fillColor,
            borderColor = borderColor,
            focusNode = effectiveFocusNode,
            semanticLabel = effectiveSemanticLabel,
            semanticHint = effectiveSemanticHint,
            semanticError = effectiveSemanticError,
            semanticRequired = resolvedDecoration?.required == true,
            legacyFacade = legacyFacade,
            key = key,
        )
        /** A null decoration returns the exact pre-M5-2 TextField subtree. */
        resolvedDecoration?.let { resolved ->
            FormFieldDecorationWidget(
                decoration = resolved,
                states = effectiveStates,
                readOnly = readOnly,
                child = field,
                key = key,
            )
        } ?: field
    }
}

/**
 * 像素风水平滑块。
 *
 * @param value      当前位置，0.0（左端）..1.0（右端）
 * @param onDrag     手指拖动时实时调用（适合需要即时反馈的场景，如 gap 大小）
 * @param onRelease  手指抬起时调用（适合代价较高的场景，如 dot 尺寸）
 * @param activeColor 填充区颜色，默认橙色
 * @param trackColor  轨道/边框颜色，默认白色
 * @param key Slider 焦点、语义与 retained 状态共用的稳定 identity。
 * @param semanticLabel 与当前值分离的无障碍控件名称。
 * @param semanticValue 可选本地化值；默认使用百分比。
 * @param semanticSteps 两端点之间的离散步数；`0` 表示连续值。
 * @param enabled 是否参与指针、键盘、DPAD 和无障碍动作。
 */
public fun Slider(
    value: Float,
    onDrag: (Float) -> Unit = {},
    onRelease: (Float) -> Unit = {},
    activeColor: PixelColor = PixelColor.fromRgb(200, 100, 0),
    trackColor: PixelColor = PixelColor.White,
    key: Any? = null,
    semanticLabel: String = "Slider",
    semanticValue: String? = null,
    semanticSteps: Int = 0,
    enabled: Boolean = true,
): Widget {
    return buildSlider(
        value = value,
        states = PixelControlStateSet.Normal,
        onDrag = onDrag,
        onRelease = onRelease,
        // Each historical default is an independent omission sentinel under an explicit theme.
        activeColor = activeColor.takeUnless { color -> color == LEGACY_SLIDER_ACTIVE_COLOR },
        trackColor = trackColor.takeUnless { color -> color == LEGACY_SLIDER_TRACK_COLOR },
        key = key,
        semanticLabel = semanticLabel.takeUnless { candidate -> candidate == LEGACY_SLIDER_SEMANTIC_LABEL },
        semanticValue = semanticValue,
        semanticSteps = semanticSteps,
        enabled = enabled,
        legacyFacade = true,
        legacyActiveColor = activeColor,
        legacyTrackColor = trackColor,
    )
}

/**
 * 执行 `PixelWidgets` 的 `Slider` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware Slider with theme-resolved colors and geometry.
 *
 * Null drag and release callbacks make the control visually disabled. Loading also suppresses
 * value mutation, but unlike Disabled it retains the current focus node.
 */
@kotlin.jvm.JvmName("SliderWithControlStates")
public fun Slider(
    value: Float,
    states: PixelControlStateSet,
    onDrag: ((Float) -> Unit)? = null,
    onRelease: ((Float) -> Unit)? = null,
    activeColor: PixelColor? = null,
    trackColor: PixelColor? = null,
    key: Any? = null,
    semanticLabel: String? = null,
    semanticValue: String? = null,
    semanticSteps: Int = 0,
    enabled: Boolean = true,
): Widget {
    return buildSlider(
        value = value,
        states = states,
        onDrag = onDrag,
        onRelease = onRelease,
        activeColor = activeColor,
        trackColor = trackColor,
        key = key,
        semanticLabel = semanticLabel,
        semanticValue = semanticValue,
        semanticSteps = semanticSteps,
        enabled = enabled,
        legacyFacade = false,
        legacyActiveColor = LEGACY_SLIDER_ACTIVE_COLOR,
        legacyTrackColor = LEGACY_SLIDER_TRACK_COLOR,
    )
}

/** Builds one Slider while retaining facade provenance until mounted theme resolution. */
@Suppress("LongParameterList")
private fun buildSlider(
    value: Float,
    states: PixelControlStateSet,
    onDrag: ((Float) -> Unit)?,
    onRelease: ((Float) -> Unit)?,
    activeColor: PixelColor?,
    trackColor: PixelColor?,
    key: Any?,
    semanticLabel: String?,
    semanticValue: String?,
    semanticSteps: Int,
    enabled: Boolean,
    /** Whether a scope-less mount must preserve the pre-token Slider visuals. */
    legacyFacade: Boolean,
    /** Exact active color passed through the historical facade. */
    legacyActiveColor: PixelColor,
    /** Exact track color passed through the historical facade. */
    legacyTrackColor: PixelColor,
): Widget {
    /** 已处理 NaN 与越界输入的当前键盘操作基准值。 */
    val normalizedValue = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    /** 离散语义步数对应的键盘增量，连续模式使用固定默认增量。 */
    val keyboardStep = if (semanticSteps > 0) 1f / (semanticSteps + 1) else DEFAULT_SLIDER_KEYBOARD_STEP
    /** Whether any consumer can observe a value submitted by this state-aware Slider. */
    val hasValueCallback = onDrag != null || onRelease != null
    /** Persistent states after enabled and callback availability are normalized. */
    var effectiveStates = states
    if (!enabled || !hasValueCallback) effectiveStates += PixelControlState.Disabled
    /** Loading blocks mutation without removing an already focused control from traversal. */
    val loading = PixelControlState.Loading in effectiveStates
    /** Disabled is the only state that removes this control's focus eligibility. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Pointer, key, and semantic mutation require a focusable non-loading control. */
    val interactive = focusable && !loading
    /** 键盘与无障碍动作共享的完整值提交契约。 */
    val commitValue: (Float) -> Boolean = { requestedValue ->
        if (!interactive) {
            false
        } else {
            /** 提交给两个公开回调的安全目标值。 */
            val nextValue = if (requestedValue.isNaN()) 0f else requestedValue.coerceIn(0f, 1f)
            onDrag?.invoke(nextValue)
            onRelease?.invoke(nextValue)
            true
        }
    }
    /** 将四个方向键映射成沿 Slider 范围的离散提交。 */
    val keyHandler: (PixelKeyEvent) -> Boolean = { event ->
        when (event.key) {
            PixelKey.ARROW_RIGHT,
            PixelKey.ARROW_UP,
            -> commitValue((normalizedValue + keyboardStep).coerceAtMost(1f))
            PixelKey.ARROW_LEFT,
            PixelKey.ARROW_DOWN,
            -> commitValue((normalizedValue - keyboardStep).coerceAtLeast(0f))
            else -> false
        }
    }
    /** Stable diagnostic label; rendered semantics resolve omitted labels from theme tokens. */
    val focusDebugLabel = semanticLabel?.takeIf { label -> label.isNotBlank() }
        ?: LEGACY_SLIDER_SEMANTIC_LABEL
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = focusDebugLabel,
        onKeyEvent = keyHandler.takeIf { interactive },
        key = key,
    ) { _, _ ->
        SliderWidget(
            value = value,
            states = effectiveStates,
            onDrag = { nextValue -> onDrag?.invoke(nextValue) },
            onRelease = { nextValue -> onRelease?.invoke(nextValue) },
            onSetValue = commitValue,
            activeColor = activeColor,
            trackColor = trackColor,
            semanticLabel = semanticLabel,
            semanticValue = semanticValue,
            semanticSteps = semanticSteps,
            enabled = interactive,
            focusable = focusable,
            legacyFacade = legacyFacade,
            legacyActiveColor = legacyActiveColor,
            legacyTrackColor = legacyTrackColor,
            key = key,
        )
    }
}

/** 创建 `OutlinedButton` retained widget，并把调用参数冻结到后续布局与绘制使用的配置中。 */
public fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    style: ButtonStyle = ButtonStyle.Default,
    enabled: Boolean = true,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
): Widget {
    return buildOutlinedButton(
        text = text,
        onPressed = onPressed,
        states = PixelControlStateSet.Normal,
        style = style,
        enabled = enabled,
        fillColor = fillColor,
        borderColor = borderColor,
        key = key,
        legacyFacade = true,
    )
}

/**
 * 执行 `PixelWidgets` 的 `OutlinedButton` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware outlined button.
 *
 * Selected, Error, and Loading are persistent caller states. Loading suppresses activation while
 * preserving focus; Disabled suppresses activation, focus traversal, hover, press, and motion.
 */
@kotlin.jvm.JvmName("OutlinedButtonWithControlStates")
public fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    states: PixelControlStateSet,
    style: ButtonStyle = ButtonStyle.Default,
    enabled: Boolean = true,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
): Widget {
    return buildOutlinedButton(
        text = text,
        onPressed = onPressed,
        states = states,
        style = style,
        enabled = enabled,
        fillColor = fillColor,
        borderColor = borderColor,
        key = key,
        legacyFacade = false,
    )
}

/** Builds an outlined button while preserving the distinction between old and state-aware facades. */
private fun buildOutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    states: PixelControlStateSet,
    style: ButtonStyle,
    enabled: Boolean,
    fillColor: PixelColor?,
    borderColor: PixelColor?,
    key: Any?,
    /** Whether scope-less mounting must retain the pre-token visual contract. */
    legacyFacade: Boolean,
): Widget {
    /** 同时满足调用方启用状态与可执行回调的实际输入状态。 */
    var effectiveStates = states
    if (!enabled || onPressed == null) effectiveStates += PixelControlState.Disabled
    /** Disabled removes focus eligibility; Loading only blocks activation. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Loading is inert while retaining the current focus node and indicator. */
    val effectiveEnabled = focusable && PixelControlState.Loading !in effectiveStates
    /** Pointer、keyboard 与 semantics 共用的布尔激活动作。 */
    val activate: (() -> Boolean)? = onPressed?.takeIf { effectiveEnabled }?.let { callback ->
        {
            callback()
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = text,
        onKeyEvent = activate?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        OutlinedButtonWidget(
            text = text,
            onPressed = onPressed,
            states = effectiveStates,
            style = style,
            enabled = enabled,
            fillColor = fillColor,
            borderColor = borderColor,
            semanticAction = activate,
            legacyFacade = legacyFacade,
            key = key,
        )
    }
}

/**
 * 无边框文字按钮。
 *
 * 默认不增加 padding，按钮尺寸由文字自然决定。需要更大的点击区域时，通过 [style]
 * 显式提供 [PixelTextButtonStyle.padding]。
 */
public fun TextButton(
    text: String,
    onPressed: (() -> Unit)?,
    style: TextButtonStyle = TextButtonStyle.Default,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return buildTextButton(
        text = text,
        onPressed = onPressed,
        states = PixelControlStateSet.Normal,
        style = style,
        enabled = enabled,
        key = key,
        legacyFacade = true,
    )
}

/**
 * 执行 `PixelWidgets` 的 `TextButton` 公开行为；具体参数、返回和副作用见下文。
 *
 * State-aware borderless text button with the same availability contract as [OutlinedButton].
 */
@kotlin.jvm.JvmName("TextButtonWithControlStates")
public fun TextButton(
    text: String,
    onPressed: (() -> Unit)?,
    states: PixelControlStateSet,
    style: TextButtonStyle = TextButtonStyle.Default,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return buildTextButton(
        text = text,
        onPressed = onPressed,
        states = states,
        style = style,
        enabled = enabled,
        key = key,
        legacyFacade = false,
    )
}

/** Builds a text button while preserving the distinction between old and state-aware facades. */
private fun buildTextButton(
    text: String,
    onPressed: (() -> Unit)?,
    states: PixelControlStateSet,
    style: TextButtonStyle,
    enabled: Boolean,
    key: Any?,
    /** Whether scope-less mounting must retain the pre-token visual contract. */
    legacyFacade: Boolean,
): Widget {
    /** 同时满足调用方启用状态与可执行回调的实际输入状态。 */
    var effectiveStates = states
    if (!enabled || onPressed == null) effectiveStates += PixelControlState.Disabled
    /** Disabled removes focus eligibility; Loading retains focus but disables activation. */
    val focusable = PixelControlState.Disabled !in effectiveStates
    /** Effective activation excludes the retained Loading state. */
    val effectiveEnabled = focusable && PixelControlState.Loading !in effectiveStates
    /** Pointer、keyboard 与 semantics 共用的布尔激活动作。 */
    val activate: (() -> Boolean)? = onPressed?.takeIf { effectiveEnabled }?.let { callback ->
        {
            callback()
            true
        }
    }
    return AutomaticFocusAction(
        enabled = focusable,
        debugLabel = text,
        onKeyEvent = activate?.let(::activationKeyHandler),
        key = key,
    ) { _, _ ->
        TextButtonWidget(
            text = text,
            onPressed = onPressed,
            states = effectiveStates,
            style = style,
            enabled = enabled,
            semanticAction = activate,
            legacyFacade = legacyFacade,
            key = key,
        )
    }
}

/** Default continuous Slider keyboard delta when no discrete semantic steps are declared. */
private const val DEFAULT_SLIDER_KEYBOARD_STEP: Float = 0.05f

/** Diagnostic fallback used when the localizable TextField semantic label is resolved later. */
private const val LEGACY_TEXT_FIELD_DEBUG_LABEL: String = "TextField"

/** Exact old Slider default interpreted as an omitted localizable semantic-label sentinel. */
private const val LEGACY_SLIDER_SEMANTIC_LABEL: String = "Slider"

/** Pre-token Slider active color retained only to identify the old default-argument pair. */
private val LEGACY_SLIDER_ACTIVE_COLOR: PixelColor = PixelColor.fromRgb(200, 100, 0)

/** Pre-token Slider track color retained only to identify the old default-argument pair. */
private val LEGACY_SLIDER_TRACK_COLOR: PixelColor = PixelColor.White
