# M0-1 升级与 Auto Backup/restore 模拟器验收

日期：2026-07-15
状态：设备迁移验收完成；凭据吊销与 D-004 已执行，M0-1 等待 GitHub Support 缓存清理确认。

## 1. 验收目标

本验收不使用真实凭据，也不把“XML 中存在 exclude”当作设备行为通过。独立历史夹具以
versionCode 1 写入两份数据：

- `pixel_launcher_ai_prefs.xml`：包含无凭据格式的历史明文标记，当前版本必须清除；
- `pixel_launcher_backup_control.xml`：包含非敏感控制标记，必须保留或恢复，用于证明升级和备份
  transport 真实搬运了数据。

当前应用以同一专用 applicationId、versionCode 2 构建，不覆盖日常 debug 包。门禁只接受显式
`emulator-*` 序列号，并在任何安装或备份操作前要求 `ro.kernel.qemu=1`；实体手机不在本验收范围内。

## 2. 实现边界

Android 11 及以下的 `fullBackupContent`，以及 Android 12 及以上云备份/设备迁移规则，永久排除
历史明文偏好。应用正常启动时由 `LegacySensitiveDataCleaner` 同步清除旧文件。Auto Restore 运行于
受限备份进程时，平台可能只实例化基础 `Application`；因此 `LegacySensitiveDataBackupAgent` 在
`onRestoreFinished()` 再次执行同步清理。

`android:fullBackupOnly="true"` 保持文件型 Auto Backup，`onFullBackup()` 继续调用
`super.onFullBackup(data)`，所以修复不会通过关闭正常备份来制造假绿。

## 3. 设备与 APK 绑定

| 项目 | API 29 | API 37 |
| --- | --- | --- |
| 模拟器 | `emulator-5556` | `emulator-5554` |
| 系统 | Android 10 | Android 17 / 16KB 页 |
| 指纹 | `google/sdk_gphone64_arm64/emulator64_arm64:10/QSR1.211112.011/13135432:userdebug/dev-keys` | `google/sdk_gphone16k_arm64/emu64a16k:17/CP21.260330.005/15181570:user/dev-keys` |
| 夹具 APK SHA-256 | `ca5cb55832bf8bed93584ec534a0ee7c4998678924aab1cdf33026e03323ea22` | 同左 |
| 当前 APK SHA-256 | `efdf110be440c5bb2150585332e462fbe487a00500df3d53431a56d06fd63dff` | 同左 |

## 4. 场景与结果

两个 API 档位均执行以下场景：

1. 安装 versionCode 1，启动夹具确认两份偏好真实落盘；使用 `adb install -r` 覆盖安装
   versionCode 2；首次启动后控制偏好仍存在，历史明文不存在。
2. 重新安装 versionCode 1 并写入数据；选择官方本地 transport，执行
   `bmgr backupnow --monitor` 并要求逐包结果为 `Success`；卸载后安装 versionCode 2，等待安装触发
   Auto Restore；在任何 Activity 启动前控制偏好已经恢复，而历史明文已经不存在；启动后再次确认。

API 29 与 API 37 的机器报告均为 `status=passed`，所有布尔断言为 `true`。API 29 同时覆盖
`fullBackupContent` 分支，API 37 覆盖 `dataExtractionRules` 与较新平台恢复行为。

首次 API 37 探针曾在 `backupnow` 前调用 `am force-stop`，平台以 `PACKAGE_STOPPED` 和
`Backup is not allowed` 正确拒绝，门禁因此失败而非忽略。夹具随后移除该错误前置动作，因为
`backupnow` 本身会按平台流程停止应用；同一套完整场景重跑通过。

## 5. 清理与持续门禁

工具在 `finally` 中卸载专用测试包、只擦除该包的本地 transport 数据集，并恢复测试前的备份启用
状态、transport、自动恢复和本地 transport 参数。两台模拟器复验后，包列表均未发现
`com.purride.pixellauncherv2.backupfixture`。所有 ADB 调用都包含显式模拟器序列号。

PR/主分支的 API 29 instrumentation job 现在继续执行同一工具；失败时仍上传 JSON 与完整命令日志，
且没有 `continue-on-error`。设备安全单测还证明非模拟器序列号会在任何 ADB 调用前被拒绝。

本轮定向 Gradle 构建、敏感数据合同测试与 app/夹具双 Lint 共 406 tasks 通过；编译后 APK 三类备份
排除合同通过；Python tooling 108/108、74 页 MkDocs 严格构建和 `git diff --check` 均通过。最终
未删项 `tools/pixel-release-check.sh` 退出码为 0，主批次 1,060 tasks 及全部兼容消费者、供应链、
六场景性能趋势、soak、两个 Baseline Profile APK 和严格文档均成功。

## 6. 证据

- API 29 JSON：`build/reports/security/m0-1-backup-restore/api29/backup-restore-emulator.json`，
  1,240 bytes，SHA-256 `6d113d42a4458027f62981b404082b3a67898ab2331f904bcafafef6b7e2cd50`；
- API 29 命令日志：`build/reports/security/m0-1-backup-restore/api29/commands.log`，
  36,856 bytes，SHA-256 `6e4910ebfebd88745cc84f0355744fd90c1b396ced7d377d5ca44f8760bdd012`；
- API 37 JSON：`build/reports/security/m0-1-backup-restore/api37/backup-restore-emulator.json`，
  1,229 bytes，SHA-256 `993d9c868c13a4e1d68752a66e12b4048a21172ea8dc5ebab21951fc100284a2`；
- API 37 命令日志：`build/reports/security/m0-1-backup-restore/api37/commands.log`，
  43,822 bytes，SHA-256 `d5a40f60d6a36699f6b0c1809d240cdc80bacdd6880b59b06a269a48c4fe9b51`。

本证据只关闭 M0-1 的设备迁移验收项。后续用户已确认旧第三方凭据吊销/轮换并授权 Git 历史
重写；远端与 GitHub 缓存清理的独立证据见
`m0-1-git-history-cleanup-support-2026-07-15.md`。模拟器数据不用于批准 M6-2 的代表性实体设备
性能基线。
