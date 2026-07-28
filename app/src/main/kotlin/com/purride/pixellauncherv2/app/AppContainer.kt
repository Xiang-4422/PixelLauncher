package com.purride.pixellauncherv2.app

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import com.purride.pixellauncherv2.data.AppCustomizationRepository
import com.purride.pixellauncherv2.data.AppRepository
import com.purride.pixellauncherv2.data.CommunicationStatusRepository
import com.purride.pixellauncherv2.data.DeviceLocationRepository
import com.purride.pixellauncherv2.data.DeviceMotionRepository
import com.purride.pixellauncherv2.data.DeviceStatusRepository
import com.purride.pixellauncherv2.data.FontSettingsRepository
import com.purride.pixellauncherv2.data.HandTrackingRepository
import com.purride.pixellauncherv2.data.LauncherStatsRepository
import com.purride.pixellauncherv2.data.MediaPlaybackRepository
import com.purride.pixellauncherv2.data.NextAlarmRepository
import com.purride.pixellauncherv2.data.NotificationSummaryRepository
import com.purride.pixellauncherv2.data.NotificationSummarySettingsRepository
import com.purride.pixellauncherv2.data.PackageManagerAppRepository
import com.purride.pixellauncherv2.data.RainForecastRepository
import com.purride.pixellauncherv2.data.ScreenUsageRepository
import com.purride.pixellauncherv2.data.SmsNotificationHelper
import com.purride.pixellauncherv2.data.SmsRepository
import java.util.concurrent.ExecutorService

/**
 * [MainActivity] 的手动依赖容器：集中创建它持有的所有 Repository。
 *
 * 这是一个纯手写的组装点，不引入任何 DI 框架——只是把原本散落在 `onCreate` 里的
 * 逐个 `new` 收敛到一处，方便一次性看清 Activity 的依赖全貌。所有属性均在构造时
 * 立即创建（无懒加载、无可变状态），生命周期与持有它的 Activity 实例一致。
 *
 * @param context 任意 Context，内部只使用其 [Context.getApplicationContext]，避免持有 Activity 引用。
 * @param backgroundExecutor 与 Activity 共享的后台执行器，供需要异步 IO 的仓库使用。
 * @param mainHandler 与 Activity 共享的主线程 Handler，供需要回调到主线程的仓库使用。
 */
internal class AppContainer(
    // 构造用的临时 Context；只取一次 applicationContext 后即不再使用，不作为字段保留。
    context: Context,
    // 与 Activity 共享的后台执行器，供需要异步 IO 的仓库使用。
    private val backgroundExecutor: ExecutorService,
    // 与 Activity 共享的主线程 Handler，供需要回调到主线程的仓库使用。
    private val mainHandler: Handler,
) {
    /** 所有仓库统一持有的应用级 Context，避免间接持有 Activity 引用导致内存泄漏。 */
    private val appContext: Context = context.applicationContext

    /** 可启动应用列表仓库（含缓存），对外仍以 [AppRepository] 接口暴露。 */
    val appRepository: AppRepository = PackageManagerAppRepository(appContext)

    /** 应用图标/名称等自定义展示信息仓库。 */
    val appCustomizationRepository = AppCustomizationRepository(appContext)

    /** 字体、外观与交互行为设置仓库。 */
    val fontSettingsRepository = FontSettingsRepository(appContext)

    /** Launcher 使用统计（启动次数等）仓库。 */
    val launcherStatsRepository = LauncherStatsRepository(appContext)

    /** 设备状态（电量、充电等）仓库。 */
    val deviceStatusRepository = DeviceStatusRepository(appContext)

    /** 下一个闹钟信息仓库。 */
    val nextAlarmRepository = NextAlarmRepository(appContext)

    /** 屏幕使用时长统计仓库。 */
    val screenUsageRepository = ScreenUsageRepository(appContext)

    /** 未接来电/未读短信等通信状态仓库。 */
    val communicationStatusRepository = CommunicationStatusRepository(appContext)

    /** 系统通知摘要仓库（进程内共享状态，无需 Context）。 */
    val notificationSummaryRepository = NotificationSummaryRepository()

    /** 通知摘要的静音/优先级规则仓库。 */
    val notificationSummarySettingsRepository = NotificationSummarySettingsRepository(appContext)

    /** 媒体播放控制仓库，需要监听通知栏媒体会话并回调到主线程。 */
    val mediaPlaybackRepository = MediaPlaybackRepository(
        context = appContext,
        notificationListener = ComponentName(appContext, LauncherNotificationListenerService::class.java),
        mainHandler = mainHandler,
    )

    /** 设备地理位置仓库。 */
    val deviceLocationRepository = DeviceLocationRepository(appContext)

    /** 设备运动传感器（重力/加速度）仓库。 */
    val deviceMotionRepository = DeviceMotionRepository(appContext)

    /** 手部追踪仓库，依赖后台执行器做相机分析并回调到主线程。 */
    val handTrackingRepository = HandTrackingRepository(
        context = appContext,
        backgroundExecutor = backgroundExecutor,
        mainHandler = mainHandler,
    )

    /** 降雨预报仓库（无状态，不依赖 Context）。 */
    val rainForecastRepository = RainForecastRepository()

    /** 短信仓库复用框架组件共用的构造边界，避免出现第二份构造逻辑。 */
    val smsRepository: SmsRepository = AndroidComponentDependencies.smsRepository(appContext)

    /** 短信通知展示辅助类，同样复用框架组件共用的构造边界。 */
    val smsNotificationHelper: SmsNotificationHelper =
        AndroidComponentDependencies.smsNotificationHelper(appContext)
}
