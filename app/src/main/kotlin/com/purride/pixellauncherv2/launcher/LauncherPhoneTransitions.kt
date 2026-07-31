package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.model.ContactEntry

/**
 * Phone / Contacts 域的纯状态转换（ADR-0001 阶段 2 拆分，来源 LauncherStateTransitions）。
 *
 * 承载拨号模块的路由流程、通话记录、拨号输入/T9 匹配、联系人目录与编辑草稿、
 * 通话能力快照的写入。对外入口仍是 [LauncherStateTransitions] facade；行为与拆分前
 * 逐字节等价。
 */
object LauncherPhoneTransitions {

    /** 打开通话记录页；选中下标归零。 */
    fun showCallLog(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.DIALER,
            returnMode = LauncherMode.HOME,
            // 读不到通话记录时直接落到拨号盘：让用户停在一个空页上没有意义，
            // 而拨号盘只需要 CALL_PHONE，与记录权限无关。
            callPageIndex = if (state.hasCallLogPermission) CallPageIndex.RECENT else CallPageIndex.DIAL,
        )
    }

    /** 关闭拨号模块，返回 Home；拨号盘输入一并清空。 */
    fun hideCallLog(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.HOME,
            dialInput = "",
            dialMatches = emptyList(),
        )
    }

    /** 切换拨号模块的页（最近通话 / 拨号盘）。 */
    fun selectCallPage(state: LauncherState, index: Int): LauncherState {
        return state.copy(callPageIndex = CallPageIndex.coerce(index))
    }

    /** 更新拨号盘输入；号码变化时匹配结果先清空，由异步检索回填。 */
    fun updateDialInput(state: LauncherState, input: String): LauncherState {
        if (state.dialInput == input) {
            return state
        }
        return state.copy(dialInput = input, dialMatches = emptyList())
    }

    /** 回填 T9 检索结果；输入已变化时丢弃这次结果，避免旧结果盖住新号码。 */
    fun updateDialMatches(
        state: LauncherState,
        input: String,
        matches: List<ContactEntry>,
    ): LauncherState {
        if (state.dialInput != input) {
            return state
        }
        return state.copy(dialMatches = matches)
    }

    /** 同步通话记录数据。 */
    fun updateCallLogGroups(
        state: LauncherState,
        groups: List<CallLogGroup>,
    ): LauncherState {
        return state.copy(
            callLogGroups = groups,
            isCallLogLoading = false,
        )
    }

    /**
     * 根据通话记录读取能力与现有缓存决定首次加载提示。
     *
     * 无读取权限或已有缓存时不展示 loading，后台刷新仍可继续执行。
     */
    fun prepareCallLogLoading(
        state: LauncherState,
        canReadCallLog: Boolean,
    ): LauncherState {
        return state.copy(
            isCallLogLoading = canReadCallLog && state.callLogGroups.isEmpty(),
        )
    }

    /**
     * 打开联系人详情。只校验 lookupKey 非空，不校验来源 mode——当前生产调用均来自
     * 拨号模块（ContactsController），是否需要来源限制留待产品决策
     * （见 docs/testing/launcher-transition-baseline.md §6）。
     */
    fun showContactDetail(state: LauncherState, lookupKey: String): LauncherState {
        if (lookupKey.isBlank()) {
            return state
        }
        return state.copy(
            mode = LauncherMode.CONTACT_DETAIL,
            contactDetailLookupKey = lookupKey,
        )
    }

    /** 关闭联系人详情，回到拨号模块的联系人页。 */
    fun hideContactDetail(state: LauncherState): LauncherState {
        return state.copy(
            mode = LauncherMode.DIALER,
            callPageIndex = CallPageIndex.CONTACTS,
            contactDetailLookupKey = "",
        )
    }

    /**
     * 打开联系人编辑器。[lookupKey] 为空串时是新建；编辑既有联系人时
     * 姓名草稿预填当前名，"新增号码"草稿始终从空开始。
     */
    fun showContactEditor(state: LauncherState, lookupKey: String): LauncherState {
        val existingName = state.contacts
            .firstOrNull { contact -> contact.lookupKey == lookupKey }
            ?.displayName
            .orEmpty()
        return state.copy(
            mode = LauncherMode.CONTACT_EDITOR,
            contactEditorLookupKey = lookupKey,
            contactEditorNameDraft = existingName,
            contactEditorNumberDraft = "",
        )
    }

    /** 关闭编辑器：编辑既有联系人回其详情，新建回联系人页；草稿一并丢弃。 */
    fun hideContactEditor(state: LauncherState): LauncherState {
        val editedExisting = state.contactEditorLookupKey.isNotBlank()
        return state.copy(
            mode = if (editedExisting) LauncherMode.CONTACT_DETAIL else LauncherMode.DIALER,
            callPageIndex = if (editedExisting) state.callPageIndex else CallPageIndex.CONTACTS,
            contactDetailLookupKey = if (editedExisting) state.contactEditorLookupKey else "",
            contactEditorLookupKey = "",
            contactEditorNameDraft = "",
            contactEditorNumberDraft = "",
        )
    }

    /** 编辑器姓名草稿。 */
    fun updateContactEditorName(state: LauncherState, name: String): LauncherState {
        return state.copy(contactEditorNameDraft = name)
    }

    /** 编辑器"新增号码"草稿。 */
    fun updateContactEditorNumber(state: LauncherState, number: String): LauncherState {
        return state.copy(contactEditorNumberDraft = number)
    }

    /** 联系人目录开始加载；已有数据时不清空，静默换新避免列表闪空。 */
    fun beginContactsLoading(state: LauncherState): LauncherState {
        return state.copy(isContactsLoading = state.contacts.isEmpty())
    }

    /** 同步联系人目录与读取权限。 */
    fun updateContacts(
        state: LauncherState,
        hasPermission: Boolean,
        contacts: List<ContactDetail>,
    ): LauncherState {
        return state.copy(
            contacts = contacts,
            isContactsLoading = false,
            hasContactsPermission = hasPermission,
        )
    }

    /** 同步是否具备发起通话的权限。 */
    fun updateCallCapability(
        state: LauncherState,
        hasCallPhonePermission: Boolean,
        hasCallLogPermission: Boolean,
    ): LauncherState {
        return state.copy(
            hasCallPhonePermission = hasCallPhonePermission,
            hasCallLogPermission = hasCallLogPermission,
        )
    }
}
