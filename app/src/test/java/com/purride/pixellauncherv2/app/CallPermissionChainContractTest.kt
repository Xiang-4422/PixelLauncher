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
        assertTrue(
            "CallController.requestMissingPermissions must request WRITE_CONTACTS alongside READ " +
                "(same group, same dialog); without it the contact editor can never write.",
            source.contains("Manifest.permission.WRITE_CONTACTS"),
        )
    }

    /** 未接角标修复入口必须同时申请读写通话记录权限。 */
    @Test
    fun dataHealthRepairRequestsReadAndWriteCallLogTogether() {
        val source = sourceOf("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
        val requestBlock = Regex(
            """DataHealthRepairAction\.REQUEST_CALL_LOG_PERMISSION[\s\S]*?\n\s*DataHealthRepairAction\.""",
        ).find(source)?.value.orEmpty()

        assertTrue(
            "DATA HEALTH call-log repair branch must be found for this contract to mean anything.",
            requestBlock.isNotEmpty(),
        )
        assertTrue(
            "Call-log repair must include READ_CALL_LOG.",
            requestBlock.contains("Manifest.permission.READ_CALL_LOG"),
        )
        assertTrue(
            "Call-log repair must also include WRITE_CALL_LOG — API 26+ only grants " +
                "the permission actually requested, so the missed-call badge would never clear.",
            requestBlock.contains("Manifest.permission.WRITE_CALL_LOG"),
        )
    }

    /** 冷启动不得自动打开设置页或弹出敏感权限请求。 */
    @Test
    fun onResumeDoesNotRequestOptionalPermissions() {
        val source = sourceOf("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
        val resumeBlock = Regex(
            """override fun onResume\(\)[\s\S]*?\n    }""",
        ).find(source)?.value.orEmpty()

        assertTrue("MainActivity.onResume must be found.", resumeBlock.isNotEmpty())
        assertTrue(
            "Cold start must not open usage-access settings.",
            !resumeBlock.contains("ACTION_USAGE_ACCESS_SETTINGS"),
        )
        assertTrue(
            "Cold start must not request optional Home-data permissions.",
            !resumeBlock.contains("requestHomeDataPermissions"),
        )
        assertTrue(
            "Cold start must not invoke the platform permission dialog.",
            !resumeBlock.contains("requestPermissions("),
        )
    }

    private fun sourceOf(relativePath: String): String {
        val workingDirectory = File(".").canonicalFile
        val moduleRoot =
            if (workingDirectory.name == "app") workingDirectory else workingDirectory.resolve("app")
        return moduleRoot.resolve(relativePath).readText()
    }
}
