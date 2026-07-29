package com.purride.pixellauncherv2.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 拨号链路"用到的权限必须真的被申请过"。
 *
 * Android 6.0 起权限要运行时申请，而 **API 26 之后系统只授予被显式请求的那一个权限**：
 * 同组的其它权限不再随之自动授予，必须等应用真的去 request 时才免弹窗补授。因此
 * "清单里声明了" + "仓库层检查了" 并不代表能用——只要申请集合里漏了它，
 * `checkSelfPermission` 就恒为 DENIED，而失败往往是静默的，现象离原因很远。
 *
 * 这类缺陷编译期与运行期都没有提示，两次都是靠人工审查才发现，故用静态契约兜住。
 */
class CallPermissionChainContractTest {

    /**
     * T9 智能拨号靠联系人库做姓名与拼音匹配。漏掉 READ_CONTACTS 时
     * `ContactSearchRepository.search` 直接返回空列表，表现为"输入数字永远没有匹配"，
     * 看起来像功能没做，而不像权限缺失。
     */
    @Test
    fun dialerPermissionRequestIncludesContactsForT9() {
        val source = sourceOf("src/main/kotlin/com/purride/pixellauncherv2/app/CallController.kt")

        assertTrue(
            "CallController.requestMissingPermissions must request READ_CONTACTS; " +
                "without it T9 contact search silently returns nothing.",
            source.contains("Manifest.permission.READ_CONTACTS"),
        )
    }

    /**
     * 未接角标的清零走 `CallLog.Calls.NEW = 0` 的写入，需要 WRITE_CALL_LOG。
     * 启动时只申请 READ_CALL_LOG 的话，WRITE 在 API 26+ 上恒为 DENIED，
     * `markCallsAcknowledged` 永久失败，用户看完通话记录角标依然挂着。
     *
     * 两者同属 CALL_LOG 权限组，一起请求只弹同一个系统框，加上它没有额外打扰成本。
     */
    @Test
    fun homeStartupRequestIncludesWriteCallLogAlongsideRead() {
        val source = sourceOf("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
        val requestBlock = Regex(
            """private fun maybeRequestHomeDataPermissions\([\s\S]*?\n    }""",
        ).find(source)?.value.orEmpty()

        assertTrue(
            "maybeRequestHomeDataPermissions must be found for this contract to mean anything.",
            requestBlock.isNotEmpty(),
        )
        assertTrue(
            "Startup permission request must include READ_CALL_LOG.",
            requestBlock.contains("Manifest.permission.READ_CALL_LOG"),
        )
        assertTrue(
            "Startup permission request must also include WRITE_CALL_LOG — API 26+ only grants " +
                "the permission actually requested, so the missed-call badge would never clear.",
            requestBlock.contains("Manifest.permission.WRITE_CALL_LOG"),
        )
    }

    private fun sourceOf(relativePath: String): String {
        val workingDirectory = File(".").canonicalFile
        val moduleRoot =
            if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
        return moduleRoot.resolve(relativePath).readText()
    }
}
