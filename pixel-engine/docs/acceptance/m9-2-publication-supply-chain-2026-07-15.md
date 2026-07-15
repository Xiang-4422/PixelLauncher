# M9-2 发布元数据与供应链阶段验收

日期：2026-07-15
状态：已完成。

## 1. 发布元数据与签名

根 Gradle 发布约定统一管理九个正式坐标的项目 URL、SCM、developer、issue tracker、职责描述、
sources、Dokka Javadoc、Gradle module metadata、远程 Maven 仓库和 Signing Plugin。受审字段来自
`pixel-engine/config/release-metadata.properties`，并与真实仓库
`https://github.com/Xiang-4422/PixelLauncher` 对齐。

用户已于 2026-07-15 明确选择 Apache License 2.0。仓库根 `LICENSE` 与 Apache 官方原文逐字节
一致，11,358 bytes、SHA-256
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`；发布元数据固定名称、官方 URL、
`CONFIRMED` 状态和相同摘要。九个 POM 与 CycloneDX SBOM 均声明同一许可证，自动门禁不仅检查文件
存在，还拒绝正文摘要不匹配、POM/SBOM 漂移或状态回退。

供应链脚本每轮创建一次性 RSA 演练密钥，以内存属性签名九坐标并发布到独占 staging。45 个 Maven
主体与 SBOM/provenance 两个补充物在隔离 keyring 中全部验签。许可证最终复验的临时指纹为
`0CC2346492EA439C9AD12A99B9511483A40524DD`；私钥已在脚本退出时销毁，该指纹不是正式发行身份。

## 2. 依赖锁定、校验与来源

九个 SDK 模块分别提交 `gradle.lockfile`，覆盖 `releaseCompileClasspath` 与
`releaseRuntimeClasspath`。`gradle/verification-metadata.xml` 对 1,243 个构建/依赖 artifact 执行
逐文件 SHA-256 校验，拒绝宽泛 trusted-artifacts 与 ignored-keys；AGP 9.0.1 的 AAPT2 精确覆盖
Linux、macOS 和 Windows，避免只在开发机通过。

真实 Release 图包含 113 个组件和 109 个依赖节点。生成器产出 CycloneDX 1.7 SBOM、in-toto
Statement v1/SLSA Provenance v1，以及 45 个主文件和两个补充物的 MD5、SHA-1、SHA-256、SHA-512
旁车。来源证明的 45 个 subject 与 staging 文件的实际 SHA-256 完全一致，并明确记录本轮
`dirtyWorktree=true`，没有把本地演练冒充干净正式构建。

## 3. 发布内容与安全扫描

自动检查逐坐标验证 POM URL/SCM/developer/issue tracker/description、直接外部依赖 allowlist、AAR 根
条目、sources/Javadoc 和 Gradle variants。生产 AAR 不包含 testFixtures、测试框架；testing DSL 只在
`pixel-testing`，inspector 只在 `pixel-debug`，没有未知嵌套 payload 或意外直接依赖。

staging secret 扫描覆盖 2,039 个文件/归档条目，发现 0，allowlist 命中 0。OSV 官方 API 扫描 104
个外部 Maven 组件，漏洞发现 0，未使用例外。仓库/应用安全门禁另验证 2,009 个条目与两个 APK 的
backup contract，均通过。

## 4. 远程样式消费者矩阵

脚本通过只读回环 HTTP 服务暴露本轮已签名 staging，不允许消费者读取工程内 file-Maven 路径：

- 最低支持：AGP 8.10.1、Gradle 8.11.1、Kotlin 2.2.10、compileSdk 36；
- 推荐支持：AGP 9.1.1、Gradle 9.3.1、built-in Kotlin 2.2.10、compileSdk 36.1；
- 两档均通过 Kotlin SPI、Java 可见性、单测、Debug APK 和 R8 Release APK；
- compileSdk 35 与 AGP 8.9.0 分别由 AAR metadata 在预期边界拒绝。

聚合 SDK、九个单 artifact、RenderObject SPI、RouteEntry、旧消费者二进制及仅依据文档建立的消费者
也全部通过。

## 5. CI、兼容与发布流程

CI 增加独立 `supply_chain` job，并把结果接入只接受全部 `success` 的 required 聚合器；失败时上传
staging、签名/SBOM/provenance/OSV/验证报告与远程矩阵。CI 与统一 release gate 均固定执行
`PIXEL_REQUIRE_LICENSE=1`，供应链脚本自身也默认严格校验许可证。

支持矩阵、构建类型、供应链和安全发布流程文档已经固化 minSdk/minCompileSdk、Kotlin/AGP/Gradle/
Java 组合，以及 experimental API、弃用周期、breaking change、安全修复、回滚和不可变版本策略。

## 6. 最终技术验证

- `./gradlew :app:assembleDebug :app:assembleRelease :pixel-engine:lintDebug
  --write-verification-metadata sha256 --no-build-cache --no-daemon`：666 tasks，成功；
- `./tools/pixel-release-check.sh`：主批次 1,060 tasks 成功，工具测试 106/106；全部 API/ABI、Lint、
  Release、消费者、六场景性能、soak、两个 Baseline Profile APK 与 72 页严格 MkDocs 通过；
- M9-2 许可证最终签名发布批次：287 tasks，45 个主文件、113 个组件、47 个签名通过；
- `python3 -m unittest tools.tests.test_supply_chain_tools`：10/10，通过；
- `python3 -m py_compile`、`bash -n` 和严格 MkDocs：通过。

核心机读证据：

- `build/reports/supply-chain/m9-2/validation.json`：SHA-256
  `0a6cb5681930c1e0dedd11d611126299bf2e8e0e7c6d26dd4033ee0791fca2dd`；
- `build/reports/supply-chain/m9-2/osv.json`：241 bytes，SHA-256
  `7a4968e7faec7bad460456263f6d3ce08df56b064bf7148072ae04fe308d2b50`；
- `build/reports/supply-chain/m9-2/publication.json`：SHA-256
  `abbc070187bfb5eb14cf6763759584ae59a3f4e32ecce5f7a90bfd83601c236a`；
- `build/reports/compatibility/m8-2/matrix.json`：SHA-256
  `ad69ed05af31513d6da4ef65a460b665d2e716a4e8e097c9b806fb261c69ab30`；
- `gradle/verification-metadata.xml`：326,638 bytes，SHA-256
  `bb39f2bac6cc1ee1d5ece88e3651c5d6900f8c5e971ba7f913830456a6499b37`。

### 最新工作树统一复验

加入模拟器/实体性能证据隔离后，再次执行完整 `bash tools/pixel-release-check.sh`，最终退出码为 0。
主 Gradle 批次为 1,060 tasks；Python tooling 116/116；全部发布兼容、最低/推荐消费者、九个隔离
artifact、旧二进制消费者、文档消费者、JVM 性能趋势、soak、Baseline Profile 和 75 页严格
MkDocs 均通过。一次性签名供应链批次仍为 287 tasks、45 个主文件、113 个组件；staging secret
扫描 2,039 项为零发现，OSV 104 个组件为零发现。

本次最新报告 SHA-256：

- 供应链 validation：`18246f2f4c43124f5917d3a7b55c81fba546186fdd2a6f01ee9c8395a44bd5e9`；
- 供应链 publication：`0e33fb77ac33f6972aa3b60e33bf3f7b650082bf203758a42e1697ac86d3aa0d`；
- 兼容矩阵：`bd3cebff45125e908f2fb4da28506b4074ee88598f231cad2d376e87d70e2fc1`；
- JVM 性能趋势：`ff851a25f98804b247bf6a32fa26da1c993c854208cd60ea0e12345f670276f3`；
- Baseline Profile：`a7797ee64c09972b2c82085ba3dec143a74b26855ff5e1c767586b3e2f36dcd5`；
- 仓库/产物 secret scan：`17f7317a7c021b3ee7a250e5b2986b0d005c69b849d3059b94a3c7a69c41f277` /
  `004cd9aa848eab645e62191bbb5edfd2c53a35b04173c0d850792af048e6e737`。

随后在 Apache-2.0 最终状态下独立执行 `bash tools/pixel-supply-chain-check.sh`：validation 明确记录
`licenseStatus=CONFIRMED`、`licenseRequired=true` 和 LICENSE 强摘要；九个 POM、SBOM、47 个签名与
checksum、OSV、staging secret scan 及 HTTP 最低/推荐消费者矩阵全部通过。M9-2 因此完成，但版本仍为
`0.1.0-SNAPSHOT`，一次性签名仍不是正式身份，本证据不冒充 M9-3 的 1.0.0 候选或真实发布。

## 7. M9-2 收口边界

Apache-2.0 决策、仓库原文、POM/SBOM、签名、checksum、依赖锁、来源、漏洞扫描、内容边界和远程样式
消费者已形成同一自动验证链，M9-2 没有剩余 P0/P1 项。M9-3 仍必须等待 M0 历史清理、M6 代表性实体
设备性能验收完成，再切换 1.0.0 候选；真实外部发布仍需要用户确认发布仓库及发布动作。
