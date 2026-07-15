# M9-1 注释、KDoc 与文档站验收

日期：2026-07-14

## 1. KDoc 门禁

原门禁只匹配同一行以 `public` 开头的声明，排除 constructor，无法正确处理 protected、多行修饰符、
注解、嵌套块注释和类 KDoc 的 `@property`。本轮把扫描逻辑迁到
`tools/check_kdoc_coverage.py`，使用忽略字符串/普通注释且支持 Kotlin 嵌套块注释的 token 流：

- 识别 public/protected class/interface/object/fun/val/var/typealias/constructor；
- 支持注解、多行修饰符、`fun interface`、同一行多个声明和反引号名称；
- 主构造属性允许使用 Kotlin 标准类 KDoc `@property`/历史 `@param` 归属；
- 拒绝空说明、TODO/FIXME/待补充模板和只复述声明名的 KDoc；
- 按仓库 AGENTS 规则要求职责首段至少包含中文，英文参数、返回和兼容细节可以继续保留；
- JSON 写出全部 missing/invalid，不再只展示 40 个样例。

迁移先识别 629 个真实缺失声明并按路由、渲染、状态、布局、生命周期、资源或兼容职责补充说明；
随后为 914 份只有英文的历史 KDoc 增加中文职责首段，原有英文参数和约束未删除。对早期批量说明又执行
模板审计，消除 116 条泛化函数、95 条泛化属性和 47 条泛化类型说明。最终报告：

```text
publicProtectedDeclarations=2829
documentedDeclarations=2829
coveragePercent=100.00
minimumPercent=100.00
missing=0
invalid=0
```

`pixel-engine/build/reports/kdoc/kdoc-coverage.json` 为 187 bytes，SHA-256
`af0023353719a2dab162447d0bd90ab26a16e8b29ed8a923eed2c516fba9ff88`。四个扫描器测试覆盖旧漏检形式、
类 `@property`、无效模板、完整报告和退出码。KDoc 插入曾暴露轻量源码 API dumper 会把主构造参数
同行注释写入 signature；生成器现先剥离 KDoc，原 public baseline、Metalava 和 JVM ABI 均保持不变。

## 2. 文档站

`tools/prepare_mkdocs_docs.py` 每次清理 `build/mkdocs-source`，只复制根 `docs` 与
`pixel-engine/docs` 的 Markdown，并保留仓库相对路径，因此跨目录链接无需重写。`mkdocs.yml` 的
缺页、无效链接、锚点、绝对链接和未识别链接均设为 warning；`--strict` 会把任一 warning 变成失败。
导航现在包含 SDK 首页、Quickstart、九类接入主题、API、架构、SPI、兼容、1.0 发布清单、长期规划
和项目文档。

新增独立消费者指南：

- `guides/quickstart.md`
- `guides/host-integration.md`
- `guides/theme-and-components.md`
- `guides/navigation.md`
- `guides/resources.md`
- `guides/custom-render-spi.md`
- `guides/testing.md`
- `guides/performance.md`
- `guides/migration.md`

同时更新 API 手册、架构、长期规划、发布/兼容策略、历史 snapshot 清单、README，并新增正式
`1.0.0发布清单.md` 和 `pixel-demo/README.md`。最终统一源码树包含 68 个 Markdown 页面；严格站点
构建 0 warning，输出 96 个文件、约 6.1 MiB。

## 3. 只依据文档的新消费者

`tools/pixel-docs-consumer-smoke.sh` 每次创建全新外部 Android 工程，只从隔离 file-Maven 解析
`pixel-android` 与 test scope 的 `pixel-testing`。同一工程按发布文档完成：

1. `PixelHostSetupConfig` 与 `createPixelHostSetup` 的最小 Host 接入；
2. `pixelRouteDestination<Unit, String>` typed route；
3. `PixelLeafRenderObjectWidget`/`PixelRenderBox` 自定义 layout/paint/update；
4. `PixelTester` 离屏渲染、route id 和真实像素断言；
5. Debug APK 与消费者侧 R8 Release APK。

测试、Debug 和 Release 全部成功。稳定报告
`build/reports/compatibility/docs-consumer.txt` 为 243 bytes，SHA-256
`e5763682cd4371af8b86ade5b950b84a98c9248548c1a4050e643f32ab57886d`；Debug/Release APK SHA-256
分别为 `2285357972e8fd85a0ac0ba31d000b4011c2e89277347ff990eb2cb532a2986b`、
`c622e2e0187e1f18e803d6c1c29d91683c97fe011887184c7843579b7592ff31`。该消费者已加入 CI consumer
分组和统一 release gate。

## 4. 最终验证

- 干净 `tools/pixel-ci-api.sh`：133 tasks 全执行，Public/Metalava/JVM ABI、stable boundary、Unicode、
  theme token、KDoc 100% 和严格 MkDocs 全部通过，耗时 3m21s。
- Python tooling：98/98，零 failure/error/skipped。
- `:pixel-engine:compileDebugKotlin`、文档消费者 JVM/Debug/R8 Release、YAML 解析和
  `git diff --check` 通过。

M9-1 六项任务和三项验收均满足，范围内 P0/P1 遗留为零。下一工作包为 M9-2 发布元数据、供应链与
兼容策略；本记录不代表真实中央仓库发布或签名动作已经执行。
