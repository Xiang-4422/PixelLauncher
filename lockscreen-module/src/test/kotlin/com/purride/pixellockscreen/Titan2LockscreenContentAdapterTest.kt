package com.purride.pixellockscreen

import org.junit.Assert.assertEquals
import org.junit.Test

/** Titan 2 锁屏通知与媒体摘要的脱敏辅助逻辑测试。 */
class Titan2LockscreenContentAdapterTest {
    /** 系统可见文字必须折叠多行空白并限制长度。 */
    @Test
    fun contentTextSanitizerProducesBoundedSingleLine() {
        /** 当前清理后的通知标题。 */
        val text = sanitizeLockscreenContentText(" A\nB  ${"X".repeat(200)} ")
        assertEquals(120, text.length)
        assertEquals(false, '\n' in text)
        assertEquals(false, "  " in text)
    }

    /** 脱敏键必须稳定且不包含原始包名、用户或通知编号。 */
    @Test
    fun notificationKeyIsStableAndOpaque() {
        /** 模拟 Android StatusBarNotification 的原始键。 */
        val rawKey = "0|com.example.private|42|null|1000"
        /** 第一次生成的脱敏键。 */
        val safeKey = notificationSafeKey(rawKey)
        assertEquals(safeKey, notificationSafeKey(rawKey))
        assertEquals(false, safeKey.contains("com.example"))
        assertEquals(true, safeKey.startsWith("N-"))
    }
}
