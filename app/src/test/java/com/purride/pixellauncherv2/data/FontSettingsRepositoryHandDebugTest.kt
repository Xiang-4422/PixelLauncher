package com.purride.pixellauncherv2.data

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 手势调试帧偏好的 Release 归一化行为（隐私门禁的状态层）。
 *
 * Release 用户看不到 HAND DEBUG 开关，因此持久化里遗留的 true 或默认 true 都会变成
 * "无法关闭的调试相机画面"。`handDebugSettingAllowed = false` 时读取必须归一化为 false，
 * 且默认值一并翻转；debug 构建（allowed = true）保持既有行为不变。
 */
class FontSettingsRepositoryHandDebugTest {

    /** 用 MockK 打桩的偏好读取端：默认全部返回调用方给出的 default 值。 */
    private fun repositoryWith(
        handDebugSettingAllowed: Boolean,
        storedHandDebug: Boolean?,
    ): FontSettingsRepository {
        val sharedPreferences = mockk<SharedPreferences>()
        every { sharedPreferences.getBoolean(any(), any()) } answers { secondArg() }
        every { sharedPreferences.getString(any(), any()) } answers { secondArg() }
        every { sharedPreferences.getInt(any(), any()) } answers { secondArg() }
        every { sharedPreferences.contains(any()) } returns false
        if (storedHandDebug != null) {
            every {
                sharedPreferences.getBoolean("pixel_matter_hand_debug_enabled", any())
            } returns storedHandDebug
        }
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        return FontSettingsRepository(
            context = context,
            handDebugSettingAllowed = handDebugSettingAllowed,
        )
    }

    @Test
    fun storedTrueIsNormalizedToFalseWhenNotAllowed() {
        val repository = repositoryWith(handDebugSettingAllowed = false, storedHandDebug = true)
        assertFalse(
            "Release 必须无视持久化里遗留的 true",
            repository.getUiBehaviorSettings().pixelMatterHandDebugEnabled,
        )
    }

    @Test
    fun defaultIsFalseWhenNotAllowed() {
        val repository = repositoryWith(handDebugSettingAllowed = false, storedHandDebug = null)
        assertFalse(
            "Release 的默认值必须是关闭，而不是历史硬编码的 true",
            repository.getUiBehaviorSettings().pixelMatterHandDebugEnabled,
        )
    }

    @Test
    fun debugBuildKeepsStoredValueAndDefaultTrue() {
        val storedTrue = repositoryWith(handDebugSettingAllowed = true, storedHandDebug = true)
        assertTrue(storedTrue.getUiBehaviorSettings().pixelMatterHandDebugEnabled)

        val storedFalse = repositoryWith(handDebugSettingAllowed = true, storedHandDebug = false)
        assertFalse(storedFalse.getUiBehaviorSettings().pixelMatterHandDebugEnabled)

        val unset = repositoryWith(handDebugSettingAllowed = true, storedHandDebug = null)
        assertTrue(
            "debug 构建保持既有默认 true 行为",
            unset.getUiBehaviorSettings().pixelMatterHandDebugEnabled,
        )
    }
}
