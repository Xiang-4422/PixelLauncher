# Security Policy

Pixel Engine 尚未发布正式 1.0.0；当前 snapshot 不承诺生产安全支持。1.0 发布后，最新 minor 的最新
patch 为默认支持版本，上一 minor 仅在新 minor 发布后的 90 天内接受安全 backport，除非 release notes
另有声明。

请通过仓库 GitHub Security 页面创建私密 security advisory。不要用公开 issue 提交漏洞利用步骤、真实
凭据、个人数据或尚未协调的披露。报告请包含受影响版本、最小复现、影响、前提条件和建议缓解方式。

维护者会按 `pixel-engine/docs/security-release-process.md` 分级、修复、发布和协调披露。普通 bug、功能
请求和不含敏感细节的加固建议仍使用 GitHub Issues。

## Launcher 本地敏感数据边界

Launcher 不内置第三方客户端凭据，也不提供已无网络消费者的明文 Key 配置入口。旧版本使用过的
`pixel_launcher_ai_prefs.xml` 被 Android 11 及以下的 `fullBackupContent`、Android 12 及以上的云备份
和设备迁移规则永久排除。版本升级时应用会同步清空并删除该文件；若历史 Auto Backup 数据已存在，
`BackupAgent.onRestoreFinished()` 会在应用可供用户启动前再次清理。该边界由 API 29 和 API 37
模拟器上的覆盖升级、卸载重装与真实本地 Auto Backup/restore 门禁持续验证。
