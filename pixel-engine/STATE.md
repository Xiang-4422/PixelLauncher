# 状态管理

pixel-engine 提供三个核心控制器，专门管理滚动列表、翻页和文本输入的状态。它们都继承 `ChangeNotifier`，可以直接 `addListener { ... }` 订阅变化。

| 控制器 | 用途 |
|---|---|
| [`PixelListController`](#pixellistcontroller) | `ListView` / `ListViewBuilder` / `SingleChildScrollView` 的滚动 |
| [`PixelPagerController`](#pixelpagercontroller) | `PageView` / `PageViewBuilder` 的翻页 |
| [`PixelTextFieldController`](#pixeltextfieldcontroller) | `TextField` 的文本与光标 |

通用结构都是 **controller**（无状态的行为持有者）+ **state**（每个 widget 实例的可变状态）。这样可以一个 controller 驱动多个 widget，或在 widget 重建间保留 state。

---

## PixelListController

负责列表滚动物理（拖动、惯性、回弹）。

### 创建

```kotlin
private val controller = PixelListController(
    physics = PixelScrollPhysics.Default,   // 可自定义滚动物理
)
private val state = controller.create(
    initialScrollOffsetPx = 0f,
)
```

把这两个对象绑定到一个 `ListViewBuilder`：

```kotlin
ListViewBuilder(
    itemCount = 100,
    itemBuilder = { i -> Text("ITEM $i") },
    state = state,
    controller = controller,
    itemExtent = 8,   // 长列表必传：启用 lazy 渲染（详见 WIDGETS.md）
)
```

### 公开方法

```kotlin
// 编程式滚动到偏移位置
controller.scrollTo(state, targetOffsetPx, viewportHeightPx, contentHeightPx)

// 滚动到指定 item（自动计算最小移动）
controller.scrollItemIntoView(state, itemIndex)

// 查询是否仍在滚动 / 惯性中
val active = controller.isActive(state)

// 监听任意状态变化
val listener: VoidCallback = { /* react */ }
controller.addListener(listener)
// ...
controller.removeListener(listener)

// 或用便利扩展，返回的句柄方便后续 remove
val handle = controller.observe { /* react */ }
```

### 读取当前滚动位置

```kotlin
ListenableBuilder(listenable = controller) {
    Text("OFFSET: ${state.scrollOffsetPx.toInt()}")
}
```

### 自定义物理

```kotlin
val customPhysics = PixelScrollPhysics(
    decelerationPxPerSecondSquared = 1200f,   // 惯性减速
    minFlingVelocityPxPerSecond = 50f,        // 最小 fling 速度阈值
    bounceEnabled = true,                     // 是否回弹
    snapEpsilonPx = 1f,                       // settle 终点容差
)
val controller = PixelListController(physics = customPhysics)
```

---

## PixelPagerController

负责整页翻页（水平 / 垂直）。

### 创建

```kotlin
private val controller = PixelPagerController()
private val state = controller.create(
    pageCount = 3,
    initialPage = 0,
    axis = Axis.HORIZONTAL,
)
```

绑定到 `PageViewBuilder`：

```kotlin
PageViewBuilder(
    axis = Axis.HORIZONTAL,
    controller = controller,
    state = state,
    itemCount = 3,
    itemBuilder = { i ->
        Center(child = Text("PAGE $i"))
    },
    onPageChanged = { newPage ->
        /* 翻页结束（settle 完）回调 */
    },
)
```

### 公开方法

```kotlin
// 编程式翻到指定页
controller.syncToPage(state, targetPage = 2)

// 取当前页快照（包含 currentPage / dragOffsetPx 等）
val snapshot = controller.snapshot(state)

// 查询是否仍在滑动 / 翻页动画中
val active = controller.isActive(state)

// 监听任意状态变化（含拖动中、settle 中、page 变化）
controller.addListener { /* react */ }
```

### 当前页

```kotlin
ListenableBuilder(listenable = controller) {
    Text("PAGE ${state.currentPage} / ${state.pageCount}")
}
```

---

## PixelTextFieldController

负责输入框文本、光标、焦点。

### 创建

```kotlin
private val controller = PixelTextFieldController()
private val state = controller.create(
    initialText = "",
    initialSelection = TextSelection(start = 0, end = 0),
)
```

绑定到 `TextField`：

```kotlin
TextField(
    state = state,
    controller = controller,
    placeholder = "ENTER YOUR NAME",
    inputType = PixelInputType.TEXT,
    imeAction = PixelTextInputAction.DONE,
    onSubmitted = { value ->
        /* IME action 触发，比如点了软键盘的 DONE */
    },
)
```

### 公开方法

```kotlin
// 编程式修改文本
controller.updateText(state, newText = "HELLO")

// 设光标 / 选区
controller.setSelection(state, start = 0, end = 5)

// 全选
controller.selectAll(state)

// 清空
controller.clear(state)

// 焦点控制
controller.requestFocus(state)   // 请求系统 IME 弹出
controller.requestBlur(state)    // 收起 IME

// 监听文本 / 焦点 / 光标变化
controller.addListener { /* react */ }
```

### 输入类型

不同 `PixelInputType` 触发不同的系统软键盘：

| 类型 | Android InputType | 软键盘形态 |
|---|---|---|
| `TEXT` | `TYPE_CLASS_TEXT` | 普通文字 |
| `NUMBER` | `TYPE_CLASS_NUMBER` | 数字（含 -. 号）|
| `NUMBER_PASSWORD` | `TYPE_CLASS_NUMBER` + password | 数字密码 |
| `EMAIL` | `TYPE_CLASS_TEXT` + email | 含 @ 键 |
| `PHONE` | `TYPE_CLASS_PHONE` | 电话拨号盘 |
| `URL` | `TYPE_CLASS_TEXT` + uri | 含 / 键 |
| `PASSWORD` | `TYPE_CLASS_TEXT` + password | 普通密码 |

### IME action

| Action | 软键盘右下角按钮 |
|---|---|
| `DONE` | "完成" |
| `NEXT` | "下一项" |
| `SEND` | "发送" |
| `SEARCH` | "搜索" |
| `GO` | "前往" |

---

## 控制器跨 Activity 重建保留

控制器和 state 都是 Kotlin 对象，**不会自动保留**。Activity 配置变化（旋转、深色模式切换等）时会被销毁。解决方案：

### 方案 A：放到 ViewModel

```kotlin
class MyViewModel : ViewModel() {
    val listController = PixelListController()
    val listState = listController.create()
}

class MyActivity : AppCompatActivity() {
    private val vm: MyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = {
                    ListViewBuilder(
                        state = vm.listState,
                        controller = vm.listController,
                        /* ... */
                    )
                },
            ),
        )
        setContentView(setup.rootView)
    }
}
```

### 方案 B：序列化关键字段

简单场景可用 `onSaveInstanceState` 把 `state.scrollOffsetPx` 等存到 `Bundle`，重建时读回。

---

## 自定义滚动物理

`PixelScrollPhysics` 字段：

```kotlin
data class PixelScrollPhysics(
    val decelerationPxPerSecondSquared: Float = 1500f,
    val minFlingVelocityPxPerSecond: Float = 30f,
    val maxFlingVelocityPxPerSecond: Float = 8000f,
    val bounceEnabled: Boolean = false,
    val bouncePxAtMaxOverscroll: Float = 32f,
    val snapEpsilonPx: Float = 0.5f,
)
```

物理参数可以通过 `PixelHostSetupConfig` 整体覆盖 SDK 默认值，影响所有列表 / 翻页：

```kotlin
createPixelHostSetup(
    context = this,
    config = PixelHostSetupConfig(
        scrollPhysics = PixelScrollPhysics(
            decelerationPxPerSecondSquared = 2400f,
            bounceEnabled = true,
        ),
    ),
)
```

或为某个 controller 单独指定：

```kotlin
val tightController = PixelListController(
    physics = PixelScrollPhysics(decelerationPxPerSecondSquared = 3000f),
)
```

---

## 接下来

- 想自定义手势行为（嵌套滚动等）→ [EXTENDING.md](EXTENDING.md) 的「自定义手势策略」
- 想看完整 widget 签名 → [WIDGETS.md](WIDGETS.md)
