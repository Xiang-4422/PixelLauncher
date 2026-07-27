package com.purride.pixellauncherv2.app

import android.content.Context
import com.purride.pixellauncherv2.data.NotificationSummarySettingsRepository
import com.purride.pixellauncherv2.data.SmsNotificationHelper
import com.purride.pixellauncherv2.data.SmsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [AndroidComponentDependencies] 的工厂函数需要真实 [Context] 才能实际调用，JVM 单测里无法
 * 安全地伪造一个能通过 [Context.getApplicationContext] 的实例（项目未引入 mockk/mockito，
 * 也不允许使用全局可变测试覆盖）。因此这里验证工厂函数的存在与签名（Java 反射，不调用），
 * 并用源码静态扫描确认三个框架组件都改走这个统一边界，不再各自 new。
 */
class AndroidComponentDependenciesContractTest {

    @Test
    fun exposesFactoryFunctionsForEveryFrameworkComponentRepository() {
        val objectInstance = AndroidComponentDependencies

        val smsRepositoryMethod = objectInstance.javaClass.getDeclaredMethod("smsRepository", Context::class.java)
        val smsNotificationHelperMethod =
            objectInstance.javaClass.getDeclaredMethod("smsNotificationHelper", Context::class.java)
        val notificationSummarySettingsMethod =
            objectInstance.javaClass.getDeclaredMethod("notificationSummarySettingsRepository", Context::class.java)

        assertEquals(SmsRepository::class.java, smsRepositoryMethod.returnType)
        assertEquals(SmsNotificationHelper::class.java, smsNotificationHelperMethod.returnType)
        assertEquals(NotificationSummarySettingsRepository::class.java, notificationSummarySettingsMethod.returnType)
    }

    @Test
    fun frameworkComponentsUseTheSharedFactoryInsteadOfNewingRepositoriesThemselves() {
        val receiverSource = readSource("SmsDeliverReceiver.kt")
        val serviceSource = readSource("RespondViaMessageService.kt")
        val listenerSource = readSource("LauncherNotificationListenerService.kt")

        assertTrue(
            "SmsDeliverReceiver must obtain its SmsRepository from AndroidComponentDependencies.",
            receiverSource.contains("AndroidComponentDependencies.smsRepository("),
        )
        assertTrue(
            "SmsDeliverReceiver must obtain its SmsNotificationHelper from AndroidComponentDependencies.",
            receiverSource.contains("AndroidComponentDependencies.smsNotificationHelper("),
        )
        assertFalse(
            "SmsDeliverReceiver must not construct SmsRepository directly anymore.",
            receiverSource.contains("SmsRepository(context)"),
        )

        assertTrue(
            "RespondViaMessageService must obtain its SmsRepository from AndroidComponentDependencies.",
            serviceSource.contains("AndroidComponentDependencies.smsRepository("),
        )
        assertFalse(
            "RespondViaMessageService must not construct SmsRepository directly anymore.",
            serviceSource.contains("SmsRepository(applicationContext)"),
        )

        assertTrue(
            "LauncherNotificationListenerService must obtain its settings repository from AndroidComponentDependencies.",
            listenerSource.contains("AndroidComponentDependencies.notificationSummarySettingsRepository("),
        )
        assertFalse(
            "LauncherNotificationListenerService must not construct NotificationSummarySettingsRepository directly anymore.",
            listenerSource.contains("NotificationSummarySettingsRepository(this)"),
        )
    }

    private fun readSource(fileName: String): String {
        return moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/$fileName")
            .readText()
    }

    private fun moduleRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
    }
}
