package com.purride.pixelui

/**
 * 标准组件使用的可本地化默认标签。
 *
 * 标签数据也是 [PixelLocalizationBundle] 的平台中立协议，因此由 runtime artifact 持有；
 * 具体标准组件只读取该协议。每个值必须非空，避免 semantics 静默失去可访问名称。
 */
public data class PixelLabelTokens(
    /** 记录 `PixelLabelTokens` 的 `confirm` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val confirm: String = "OK",
    /** 表示 `PixelLabelTokens` 当前是否满足 `cancel` 对应条件。 */
    public val cancel: String = "CANCEL",
    /** 记录 `PixelLabelTokens` 的 `dismiss` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val dismiss: String = "Dismiss",
    /** 记录 `PixelLabelTokens` 的 `empty` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val empty: String = "EMPTY",
    /** 保存 `PixelLabelTokens` 的 `error` 结果或失败信息。 */
    public val error: String = "ERROR",
    /** 记录 `PixelLabelTokens` 的 `loading` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val loading: String = "LOADING",
    /** 记录 `PixelLabelTokens` 的 `button` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val button: String = "Button",
    /** 保存 `PixelLabelTokens` 对外传递的 `textButton` 数据。 */
    public val textButton: String = "Text button",
    /** 保存 `PixelLabelTokens` 对外传递的 `textField` 数据。 */
    public val textField: String = "Text field",
    /** 记录 `PixelLabelTokens` 的 `listTile` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val listTile: String = "ListTile",
    /** 记录 `PixelLabelTokens` 的 `checkbox` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val checkbox: String = "Checkbox",
    /** 记录 `PixelLabelTokens` 的 `switch` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val switch: String = "Switch",
    /** 提供 `PixelLabelTokens` 用于识别或兼容校验的 `slider` 值。 */
    public val slider: String = "Slider",
    /** 记录 `PixelLabelTokens` 的 `tabs` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val tabs: String = "Tabs",
    /** 记录 `PixelLabelTokens` 的 `segmentedControl` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val segmentedControl: String = "SegmentedControl",
    /** 保存 `PixelLabelTokens` 对外传递的 `valueAdjuster` 数据。 */
    public val valueAdjuster: String = "ValueAdjuster",
    /** 记录 `PixelLabelTokens` 的 `decrease` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val decrease: String = "Decrease",
    /** 记录 `PixelLabelTokens` 的 `increase` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val increase: String = "Increase",
    /** 记录 `PixelLabelTokens` 的 `menu` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val menu: String = "Menu",
    /** 记录 `PixelLabelTokens` 的 `dropdown` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val dropdown: String = "Dropdown",
    /** 记录 `PixelLabelTokens` 的 `dialog` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val dialog: String = "Dialog",
    /** 记录 `PixelLabelTokens` 的 `bottomSheet` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val bottomSheet: String = "Bottom sheet",
    /** 记录 `PixelLabelTokens` 的 `toast` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val toast: String = "Toast",
    /** 记录 `PixelLabelTokens` 的 `snackbar` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val snackbar: String = "Snackbar",
    /** 记录 `PixelLabelTokens` 的 `tooltip` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val tooltip: String = "Tooltip",
    /** 记录 `PixelLabelTokens` 的 `progress` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val progress: String = "Progress",
    /** 记录 `PixelLabelTokens` 的 `refresh` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val refresh: String = "Refresh",
    /** 记录 `PixelLabelTokens` 的 `scrollbar` 配置或运行值，读取与更新均遵守所属类型约束。 */
    public val scrollbar: String = "Scrollbar",
    /** 提供 `PixelLabelTokens` 用于识别或兼容校验的 `slidable` 值。 */
    public val slidable: String = "Slidable",
) {
    init {
        validateRuntimeLabels(
            "confirm" to confirm,
            "cancel" to cancel,
            "dismiss" to dismiss,
            "empty" to empty,
            "error" to error,
            "loading" to loading,
            "button" to button,
            "textButton" to textButton,
            "textField" to textField,
            "listTile" to listTile,
            "checkbox" to checkbox,
            "switch" to switch,
            "slider" to slider,
            "tabs" to tabs,
            "segmentedControl" to segmentedControl,
            "valueAdjuster" to valueAdjuster,
            "decrease" to decrease,
            "increase" to increase,
            "menu" to menu,
            "dropdown" to dropdown,
            "dialog" to dialog,
            "bottomSheet" to bottomSheet,
            "toast" to toast,
            "snackbar" to snackbar,
            "tooltip" to tooltip,
            "progress" to progress,
            "refresh" to refresh,
            "scrollbar" to scrollbar,
            "slidable" to slidable,
        )
    }

    /** 集中提供 `PixelLabelTokens` 共享的工厂、常量或无状态辅助入口。 */
    public companion object {
        /** 未安装自定义 localization provider 时使用的兼容英文标签。 */
        public val Default: PixelLabelTokens = PixelLabelTokens()
    }
}

/** 校验平台中立标签组，确保每个 semantics fallback 都非空。 */
private fun validateRuntimeLabels(vararg labels: Pair<String, String>) {
    labels.forEach { (name, value) ->
        require(value.isNotBlank()) { "PixelLabelTokens.$name must not be blank" }
    }
}
