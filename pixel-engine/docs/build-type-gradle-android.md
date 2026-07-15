# Pixel Engine Gradle Android build type

本文是 SLSA provenance `buildType` 指向的仓库内契约，描述发布 subject 如何产生。

## 输入

- 当前 Git tree 与 commit；
- 九个 SDK 模块的 `0.1.0-SNAPSHOT` 或候选正式版本；
- 各模块 Release compile/runtime lockfile；
- `gradle/verification-metadata.xml` 中受审 SHA-256；
- 通过环境变量注入的 ASCII-armored OpenPGP 私钥与可选 Maven 凭据。

## 步骤

1. Gradle Wrapper 解析受锁定、受 checksum 验证的依赖。
2. 九个 `release` publication 生成 AAR、POM、Gradle module metadata、sources 与 Javadoc/KDoc。
3. Signing Plugin 对每个 publication 主体生成 detached OpenPGP signature。
4. 已解析依赖图转换为 CycloneDX 1.7 SBOM。
5. Maven 主体摘要、Git commit、dirty 状态和依赖材料写入 in-toto/SLSA provenance。
6. 生成四种 checksum，在隔离 keyring 中验证全部签名。
7. staging 通过只读 HTTP 服务提供给最低与推荐消费者矩阵。

## 输出

每个坐标固定包含 AAR、POM、`.module`、sources JAR、Javadoc JAR及其签名/checksum。聚合坐标额外包含
`-sbom.cdx.json` 与 `-provenance.intoto.json`。正式 1.0.0 输出不得使用 dirty worktree、临时签名身份或
`SNAPSHOT` 文件名。
