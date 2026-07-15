package com.purride.pixellauncherv2.backupfixture;

import android.app.Application;
import android.content.SharedPreferences;

/** 模拟曾把占位明文写入普通 SharedPreferences 的历史应用版本。 */
public final class LegacyBackupFixtureApplication extends Application {

    /** 当前应用已永久禁止恢复的历史偏好文件。 */
    public static final String LEGACY_PREFERENCES_NAME = "pixel_launcher_ai_prefs";

    /** 用于证明备份/恢复传输实际发生的非敏感控制偏好文件。 */
    public static final String CONTROL_PREFERENCES_NAME = "pixel_launcher_backup_control";

    /** 历史偏好中的测试键，不使用任何真实供应商字段或凭据格式。 */
    public static final String LEGACY_VALUE_KEY = "legacy_fixture_value";

    /** 控制偏好中的测试键。 */
    public static final String CONTROL_VALUE_KEY = "control_fixture_value";

    /** 明确不具备真实凭据格式的历史明文占位值。 */
    public static final String LEGACY_VALUE = "fixture-plaintext-not-a-credential";

    /** 只有备份/恢复成功时才会重新出现的控制值。 */
    public static final String CONTROL_VALUE = "fixture-control-must-restore";

    /** 应用启动时确定性写入历史偏好和控制偏好。 */
    @Override
    public void onCreate() {
        super.onCreate();
        persistFixtureValue(LEGACY_PREFERENCES_NAME, LEGACY_VALUE_KEY, LEGACY_VALUE);
        persistFixtureValue(CONTROL_PREFERENCES_NAME, CONTROL_VALUE_KEY, CONTROL_VALUE);
    }

    /**
     * 同步写入一个测试值，确保后续备份不会与异步磁盘提交竞争。
     *
     * @param preferencesName 目标偏好文件名。
     * @param key 测试键名。
     * @param value 测试值。
     */
    private void persistFixtureValue(String preferencesName, String key, String value) {
        // 目标偏好只属于专用测试包，不会访问日常应用数据。
        final SharedPreferences preferences = getSharedPreferences(preferencesName, MODE_PRIVATE);
        // 同步提交结果用于阻止备份与尚未落盘的异步写入竞争。
        final boolean persisted = preferences.edit().putString(key, value).commit();
        if (!persisted) {
            throw new IllegalStateException("Unable to persist backup fixture value.");
        }
    }
}
