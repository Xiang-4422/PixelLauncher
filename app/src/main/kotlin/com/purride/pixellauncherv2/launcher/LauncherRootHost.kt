package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.widget.FrameLayout
import com.purride.pixelcore.PixelAxis
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelShape as EnginePixelShape
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixelui.Alignment
import com.purride.pixelui.Axis
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.GestureDetector
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.MainAxisSize
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PageController
import com.purride.pixelui.PageView
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.TextField
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.jumpToStart
import com.purride.pixelui.showItem
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.ui.screen.DiagnosticsScreen
import com.purride.pixellauncherv2.ui.screen.HomeScreen
import com.purride.pixellauncherv2.ui.screen.IdleScreen
import com.purride.pixellauncherv2.ui.screen.SmsInboxScreen
import com.purride.pixellauncherv2.ui.screen.SmsRolePromptScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadDetailScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadsScreen
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * Phase 8：统一 Launcher 宿主，替换原来 5 个独立 PixelEngineXxxHost。
 *
 * 架构：
 * - HOME / APP_DRAWER / SETTINGS → 3 页横向 [PageView]（主页面 Pager）
 * - SMS_ROLE_PROMPT / SMS_THREADS / SMS_INBOX / SMS_THREAD_DETAIL → 全屏 SMS 内容
 * - DIAGNOSTICS / IDLE → 全屏杂项内容
 *
 * 所有控制器/状态均由本类持有，[update] 负责外部状态同步。
 */
internal class LauncherRootHost(
    context: Context,
    private val callbacks: LauncherCallbacks,
) {
    // ── Mutable model fields ──────────────────────────────────────────────────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.GREEN_PHOSPHOR
    private var chargeTick: Int = 0
    private var screenProfile: ScreenProfile = ScreenProfile(logicalWidth = 1, logicalHeight = 1, dotSizePx = 1)
    private var pixelGapEnabled: Boolean = true

    // ── Main pager: HOME=0, APP_DRAWER=1, SETTINGS=2 ─────────────────────────
    private val mainPagerController = PageController()
    private val mainPagerState = mainPagerController.create(pageCount = MAIN_PAGE_COUNT)

    // ── APP_DRAWER controllers ────────────────────────────────────────────────
    private val drawerTextController = TextEditingController()
    private val drawerQueryState = drawerTextController.create()
    private val drawerListController = ScrollController()
    private val drawerListState = drawerListController.create()
    private var drawerLastSelectedIndex = -1

    // ── SMS_THREADS list ──────────────────────────────────────────────────────
    private val threadListController = ScrollController()
    private val threadListState = threadListController.create()

    // ── SMS_INBOX pager + per-page scroll ─────────────────────────────────────
    private val inboxPagerController = PageController()
    private val inboxPagerState = inboxPagerController.create(pageCount = 1)
    private val inboxScrollController = ScrollController()
    private val inboxScrollStates = mutableListOf<PixelListState>()

    // ── SMS_THREAD_DETAIL message list + draft ────────────────────────────────
    private val msgListController = ScrollController()
    private val msgListState = msgListController.create()
    private val draftController = TextEditingController()
    private val draftState = draftController.create()

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            content = { buildRoot() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    /**
     * 每帧调用。更新内部状态并触发重绘。
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: ScreenProfile,
        chargeTick: Int,
        pixelGapEnabled: Boolean = state.isPixelGapEnabled,
    ) {
        uiState = state
        this.theme = theme
        this.chargeTick = chargeTick
        this.screenProfile = screenProfile
        this.pixelGapEnabled = pixelGapEnabled

        setup.hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape.toEngineShape(),
        )
        setup.hostView.setPixelGapEnabled(pixelGapEnabled)
        setup.hostView.backgroundColor = theme.backgroundColor
        setup.hostView.pixelGridColor  = theme.pixelGridColor

        // ── Sync main pager ───────────────────────────────────────────────────
        val targetMainPage = modeToMainPage(state.mode)
        if (targetMainPage != null && mainPagerState.currentPage != targetMainPage) {
            mainPagerController.jumpToPage(mainPagerState, targetMainPage)
        }

        // ── Sync drawer ───────────────────────────────────────────────────────
        syncDrawerQueryState()
        val selectedIndex = state.selectedIndex.coerceIn(
            0, (drawerApps().size - 1).coerceAtLeast(0),
        )
        if (state.mode == LauncherMode.APP_DRAWER &&
            selectedIndex != drawerLastSelectedIndex
        ) {
            drawerListController.showItem(drawerListState, selectedIndex)
            drawerLastSelectedIndex = selectedIndex
        }

        // ── Sync SMS inbox pager page count ───────────────────────────────────
        val needed = state.unreadSmsEntries.size.coerceAtLeast(1)
        while (inboxScrollStates.size < needed) {
            inboxScrollStates.add(inboxScrollController.create())
        }
        if (inboxPagerState.pageCount != needed) {
            inboxPagerController.sync(
                state = inboxPagerState,
                axis = PixelAxis.HORIZONTAL,
                pageCount = needed,
            )
        }
        if (state.mode == LauncherMode.SMS_INBOX) {
            val targetInboxPage = state.smsSelectedIndex.coerceIn(
                0, (state.unreadSmsEntries.size - 1).coerceAtLeast(0),
            )
            if (inboxPagerState.currentPage != targetInboxPage) {
                inboxPagerController.jumpToPage(inboxPagerState, targetInboxPage)
            }
        }

        // ── Sync SMS thread list scroll ───────────────────────────────────────
        if (state.mode == LauncherMode.SMS_THREADS) {
            val target = state.smsThreadSelectedIndex.coerceIn(
                0, (state.smsThreads.size - 1).coerceAtLeast(0),
            )
            threadListController.showItem(threadListState, target)
        }

        // ── Sync SMS message list to bottom ───────────────────────────────────
        if (state.mode == LauncherMode.SMS_THREAD_DETAIL && state.smsMessages.isNotEmpty()) {
            msgListController.jumpToEnd(msgListState)
        }

        // ── Sync draft text ───────────────────────────────────────────────────
        syncDraftState()

        setup.hostView.invalidate()
    }

    // ── Content dispatching ───────────────────────────────────────────────────

    private fun buildRoot(): Widget = when (uiState.mode) {
        LauncherMode.HOME,
        LauncherMode.APP_DRAWER,
        LauncherMode.SETTINGS          -> buildMainPager()
        LauncherMode.SMS_ROLE_PROMPT   -> SmsRolePromptScreen(
            theme = theme,
            onRequestRole = callbacks.onRequestSmsRole,
        )
        LauncherMode.SMS_THREADS       -> SmsThreadsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            listState = threadListState,
            listController = threadListController,
            onOpenThread = callbacks.onOpenThread,
        )
        LauncherMode.SMS_INBOX         -> SmsInboxScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            pagerController = inboxPagerController,
            pagerState = inboxPagerState,
            scrollController = inboxScrollController,
            scrollStates = inboxScrollStates.toList().ifEmpty {
                listOf(inboxScrollController.create())
            },
            onSelectSmsIndex = callbacks.onSelectSmsIndex,
            onOpenThread = callbacks.onOpenThread,
        )
        LauncherMode.SMS_THREAD_DETAIL -> SmsThreadDetailScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            msgListState = msgListState,
            msgListController = msgListController,
            draftController = draftController,
            draftState = draftState,
            onDraftChanged = callbacks.onDraftChanged,
            onSendDraft = callbacks.onSendDraft,
        )
        LauncherMode.DIAGNOSTICS       -> DiagnosticsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            screenProfile = screenProfile,
        )
        LauncherMode.IDLE              -> IdleScreen(
            uiState = uiState,
            theme = theme,
        )
    }

    // ── Main pager ────────────────────────────────────────────────────────────

    private fun buildMainPager(): Widget = PageView(
        axis = Axis.HORIZONTAL,
        controller = mainPagerController,
        state = mainPagerState,
        pages = listOf(
            buildHomePage(),
            buildDrawerPage(),
            buildSettingsPage(),
        ),
        onPageChanged = { page ->
            MAIN_PAGE_MODES.getOrNull(page)?.let { mode ->
                callbacks.onMainPageChanged(mode)
            }
        },
    )

    private fun buildHomePage(): Widget = HomeScreen(
        uiState = uiState,
        theme = theme,
        chargeTick = chargeTick,
        onOpenContacts = callbacks.onOpenContacts,
        onOpenSms = callbacks.onOpenSms,
    )

    private fun buildSettingsPage(): Widget = com.purride.pixellauncherv2.ui.screen.SettingsScreen(
        uiState = uiState,
        theme = theme,
        onItemAction = callbacks.onSettingsItemAction,
    )

    // ── APP_DRAWER content ────────────────────────────────────────────────────

    private fun buildDrawerPage(): Widget {
        val apps = drawerApps()
        val selectedIndex = uiState.selectedIndex.coerceIn(
            0, apps.lastIndex.coerceAtLeast(0),
        )
        val selectedLabel = apps.getOrNull(selectedIndex)?.label ?: "NONE"
        return Container(
            fillColor = PixelColor.Transparent,
            borderColor = null,
            padding = EdgeInsets.all(4),
            child = Column(
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    drawerSectionTitle(),
                    Row(
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        children = listOf(
                            Expanded(
                                child = drawerInfoCard(
                                    label = "RESULTS",
                                    value = apps.size.toString(),
                                    accent = apps.isEmpty(),
                                ),
                            ),
                            Expanded(
                                child = drawerInfoCard(
                                    label = "SELECTED",
                                    value = selectedLabel.uppercase(),
                                    accent = apps.isNotEmpty(),
                                ),
                            ),
                        ),
                    ),
                    SizedBox(
                        height = 16,
                        child = TextField(
                            state = drawerQueryState,
                            controller = drawerTextController,
                            placeholder = "SEARCH APP",
                            autofocus = uiState.isDrawerSearchFocused,
                            textInputAction = TextInputAction.SEARCH,
                            onChanged = callbacks.onDrawerQueryChanged,
                            onSubmitted = { callbacks.onDrawerSubmitSearch() },
                        ),
                    ),
                    drawerActions(apps),
                    drawerAlphaIndex(apps),
                    SizedBox(
                        height = 54,
                        child = ListViewBuilder(
                            itemCount = apps.size.coerceAtLeast(1),
                            state = drawerListState,
                            controller = drawerListController,
                            itemExtent = DRAWER_ROW_HEIGHT,
                            cacheExtent = 2,
                            spacing = 2,
                            itemBuilder = { index ->
                                val app = apps.getOrNull(index)
                                val isSelected = index == selectedIndex && app != null
                                drawerListItem(
                                    label = app?.label?.uppercase() ?: "NO RESULTS",
                                    selected = isSelected,
                                    enabled = app != null,
                                    onTap = app?.let { { callbacks.onDrawerAppPressed(index) } },
                                )
                            },
                        ),
                    ),
                ),
            ),
        )
    }

    private fun drawerListItem(
        label: String,
        selected: Boolean,
        enabled: Boolean,
        onTap: (() -> Unit)?,
    ): Widget {
        val item = Container(
            height = DRAWER_ROW_HEIGHT,
            fillColor = PixelColor.Transparent,
            borderColor = if (selected) theme.accentColor else theme.primaryColor,
            padding = EdgeInsets.symmetric(horizontal = 2, vertical = 1),
            alignment = when (uiState.drawerListAlignment) {
                DrawerListAlignment.LEFT   -> Alignment.CENTER_START
                DrawerListAlignment.CENTER -> Alignment.CENTER
                DrawerListAlignment.RIGHT  -> Alignment.CENTER_END
            },
            child = Text(
                label,
                style = TextStyle(color = if (enabled) theme.primaryColor else theme.primaryColor),
                overflow = TextOverflow.ELLIPSIS,
            ),
        )
        return if (onTap != null) GestureDetector(onTap = onTap, child = item) else item
    }

    private fun drawerActions(apps: List<AppEntry>): Widget = SizedBox(
        height = 14,
        child = Row(
            spacing = 2,
            crossAxisAlignment = CrossAxisAlignment.STRETCH,
            children = listOf(
                Expanded(
                    child = OutlinedButton(
                        text = "TOP",
                        onPressed = { drawerListController.jumpToStart(drawerListState) },
                        borderColor = theme.primaryColor,
                    ),
                ),
                Expanded(
                    child = OutlinedButton(
                        text = "MID",
                        onPressed = {
                            val index = (apps.size / 2).coerceAtLeast(0)
                            if (apps.isNotEmpty()) {
                                drawerListController.showItem(drawerListState, index)
                                callbacks.onDrawerShowIndex(index)
                            }
                        },
                        borderColor = theme.accentColor,
                    ),
                ),
                Expanded(
                    child = OutlinedButton(
                        text = "END",
                        onPressed = { drawerListController.jumpToEnd(drawerListState) },
                        borderColor = theme.primaryColor,
                    ),
                ),
            ),
        ),
    )

    private fun drawerAlphaIndex(apps: List<AppEntry>): Widget {
        val selectedLetter = apps.getOrNull(
            uiState.selectedIndex.coerceIn(0, apps.lastIndex.coerceAtLeast(0)),
        )?.label?.firstOrNull()?.uppercaseChar()
        return SizedBox(
            height = 14,
            child = Row(
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = INDEX_LETTERS.map { letter ->
                    val isActive = selectedLetter?.let { letter.firstOrNull() == it } == true
                    Expanded(
                        child = OutlinedButton(
                            text = letter,
                            onPressed = {
                                val index = apps.indexOfFirst { app ->
                                    app.label.startsWith(letter, ignoreCase = true) ||
                                        app.englishLabel.startsWith(letter, ignoreCase = true)
                                }
                                if (index >= 0) {
                                    drawerListController.showItem(drawerListState, index)
                                    callbacks.onDrawerShowIndex(index)
                                }
                            },
                            borderColor = if (isActive) theme.accentColor else theme.primaryColor,
                        ),
                    )
                },
            ),
        )
    }

    private fun drawerSectionTitle(): Widget = Container(
        height = 18,
        fillColor = PixelColor.Transparent,
        borderColor = theme.accentColor,
        padding = EdgeInsets.all(3),
        child = Center(
            child = Text(
                data = "APP DRAWER",
                style = TextStyle(color = theme.accentColor),
                overflow = TextOverflow.ELLIPSIS,
            ),
        ),
    )

    private fun drawerInfoCard(label: String, value: String, accent: Boolean): Widget =
        Container(
            height = 20,
            fillColor = PixelColor.Transparent,
            borderColor = if (accent) theme.accentColor else theme.primaryColor,
            padding = EdgeInsets.all(2),
            alignment = Alignment.TOP_START,
            child = Column(
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    Text(
                        data = label,
                        style = TextStyle(color = theme.accentColor),
                        overflow = TextOverflow.ELLIPSIS,
                    ),
                    Text(
                        data = value,
                        style = TextStyle(color = theme.primaryColor),
                        overflow = TextOverflow.ELLIPSIS,
                    ),
                ),
            ),
        )

    // ── Sync helpers ──────────────────────────────────────────────────────────

    private fun syncDrawerQueryState() {
        if (drawerQueryState.text != uiState.drawerQuery) {
            drawerTextController.updateText(
                state = drawerQueryState,
                text = uiState.drawerQuery,
                selectionStart = uiState.drawerQuery.length,
            )
        }
        if (uiState.isDrawerSearchFocused) {
            drawerTextController.requestFocus(drawerQueryState)
        } else if (drawerQueryState.isFocused) {
            drawerTextController.requestBlur(drawerQueryState)
        }
    }

    private fun syncDraftState() {
        val text = uiState.smsDraftText
        if (draftState.text != text) {
            draftController.updateText(
                state = draftState,
                text = text,
                selectionStart = text.length,
            )
        }
    }

    /** 计算当前应显示的 APP 列表（等同于 MainActivity.currentDrawerApps()）。 */
    private fun drawerApps(): List<AppEntry> {
        if (uiState.drawerVisibleApps.isNotEmpty()) return uiState.drawerVisibleApps
        if (uiState.drawerQuery.isNotBlank()) return emptyList()
        return uiState.apps
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        const val MAIN_PAGE_COUNT = 3
        val MAIN_PAGE_MODES = listOf(
            LauncherMode.HOME,
            LauncherMode.APP_DRAWER,
            LauncherMode.SETTINGS,
        )

        private const val DRAWER_ROW_HEIGHT = 13
        private val INDEX_LETTERS = listOf("A", "M", "Z")

        fun modeToMainPage(mode: LauncherMode): Int? = MAIN_PAGE_MODES.indexOf(mode).takeIf { it >= 0 }

        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}
