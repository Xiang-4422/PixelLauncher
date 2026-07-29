package com.purride.pixellauncherv2.ui.screen

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AnimatedPixelLoadingBar
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.Padding
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Row
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.Widget
import com.purride.pixelui.animation.PixelTickerProvider
import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.launcher.ContactListModel
import com.purride.pixellauncherv2.launcher.LauncherSpacing
import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 拨号模块的「联系人」页：拼音首字母分组的目录列表。
 *
 * 字母头用低对比文字而非实心条——目录里分组头是高频元素，逐组实心会让整页
 * 布满色块，噪声盖过姓名本身（与键盘去边框同一个道理）。
 */
fun ContactsPage(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
    listState: PixelListState,
    listController: PixelListController,
    onContactPressed: (lookupKey: String) -> Unit,
    onRequestContactsPermission: () -> Unit,
): Widget {
    // 缺权限与"通讯录为空"是两件事：前者可操作，必须给出恢复路径。
    if (!uiState.hasContactsPermission && !uiState.isContactsLoading) {
        return contactsPermissionEmptyState(theme, onRequestContactsPermission)
    }
    val rows = ContactListModel.rows(uiState.contacts)
    if (uiState.isContactsLoading && rows.isEmpty()) {
        return centeredContactsLoading(theme, vsync)
    }
    if (rows.isEmpty()) {
        return centeredContactsStatus("NO CONTACTS", theme)
    }
    return ListViewBuilder(
        itemCount = rows.size,
        state = listState,
        controller = listController,
        spacing = LauncherSpacing.ROW_SPACING,
        itemBuilder = { index ->
            when (val row = rows[index]) {
                is ContactListModel.Row.Header -> contactGroupHeader(row.letter, theme)
                is ContactListModel.Row.Person -> contactRow(row.contact, theme, onContactPressed)
            }
        },
    )
}

/** 分组字母头：低对比、不可点。 */
private fun contactGroupHeader(letter: String, theme: LauncherTheme): Widget = Padding(
    horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
    child = contactText(
        text = letter,
        color = theme.sms.timestamp,
        theme = theme,
    ),
)

/** 联系人行：姓名为主，多号码时右侧低对比 xN 徽标。 */
private fun contactRow(
    contact: ContactDetail,
    theme: LauncherTheme,
    onContactPressed: (lookupKey: String) -> Unit,
): Widget {
    val badge = ContactListModel.numberBadge(contact.numbers.size)
    return Semantics(
        label = contact.displayName,
        role = PixelSemanticRole.BUTTON,
        child = GestureDetector(
            onTap = { onContactPressed(contact.lookupKey) },
            child = Padding(
                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                vertical = LauncherSpacing.ROW_SPACING,
                child = Row(
                    spacing = LauncherSpacing.ROW_SPACING,
                    crossAxisAlignment = CrossAxisAlignment.CENTER,
                    children = buildList {
                        add(
                            Expanded(
                                child = contactText(
                                    text = contact.displayName.uppercase(),
                                    color = theme.sms.sender,
                                    theme = theme,
                                ),
                            ),
                        )
                        if (badge.isNotEmpty()) {
                            add(contactText(text = badge, color = theme.sms.timestamp, theme = theme))
                        }
                    },
                ),
            ),
        ),
    )
}

/**
 * 联系人详情：全部号码，每个号码都可拨打或发短信。
 *
 * 多号码联系人不猜"默认号码"——外呼不可逆，选择权必须留给用户；
 * 这也是列表点按进详情而不是直接拨打的原因。
 */
fun ContactDetailScreen(
    uiState: LauncherUiState,
    theme: LauncherTheme,
    onCallNumber: (number: String) -> Unit,
    onSmsNumber: (number: String) -> Unit,
): Widget {
    val contact = uiState.contacts.firstOrNull { entry ->
        entry.lookupKey == uiState.contactDetailLookupKey
    } ?: return centeredContactsStatus("CONTACT NOT FOUND", theme)
    return Padding(
        horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
        vertical = LauncherSpacing.CONTENT_VERTICAL,
        child = Column(
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            mainAxisSize = MainAxisSize.MAX,
            spacing = LauncherSpacing.SETTINGS_SECTION_GAP,
            children = buildList {
                add(
                    contactText(
                        text = contact.displayName.uppercase(),
                        color = theme.text.primary,
                        theme = theme,
                    ),
                )
                contact.numbers.forEach { phone ->
                    add(contactNumberBlock(phone.typeLabel, phone.number, theme, onCallNumber, onSmsNumber))
                }
            },
        ),
    )
}

/** 一个号码块：类型标签（可缺省）+ 号码 + CALL/SMS 动作。 */
private fun contactNumberBlock(
    typeLabel: String,
    number: String,
    theme: LauncherTheme,
    onCallNumber: (number: String) -> Unit,
    onSmsNumber: (number: String) -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MIN,
    spacing = 1,
    children = buildList {
        if (typeLabel.isNotEmpty()) {
            add(contactText(text = typeLabel, color = theme.sms.timestamp, theme = theme))
        }
        add(
            Row(
                spacing = LauncherSpacing.ROW_SPACING,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Expanded(
                        child = contactText(
                            text = number,
                            color = theme.text.primary,
                            theme = theme,
                        ),
                    ),
                    contactActionButton("CALL", theme) { onCallNumber(number) },
                    contactActionButton("SMS", theme) { onSmsNumber(number) },
                ),
            ),
        )
    },
)

/** 边框动作按钮；详情页没有唯一主操作（可能有多个号码），一律不反色。 */
private fun contactActionButton(
    label: String,
    theme: LauncherTheme,
    onTap: () -> Unit,
): Widget = Semantics(
    label = label,
    role = PixelSemanticRole.BUTTON,
    child = GestureDetector(
        onTap = onTap,
        child = Container(
            borderColor = theme.button.border,
            padding = EdgeInsets.symmetric(
                horizontal = LauncherSpacing.CONTENT_HORIZONTAL,
                vertical = LauncherSpacing.ROW_SPACING,
            ),
            child = contactText(text = label, color = theme.button.text, theme = theme),
        ),
    ),
)

/** 缺少联系人权限时的空态：说明原因 + 可点的授权入口（被拒后唯一恢复路径）。 */
private fun contactsPermissionEmptyState(
    theme: LauncherTheme,
    onRequestContactsPermission: () -> Unit,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.CENTER,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    spacing = LauncherSpacing.ROW_SPACING * 2,
    children = listOf(
        smsStatusText("NO CONTACTS ACCESS", theme),
        Semantics(
            label = "GRANT CONTACTS ACCESS",
            role = PixelSemanticRole.BUTTON,
            child = GestureDetector(
                onTap = onRequestContactsPermission,
                child = Container(
                    borderColor = theme.button.border,
                    padding = EdgeInsets.symmetric(
                        horizontal = LauncherSpacing.CONTENT_HORIZONTAL * 2,
                        vertical = LauncherSpacing.ROW_SPACING,
                    ),
                    child = contactText(text = "GRANT", color = theme.button.text, theme = theme),
                ),
            ),
        ),
    ),
)

private fun centeredContactsStatus(text: String, theme: LauncherTheme): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(smsStatusText(text, theme)),
)

private fun centeredContactsLoading(
    theme: LauncherTheme,
    vsync: PixelTickerProvider,
): Widget = Column(
    crossAxisAlignment = CrossAxisAlignment.STRETCH,
    mainAxisSize = MainAxisSize.MAX,
    mainAxisAlignment = MainAxisAlignment.CENTER,
    spacing = 0,
    children = listOf(
        Padding(
            horizontal = LauncherSpacing.CONTENT_HORIZONTAL * 2,
            child = AnimatedPixelLoadingBar(
                vsync = vsync,
                color = theme.text.primary,
                trackColor = theme.text.primary,
                width = 96,
                height = 9,
                blockWidth = 9,
                trailWidth = 5,
                key = "contacts-loading-bar",
            ),
        ),
    ),
)

private fun contactText(
    text: String,
    color: PixelColor,
    theme: LauncherTheme,
): Widget = Text(
    text,
    style = theme.typography.textStyle(color = color),
    overflow = TextOverflow.ELLIPSIS,
    softWrap = false,
    maxLines = 1,
)
