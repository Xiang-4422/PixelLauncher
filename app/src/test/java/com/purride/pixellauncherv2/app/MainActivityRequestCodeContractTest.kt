package com.purride.pixellauncherv2.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MainActivity] 的权限与角色请求码必须两两不同。
 *
 * `onRequestPermissionsResult` 用 `when (requestCode)` 按声明顺序匹配：一旦两个常量取值相同，
 * 靠后的分支就永久不可达，回调会被派发给错误的模块——例如相机授权回来却执行拨号的
 * `onPermissionsResult()`，把用户直接扔进拨号页。
 *
 * Kotlin 不会对重复的 `const` 取值报错，`when` 分支也因为常量名不同而不被判为不可达，
 * 编译期与运行时都没有任何提示，只能靠这条契约拦住。
 */
class MainActivityRequestCodeContractTest {

    @Test
    fun everyRequestCodeIsUnique() {
        val codes = mapOf(
            "homeDataPermissionRequestCode" to MainActivity.homeDataPermissionRequestCode,
            "smsPermissionRequestCode" to MainActivity.smsPermissionRequestCode,
            "smsRoleRequestCode" to MainActivity.smsRoleRequestCode,
            "cameraPermissionRequestCode" to MainActivity.cameraPermissionRequestCode,
            "callPermissionRequestCode" to MainActivity.callPermissionRequestCode,
        )

        val collisions = codes.entries
            .groupBy({ entry -> entry.value }, { entry -> entry.key })
            .filterValues { names -> names.size > 1 }

        assertTrue(
            "Request codes must be unique or when-branches become unreachable: $collisions",
            collisions.isEmpty(),
        )
        assertEquals("all request codes must stay distinct", codes.size, codes.values.distinct().size)
    }

    /**
     * 新增模块时必须同时把请求码登记进上面那张表，否则契约会漏掉它。
     * 这里用源码扫描兜底：companion object 里所有 *RequestCode 常量都要被本测试覆盖。
     */
    @Test
    fun contractCoversEveryDeclaredRequestCode() {
        val source = java.io.File(".").canonicalFile
            .let { cwd -> if (cwd.name == "app") cwd else cwd.resolve("app") }
            .resolve("src/main/kotlin/com/purride/pixellauncherv2/app/MainActivity.kt")
            .readText()

        val declared = Regex("""const val (\w*RequestCode) = """").findAll(source)
            .map { match -> match.groupValues[1] }
            .toSet()
        val covered = Regex(""""(\w*RequestCode)" to MainActivity\.""")
            .findAll(
                java.io.File(".").canonicalFile
                    .let { cwd -> if (cwd.name == "app") cwd else cwd.resolve("app") }
                    .resolve("src/test/java/com/purride/pixellauncherv2/app/MainActivityRequestCodeContractTest.kt")
                    .readText(),
            )
            .map { match -> match.groupValues[1] }
            .toSet()

        assertEquals(
            "every *RequestCode constant must be listed in everyRequestCodeIsUnique",
            emptySet<String>(),
            declared - covered,
        )
    }
}
