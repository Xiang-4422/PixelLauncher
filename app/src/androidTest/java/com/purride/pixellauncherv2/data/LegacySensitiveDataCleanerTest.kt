package com.purride.pixellauncherv2.data

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 在真实 Android SharedPreferences 实现上验证旧版明文数据升级清理。 */
@RunWith(AndroidJUnit4::class)
class LegacySensitiveDataCleanerTest {

    /** 当前被测应用的 credential-protected application context。 */
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    /** 每个用例结束后再次清理，避免测试数据影响同一设备上的后续用例。 */
    @After
    fun clearTestData() {
        LegacySensitiveDataCleaner.clear(targetContext)
    }

    /** 模拟旧版本升级时，清理器必须同步删除全部键值和底层 XML 文件。 */
    @Test
    fun clearRemovesLegacyPlaintextDuringUpgrade() {
        val legacyPreferences = targetContext.getSharedPreferences(
            LegacySensitiveDataCleaner.LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val seeded = legacyPreferences.edit()
            .putString(LEGACY_TEST_KEY, LEGACY_TEST_VALUE)
            .putBoolean(LEGACY_TEST_FLAG, true)
            .commit()
        assertTrue("Test setup must persist the simulated legacy preferences.", seeded)

        LegacySensitiveDataCleaner.clear(targetContext)

        val reloadedPreferences = targetContext.getSharedPreferences(
            LegacySensitiveDataCleaner.LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertTrue("All legacy values must be removed.", reloadedPreferences.all.isEmpty())
        assertFalse("The legacy preferences XML must be deleted.", legacyPreferencesFile().exists())
    }

    /** 在没有旧文件时重复执行清理也必须保持成功和空状态。 */
    @Test
    fun clearIsIdempotentWhenLegacyPreferencesDoNotExist() {
        LegacySensitiveDataCleaner.clear(targetContext)
        LegacySensitiveDataCleaner.clear(targetContext)

        val reloadedPreferences = targetContext.getSharedPreferences(
            LegacySensitiveDataCleaner.LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertTrue("Repeated cleanup must leave an empty preference store.", reloadedPreferences.all.isEmpty())
        assertFalse("Repeated cleanup must not recreate the legacy XML file.", legacyPreferencesFile().exists())
    }

    /** 返回旧版 SharedPreferences 在应用私有目录中的 XML 路径。 */
    private fun legacyPreferencesFile(): File {
        return File(
            targetContext.applicationInfo.dataDir,
            "shared_prefs/${LegacySensitiveDataCleaner.LEGACY_PREFERENCES_NAME}.xml",
        )
    }

    private companion object {
        /** 模拟旧版本中敏感字符串使用的测试键名。 */
        const val LEGACY_TEST_KEY: String = "legacy_sensitive_value"

        /** 模拟旧偏好文件内其他数据的测试键名，确保按文件整体清理。 */
        const val LEGACY_TEST_FLAG: String = "legacy_sensitive_flag"

        /** 明确不具备真实凭据格式的测试占位内容。 */
        const val LEGACY_TEST_VALUE: String = "not-a-real-credential"
    }
}
