# Pixel Engine Demo

Demo 是 1.0 SDK 的可运行能力目录，不承担 Launcher 业务。catalog 覆盖标准组件、五套主题、RTL 与
textScale、输入/焦点/Accessibility、Overlay、typed navigation、多返回栈、动画、Inspector、帧诊断和
Performance Lab；每个 scene 声明稳定 ID、分类、标签和相关公开 API，便于测试按能力定位。

本地验证：

```bash
./gradlew :pixel-demo:testDebugUnitTest :pixel-demo:assembleDebug --no-daemon
```

消费者示例应优先复制 `pixel-engine/docs/guides` 中的最小写法，不把 demo scaffold、颜色常量或
内部 catalog 类型当成 SDK API。
