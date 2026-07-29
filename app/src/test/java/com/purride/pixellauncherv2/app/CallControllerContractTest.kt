package com.purride.pixellauncherv2.app

import android.os.Handler
import com.purride.pixellauncherv2.data.CallLogRepository
import com.purride.pixellauncherv2.data.DialerRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.ExecutorService

/**
 * [CallController] 依赖真实 Android 环境，在纯 JVM 单测中无法真正实例化。
 * 与 [SmsControllerContractTest] 同构，改用两类不需要运行时环境的契约：
 * 1. 构造函数签名必须接收外部注入的仓库（Java 反射，不实例化）。
 * 2. 源码中不得出现内部 new 仓库的写法。
 */
class CallControllerContractTest {

    @Test
    fun constructorAcceptsInjectedRepositories() {
        val constructor = CallController::class.java.getDeclaredConstructor(
            CallLogRepository::class.java,
            DialerRepository::class.java,
            ExecutorService::class.java,
            Handler::class.java,
            CallController.Host::class.java,
        )

        assertTrue(
            "CallController must declare a constructor taking an injected CallLogRepository.",
            constructor.parameterTypes.contains(CallLogRepository::class.java),
        )
        assertTrue(
            "CallController must declare a constructor taking an injected DialerRepository.",
            constructor.parameterTypes.contains(DialerRepository::class.java),
        )
    }

    @Test
    fun doesNotConstructRepositoriesInternally() {
        val source = moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/CallController.kt")
            .readText()

        assertFalse(
            "CallController must not construct its own repositories; they must be injected.",
            source.contains("CallLogRepository(") ||
                source.contains("DialerRepository("),
        )
    }

    /** 后台任务必须经守卫提交：宿主销毁后裸 execute 会抛异常并杀掉进程。 */
    @Test
    fun backgroundWorkGoesThroughRejectionGuard() {
        val source = moduleRoot()
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/CallController.kt")
            .readText()

        assertTrue(
            "CallController must submit background work through a RejectedExecutionException guard.",
            source.contains("RejectedExecutionException") &&
                source.contains("private fun runInBackground("),
        )
        assertFalse(
            "CallController must not call backgroundExecutor.execute outside the guard.",
            source.replace("backgroundExecutor.execute { task() }", "")
                .contains("backgroundExecutor.execute"),
        )
    }

    private fun moduleRoot(): File {
        val workingDirectory = File(".").canonicalFile
        return if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
    }
}
