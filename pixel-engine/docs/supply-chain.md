# 发布元数据与供应链

Pixel Engine 的发布门禁同时验证“发布了什么、由谁签名、依赖解析到了什么、消费者能否从远程样式
仓库使用”。本页描述 M9-2 建立的可复现流程；真实外部发布仍需要 M9-3 的用户确认和正式凭据。

## 统一发布元数据

九个 MavenPublication 共用
`pixel-engine/config/release-metadata.properties`：项目 URL、SCM、developer 和 issue tracker 均来自仓库
真实 `origin`。各模块只保留自己的 artifactId 与职责描述，避免复制后漂移。

用户已于 2026-07-15 明确选择 Apache License 2.0，仓库根目录的 `LICENSE` 是许可证原文，`NOTICE`
固定项目归属以及随 `pixel-runtime` 分发的 Unicode 17 生成数据声明；
`licenseStatus=CONFIRMED`、许可证名称和官方 URL 是 POM/SBOM 的唯一元数据来源。CI、统一 release gate
和独立供应链门禁都以 `PIXEL_REQUIRE_LICENSE=1` 执行；缺少 LICENSE/NOTICE、POM/SBOM 声明不一致
或状态回退都会失败，不再保留无许可证技术演练作为正式入口。`licenseFileSha256` 与
`noticeFileSha256` 固定两份正文摘要，防止任意占位文件仅凭文件名通过许可证验收。

## 签名与临时远程仓库

Gradle Signing Plugin 从以下受保护属性读取 ASCII-armored OpenPGP 私钥，不把密钥写入仓库：

- `ORG_GRADLE_PROJECT_signingKey`
- `ORG_GRADLE_PROJECT_signingPassword`
- 可选远程凭据 `ORG_GRADLE_PROJECT_pixelRepositoryUsername` / `pixelRepositoryPassword`
- 目标 `ORG_GRADLE_PROJECT_pixelStagingRepositoryUrl`

`tools/pixel-supply-chain-check.sh` 会生成一次性 RSA 演练密钥，发布九个坐标到独占 staging，随后删除
私钥。该密钥只证明流水线具备真实签名/验签能力，不是正式发行身份。AAR、POM、Gradle `.module`、
sources 和 Javadoc 共 45 个主体，以及 SBOM/provenance 两个补充物，全部在隔离 keyring 中验签。

正式发布必须由维护者提供长期或受管签名子密钥，并把公钥发布到可验证的 key server；不得复用演练
密钥。Maven Central 的真实上传、close/publish 和不可变发布只在 M9-3 获得明确授权后执行。

### 正式 namespace 决策

当前九个候选坐标使用 `com.purride`。本地 Maven staging 可以验证坐标结构，却不能证明发布者有权在
公共仓库占用该 groupId。Maven Central 把 groupId 作为 namespace 管理；DNS 型 `com.purride`
需要证明对 `purride.com` 的控制。若无法提供该证明，正式发布前必须切换到 Central Portal 实际授予的
已验证 namespace（GitHub 登录通常可申请 `io.github.<用户名>`），并重新执行全部 publication、consumer、
API 文档和供应链验收。规则见 [Sonatype namespace 官方说明](https://central.sonatype.org/register/namespace/)。

因此 `com.purride` 在 D-003 解决前只是候选 groupId；artifactId 和 `1.0.0` 版本已经冻结，但不得把
一次性签名 staging 的成功解释为 Maven Central namespace 已确认。

## 依赖锁定与验证

九个 SDK 模块分别提交 `gradle.lockfile`，冻结 `releaseCompileClasspath` 和
`releaseRuntimeClasspath`。`gradle/verification-metadata.xml` 对插件、构建工具和依赖 artifact 执行
SHA-256 严格验证。两者职责不同：lockfile 固定解析版本，verification metadata 拒绝同坐标内容被替换。
门禁还拒绝宽泛 trusted-artifacts/ignored-keys，要求每个条目都有合法 SHA-256，并固定校验当前 AGP
所需的 Linux、macOS、Windows 三平台 AAPT2，避免只在生成元数据的单一开发机上通过。

升级依赖时使用以下受审流程：

1. 在独立变更中修改版本声明。
2. 执行 `./gradlew writePixelReleaseDependencyGraph --write-locks`，审阅所有 lockfile diff。
3. 对新 artifact 确认官方来源后，在真实任务上执行
   `--write-verification-metadata sha256`，审阅新增 group/name/version 与摘要。
4. 重新执行 API、consumer、publication、OSV 和完整 release gate。

不得删除 verification 条目、切到 `lenient/off` 或用宽泛 trusted artifact 规则让构建变绿。

## SBOM、来源和 checksum

`tools/generate_pixel_supply_chain.py` 根据 Gradle 已解析图生成：

- CycloneDX 1.7 JSON SBOM，包含 Maven purl、完整传递组件与依赖边；
- in-toto Statement v1、SLSA Provenance v1 结构的构建来源；
- 45 个 Maven 主体和两个补充物的 MD5、SHA-1、SHA-256、SHA-512 旁车。

MD5/SHA-1 用于满足 Maven 仓库兼容要求，不作为安全强摘要；完整性判断使用 SHA-256/SHA-512 与
OpenPGP 签名。provenance 记录 Git commit、dirty worktree、锁文件和 verification metadata 摘要；本地
脏树演练会明确写 `dirtyWorktree=true`，不能冒充正式可复现发布。

## 漏洞与内容扫描

`tools/scan_pixel_osv.py` 把所有外部 Maven 组件批量提交到 OSV 官方 API：

- HIGH/CRITICAL 未解释发现使门禁失败；
- 没有可判定严重度的发现也失败，不能静默降级；
- 例外只能写入 `pixel-engine/config/osv-allowlist.json`，且必须包含 ID、理由和过期日期；
- 当前 Release 图不使用例外。

发布内容检查同时拒绝测试框架/testFixtures、testing/debug 类跨 artifact、未知 AAR 根条目、空文档包、
意外内部依赖和 secret。资源条目进入机读报告，供发布复核确认没有无用资源。

## 聚合命令与证据

许可证确认后，正式执行：

```bash
bash tools/pixel-supply-chain-check.sh
```

输出位于 `build/reports/supply-chain/m9-2/`，包括公钥、SBOM、provenance、OSV 和 47 文件验证报告。
脚本随后把 staging 暴露为只读回环 HTTP Maven 仓库，最低/推荐/拒绝组合都从远程样式坐标重新解析。

当前 1.0 Goal 不执行性能或 soak。统一 release gate 默认跳过这些专项；后续独立性能 Goal 如需复用
原有入口，必须显式设置 `PIXEL_RELEASE_INCLUDE_PERFORMANCE_GATES=1`。
