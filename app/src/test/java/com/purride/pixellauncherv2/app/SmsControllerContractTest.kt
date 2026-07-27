package com.purride.pixellauncherv2.app

import android.content.Context
import android.os.Handler
import com.purride.pixellauncherv2.data.SmsRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * [SmsController] 依赖真实 Android [Context]，在纯 JVM 单测中无法真正实例化。
 * 这里改用两类不需要运行时环境即可验证的契约：
 * 1. 构造函数签名必须接收外部注入的 [SmsRepository]（Java 反射，不实例化）。
 * 2. 源码中不得再出现 `SmsRepository(context)` 这种内部 new。
 */
class SmsControllerContractTest {

    @Test
    fun constructorAcceptsInjectedSmsRepository() {
        val constructor = SmsController::class.java.getDeclaredConstructor(
            Context::class.java,
            SmsRepository::class.java,
            ExecutorService::class.java,
            Handler::class.java,
            SmsController.Host::class.java,
        )

        assertTrue(
            "SmsController must declare a constructor taking an injected SmsRepository.",
            constructor.parameterTypes.contains(SmsRepository::class.java),
        )
    }

    @Test
    fun doesNotConstructSmsRepositoryInternally() {
        val source = moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/SmsController.kt")
            .readText()

        assertFalse(
            "SmsController must not construct its own SmsRepository; it must be injected via the constructor.",
            source.contains("SmsRepository(context)") || source.contains("= SmsRepository("),
        )
    }

    private fun moduleRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
    }
}
