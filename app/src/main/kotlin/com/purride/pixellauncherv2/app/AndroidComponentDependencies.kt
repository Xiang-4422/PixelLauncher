package com.purride.pixellauncherv2.app

import android.content.Context
import com.purride.pixellauncherv2.data.NotificationSummarySettingsRepository
import com.purride.pixellauncherv2.data.SmsNotificationHelper
import com.purride.pixellauncherv2.data.SmsRepository

/**
 * 为由 Android 框架直接实例化的组件（[SmsDeliverReceiver]、[RespondViaMessageService]、
 * [LauncherNotificationListenerService]）提供统一、最小的依赖构造边界。
 *
 * 这里构造的不只是 Repository（如 [SmsRepository]），也包括 [SmsNotificationHelper] 这样的
 * 展示辅助类，因此命名为“依赖”而非“仓库”。这些组件的实例由系统创建，无法像 [MainActivity]
 * 那样通过构造参数注入依赖；因此把它们各自需要的构造逻辑集中在此处，避免同样的 `new` 散落在
 * 每个组件内部。
 *
 * 这里只提供无状态的工厂函数：每次调用都返回一个新实例，不持有任何可替换的全局字段，
 * 因此不能也不应被当作测试替身使用——单元测试请直接构造被测组件所需的真实/伪造依赖。
 */
object AndroidComponentDependencies {

    /** 构造短信仓库；统一使用 applicationContext，避免持有传入的临时 Context。 */
    fun smsRepository(context: Context): SmsRepository =
        SmsRepository(context.applicationContext)

    /** 构造短信到达通知的展示辅助类。 */
    fun smsNotificationHelper(context: Context): SmsNotificationHelper =
        SmsNotificationHelper(context.applicationContext)

    /** 构造通知摘要的静音/优先级规则仓库。 */
    fun notificationSummarySettingsRepository(context: Context): NotificationSummarySettingsRepository =
        NotificationSummarySettingsRepository(context.applicationContext)
}
