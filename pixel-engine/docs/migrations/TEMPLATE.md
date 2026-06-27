# pixel-engine x.y.z 迁移指南

## 影响范围

- 说明受影响的 package、class、function 或行为。
- 说明是否影响 source compatibility、binary compatibility 或仅影响运行时行为。

## 破坏性变更

### 变更名称

旧写法：

```kotlin
// TODO
```

新写法：

```kotlin
// TODO
```

迁移步骤：

1. TODO
2. TODO

## 验证

```bash
./tools/pixel-release-check.sh
./tools/pixel-sdk-consumer-smoke.sh
```

如果变更涉及真实设备输入、性能或宿主行为，补充运行：

```bash
./tools/pixel-device-smoke.sh
./tools/pixel-perf-smoke.sh
```

