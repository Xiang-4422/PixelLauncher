# Security Policy

本文是仓库级安全报告入口。Pixel Engine 的版本支持、分级与发布规则位于
[Engine 发布与维护](pixel-engine/docs/发布与维护.md#6-安全支持与响应)。

请通过仓库 GitHub Security 页面创建私密 security advisory。不要用公开 issue 提交漏洞利用步骤、真实
凭据、个人数据或尚未协调的披露。报告请包含受影响版本、最小复现、影响、前提条件和建议缓解方式。

维护者会按 Engine 或 Launcher 对应流程分级、修复、发布和协调披露。普通 bug、功能请求和不含敏感
细节的加固建议仍使用 GitHub Issues。

## Launcher 本地敏感数据边界

Launcher 不内置第三方客户端凭据，也不提供已无网络消费者的明文 Key 配置入口。旧版本使用过的
`pixel_launcher_ai_prefs.xml` 被 Android 11 及以下的 `fullBackupContent`、Android 12 及以上的云备份
和设备迁移规则永久排除。版本升级时应用会同步清空并删除该文件；若历史 Auto Backup 数据已存在，
`BackupAgent.onRestoreFinished()` 会在应用可供用户启动前再次清理。该边界由 API 29 和 API 37
模拟器上的覆盖升级、卸载重装与真实本地 Auto Backup/restore 门禁持续验证。
