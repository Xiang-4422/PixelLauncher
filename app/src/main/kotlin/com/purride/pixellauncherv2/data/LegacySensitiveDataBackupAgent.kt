package com.purride.pixellauncherv2.data

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor

/**
 * 在 Android 受限备份进程中阻止历史明文偏好恢复后继续留在磁盘。
 *
 * XML 规则负责当前版本以后不再备份旧文件；本代理额外覆盖已经存在于历史备份集中的数据。
 * `fullBackupOnly` 保持系统文件型 Auto Backup，本类不建立另一套 key/value 数据格式。
 */
class LegacySensitiveDataBackupAgent : BackupAgent() {

    /**
     * 保留系统默认的文件型 Auto Backup 行为和 XML include/exclude 规则。
     *
     * @param data 系统提供的完整备份输出流。
     */
    override fun onFullBackup(data: FullBackupDataOutput) {
        super.onFullBackup(data)
    }

    /**
     * 当前应用不使用 key/value 备份；`fullBackupOnly` 下该入口只用于满足平台抽象契约。
     *
     * @param oldState 平台提供的旧状态描述符。
     * @param data key/value 输出流，本实现不会写入。
     * @param newState 平台提供的新状态描述符。
     */
    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    /**
     * 当前应用不读取 key/value 备份；历史文件恢复由系统 Auto Backup 完成。
     *
     * @param data key/value 输入流，本实现不会读取。
     * @param appVersionCode 生成历史数据的应用版本号。
     * @param newState 平台提供的新状态描述符。
     */
    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    /** 系统恢复完成后、应用可供用户启动前，再次同步删除历史敏感偏好。 */
    override fun onRestoreFinished() {
        super.onRestoreFinished()
        LegacySensitiveDataCleaner.clear(this)
    }
}
