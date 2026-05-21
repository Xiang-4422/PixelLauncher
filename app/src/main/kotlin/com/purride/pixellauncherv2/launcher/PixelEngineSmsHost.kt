package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.purride.pixelcore.PixelShape as EnginePixelShape
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixelui.PageController
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.ScrollController
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToPage
import com.purride.pixelui.showItem
import com.purride.pixelui.state.PixelListState
import com.purride.pixellauncherv2.ui.screen.SmsInboxScreen
import com.purride.pixellauncherv2.ui.screen.SmsRolePromptScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadDetailScreen
import com.purride.pixellauncherv2.ui.screen.SmsThreadsScreen
import com.purride.pixellauncherv2.ui.theme.LauncherTheme
import com.purride.pixellauncherv2.ui.theme.LauncherThemes
import com.purride.pixellauncherv2.viewmodel.LauncherUiState

/**
 * 持有所有 SMS 屏幕的 pixel-engine 宿主。
 *
 * 一个宿主承接四个 SMS 模式：
 * - [LauncherMode.SMS_ROLE_PROMPT]    — 提示用户设为默认短信应用
 * - [LauncherMode.SMS_THREADS]        — 会话列表
 * - [LauncherMode.SMS_INBOX]          — 未读消息翻页
 * - [LauncherMode.SMS_THREAD_DETAIL]  — 消息详情 + 草稿输入
 *
 * 所有控制器/状态由宿主持有，每次 [update] 同步来自 [LauncherUiState] 的最新数据。
 */
internal class PixelEngineSmsHost(
    context: Context,
    private val callbacks: Callbacks,
) {
    // ── State fields ──────────────────────────────────────────────────────────
    private var uiState: LauncherUiState = LauncherUiState()
    private var theme: LauncherTheme = LauncherThemes.GREEN_PHOSPHOR
    private var chargeTick: Int = 0

    // ── SMS_THREADS list ──────────────────────────────────────────────────────
    private val threadListController = ScrollController()
    private val threadListState = threadListController.create()

    // ── SMS_INBOX pager + per-page scroll ─────────────────────────────────────
    private val inboxPagerController = PageController()
    /** Created with pageCount=1; synced to actual count in [update]. */
    private val inboxPagerState = inboxPagerController.create(pageCount = 1)
    private val inboxScrollController = ScrollController()
    /** Per-page scroll states, lazily grown as needed. */
    private val inboxScrollStates = mutableListOf<PixelListState>()

    // ── SMS_THREAD_DETAIL message list + draft ────────────────────────────────
    private val msgListController = ScrollController()
    private val msgListState = msgListController.create()
    private val draftController = TextEditingController()
    private val draftState = draftController.create()

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            content = { buildContent() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    /**
     * 每次 MainActivity 渲染帧时调用。
     *
     * 负责：
     * 1. 更新内部 model 字段
     * 2. 按需同步翻页/滚动位置
     * 3. 按需同步草稿文本
     * 4. 触发 hostView 重绘
     */
    fun update(
        state: LauncherUiState,
        theme: LauncherTheme,
        screenProfile: ScreenProfile,
        chargeTick: Int,
    ) {
        val active = state.mode in SMS_MODES
        uiState = state
        this.theme = theme
        this.chargeTick = chargeTick

        rootView.visibility = if (active) View.VISIBLE else View.GONE
        if (!active) return

        setup.hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape.toEngineShape(),
        )
        setup.hostView.backgroundColor = theme.backgroundColor
        setup.hostView.pixelGridColor  = theme.pixelGridColor

        // Grow inbox scroll-state list if needed; sync pager page count
        val needed = state.unreadSmsEntries.size.coerceAtLeast(1)
        while (inboxScrollStates.size < needed) {
            inboxScrollStates.add(inboxScrollController.create())
        }
        if (inboxPagerState.pageCount != needed) {
            inboxPagerController.sync(
                state = inboxPagerState,
                axis = com.purride.pixelcore.PixelAxis.HORIZONTAL,
                pageCount = needed,
            )
        }

        // Sync inbox pager page
        if (state.mode == LauncherMode.SMS_INBOX) {
            val targetPage = state.smsSelectedIndex.coerceIn(
                0, (state.unreadSmsEntries.size - 1).coerceAtLeast(0),
            )
            if (inboxPagerState.currentPage != targetPage) {
                inboxPagerController.jumpToPage(inboxPagerState, targetPage)
            }
        }

        // Sync thread list scroll
        if (state.mode == LauncherMode.SMS_THREADS) {
            val target = state.smsThreadSelectedIndex.coerceIn(
                0, (state.smsThreads.size - 1).coerceAtLeast(0),
            )
            threadListController.showItem(threadListState, target)
        }

        // Sync message list scroll to end (newest message)
        if (state.mode == LauncherMode.SMS_THREAD_DETAIL && state.smsMessages.isNotEmpty()) {
            msgListController.jumpToEnd(msgListState)
        }

        // Sync draft text
        syncDraftState()

        setup.hostView.invalidate()
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

    private fun buildContent(): Widget = when (uiState.mode) {
        LauncherMode.SMS_ROLE_PROMPT -> SmsRolePromptScreen(
            theme = theme,
            onRequestRole = callbacks.onRequestSmsRole,
        )
        LauncherMode.SMS_THREADS -> SmsThreadsScreen(
            uiState = uiState,
            theme = theme,
            chargeTick = chargeTick,
            listState = threadListState,
            listController = threadListController,
            onOpenThread = callbacks.onOpenThread,
        )
        LauncherMode.SMS_INBOX -> SmsInboxScreen(
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
        else -> com.purride.pixelui.Center(
            child = com.purride.pixelui.Text(""),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────

    data class Callbacks(
        /** 用户点击 CONTINUE → 申请默认短信角色 */
        val onRequestSmsRole: () -> Unit,
        /** 用户点击会话行或收件箱消息 → 进入详情 */
        val onOpenThread: (threadId: Long, address: String) -> Unit,
        /** 用户在收件箱翻页 → 更新选中索引 */
        val onSelectSmsIndex: (Int) -> Unit,
        /** 草稿文字变更 */
        val onDraftChanged: (String) -> Unit,
        /** 用户点击 SEND 或提交输入法 */
        val onSendDraft: () -> Unit,
    )

    companion object {
        val SMS_MODES = setOf(
            LauncherMode.SMS_ROLE_PROMPT,
            LauncherMode.SMS_THREADS,
            LauncherMode.SMS_INBOX,
            LauncherMode.SMS_THREAD_DETAIL,
        )

        private fun PixelShape.toEngineShape(): EnginePixelShape =
            EnginePixelShape.valueOf(name)
    }
}
