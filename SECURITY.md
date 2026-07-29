# Security Policy

本文是仓库级安全报告入口。Pixel Engine 的版本支持、分级与发布规则位于
[Engine 发布与维护](pixel-engine/docs/发布与维护.md#6-安全支持与响应)。

请通过仓库 GitHub Security 页面创建私密 security advisory。不要用公开 issue 提交漏洞利用步骤、真实
凭据、个人数据或尚未协调的披露。报告请包含受影响版本、最小复现、影响、前提条件和建议缓解方式。

维护者会按 Engine 或 Launcher 对应流程分级、修复、发布和协调披露。普通 bug、功能请求和不含敏感
细节的加固建议仍使用 GitHub Issues。

## Launcher 本地敏感数据边界

Launcher 不内置第三方客户端凭据，也不提供明文 Key 配置入口；单元测试会扫描 `app/src/main` 全部
文本资源，禁止任何具备凭据形状的长字符串进入主源码。

两类本地偏好被 Android 11 及以下的 `fullBackupContent`、Android 12 及以上的云备份和设备迁移
规则同时排除，因此不会随备份或换机迁移离开当前设备：

- `app_repository_cache.xml`——只对本机有效的已安装应用清单缓存，用户的装机列表不出本机。
- `sms_mute_settings.xml`——短信会话静音规则，会话键形如 `person:<手机号>`，内含个人可识别信息。
  代价是静音规则不跨设备恢复、换机后需重新设置；相比手机号进入云备份后无法追溯清除，这个可重做的
  代价是有意接受的取舍。

该边界由源码级契约测试与 `tools/verify_backup_contract.py` 对 debug/release 两个 APK 的合并
Manifest 与编译后 XML 资源共同验证。新增持久化个人可识别信息的偏好文件时，必须同时登记到这两处
契约，否则门禁不会覆盖它。
