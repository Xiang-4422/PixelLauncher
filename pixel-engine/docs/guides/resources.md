# 资源与内存

资源键先经 `PixelResourceResolver` 规范化，再由 loader 和 `PixelResourceCache` 解码/缓存。应用层
应使用 manifest/catalog 中的稳定逻辑 key，不把绝对文件路径、Content URI 或 Android resource
实现细节传进 widget。

- bitmap/sprite/glyph 输入在分配前校验尺寸、stride、像素数、字节数和压缩展开上限。
- cache 同时受 entry 数和 byte weight 约束，LRU 驱逐必须释放池化缓冲；负缓存有 TTL。
- Engine 默认独占 cache；只有显式向多个 Engine 注入同一 cache 才共享。
- Activity/Fragment 销毁时释放 Host 引用；不要让 drawable/stream/context 进入长期 cache value。
- 生产资源失败通过 sealed result/error reporter 上报，不能在渲染热路径抛出未分类异常。

资源格式、manifest 与预热 API 见 [API 手册](../使用说明与API手册.md)，安全与内存边界变化见
[资源加载迁移指南](../migrations/1.0.0-resource-loading-memory.md)。
