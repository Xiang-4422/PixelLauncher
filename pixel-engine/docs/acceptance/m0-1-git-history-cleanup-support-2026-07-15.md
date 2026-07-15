# M0-1 Git 历史清理与 GitHub 缓存清除（2026-07-15）

## 结论

用户已确认旧第三方凭据完成吊销/轮换，并明确授权清理 Git 历史。包含真实 provider credential 的
Git 对象已经通过敏感数据重写从所有当前远端 ref 中移除，远端 fresh mirror、当前本地历史、工作树、
构建产物和 GitHub Actions 日志扫描均为零发现。

GitHub 仍能按旧边界 commit id 提供被改写历史，其祖先可以到达敏感 blob。按照 GitHub 官方
“Removing sensitive data from a repository”流程，已提交 Support 工单 `#4565348` 请求移除缓存
引用、执行服务端垃圾回收。Support 完成并复验旧对象不可访问前，M0-1 保持进行中。

## 授权与清理边界

- 凭据：用户确认已吊销/轮换；报告和工单只保留单向 fingerprint，不保存或发送密钥值；
- D-004：用户明确授权历史重写、强制更新远端以及实体设备性能采集；
- 仓库：`Xiang-4422/PixelLauncher`，公开 GitHub 仓库；
- 当前远端：只有 `refs/heads/main`，HEAD 为
  `c1c33e6b41bbd335a52c01a972eb8b5dca10a80b`；
- 受影响 PR：0；fork：0；孤立 LFS object：无；main 无 branch protection；
- `git-filter-repo` First Changed Commit：
  `599a1c454fe96e3ef840fa0d24279ef1363ed1e4` →
  `a2ed993dc09b71b199e4324e6f37f06dfa31ee4d`。

历史处理使用隔离副本，不对包含大量未提交 SDK 成果的当前工作树执行 reset、checkout、clean 或
filter。远端 ref 更新后再从 GitHub 全新 mirror 验证，避免以本地对象回收代替远端事实。

## 脱敏扫描证据

| 证据 | 扫描量 | 结果 | SHA-256 |
|---|---:|---|---|
| 清理前 Git 历史 | 6,385 | 1 个脱敏 finding | `c7ebea7af0ee9d1f2a3bbfcb1a9699c3afe6aeefc23de747bd172ea90b5760b7` |
| 清理后本地 Git 历史 | 6,247 | 0 finding | `6ef1bf7c3d0c250c171fa1f132c1d3d88c0eb2c2373a424c76c12ebf70e82ec1` |
| 全新远端 mirror | 5,504 | 0 finding | `5949e00c45456ad0e043d2b61d87780cbfd2d47396d8a248fbac1d92b15c903d` |
| GitHub Actions 日志 | 99 | 0 finding | `e6edca5e7eebf297924f419cc0a9b3fcb7f5a9090b128c2c9a53ceb50e66189d` |

清理前 finding 只记录：rule `provider-api-key`、历史路径、行号与 fingerprint
`58f1c75b30aef7db`。当前工作树 secret scan 6,707 项和发布归档 scan 2,009 项也均为零发现；
Auto Backup/restore 与升级清理证据由相邻 M0-1 验收页覆盖。

机器报告位于：

- `build/reports/security/git-history-secret-scan-before.json`；
- `build/reports/security/git-history-secret-scan-after.json`；
- `build/reports/security/remote-history-secret-scan.json`；
- `build/reports/security/github-actions-log-secret-scan.json`。

## GitHub 缓存与 Support 工单

直接查询旧敏感提交 `9982b25c9b6165305f5115122d6a3177c0194103` 已不可用，但旧边界提交
`599a1c454fe96e3ef840fa0d24279ef1363ed1e4` 仍可按 object id 获取；把该边界提交提取到一次性
bare repository 后，脱敏 scanner 能沿祖先重新发现同一 fingerprint。因此“当前 refs 已清洁”不能
冒充“GitHub 已彻底清除缓存对象”。

已向 GitHub Support 提交：

- ticket：`#4565348`；
- subject：`Purge cached sensitive Git history for Xiang-4422/PixelLauncher`；
- 内容：仓库、0 个受影响 PR、First Changed Commit、无 fork/LFS、当前 ref/HEAD、fresh-mirror
  零发现、旧缓存链仍可取回敏感祖先，以及凭据已吊销；
- 状态：`pending-support`；
- 机器记录：`build/reports/security/github-support-cache-purge.json`，SHA-256
  `72b60b810c735c88cc9081664500d9f0682de2d6e061f521699890c62987d887`。

`2026-07-15T17:31:23Z` 再次通过 GitHub API 只读复核：旧边界 commit 仍返回 HTTP 200，旧敏感
commit 返回 HTTP 422 且正文明确为 `No commit found for SHA`；本轮没有重新打开 Support portal，
也没有发送消息或修改工单。旧边界仍可取回已经足以证明服务端缓存链尚未清除，M0-1 状态继续为
`pending-support`。

`2026-07-15T18:08:29Z` 又进行了一次只读复核，结果未变化：旧边界 commit 仍返回 HTTP 200，
旧敏感 commit 仍返回 HTTP 422（`No commit found for SHA`）。机器记录已刷新本次复核时间；由于
旧边界对象仍可从 GitHub 取回，Support 缓存清除仍是 M0-1 唯一未满足的外部验收条件。

GitHub Support 完成后必须再次执行三项验收：旧边界与敏感 commit id 均不可 fetch；fresh mirror
历史扫描仍为零发现；Support 工单状态与复验时间写入机器报告。只有届时才能关闭 M0-1。
