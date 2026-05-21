package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.internal.AlignDirectionalWidget
import com.purride.pixelui.internal.AlignWidget
import com.purride.pixelui.internal.ColumnWidget
import com.purride.pixelui.internal.ContainerDirectionalWidget
import com.purride.pixelui.internal.ContainerWidget
import com.purride.pixelui.internal.DecoratedBoxWidget
import com.purride.pixelui.internal.FlexWrapperWidget
import com.purride.pixelui.internal.GestureDetectorWidget
import com.purride.pixelui.internal.LazyListViewWidget
import com.purride.pixelui.internal.LazySeparatedListViewWidget
import com.purride.pixelui.internal.ListViewWidget
import com.purride.pixelui.internal.OutlinedButtonWidget
import com.purride.pixelui.internal.SliderWidget
import com.purride.pixelui.internal.PaddingDirectionalWidget
import com.purride.pixelui.internal.PaddingWidget
import com.purride.pixelui.internal.PageViewWidget
import com.purride.pixelui.internal.PositionedDirectionalWidget
import com.purride.pixelui.internal.PositionedWidget
import com.purride.pixelui.internal.RowWidget
import com.purride.pixelui.internal.SingleChildScrollViewWidget
import com.purride.pixelui.internal.SizedBoxWidget
import com.purride.pixelui.internal.StackWidget
import com.purride.pixelui.internal.TextFieldWidget
import com.purride.pixelui.internal.RichTextWidget
import com.purride.pixelui.internal.TextWidget
import com.purride.pixelui.internal.VariableLazyListViewWidget
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelPagerState
import com.purride.pixelui.state.PixelTextFieldController
import com.purride.pixelui.state.PixelTextFieldState

/**
 * Flutter 风格公开别名层。
 *
 * 公开组件先形成 retained build tree，再由 direct render object pipeline 输出像素结果。
 */
public typealias TextStyle = PixelTextStyle
public typealias ButtonStyle = PixelButtonStyle
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
    key: Any? = null,
): Widget {
    return GestureDetectorWidget(child = child, onTap = onTap, key = key)
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
    itemExtent: Int,
    separatorExtent: Int,
    cacheExtent: Int = 1,
    key: Any? = null,
): Widget {
    return LazySeparatedListViewWidget(
        itemCount = itemCount,
        itemBuilder = itemBuilder,
        separatorBuilder = separatorBuilder,
        itemExtent = itemExtent,
        separatorExtent = separatorExtent,
        state = state,
        controller = controller,
        cacheExtent = cacheExtent,
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
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    fillColor: PixelColor? = null,
    borderColor: PixelColor? = null,
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
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        fillColor = fillColor,
        borderColor = borderColor,
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
