package com.purride.pixellauncherv2.launcher

import com.purride.pixellauncherv2.model.DeviceStatus
import com.purride.pixellauncherv2.model.LauncherStatsSnapshot
import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.model.ContactEntry
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import com.purride.pixelcore.PixelShape

/**
 * Launcher 状态转换的统一入口 facade（ADR-0001 阶段 2）。
 *
 * 108 个公开入口的实现已按九个状态域拆分到独立文件：
 * [LauncherShellTransitions]、[LauncherAppCatalogTransitions]、[LauncherSettingsTransitions]、
 * [LauncherSmsTransitions]、[LauncherPhoneTransitions]、[LauncherEffectTransitions]、
 * [LauncherHomeTransitions]、[LauncherNotificationTransitions]、[LauncherSystemTransitions]。
 *
 * 本对象只做零逻辑委托：签名、默认参数与行为同拆分前逐一等价，既有调用点无需改名。
 * 各入口的领域归属与行为测试证据见 docs/testing/launcher-transition-baseline.md。
 */
object LauncherStateTransitions {

    // ── Shell 与跨页 Flow ────────────────────────────────────────────────────

    /** 委托 [LauncherShellTransitions.showHome]。 */
    fun showHome(state: LauncherState): LauncherState =
        LauncherShellTransitions.showHome(state)

    /** 委托 [LauncherShellTransitions.showSettings]。 */
    fun showSettings(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherShellTransitions.showSettings(state, visibleRows)

    /** 委托 [LauncherShellTransitions.hideSettings]。 */
    fun hideSettings(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideSettings(state)

    /** 委托 [LauncherShellTransitions.showSnake]。 */
    fun showSnake(state: LauncherState): LauncherState =
        LauncherShellTransitions.showSnake(state)

    /** 委托 [LauncherShellTransitions.hideSnake]。 */
    fun hideSnake(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideSnake(state)

    /** 委托 [LauncherShellTransitions.showDiagnostics]。 */
    fun showDiagnostics(state: LauncherState): LauncherState =
        LauncherShellTransitions.showDiagnostics(state)

    /** 委托 [LauncherShellTransitions.hideDiagnostics]。 */
    fun hideDiagnostics(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideDiagnostics(state)

    /** 委托 [LauncherShellTransitions.showDataHealth]。 */
    fun showDataHealth(state: LauncherState): LauncherState =
        LauncherShellTransitions.showDataHealth(state)

    /** 委托 [LauncherShellTransitions.hideDataHealth]。 */
    fun hideDataHealth(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideDataHealth(state)

    /** 委托 [LauncherShellTransitions.showNotificationSettings]。 */
    fun showNotificationSettings(state: LauncherState): LauncherState =
        LauncherShellTransitions.showNotificationSettings(state)

    /** 委托 [LauncherShellTransitions.hideNotificationSettings]。 */
    fun hideNotificationSettings(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideNotificationSettings(state)

    /** 委托 [LauncherShellTransitions.showLoadingPreview]。 */
    fun showLoadingPreview(state: LauncherState): LauncherState =
        LauncherShellTransitions.showLoadingPreview(state)

    /** 委托 [LauncherShellTransitions.hideLoadingPreview]。 */
    fun hideLoadingPreview(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideLoadingPreview(state)

    /** 委托 [LauncherShellTransitions.showIdle]。 */
    fun showIdle(state: LauncherState): LauncherState =
        LauncherShellTransitions.showIdle(state)

    /** 委托 [LauncherShellTransitions.hideIdle]。 */
    fun hideIdle(state: LauncherState): LauncherState =
        LauncherShellTransitions.hideIdle(state)

    /** 委托 [LauncherShellTransitions.updateStatusBarMessage]。 */
    fun updateStatusBarMessage(
        state: LauncherState,
        message: String,
    ): LauncherState =
        LauncherShellTransitions.updateStatusBarMessage(state, message)

    /** 委托 [LauncherShellTransitions.updateStatusBarAction]。 */
    fun updateStatusBarAction(
        state: LauncherState,
        leadingText: String,
        actionLabel: String,
        isDanger: Boolean,
    ): LauncherState =
        LauncherShellTransitions.updateStatusBarAction(state, leadingText, actionLabel, isDanger)

    // ── AppCatalog / Drawer ─────────────────────────────────────────────────

    /** 委托 [LauncherAppCatalogTransitions.showAppActionMenu]。 */
    fun showAppActionMenu(state: LauncherState, selectedIndex: Int): LauncherState =
        LauncherAppCatalogTransitions.showAppActionMenu(state, selectedIndex)

    /** 委托 [LauncherAppCatalogTransitions.hideAppActionMenu]。 */
    fun hideAppActionMenu(state: LauncherState): LauncherState =
        LauncherAppCatalogTransitions.hideAppActionMenu(state)

    /** 委托 [LauncherAppCatalogTransitions.dismissDrawerOverlaysForPagerDrag]。 */
    fun dismissDrawerOverlaysForPagerDrag(state: LauncherState): LauncherState =
        LauncherAppCatalogTransitions.dismissDrawerOverlaysForPagerDrag(state)

    /** 委托 [LauncherAppCatalogTransitions.showAppManagement]。 */
    fun showAppManagement(state: LauncherState, selectedIndex: Int = state.appEditorSelectedIndex): LauncherState =
        LauncherAppCatalogTransitions.showAppManagement(state, selectedIndex)

    /** 委托 [LauncherAppCatalogTransitions.hideAppManagement]。 */
    fun hideAppManagement(state: LauncherState): LauncherState =
        LauncherAppCatalogTransitions.hideAppManagement(state)

    /** 委托 [LauncherAppCatalogTransitions.moveAppEditorSelection]。 */
    fun moveAppEditorSelection(state: LauncherState, direction: Int): LauncherState =
        LauncherAppCatalogTransitions.moveAppEditorSelection(state, direction)

    /** 委托 [LauncherAppCatalogTransitions.updateAppEditorNameDraft]。 */
    fun updateAppEditorNameDraft(state: LauncherState, nameDraft: String): LauncherState =
        LauncherAppCatalogTransitions.updateAppEditorNameDraft(state, nameDraft)

    /** 委托 [LauncherAppCatalogTransitions.updateAppEditorAliasDraft]。 */
    fun updateAppEditorAliasDraft(state: LauncherState, aliasDraft: String): LauncherState =
        LauncherAppCatalogTransitions.updateAppEditorAliasDraft(state, aliasDraft)

    /** 委托 [LauncherAppCatalogTransitions.showAppDrawer]。 */
    fun showAppDrawer(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.showAppDrawer(state, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.prepareDrawerEntryFocus]。 */
    fun prepareDrawerEntryFocus(
        state: LauncherState,
        focusSearch: Boolean,
    ): LauncherState =
        LauncherAppCatalogTransitions.prepareDrawerEntryFocus(state, focusSearch)

    /** 委托 [LauncherAppCatalogTransitions.focusDrawerSearchInput]。 */
    fun focusDrawerSearchInput(state: LauncherState): LauncherState =
        LauncherAppCatalogTransitions.focusDrawerSearchInput(state)

    /** 委托 [LauncherAppCatalogTransitions.beginAppCatalogLoading]。 */
    fun beginAppCatalogLoading(state: LauncherState): LauncherState =
        LauncherAppCatalogTransitions.beginAppCatalogLoading(state)

    /** 委托 [LauncherAppCatalogTransitions.withApps]。 */
    fun withApps(previous: LauncherState, apps: List<AppEntry>, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.withApps(previous, apps, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.moveSelection]。 */
    fun moveSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.moveSelection(state, delta, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.scrollDrawerWindow]。 */
    fun scrollDrawerWindow(state: LauncherState, delta: Int, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.scrollDrawerWindow(state, delta, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.pageSelection]。 */
    fun pageSelection(state: LauncherState, direction: Int, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.pageSelection(state, direction, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.selectIndex]。 */
    fun selectIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.selectIndex(state, index, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.selectDrawerPage]。 */
    fun selectDrawerPage(state: LauncherState, pageIndex: Int, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.selectDrawerPage(state, pageIndex, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.selectByPackageName]。 */
    fun selectByPackageName(state: LauncherState, packageName: String, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.selectByPackageName(state, packageName, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.selectByLetterIndex]。 */
    fun selectByLetterIndex(state: LauncherState, letterIndex: Int, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.selectByLetterIndex(state, letterIndex, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.updateDrawerQuery]。 */
    fun updateDrawerQuery(state: LauncherState, query: String, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.updateDrawerQuery(state, query, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.appendDrawerQuery]。 */
    fun appendDrawerQuery(state: LauncherState, text: String, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.appendDrawerQuery(state, text, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.backspaceDrawerQuery]。 */
    fun backspaceDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.backspaceDrawerQuery(state, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.clearDrawerQuery]。 */
    fun clearDrawerQuery(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.clearDrawerQuery(state, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.exitDrawerSearch]。 */
    fun exitDrawerSearch(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.exitDrawerSearch(state, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.reflowWindow]。 */
    fun reflowWindow(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherAppCatalogTransitions.reflowWindow(state, visibleRows)

    /** 委托 [LauncherAppCatalogTransitions.calculateListStartIndex]。 */
    fun calculateListStartIndex(selectedIndex: Int, visibleRows: Int, totalCount: Int): Int =
        LauncherAppCatalogTransitions.calculateListStartIndex(selectedIndex, visibleRows, totalCount)

    /** 委托 [LauncherAppCatalogTransitions.updateStats]。 */
    fun updateStats(state: LauncherState, stats: LauncherStatsSnapshot): LauncherState =
        LauncherAppCatalogTransitions.updateStats(state, stats)

    // ── Settings ────────────────────────────────────────────────────────────

    /** 委托 [LauncherSettingsTransitions.selectSettingsIndex]。 */
    fun selectSettingsIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState =
        LauncherSettingsTransitions.selectSettingsIndex(state, index, visibleRows)

    /** 委托 [LauncherSettingsTransitions.moveSettingsSelection]。 */
    fun moveSettingsSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState =
        LauncherSettingsTransitions.moveSettingsSelection(state, delta, visibleRows)

    /** 委托 [LauncherSettingsTransitions.scrollSettingsWindow]。 */
    fun scrollSettingsWindow(state: LauncherState, delta: Int, visibleRows: Int): LauncherState =
        LauncherSettingsTransitions.scrollSettingsWindow(state, delta, visibleRows)

    /** 委托 [LauncherSettingsTransitions.reflowSettingsWindow]。 */
    fun reflowSettingsWindow(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherSettingsTransitions.reflowSettingsWindow(state, visibleRows)

    /** 委托 [LauncherSettingsTransitions.updateAppearance]。 */
    fun updateAppearance(
        state: LauncherState,
        selectedPixelShape: PixelShape = state.selectedPixelShape,
        selectedDotSizePx: Int = state.selectedDotSizePx,
        isPixelGapEnabled: Boolean = state.isPixelGapEnabled,
        selectedThemeFamily: LauncherThemeFamily = state.selectedThemeFamily,
        selectedThemeMode: LauncherThemeMode = state.selectedThemeMode,
        fontSelection: LauncherFontSelection = state.fontSelection,
    ): LauncherState =
        LauncherSettingsTransitions.updateAppearance(
            state = state,
            selectedPixelShape = selectedPixelShape,
            selectedDotSizePx = selectedDotSizePx,
            isPixelGapEnabled = isPixelGapEnabled,
            selectedThemeFamily = selectedThemeFamily,
            selectedThemeMode = selectedThemeMode,
            fontSelection = fontSelection,
        )

    /** 委托 [LauncherSettingsTransitions.updateFontLoading]。 */
    fun updateFontLoading(state: LauncherState, isLoading: Boolean): LauncherState =
        LauncherSettingsTransitions.updateFontLoading(state, isLoading)

    /** 委托 [LauncherSettingsTransitions.updateFontCacheSummary]。 */
    fun updateFontCacheSummary(state: LauncherState, summary: String): LauncherState =
        LauncherSettingsTransitions.updateFontCacheSummary(state, summary)

    /** 委托 [LauncherSettingsTransitions.updateUiBehavior]。 */
    fun updateUiBehavior(
        state: LauncherState,
        drawerListAlignment: DrawerListAlignment = state.drawerListAlignment,
        isIdlePageEnabled: Boolean = state.isIdlePageEnabled,
        chargeAutoIdleEnabled: Boolean = state.chargeAutoIdleEnabled,
        inactivityAutoIdleEnabled: Boolean = state.inactivityAutoIdleEnabled,
        idleTimeoutSeconds: Int = state.idleTimeoutSeconds,
        openDrawerInSearchMode: Boolean = state.openDrawerInSearchMode,
        chargeIdleEffect: ChargeIdleEffect = state.chargeIdleEffect,
        isPixelMatterEffectEnabled: Boolean = state.isPixelMatterEffectEnabled,
        pixelMatterEffectMode: PixelMatterEffectMode = state.pixelMatterEffectMode,
        isPixelMatterHandControlEnabled: Boolean = state.isPixelMatterHandControlEnabled,
        isPixelMatterHandDebugEnabled: Boolean = state.isPixelMatterHandDebugEnabled,
    ): LauncherState =
        LauncherSettingsTransitions.updateUiBehavior(
            state = state,
            drawerListAlignment = drawerListAlignment,
            isIdlePageEnabled = isIdlePageEnabled,
            chargeAutoIdleEnabled = chargeAutoIdleEnabled,
            inactivityAutoIdleEnabled = inactivityAutoIdleEnabled,
            idleTimeoutSeconds = idleTimeoutSeconds,
            openDrawerInSearchMode = openDrawerInSearchMode,
            chargeIdleEffect = chargeIdleEffect,
            isPixelMatterEffectEnabled = isPixelMatterEffectEnabled,
            pixelMatterEffectMode = pixelMatterEffectMode,
            isPixelMatterHandControlEnabled = isPixelMatterHandControlEnabled,
            isPixelMatterHandDebugEnabled = isPixelMatterHandDebugEnabled,
        )

    // ── SMS ─────────────────────────────────────────────────────────────────

    /** 委托 [LauncherSmsTransitions.showSmsRolePrompt]。 */
    fun showSmsRolePrompt(state: LauncherState): LauncherState =
        LauncherSmsTransitions.showSmsRolePrompt(state)

    /** 委托 [LauncherSmsTransitions.showSmsThreads]。 */
    fun showSmsThreads(
        state: LauncherState,
        visibleRows: Int,
        pageIndex: Int = SmsPageIndex.UNREAD,
    ): LauncherState =
        LauncherSmsTransitions.showSmsThreads(state, visibleRows, pageIndex)

    /** 委托 [LauncherSmsTransitions.hideSmsThreads]。 */
    fun hideSmsThreads(state: LauncherState): LauncherState =
        LauncherSmsTransitions.hideSmsThreads(state)

    /** 委托 [LauncherSmsTransitions.showSmsThreadDetail]。 */
    fun showSmsThreadDetail(
        state: LauncherState,
        conversationKey: String,
        conversationTitle: String,
        isServiceConversation: Boolean,
        threadId: Long?,
        address: String,
    ): LauncherState =
        LauncherSmsTransitions.showSmsThreadDetail(
            state = state,
            conversationKey = conversationKey,
            conversationTitle = conversationTitle,
            isServiceConversation = isServiceConversation,
            threadId = threadId,
            address = address,
        )

    /** 委托 [LauncherSmsTransitions.showSmsMessageMenu]。 */
    fun showSmsMessageMenu(state: LauncherState, messageId: Long): LauncherState =
        LauncherSmsTransitions.showSmsMessageMenu(state, messageId)

    /** 委托 [LauncherSmsTransitions.hideSmsMessageMenu]。 */
    fun hideSmsMessageMenu(state: LauncherState): LauncherState =
        LauncherSmsTransitions.hideSmsMessageMenu(state)

    /** 委托 [LauncherSmsTransitions.showSmsThreadMenu]。 */
    fun showSmsThreadMenu(state: LauncherState, conversationKey: String): LauncherState =
        LauncherSmsTransitions.showSmsThreadMenu(state, conversationKey)

    /** 委托 [LauncherSmsTransitions.hideSmsThreadMenu]。 */
    fun hideSmsThreadMenu(state: LauncherState): LauncherState =
        LauncherSmsTransitions.hideSmsThreadMenu(state)

    /** 委托 [LauncherSmsTransitions.updateSmsMutedConversations]。 */
    fun updateSmsMutedConversations(state: LauncherState, mutedKeys: Set<String>): LauncherState =
        LauncherSmsTransitions.updateSmsMutedConversations(state, mutedKeys)

    /** 委托 [LauncherSmsTransitions.hideSmsThreadDetail]。 */
    fun hideSmsThreadDetail(state: LauncherState): LauncherState =
        LauncherSmsTransitions.hideSmsThreadDetail(state)

    /** 委托 [LauncherSmsTransitions.finishSmsThreadsLoading]。 */
    fun finishSmsThreadsLoading(state: LauncherState): LauncherState =
        LauncherSmsTransitions.finishSmsThreadsLoading(state)

    /** 委托 [LauncherSmsTransitions.beginForcedSmsRefresh]。 */
    fun beginForcedSmsRefresh(state: LauncherState): LauncherState =
        LauncherSmsTransitions.beginForcedSmsRefresh(state)

    /** 委托 [LauncherSmsTransitions.updateUnreadSmsEntries]。 */
    fun updateUnreadSmsEntries(state: LauncherState, entries: List<SmsMessageEntry>, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.updateUnreadSmsEntries(state, entries, visibleRows)

    /** 委托 [LauncherSmsTransitions.selectSmsIndex]。 */
    fun selectSmsIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.selectSmsIndex(state, index, visibleRows)

    /** 委托 [LauncherSmsTransitions.selectSmsPage]。 */
    fun selectSmsPage(state: LauncherState, index: Int): LauncherState =
        LauncherSmsTransitions.selectSmsPage(state, index)

    /** 委托 [LauncherSmsTransitions.moveSmsSelection]。 */
    fun moveSmsSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.moveSmsSelection(state, delta, visibleRows)

    /** 委托 [LauncherSmsTransitions.reflowSmsWindow]。 */
    fun reflowSmsWindow(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.reflowSmsWindow(state, visibleRows)

    /** 委托 [LauncherSmsTransitions.updateSmsCapability]。 */
    fun updateSmsCapability(
        state: LauncherState,
        isDefaultSmsApp: Boolean,
        smsPermissionState: SmsPermissionState,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsCapability(state, isDefaultSmsApp, smsPermissionState)

    /** 委托 [LauncherSmsTransitions.updateSmsThreads]。 */
    fun updateSmsThreads(
        state: LauncherState,
        threads: List<SmsThreadSummary>,
        visibleRows: Int,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsThreads(state, threads, visibleRows)

    /** 委托 [LauncherSmsTransitions.selectSmsThreadIndex]。 */
    fun selectSmsThreadIndex(state: LauncherState, index: Int, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.selectSmsThreadIndex(state, index, visibleRows)

    /** 委托 [LauncherSmsTransitions.moveSmsThreadSelection]。 */
    fun moveSmsThreadSelection(state: LauncherState, delta: Int, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.moveSmsThreadSelection(state, delta, visibleRows)

    /** 委托 [LauncherSmsTransitions.moveSmsSearchSelection]。 */
    fun moveSmsSearchSelection(
        state: LauncherState,
        delta: Int,
        resultCount: Int,
    ): LauncherState =
        LauncherSmsTransitions.moveSmsSearchSelection(state, delta, resultCount)

    /** 委托 [LauncherSmsTransitions.reflowSmsThreadWindow]。 */
    fun reflowSmsThreadWindow(state: LauncherState, visibleRows: Int): LauncherState =
        LauncherSmsTransitions.reflowSmsThreadWindow(state, visibleRows)

    /** 委托 [LauncherSmsTransitions.updateSmsMessages]。 */
    fun updateSmsMessages(
        state: LauncherState,
        conversationKey: String = state.smsCurrentConversationKey,
        conversationTitle: String = state.smsCurrentConversationTitle,
        isServiceConversation: Boolean = state.smsCurrentIsServiceConversation,
        threadId: Long?,
        address: String,
        messages: List<SmsMessageEntry>,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsMessages(
            state = state,
            conversationKey = conversationKey,
            conversationTitle = conversationTitle,
            isServiceConversation = isServiceConversation,
            threadId = threadId,
            address = address,
            messages = messages,
        )

    /** 委托 [LauncherSmsTransitions.updateSmsAllMessages]。 */
    fun updateSmsAllMessages(
        state: LauncherState,
        messages: List<SmsMessageEntry>,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsAllMessages(state, messages)

    /** 委托 [LauncherSmsTransitions.updateSmsDraftText]。 */
    fun updateSmsDraftText(
        state: LauncherState,
        smsDraftText: String,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsDraftText(state, smsDraftText)

    /** 委托 [LauncherSmsTransitions.updateSmsThreadSearchQuery]。 */
    fun updateSmsThreadSearchQuery(
        state: LauncherState,
        query: String,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsThreadSearchQuery(state, query)

    /** 委托 [LauncherSmsTransitions.updateSmsSendStatus]。 */
    fun updateSmsSendStatus(
        state: LauncherState,
        smsSendStatus: SmsSendStatus,
    ): LauncherState =
        LauncherSmsTransitions.updateSmsSendStatus(state, smsSendStatus)

    // ── Phone / Contacts ────────────────────────────────────────────────────

    /** 委托 [LauncherPhoneTransitions.showCallLog]。 */
    fun showCallLog(state: LauncherState): LauncherState =
        LauncherPhoneTransitions.showCallLog(state)

    /** 委托 [LauncherPhoneTransitions.hideCallLog]。 */
    fun hideCallLog(state: LauncherState): LauncherState =
        LauncherPhoneTransitions.hideCallLog(state)

    /** 委托 [LauncherPhoneTransitions.selectCallPage]。 */
    fun selectCallPage(state: LauncherState, index: Int): LauncherState =
        LauncherPhoneTransitions.selectCallPage(state, index)

    /** 委托 [LauncherPhoneTransitions.updateDialInput]。 */
    fun updateDialInput(state: LauncherState, input: String): LauncherState =
        LauncherPhoneTransitions.updateDialInput(state, input)

    /** 委托 [LauncherPhoneTransitions.updateDialMatches]。 */
    fun updateDialMatches(
        state: LauncherState,
        input: String,
        matches: List<ContactEntry>,
    ): LauncherState =
        LauncherPhoneTransitions.updateDialMatches(state, input, matches)

    /** 委托 [LauncherPhoneTransitions.updateCallLogGroups]。 */
    fun updateCallLogGroups(
        state: LauncherState,
        groups: List<CallLogGroup>,
    ): LauncherState =
        LauncherPhoneTransitions.updateCallLogGroups(state, groups)

    /** 委托 [LauncherPhoneTransitions.prepareCallLogLoading]。 */
    fun prepareCallLogLoading(
        state: LauncherState,
        canReadCallLog: Boolean,
    ): LauncherState =
        LauncherPhoneTransitions.prepareCallLogLoading(state, canReadCallLog)

    /** 委托 [LauncherPhoneTransitions.showContactDetail]。 */
    fun showContactDetail(state: LauncherState, lookupKey: String): LauncherState =
        LauncherPhoneTransitions.showContactDetail(state, lookupKey)

    /** 委托 [LauncherPhoneTransitions.hideContactDetail]。 */
    fun hideContactDetail(state: LauncherState): LauncherState =
        LauncherPhoneTransitions.hideContactDetail(state)

    /** 委托 [LauncherPhoneTransitions.showContactEditor]。 */
    fun showContactEditor(state: LauncherState, lookupKey: String): LauncherState =
        LauncherPhoneTransitions.showContactEditor(state, lookupKey)

    /** 委托 [LauncherPhoneTransitions.hideContactEditor]。 */
    fun hideContactEditor(state: LauncherState): LauncherState =
        LauncherPhoneTransitions.hideContactEditor(state)

    /** 委托 [LauncherPhoneTransitions.updateContactEditorName]。 */
    fun updateContactEditorName(state: LauncherState, name: String): LauncherState =
        LauncherPhoneTransitions.updateContactEditorName(state, name)

    /** 委托 [LauncherPhoneTransitions.updateContactEditorNumber]。 */
    fun updateContactEditorNumber(state: LauncherState, number: String): LauncherState =
        LauncherPhoneTransitions.updateContactEditorNumber(state, number)

    /** 委托 [LauncherPhoneTransitions.beginContactsLoading]。 */
    fun beginContactsLoading(state: LauncherState): LauncherState =
        LauncherPhoneTransitions.beginContactsLoading(state)

    /** 委托 [LauncherPhoneTransitions.updateContacts]。 */
    fun updateContacts(
        state: LauncherState,
        hasPermission: Boolean,
        contacts: List<ContactDetail>,
    ): LauncherState =
        LauncherPhoneTransitions.updateContacts(state, hasPermission, contacts)

    /** 委托 [LauncherPhoneTransitions.updateCallCapability]。 */
    fun updateCallCapability(
        state: LauncherState,
        hasCallPhonePermission: Boolean,
        hasCallLogPermission: Boolean,
    ): LauncherState =
        LauncherPhoneTransitions.updateCallCapability(state, hasCallPhonePermission, hasCallLogPermission)

    // ── Effect / Idle ───────────────────────────────────────────────────────

    /** 委托 [LauncherEffectTransitions.recordInteraction]。 */
    fun recordInteraction(state: LauncherState, uptimeMs: Long): LauncherState =
        LauncherEffectTransitions.recordInteraction(state, uptimeMs)

    // ── Home feed ───────────────────────────────────────────────────────────

    /** 委托 [LauncherHomeTransitions.updateTime]。 */
    fun updateTime(
        state: LauncherState,
        currentTimeText: String,
        currentDateText: String = state.currentDateText,
        currentWeekdayText: String = state.currentWeekdayText,
    ): LauncherState =
        LauncherHomeTransitions.updateTime(state, currentTimeText, currentDateText, currentWeekdayText)

    /** 委托 [LauncherHomeTransitions.updateNextAlarmText]。 */
    fun updateNextAlarmText(state: LauncherState, nextAlarmText: String): LauncherState =
        LauncherHomeTransitions.updateNextAlarmText(state, nextAlarmText)

    /** 委托 [LauncherHomeTransitions.updateCommunicationStatus]。 */
    fun updateCommunicationStatus(
        state: LauncherState,
        missedCallCount: Int,
        unreadSmsCount: Int,
    ): LauncherState =
        LauncherHomeTransitions.updateCommunicationStatus(state, missedCallCount, unreadSmsCount)

    /** 委托 [LauncherHomeTransitions.updateMediaPlayback]。 */
    fun updateMediaPlayback(
        state: LauncherState,
        mediaPlayback: MediaPlaybackSnapshot,
    ): LauncherState =
        LauncherHomeTransitions.updateMediaPlayback(state, mediaPlayback)

    /** 委托 [LauncherHomeTransitions.updateRainHintText]。 */
    fun updateRainHintText(
        state: LauncherState,
        rainHintText: String,
        rainUpdatedTimeText: String = state.rainUpdatedTimeText,
    ): LauncherState =
        LauncherHomeTransitions.updateRainHintText(state, rainHintText, rainUpdatedTimeText)

    /** 委托 [LauncherHomeTransitions.updateScreenUsageSummary]。 */
    fun updateScreenUsageSummary(
        state: LauncherState,
        screenUsageTimeText: String,
        screenOpenCountText: String,
    ): LauncherState =
        LauncherHomeTransitions.updateScreenUsageSummary(state, screenUsageTimeText, screenOpenCountText)

    // ── Notification ────────────────────────────────────────────────────────

    /** 委托 [LauncherNotificationTransitions.updateNotificationSummary]。 */
    fun updateNotificationSummary(
        state: LauncherState,
        notificationSummaryText: String,
        notificationCount: Int,
        notificationSources: List<NotificationSourceInfo> = state.notificationSources,
        notificationItems: List<NotificationSignal> = state.notificationItems,
    ): LauncherState =
        LauncherNotificationTransitions.updateNotificationSummary(
            state = state,
            notificationSummaryText = notificationSummaryText,
            notificationCount = notificationCount,
            notificationSources = notificationSources,
            notificationItems = notificationItems,
        )

    /** 委托 [LauncherNotificationTransitions.updateNotificationRules]。 */
    fun updateNotificationRules(
        state: LauncherState,
        mutedSourceIds: Set<String>,
        prioritySourceIds: Set<String>,
    ): LauncherState =
        LauncherNotificationTransitions.updateNotificationRules(state, mutedSourceIds, prioritySourceIds)

    // ── System / Capabilities ───────────────────────────────────────────────

    /** 委托 [LauncherSystemTransitions.updateDeviceStatus]。 */
    fun updateDeviceStatus(state: LauncherState, deviceStatus: DeviceStatus): LauncherState =
        LauncherSystemTransitions.updateDeviceStatus(state, deviceStatus)

    /** 委托 [LauncherSystemTransitions.updateDataHealth]。 */
    fun updateDataHealth(
        state: LauncherState,
        hasUsageAccess: Boolean,
        hasLocationPermission: Boolean,
        hasCallLogPermission: Boolean,
        hasSmsReadPermission: Boolean,
        hasPostNotificationPermission: Boolean,
        hasNotificationListenerAccess: Boolean,
        dataHealthUpdatedTimeText: String = state.dataHealthUpdatedTimeText,
    ): LauncherState =
        LauncherSystemTransitions.updateDataHealth(
            state = state,
            hasUsageAccess = hasUsageAccess,
            hasLocationPermission = hasLocationPermission,
            hasCallLogPermission = hasCallLogPermission,
            hasSmsReadPermission = hasSmsReadPermission,
            hasPostNotificationPermission = hasPostNotificationPermission,
            hasNotificationListenerAccess = hasNotificationListenerAccess,
            dataHealthUpdatedTimeText = dataHealthUpdatedTimeText,
        )
}
