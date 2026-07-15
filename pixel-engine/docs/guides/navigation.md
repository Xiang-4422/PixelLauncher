# 路由与恢复

优先使用 typed entry API。`PixelRouteDestination<A, R>` 是可复用定义，`PixelRouteRequest` 每次
创建独立 entry；entry 拥有自己的 ID、参数、状态桶、结果通道和生命周期。

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

完整恢复、嵌套、多栈与 Predictive Back 示例见
[API 手册导航章节](../使用说明与API手册.md) 和
[导航恢复迁移指南](../migrations/1.0.0-navigation-restoration.md)。
