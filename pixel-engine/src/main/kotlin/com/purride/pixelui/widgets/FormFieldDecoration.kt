package com.purride.pixelui

/**
 * 定义 `FormFieldDecoration` 在 `FormFieldDecoration` 中承担的数据与行为边界。
 *
 * Describes the visible and accessible decoration surrounding one controlled [TextField].
 *
 * Decoration is deliberately caller-owned and contains no validation or text-counting logic.
 * A non-blank [error] replaces [helper] while [counter] remains visible. [required] adds only a
 * required marker to the visible and spoken label; callers must still provide their own validator.
 *
 * @property label Optional label painted before the input surface.
 * @property helper Optional supporting text painted when [error] is absent or blank.
 * @property error Optional validation message that takes visual and semantic priority over [helper].
 * @property required Whether the label carries a visual and semantic required marker.
 * @property counter Optional caller-formatted counter; the SDK performs no UTF-16 or grapheme count.
 */
public data class FormFieldDecoration(
    public val label: String? = null,
    public val helper: String? = null,
    public val error: String? = null,
    public val required: Boolean = false,
    public val counter: String? = null,
)
