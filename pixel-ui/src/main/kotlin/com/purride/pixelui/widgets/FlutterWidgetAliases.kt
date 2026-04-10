package com.purride.pixelui

import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelTone
import com.purride.pixelui.internal.AlignDirectionalWidget
import com.purride.pixelui.internal.AlignWidget
import com.purride.pixelui.internal.ColumnWidget
import com.purride.pixelui.internal.ContainerDirectionalWidget
import com.purride.pixelui.internal.ContainerWidget
import com.purride.pixelui.internal.DecoratedBoxWidget
import com.purride.pixelui.internal.FlexWrapperWidget
import com.purride.pixelui.internal.GestureDetectorWidget
import com.purride.pixelui.internal.ListViewWidget
import com.purride.pixelui.internal.OutlinedButtonWidget
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
import com.purride.pixelui.internal.TextWidget
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
typealias TextStyle = PixelTextStyle
typealias ButtonStyle = PixelButtonStyle
typealias ContainerStyle = PixelContainerStyle
typealias TextFieldStyle = PixelTextFieldStyle
typealias TextOverflow = PixelTextOverflow
typealias TextInputAction = PixelTextInputAction
typealias ThemeData = PixelThemeData

fun Padding(
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

fun Padding(
    child: Widget,
    padding: EdgeInsets,
    key: Any? = null,
): Widget {
    return PaddingWidget(child = child, padding = padding, key = key)
}

fun Padding(
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

fun PaddingDirectional(
    child: Widget,
    padding: EdgeInsetsDirectional,
    key: Any? = null,
): Widget {
    return PaddingDirectionalWidget(child = child, padding = padding, key = key)
}

fun Padding(
    child: Widget,
    left: Int = 0,
    top: Int = 0,
    right: Int = 0,
    bottom: Int = 0,
    key: Any? = null,
): Widget {
    return Padding(
        child = child,
        padding = EdgeInsets.only(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
        ),
        key = key,
    )
}

fun Align(
    child: Widget,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return AlignWidget(child = child, alignment = alignment, key = key)
}

fun Center(
    child: Widget,
    key: Any? = null,
): Widget {
    return Align(
        child = child,
        alignment = Alignment.CENTER,
        key = key,
    )
}

fun AlignDirectional(
    child: Widget,
    alignment: AlignmentDirectional = AlignmentDirectional.CENTER,
    key: Any? = null,
): Widget {
    return AlignDirectionalWidget(child = child, alignment = alignment, key = key)
}

fun SizedBox(
    width: Int? = null,
    height: Int? = null,
    child: Widget? = null,
    key: Any? = null,
): Widget {
    return SizedBoxWidget(width = width, height = height, child = child, key = key)
}

fun Expanded(
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

fun Flexible(
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

fun Spacer(
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

fun GestureDetector(
    child: Widget,
    onTap: () -> Unit,
    key: Any? = null,
): Widget {
    return GestureDetectorWidget(child = child, onTap = onTap, key = key)
}

fun Text(
    data: String,
    style: TextStyle = TextStyle.Default,
    theme: ThemeData? = null,
    softWrap: Boolean = false,
    maxLines: Int = 1,
    overflow: PixelTextOverflow = PixelTextOverflow.CLIP,
    textAlign: TextAlign = TextAlign.START,
    key: Any? = null,
): Widget {
    return TextWidget(
        data = data,
        style = style,
        theme = theme,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        key = key,
    )
}

fun DecoratedBox(
    child: Widget? = null,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    padding: Int = 2,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return DecoratedBoxWidget(
        child = child,
        fillTone = fillTone,
        borderTone = borderTone,
        padding = padding,
        alignment = alignment,
        key = key,
    )
}

fun Container(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    margin: EdgeInsets? = null,
    style: ContainerStyle? = null,
    theme: ThemeData? = null,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
    alignment: Alignment = Alignment.CENTER,
    key: Any? = null,
): Widget {
    return ContainerWidget(
        child = child,
        width = width,
        height = height,
        padding = padding,
        margin = margin,
        style = style,
        theme = theme,
        fillTone = fillTone,
        borderTone = borderTone,
        alignment = alignment,
        key = key,
    )
}

fun ContainerDirectional(
    child: Widget? = null,
    width: Int? = null,
    height: Int? = null,
    padding: EdgeInsets? = null,
    paddingDirectional: EdgeInsetsDirectional? = null,
    margin: EdgeInsets? = null,
    marginDirectional: EdgeInsetsDirectional? = null,
    style: ContainerStyle? = null,
    theme: ThemeData? = null,
    fillTone: PixelTone = PixelTone.OFF,
    borderTone: PixelTone? = PixelTone.ON,
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
        style = style,
        theme = theme,
        fillTone = fillTone,
        borderTone = borderTone,
        alignment = alignment,
        key = key,
    )
}

fun Stack(
    children: List<Widget>,
    alignment: Alignment = Alignment.TOP_START,
    key: Any? = null,
): Widget {
    return StackWidget(children = children, alignment = alignment, key = key)
}

fun Positioned(
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

fun PositionedDirectional(
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

fun PositionedFill(
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

fun Row(
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

fun Column(
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

fun PageView(
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

fun PageViewBuilder(
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

fun ListView(
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

fun ListViewBuilder(
    itemCount: Int,
    itemBuilder: (Int) -> Widget,
    state: PixelListState,
    controller: PixelListController,
    spacing: Int = 0,
    key: Any? = null,
): Widget {
    return ListView(
        items = List(itemCount) { index -> itemBuilder(index) },
        state = state,
        controller = controller,
        spacing = spacing,
        key = key,
    )
}

fun ListViewSeparated(
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

fun SingleChildScrollView(
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

fun TextField(
    state: PixelTextFieldState,
    controller: PixelTextFieldController,
    placeholder: String = "",
    style: TextFieldStyle = TextFieldStyle.Default,
    theme: ThemeData? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    autofocus: Boolean = false,
    textInputAction: TextInputAction = TextInputAction.DONE,
    onChanged: ((String) -> Unit)? = null,
    onSubmitted: ((String) -> Unit)? = null,
    key: Any? = null,
): Widget {
    return TextFieldWidget(
        state = state,
        controller = controller,
        placeholder = placeholder,
        style = style,
        theme = theme,
        enabled = enabled,
        readOnly = readOnly,
        autofocus = autofocus,
        textInputAction = textInputAction,
        onChanged = onChanged,
        onSubmitted = onSubmitted,
        key = key,
    )
}

fun OutlinedButton(
    text: String,
    onPressed: (() -> Unit)?,
    style: ButtonStyle = ButtonStyle.Default,
    theme: ThemeData? = null,
    enabled: Boolean = true,
    key: Any? = null,
): Widget {
    return OutlinedButtonWidget(
        text = text,
        onPressed = onPressed,
        style = style,
        theme = theme,
        enabled = enabled,
        key = key,
    )
}
