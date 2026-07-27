# 发布元数据与供应链

Pixel Engine 的发布门禁验证统一坐标的内容、签名、依赖和外部消费能力。真实公共发布仍需要正式凭据与 namespace 授权。

## 发布元数据

`pixel-engine/config/release-metadata.properties` 是项目 URL、SCM、developer、issue tracker 和许可证信息的唯一来源。仓库使用 Apache-2.0；`LICENSE` 与 `NOTICE` 的摘要被固定，防止占位文件通过门禁。

## 签名与 staging

Gradle Signing Plugin 从受保护属性读取 ASCII-armored OpenPGP 私钥。`tools/pixel-supply-chain-check.sh` 会创建一次性演练密钥，把 `com.purride:pixel-engine:1.0.0` 发布到独占 staging，并在退出时销毁私钥。演练密钥不代表正式发行身份。

候选 groupId `com.purride` 在公共发布前仍需证明 namespace 所有权；无法证明时必须切换到实际获批 namespace，并重跑全部发布门禁。

## 依赖锁定与验证

`pixel-engine/gradle.lockfile` 冻结 release compile/runtime 解析版本；`gradle/verification-metadata.xml` 固定依赖内容摘要。升级依赖时应在独立变更中更新版本、lockfile 和 verification metadata，并审阅新增坐标与摘要。

## SBOM 与来源

供应链脚本生成 CycloneDX 1.7 SBOM、in-toto/SLSA provenance，以及 Maven 主体和补充物的 MD5、SHA-1、SHA-256、SHA-512 旁车。安全完整性判断使用 SHA-256/SHA-512 和 OpenPGP；MD5/SHA-1 仅用于 Maven 兼容。

## 执行

```bash
bash tools/pixel-supply-chain-check.sh
```

结果位于 `build/reports/supply-chain/m9-2/`。脚本还会把 staging 暴露为只读回环 HTTP Maven 仓库，运行最低和推荐工具链消费者。性能与 soak 不属于当前供应链门禁。
