package com.purride.pixellauncherv2.viewmodel

import com.purride.pixelcore.PixelShape
import com.purride.pixellauncherv2.launcher.AppEntry
import com.purride.pixellauncherv2.launcher.CallPageIndex
import com.purride.pixellauncherv2.launcher.ChargeIdleEffect
import com.purride.pixellauncherv2.launcher.DrawerFocus
import com.purride.pixellauncherv2.launcher.DrawerListAlignment
import com.purride.pixellauncherv2.launcher.LauncherFontFamily
import com.purride.pixellauncherv2.launcher.LauncherFontSelection
import com.purride.pixellauncherv2.launcher.LauncherFontWidthMode
import com.purride.pixellauncherv2.launcher.LauncherMode
import com.purride.pixellauncherv2.launcher.LauncherState
import com.purride.pixellauncherv2.launcher.MediaPlaybackSnapshot
import com.purride.pixellauncherv2.launcher.NotificationSignal
import com.purride.pixellauncherv2.launcher.NotificationSourceInfo
import com.purride.pixellauncherv2.launcher.PixelFontSize
import com.purride.pixellauncherv2.launcher.PixelMatterEffectMode
import com.purride.pixellauncherv2.launcher.PixelTheme
import com.purride.pixellauncherv2.launcher.SmsPageIndex
import com.purride.pixellauncherv2.launcher.SmsPermissionState
import com.purride.pixellauncherv2.launcher.SmsSendStatus
import com.purride.pixellauncherv2.model.CallLogGroup
import com.purride.pixellauncherv2.model.ContactDetail
import com.purride.pixellauncherv2.model.ContactEntry
import com.purride.pixellauncherv2.model.ContactPhone
import com.purride.pixellauncherv2.model.SmsMessageEntry
import com.purride.pixellauncherv2.model.SmsThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 以完整非默认快照保护 [LauncherState] 到 [LauncherUiState] 的 108 字段身份映射。
 *
 * 运行时断言负责验证真实 mapper 行为；源码契约仅用于在 schema 增删或 mapper 漏项时提供明确诊断。
 */
class LauncherUiStateMapperCompletenessTest {

    /**
     * 验证 fixture 除单值枚举外全部偏离默认态，避免 mapper 漏项被相同默认值掩盖。
     */
    @Test
    fun completeFixture_usesNonDefaultValueForEveryConfigurableField() {
        val defaultState = LauncherState()
        val completeState = completeNonDefaultState()
        val fieldsStillAtDefault = EXPECTED_FIELD_NAMES.filter { fieldName ->
            readBackingField(defaultState, fieldName) == readBackingField(completeState, fieldName)
        }

        assertEquals(
            "DrawerFocus 当前只有 LIST 一个合法值，除此之外每个 fixture 字段都必须使用非默认值。",
            setOf("drawerFocus"),
            fieldsStillAtDefault.toSet(),
        )
        assertEquals(
            "如果 DrawerFocus 增加了可选值，应同步把 fixture 改成非默认值并移除例外。",
            1,
            DrawerFocus.values().size,
        )
    }

    /**
     * 调用真实 [toLauncherUiState]，按字段名逐项比较完整输入与输出值。
     */
    @Test
    fun toLauncherUiState_mapsAll108NonDefaultFieldsWithoutCrossingValues() {
        val state = completeNonDefaultState()
        val uiState = state.toLauncherUiState()

        EXPECTED_FIELD_NAMES.forEach { fieldName ->
            assertEquals(
                "字段 `$fieldName` 未按原值映射到 LauncherUiState。",
                readBackingField(state, fieldName),
                readBackingField(uiState, fieldName),
            )
        }
    }

    /**
     * 补充验证两个主构造器和 mapper 的精确清单，让新增、缺失、重复或错接字段直接报出名称。
     */
    @Test
    fun sourceSchemasAndMapper_keepExact108FieldContract() {
        val moduleRoot = resolveModuleRoot()
        val launcherStateSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/launcher/LauncherState.kt")
            .readText()
        val launcherUiStateSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiState.kt")
            .readText()
        val mapperSource = moduleRoot
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/viewmodel/LauncherUiStateMapper.kt")
            .readText()

        val launcherStateFields = extractConstructorFieldNames(launcherStateSource, "LauncherState")
        val launcherUiStateFields = extractConstructorFieldNames(launcherUiStateSource, "LauncherUiState")
        val mapperAssignments = extractMapperAssignments(mapperSource)

        assertExactFieldContract("LauncherState 主构造器", launcherStateFields)
        assertExactFieldContract("LauncherUiState 主构造器", launcherUiStateFields)
        assertExactFieldContract("LauncherUiState mapper 左值", mapperAssignments.map(Pair<String, String>::first))

        val crossedAssignments = mapperAssignments.filter { (target, source) -> target != source }
        assertTrue(
            "mapper 必须保持同名赋值，发现错接：${crossedAssignments.joinToString()}",
            crossedAssignments.isEmpty(),
        )
    }

    /**
     * 构造全部 108 字段均显式赋值的合法状态；同类型字段使用不同哨兵以识别交叉映射。
     */
    private fun completeNonDefaultState(): LauncherState {
        // Drawer 数据同时保证选择下标与可见列表范围一致。
        val apps = List(6) { index ->
            AppEntry(
                label = "应用${index + 1}",
                packageName = "com.example.fixture.app${index + 1}",
                activityName = "FixtureActivity${index + 1}",
                englishLabel = "Fixture App ${index + 1}",
                systemLabel = "System App ${index + 1}",
                aliases = listOf("alias-${index + 1}"),
            )
        }

        // 三组短信数据使用不同消息、会话和标识，避免 List 字段被互换后仍然相等。
        val unreadSmsEntries = listOf(
            smsMessage(
                messageId = 101L,
                threadId = 201L,
                address = "10010",
                body = "未读消息一",
                conversationKey = "thread:201",
            ),
            smsMessage(
                messageId = 102L,
                threadId = 202L,
                address = "10086",
                body = "未读消息二",
                conversationKey = "thread:202",
            ),
            smsMessage(
                messageId = 103L,
                threadId = 203L,
                address = "95533",
                body = "未读消息三",
                conversationKey = "thread:203",
            ),
        )
        val smsThreads = listOf(
            smsThread(threadId = 401L, address = "95588", conversationKey = "service:bank"),
            smsThread(threadId = 402L, address = "13800000002", conversationKey = "thread:402"),
            smsThread(threadId = 403L, address = "13800000003", conversationKey = "thread:403"),
            smsThread(threadId = 404L, address = "13800000004", conversationKey = "thread:404"),
        )
        val smsAllMessages = listOf(
            smsMessage(
                messageId = 201L,
                threadId = 301L,
                address = "10690001",
                body = "全部消息一",
                conversationKey = "thread:301",
            ),
            smsMessage(
                messageId = 202L,
                threadId = 302L,
                address = "10690002",
                body = "全部消息二",
                conversationKey = "thread:302",
            ),
        )
        val smsMessages = listOf(
            smsMessage(
                messageId = 301L,
                threadId = 401L,
                address = "95588",
                body = "当前会话消息",
                conversationKey = "service:bank",
                isServiceConversation = true,
            ),
        )

        // 电话与联系人快照保持 lookupKey、号码及选择目标相互一致。
        val callLogGroups = listOf(
            CallLogGroup(
                callId = 501L,
                number = "13800000001",
                displayTitle = "联系人甲",
                dateMillis = 1_725_000_000_001L,
                durationSeconds = 61L,
                type = 3,
                callCount = 2,
                hasNew = true,
                callIds = listOf(501L, 500L),
            ),
        )
        val dialMatches = listOf(
            ContactEntry(
                displayName = "联系人乙",
                number = "13800000002",
                phoneticName = "lianxirenyi",
            ),
        )
        val contacts = listOf(
            ContactDetail(
                contactId = 601L,
                lookupKey = "lookup-detail",
                rawContactId = 701L,
                displayName = "联系人详情",
                phoneticName = "lianxirenxiangqing",
                groupLabel = "L",
                numbers = listOf(
                    ContactPhone(
                        dataId = 801L,
                        number = "13800000003",
                        typeLabel = "MOBILE",
                    ),
                ),
            ),
            ContactDetail(
                contactId = 602L,
                lookupKey = "lookup-editor",
                rawContactId = 702L,
                displayName = "联系人编辑",
                phoneticName = "lianxirenbianji",
                groupLabel = "B",
                numbers = listOf(
                    ContactPhone(
                        dataId = 802L,
                        number = "13800000004",
                        typeLabel = "WORK",
                    ),
                ),
            ),
        )

        // 通知列表和来源列表使用不同元素类型与标识，保证集合映射可区分。
        val notificationSources = listOf(
            NotificationSourceInfo(sourceId = "fixture.mail", sourceLabel = "邮件"),
            NotificationSourceInfo(sourceId = "fixture.bank", sourceLabel = "银行"),
        )
        val notificationItems = listOf(
            NotificationSignal(
                sourceId = "fixture.bank",
                sourceLabel = "银行",
                key = "notification-key-901",
                title = "交易提醒",
                text = "支出 12.34 元",
                postedAtMillis = 1_725_000_000_901L,
            ),
        )

        // Boolean 只有两个合法值，完整 fixture 逐项反转默认值；同为 Boolean 的错接由同名赋值契约补充识别。
        return LauncherState(
            apps = apps,
            drawerVisibleApps = apps.drop(1),
            drawerQuery = "drawer-query-fixture",
            isDrawerSearchFocused = true,
            isDrawerRailSliding = true,
            isAppActionMenuVisible = true,
            selectedIndex = 3,
            listStartIndex = 1,
            drawerPageIndex = 2,
            // DrawerFocus 目前只有 LIST 一个枚举值，因此它是唯一无法设置非默认值的字段。
            drawerFocus = DrawerFocus.LIST,
            isLoading = false,
            currentTimeText = "09:41:27",
            currentDateText = "2026-07-30",
            currentWeekdayText = "THURSDAY",
            mode = LauncherMode.APP_DRAWER,
            returnMode = LauncherMode.SETTINGS,
            settingsSelectedIndex = 4,
            settingsListStartIndex = 2,
            appEditorSelectedIndex = 1,
            appEditorNameDraft = "应用名称草稿",
            appEditorAliasDraft = "应用别名草稿",
            unreadSmsEntries = unreadSmsEntries,
            smsPageIndex = SmsPageIndex.ALL,
            smsSelectedIndex = 2,
            smsListStartIndex = 1,
            smsThreads = smsThreads,
            isSmsThreadsLoading = true,
            smsThreadSelectedIndex = 3,
            smsThreadListStartIndex = 2,
            smsAllMessages = smsAllMessages,
            smsCurrentConversationKey = "service:bank",
            smsCurrentConversationTitle = "银行服务",
            smsCurrentIsServiceConversation = true,
            smsCurrentThreadId = 401L,
            smsCurrentAddress = "95588",
            smsMessages = smsMessages,
            smsThreadSearchQuery = "交易",
            smsDraftText = "待发送短信草稿",
            smsSendStatus = SmsSendStatus.FAILED,
            isSmsMessageMenuVisible = true,
            smsMessageMenuMessageId = 301L,
            isSmsThreadMenuVisible = true,
            smsThreadMenuConversationKey = "thread:402",
            smsMutedConversationKeys = setOf("thread:402", "thread:404"),
            isDefaultSmsApp = true,
            smsPermissionState = SmsPermissionState.READY,
            callLogGroups = callLogGroups,
            isCallLogLoading = true,
            hasCallPhonePermission = true,
            callPageIndex = CallPageIndex.DIAL,
            dialInput = "13800000002",
            dialMatches = dialMatches,
            contacts = contacts,
            isContactsLoading = true,
            hasContactsPermission = true,
            contactDetailLookupKey = "lookup-detail",
            contactEditorLookupKey = "lookup-editor",
            contactEditorNameDraft = "联系人姓名草稿",
            contactEditorNumberDraft = "13900000009",
            selectedPixelShape = PixelShape.DIAMOND,
            selectedDotSizePx = 9,
            isPixelGapEnabled = false,
            selectedTheme = PixelTheme.NIGHT,
            fontSelection = LauncherFontSelection(
                family = LauncherFontFamily.ARK,
                widthMode = LauncherFontWidthMode.MONOSPACED,
                size = PixelFontSize.PX_16,
            ),
            isFontLoading = true,
            fontCacheSummary = "17/29K",
            drawerListAlignment = DrawerListAlignment.RIGHT,
            isIdlePageEnabled = true,
            chargeAutoIdleEnabled = true,
            inactivityAutoIdleEnabled = false,
            idleTimeoutSeconds = 60,
            openDrawerInSearchMode = true,
            chargeIdleEffect = ChargeIdleEffect.CASCADE,
            isPixelMatterEffectEnabled = false,
            pixelMatterEffectMode = PixelMatterEffectMode.SMOKE,
            isPixelMatterHandControlEnabled = true,
            isPixelMatterHandDebugEnabled = false,
            batteryLevel = 47,
            isCharging = true,
            recentApps = listOf(
                "com.example.fixture.recent.one",
                "com.example.fixture.recent.two",
            ),
            lastInteractionUptimeMs = 987_654_321L,
            launchCount = 23,
            lastLaunchPackageName = "com.example.fixture.last",
            nextAlarmText = "06:37",
            missedCallCount = 4,
            unreadSmsCount = 7,
            mediaPlayback = MediaPlaybackSnapshot(
                isActive = true,
                packageName = "com.example.fixture.music",
                title = "映射测试曲目",
                artist = "映射测试歌手",
                isPlaying = true,
                canPlayPause = true,
                canSkipPrevious = true,
                canSkipNext = true,
                canSeek = true,
                positionMillis = 12_345L,
                durationMillis = 98_765L,
                positionUpdatedRealtimeMillis = 456_789L,
                playbackSpeed = 1.25f,
                canToggleFavorite = true,
                isFavorite = true,
            ),
            notificationSummaryText = "银行 交易提醒 +1",
            notificationCount = 2,
            notificationSources = notificationSources,
            notificationItems = notificationItems,
            mutedNotificationSourceIds = setOf("fixture.noisy"),
            priorityNotificationSourceIds = setOf("fixture.bank"),
            rainHintText = "18:00 有雨",
            rainUpdatedTimeText = "09:42",
            screenUsageTimeText = "02:34",
            screenOpenCountText = "57",
            statusBarMessageText = "状态栏消息",
            statusBarActionLeadingText = "前导文本",
            statusBarActionLabel = "立即处理",
            isStatusBarActionDanger = true,
            hasUsageAccess = true,
            hasLocationPermission = true,
            hasCallLogPermission = true,
            hasSmsReadPermission = true,
            hasPostNotificationPermission = true,
            hasNotificationListenerAccess = true,
            dataHealthUpdatedTimeText = "09:43",
        )
    }

    /**
     * 创建带稳定标识的短信消息 fixture。
     *
     * @param messageId 消息唯一标识。
     * @param threadId 所属线程标识。
     * @param address 对方号码或服务号。
     * @param body 用于区分列表的正文。
     * @param conversationKey UI 会话标识。
     * @param isServiceConversation 是否为服务号会话。
     */
    private fun smsMessage(
        messageId: Long,
        threadId: Long,
        address: String,
        body: String,
        conversationKey: String,
        isServiceConversation: Boolean = false,
    ): SmsMessageEntry = SmsMessageEntry(
        messageId = messageId,
        threadId = threadId,
        address = address,
        body = body,
        dateMillis = 1_725_000_000_000L + messageId,
        type = 1,
        isRead = false,
        deliveryStatus = 0,
        subscriptionId = 1,
        displayName = "联系人-$messageId",
        conversationKey = conversationKey,
        conversationTitle = "会话-$messageId",
        isServiceConversation = isServiceConversation,
    )

    /**
     * 创建会话列表使用的非默认短信线程 fixture。
     *
     * @param threadId 短信线程标识。
     * @param address 对方号码或服务号。
     * @param conversationKey UI 会话标识。
     */
    private fun smsThread(
        threadId: Long,
        address: String,
        conversationKey: String,
    ): SmsThreadSummary = SmsThreadSummary(
        threadId = threadId,
        address = address,
        snippet = "会话摘要-$threadId",
        dateMillis = 1_725_100_000_000L + threadId,
        unreadCount = 2,
        messageCount = 5,
        displayName = "会话联系人-$threadId",
        conversationKey = conversationKey,
        isServiceConversation = conversationKey.startsWith("service:"),
    )

    /**
     * 读取 data class 的真实 JVM backing field，用于逐字段比较两个不同类型的快照。
     */
    private fun readBackingField(instance: Any, fieldName: String): Any? {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(instance)
    }

    /**
     * 从 data class 主构造器源码中提取声明顺序，不依赖绝对行号。
     */
    private fun extractConstructorFieldNames(source: String, className: String): List<String> {
        val constructorBody = extractParenthesizedBody(source, "data class $className(")
        return FIELD_DECLARATION_PATTERN.findAll(constructorBody)
            .map { match -> match.groupValues[1] }
            .toList()
    }

    /**
     * 从真实 mapper 的 [LauncherUiState] 构造调用中提取左值与来源字段。
     */
    private fun extractMapperAssignments(source: String): List<Pair<String, String>> {
        val mapperBody = extractParenthesizedBody(source, "= LauncherUiState(")
        return MAPPER_ASSIGNMENT_PATTERN.findAll(mapperBody)
            .map { match -> match.groupValues[1] to match.groupValues[2] }
            .toList()
    }

    /**
     * 提取指定标记后成对圆括号内的源码，允许内部出现嵌套函数调用。
     */
    private fun extractParenthesizedBody(source: String, marker: String): String {
        val markerIndex = source.indexOf(marker)
        require(markerIndex >= 0) { "未找到源码标记：$marker" }
        val openingIndex = source.indexOf('(', markerIndex)
        require(openingIndex >= 0) { "源码标记后缺少左括号：$marker" }

        var depth = 0
        for (index in openingIndex until source.length) {
            when (source[index]) {
                '(' -> depth += 1
                ')' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(openingIndex + 1, index)
                    }
                }
            }
        }
        error("源码标记后的圆括号未闭合：$marker")
    }

    /**
     * 验证字段数量、唯一性、名称与顺序，并在失败时列出增删差异。
     */
    private fun assertExactFieldContract(label: String, actualNames: List<String>) {
        val duplicateNames = actualNames
            .groupingBy { fieldName -> fieldName }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        val missingNames = EXPECTED_FIELD_NAMES.toSet() - actualNames.toSet()
        val unexpectedNames = actualNames.toSet() - EXPECTED_FIELD_NAMES.toSet()

        assertTrue(
            "$label 字段契约变化：expected=$EXPECTED_FIELD_COUNT，actual=${actualNames.size}，" +
                "missing=$missingNames，unexpected=$unexpectedNames，duplicates=$duplicateNames",
            actualNames.size == EXPECTED_FIELD_COUNT &&
                missingNames.isEmpty() &&
                unexpectedNames.isEmpty() &&
                duplicateNames.isEmpty(),
        )
        assertEquals("$label 字段顺序发生变化。", EXPECTED_FIELD_NAMES, actualNames)
    }

    /**
     * 定位 Gradle 测试进程中的 app 模块目录。
     */
    private fun resolveModuleRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") {
            workingDirectory
        } else {
            workingDirectory.resolve("app")
        }
    }

    private companion object {
        /** 当前扁平 schema 的字段总数。 */
        const val EXPECTED_FIELD_COUNT = 108

        /** 按规范 schema 顺序列出全部字段，供行为循环与结构诊断共用。 */
        val EXPECTED_FIELD_NAMES: List<String> = listOf(
            "apps",
            "drawerVisibleApps",
            "drawerQuery",
            "isDrawerSearchFocused",
            "isDrawerRailSliding",
            "isAppActionMenuVisible",
            "selectedIndex",
            "listStartIndex",
            "drawerPageIndex",
            "drawerFocus",
            "isLoading",
            "currentTimeText",
            "currentDateText",
            "currentWeekdayText",
            "mode",
            "returnMode",
            "settingsSelectedIndex",
            "settingsListStartIndex",
            "appEditorSelectedIndex",
            "appEditorNameDraft",
            "appEditorAliasDraft",
            "unreadSmsEntries",
            "smsPageIndex",
            "smsSelectedIndex",
            "smsListStartIndex",
            "smsThreads",
            "isSmsThreadsLoading",
            "smsThreadSelectedIndex",
            "smsThreadListStartIndex",
            "smsAllMessages",
            "smsCurrentConversationKey",
            "smsCurrentConversationTitle",
            "smsCurrentIsServiceConversation",
            "smsCurrentThreadId",
            "smsCurrentAddress",
            "smsMessages",
            "smsThreadSearchQuery",
            "smsDraftText",
            "smsSendStatus",
            "isSmsMessageMenuVisible",
            "smsMessageMenuMessageId",
            "isSmsThreadMenuVisible",
            "smsThreadMenuConversationKey",
            "smsMutedConversationKeys",
            "isDefaultSmsApp",
            "smsPermissionState",
            "callLogGroups",
            "isCallLogLoading",
            "hasCallPhonePermission",
            "callPageIndex",
            "dialInput",
            "dialMatches",
            "contacts",
            "isContactsLoading",
            "hasContactsPermission",
            "contactDetailLookupKey",
            "contactEditorLookupKey",
            "contactEditorNameDraft",
            "contactEditorNumberDraft",
            "selectedPixelShape",
            "selectedDotSizePx",
            "isPixelGapEnabled",
            "selectedTheme",
            "fontSelection",
            "isFontLoading",
            "fontCacheSummary",
            "drawerListAlignment",
            "isIdlePageEnabled",
            "chargeAutoIdleEnabled",
            "inactivityAutoIdleEnabled",
            "idleTimeoutSeconds",
            "openDrawerInSearchMode",
            "chargeIdleEffect",
            "isPixelMatterEffectEnabled",
            "pixelMatterEffectMode",
            "isPixelMatterHandControlEnabled",
            "isPixelMatterHandDebugEnabled",
            "batteryLevel",
            "isCharging",
            "recentApps",
            "lastInteractionUptimeMs",
            "launchCount",
            "lastLaunchPackageName",
            "nextAlarmText",
            "missedCallCount",
            "unreadSmsCount",
            "mediaPlayback",
            "notificationSummaryText",
            "notificationCount",
            "notificationSources",
            "notificationItems",
            "mutedNotificationSourceIds",
            "priorityNotificationSourceIds",
            "rainHintText",
            "rainUpdatedTimeText",
            "screenUsageTimeText",
            "screenOpenCountText",
            "statusBarMessageText",
            "statusBarActionLeadingText",
            "statusBarActionLabel",
            "isStatusBarActionDanger",
            "hasUsageAccess",
            "hasLocationPermission",
            "hasCallLogPermission",
            "hasSmsReadPermission",
            "hasPostNotificationPermission",
            "hasNotificationListenerAccess",
            "dataHealthUpdatedTimeText",
        )

        /** 匹配主构造器中的 Kotlin 属性声明。 */
        val FIELD_DECLARATION_PATTERN: Regex =
            Regex("""(?m)^\s*val\s+([A-Za-z_][A-Za-z0-9_]*)\s*:""")

        /** 匹配 mapper 中保持简单字段引用的命名赋值。 */
        val MAPPER_ASSIGNMENT_PATTERN: Regex =
            Regex("""(?m)^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*)\s*,\s*$""")
    }
}
