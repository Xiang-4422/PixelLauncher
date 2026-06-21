package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelBitmap
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelSpriteSheet
import com.purride.pixelui.internal.AlignDirectionalWidget
import com.purride.pixelui.internal.AlignWidget
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
public typealias ButtonStyle = PixelButtonStyle
public typealias TextButtonStyle = PixelTextButtonStyle
public typealias TextFieldStyle = PixelTextFieldStyle
public typealias TextOverflow = PixelTextOverflow
public typealias TextInputAction = PixelTextInputAction

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

public fun Padding(
    child: Widget,
    padding: EdgeInsets,
    key: Any? = null,
): Widget {
    return PaddingWidget(child = child, padding = padding, key = key)
}

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

public fun PaddingDirectional(
    child: Widget,
    padding: EdgeInsetsDirectional,
    key: Any? = null,
): Widget {
    return PaddingDirectionalWidget(child = child, padding = padding, key = key)
}

public fun Align(
    child: Widget,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return AlignWidget(child = child, alignment = alignment, key = key)
}

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

public fun AlignDirectional(
    child: Widget,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    key: Any? = null,
): Widget {
    return AlignDirectionalWidget(child = child, alignment = alignment, key = key)
}

public fun SizedBox(
    width: Int? = null,
    height: Int? = null,
    child: Widget? = null,
    key: Any? = null,
): Widget {
    return SizedBoxWidget(width = width, height = height, child = child, key = key)
}

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

public fun GestureDetector(
    child: Widget,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    key: Any? = null,
): Widget {
    return GestureDetectorWidget(child = child, onTap = onTap, onLongPress = onLongPress, key = key)
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
 * Draws one frame from [PixelSpriteSheet] without scaling.
 */
public fun Sprite(
    sheet: PixelSpriteSheet,
    frameIndex: Int,
    key: Any? = null,
): Widget {
    return SpriteWidget(sheet = sheet, frameIndex = frameIndex, key = key)
}

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

public fun Stack(
    children: List<Widget>,
    alignment: Alignment = Alignment.TOP_START,
    key: Any? = null,
): Widget {
    return StackWidget(children = children, alignment = alignment, key = key)
}

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

public fun Opacity(
    opacity: Float,
    child: Widget,
    key: Any? = null,
): Widget {
    return OpacityWidget(opacity = opacity, child = child, key = key)
}

public fun ClipRect(
    child: Widget,
    key: Any? = null,
): Widget {
    return ClipRectWidget(child = child, key = key)
}

public object Transform {
    public fun translate(
        offset: IntOffset,
        child: Widget,
        key: Any? = null,
    ): Widget {
        return TransformTranslateWidget(offset = offset, child = child, key = key)
    }
}

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

public fun Wrap(
    children: List<Widget>,
    spacing: Int = 0,
    runSpacing: Int = 0,
    key: Any? = null,
): Widget {
    return WrapWidget(children = children, spacing = spacing, runSpacing = runSpacing, key = key)
}

public fun AspectRatio(
    aspectRatio: Float,
    child: Widget,
    key: Any? = null,
): Widget {
    return AspectRatioWidget(aspectRatio = aspectRatio, child = child, key = key)
}

public fun ConstrainedBox(
    constraints: PixelBoxConstraints,
    child: Widget,
    key: Any? = null,
): Widget {
    return ConstrainedBoxWidget(constraints = constraints, child = child, key = key)
}

public fun FittedBox(
    child: Widget,
    key: Any? = null,
): Widget {
    return FittedBoxWidget(child = child, key = key)
}

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

public fun PageView(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    pages: List<Widget>,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PageViewWidget(
        axis = axis,
        controller = controller,
        state = state,
        pages = pages,
        onPageChanged = onPageChanged,
        key = key,
    )
}

public fun PageViewBuilder(
    axis: Axis,
    controller: PixelPagerController,
    state: PixelPagerState,
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    onPageChanged: ((Int) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return PageViewWidget(
        axis = axis,
        controller = controller,
        state = state,
        pages = List(itemCount) { index -> itemBuilder(index) },
        key = key,
        onPageChanged = onPageChanged,
    )
}

public fun ListView(
    items: List<Widget>,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return ListViewWidget(
        items = items,
        state = state,
        controller = controller,
        spacing = spacing,
        key = key,
    )
}

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
        return LazyListViewWidget(
            itemCount = itemCount,
            itemBuilder = itemBuilder,
            itemExtent = fixedItemExtent,
            state = state,
            controller = controller,
            spacing = spacing,
            cacheExtent = cacheExtent,
            key = key,
        )
    }
    val estimatedExtent = estimatedItemExtent
    if (estimatedExtent != null && estimatedExtent > 0) {
        return VariableLazyListViewWidget(
            itemCount = itemCount,
            itemBuilder = itemBuilder,
            estimatedItemExtent = estimatedExtent,
            state = state,
            controller = controller,
            spacing = spacing,
            cacheExtent = cacheExtent,
            key = key,
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
    return ListView(
        items = separatedItems,
        state = state,
        controller = controller,
        spacing = 0,
        key = key,
    )
}

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
    return LazySeparatedListViewWidget(
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
    )
}

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

public fun SliverPinnedHeader(
    child: Widget,
    key: Any? = null,
): PixelSliver {
    return PixelSliverPinnedHeader(child = child, key = key)
}

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

public fun CustomScrollView(
    slivers: List<PixelSliver>,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    return CustomScrollViewWidget(
        slivers = slivers,
        state = state,
        controller = controller,
        key = key,
    )
}

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
    return GridViewWidget(
        items = items,
        cellWidth = cellWidth,
        cellHeight = cellHeight,
        state = state,
        controller = controller,
        spacing = spacing,
        runSpacing = runSpacing,
        key = key,
    )
}

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
    return LazyGridViewWidget(
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
    )
}

public fun Scrollbar(
    child: Widget,
    state: PixelListState,
    thumbColor: PixelColor = PixelColor.White,
    trackColor: PixelColor? = null,
    width: Int = 1,
    key: Any? = null,
): Widget {
    return ScrollbarWidget(
        child = child,
        state = state,
        thumbColor = thumbColor,
        trackColor = trackColor,
        width = width,
        key = key,
    )
}

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
): Widget {
    return RefreshIndicatorWidget(
        child = child,
        state = state,
        controller = controller,
        thresholdPx = thresholdPx,
        enabled = enabled,
        indicatorColor = indicatorColor,
        armedColor = armedColor,
        refreshingColor = refreshingColor,
        onRefresh = onRefresh,
        key = key,
    )
}

public fun SingleChildScrollView(
    child: Widget,
    state: PixelListState,
    controller: PixelListController,
    key: Any? = null,
): Widget {
    return SingleChildScrollViewWidget(
        child = child,
        state = state,
        controller = controller,
        key = key,
    )
}

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
): Widget {
    return TextFieldWidget(
        state = state,
        controller = controller,
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
    )
}

/**
 * 像素风水平滑块。
 *
 * @param value      当前位置，0.0（左端）..1.0（右端）
 * @param onDrag     手指拖动时实时调用（适合需要即时反馈的场景，如 gap 大小）
 * @param onRelease  手指抬起时调用（适合代价较高的场景，如 dot 尺寸）
 * @param activeColor 填充区颜色，默认橙色
 * @param trackColor  轨道/边框颜色，默认白色
 */
public fun Slider(
    value: Float,
    onDrag: (Float) -> Unit = {},
    onRelease: (Float) -> Unit = {},
    activeColor: PixelColor = PixelColor.fromRgb(200, 100, 0),
    trackColor: PixelColor = PixelColor.White,
    key: Any? = null,
): Widget = SliderWidget(
    value = value,
    onDrag = onDrag,
    onRelease = onRelease,
    activeColor = activeColor,
    trackColor = trackColor,
    key = key,
)

public fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    style: ButtonStyle = ButtonStyle.Default,
    enabled: Boolean = true,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
    key: Any? = null,
): Widget {
    return OutlinedButtonWidget(
        text = text,
        onPressed = onPressed,
        style = style,
        enabled = enabled,
        fillColor = fillColor,
        borderColor = borderColor,
        key = key,
    )
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
    return TextButtonWidget(
        text = text,
        onPressed = onPressed,
        style = style,
        enabled = enabled,
        key = key,
    )
}
