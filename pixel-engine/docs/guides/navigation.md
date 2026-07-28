# 路由与恢复

导航只有 typed entry API，全部位于根包 `com.purride.pixelui`。`PixelRouteDestination<A, R>` 是
可复用定义，`PixelRouteRequest` 每次创建独立 entry；entry 拥有自己的 ID、参数、状态桶、结果通道
和生命周期。

```kotlin
data class EditorArgs(val documentId: String)

val editor = pixelRouteDestination<EditorArgs, String>(id = "editor") { _, scope ->
    Column(
        children = listOf(
            Text("EDIT ${scope.arguments.documentId}"),
            TextButton(text = "DONE", onPressed = { scope.complete("saved") }),
            TextButton(text = "CANCEL", onPressed = { scope.cancel() }),
        ),
    )
}

val request = PixelRouteRequest(editor, EditorArgs("doc-42"))
navigator.push(request) { outcome ->
    when (outcome) {
        is PixelRouteOutcome.Success -> consume(outcome.value)
        is PixelRouteOutcome.Cancelled -> recordCancellation(outcome.reason)
    }
}
```

规则：

- `Success(null)` 与取消不同；结果回调和 dispose 都恰好一次。
- replace/remove/clear/Host destroy 必须给未完成 entry 明确取消原因。
- snapshot 只恢复注册表 allowlist 中的 destination；codec 先完整校验版本、大小、重复 ID 和 payload。
- `PixelMultiStackNavigator` 保持各栈挂载与 Back 隔离；inactive parent 不抢占 child Back。
- deep link 先匹配、再 typed decode、最后原子提交导航，拒绝时不改变当前栈。

## 快照与结果类型

| 类型 | 语义 |
|---|---|
| `PixelNavigatorSnapshotEncoded` / `PixelNavigatorSnapshotEncodeRejected` | 单栈快照编码成功或被拒绝 |
| `PixelNavigatorSnapshotDecoded` / `PixelNavigatorSnapshotDecodeRejected` | 单栈 envelope 解码成功或被拒绝 |
| `PixelRoutePayloadDecoded` / `PixelRoutePayloadRejected` | destination 参数 payload 解码结果 |
| `PixelRouteStateDecoded` / `PixelRouteStateRejected` | entry 局部状态解码结果 |
| `PixelTypedNavigatorStack` | 独立保留的 typed retained stack 定义 |
| `PixelDeepLinkDecodeResult` | 一个匹配 route 的 typed 参数解码结果 |
| `PixelTypedDeepLinkResult` | deep link 导航的终态结果 |
| `PixelMultiStackSnapshotEncoded` / `PixelMultiStackSnapshotEncodeRejected` | 多栈 snapshot 编码结果 |
| `PixelMultiStackSnapshotDecodeResult` | 多栈 snapshot envelope 的解码结果父类型 |
| `PixelMultiStackSnapshotDecoded` / `PixelMultiStackSnapshotDecodeRejected` | 多栈 snapshot 解码成功或拒绝 |
| `PixelMultiStackRestored` / `PixelMultiStackRestoreRejected` | 多栈原子恢复结果 |
| `PixelMultiStackSnapshotFailure` | 多栈 snapshot 失败的结构化上下文 |

所有 rejected 结果都必须保留原 live stack，不允许半提交。

完整恢复、嵌套、多栈与 Predictive Back 示例见[API 手册导航章节](../使用说明与API手册.md)；
当前唯一 snapshot schema 与 adapter 约定见[接入与升级指南](migration.md)。
