# pixel-engine

**像素风 Android UI SDK** —— 用 Flutter 风格的声明式 API 构建完全像素化的 Android 应用。

pixel-engine 不依赖 Android View 树，所有内容由一个继承自 `android.view.View` 的 `PixelHostView` 直接通过 `Canvas` 绘制：每个屏幕像素都是你定义的、点对点可控的"逻辑像素方块"。这让你能为复古游戏、单色 / 双色界面、像素艺术工具、e-ink 风设备等场景搭建从字体到手势都纯像素化的 UI。

---

## 你能用它做什么

- **像素艺术启动器 / 工具应用**：每帧只用 ON / OFF / ACCENT 三个色阶，配合自定义点阵字体
- **复古游戏 UI / HUD**：高度可控的逐像素布局，无系统控件污染
- **e-ink / 单色设备界面**：单色调色板下保证视觉一致
- **任何"以像素为一等公民"的 App**：像素被当作逻辑单位而非物理单位，自动按设备 DPI 缩放

---

## 核心特性

- **Flutter 风格 API**：`Row` / `Column` / `Stack` / `Padding` / `Text` / `Container` 等声明式 widget，组合即可
- **完整状态机制**：`StatefulWidget` / `State` / `setState` / `InheritedWidget` / `ValueNotifier` 一应俱全
- **完整滚动 / 翻页**：`ListView` / `PageView` / `SingleChildScrollView` + 手势惯性物理
- **主题与 token**：三色调色板 + 主题数据，全局换肤或单 widget 覆盖
- **可扩展**：自定义 `RenderObject`、手势策略、字体栅格器、帧调度器
- **零分配热路径**：每帧 ~1KB 分配、内置 buffer 池、O(n) 文本换行、LRU 字形缓存

---

## 安装

pixel-engine 是 Android library 模块，最低 `minSdk = 24`，编译目标 `compileSdk = 36`。

### 方式 A：作为本仓库子模块使用

```kotlin
// settings.gradle.kts
include(":pixel-engine")
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(project(":pixel-engine"))
}
```

### 方式 B：发布为 aar 后引入（待定）

后续会发布到 Maven 仓库，届时通过 Gradle 坐标引入：

```kotlin
implementation("com.purride:pixel-engine:<version>")
```

---

## Hello, Pixel World

下面是从空 Activity 到可见像素文字的完整最小示例：

```kotlin
class HelloPixelActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = {
                    Center(
                        child = Text("HELLO PIXEL"),
                    )
                },
            ),
        )
        setContentView(setup.rootView)
    }
}
```

跑起来：屏幕中央显示像素点阵 `HELLO PIXEL`。手势、IME、调色板、字体等会用 SDK 默认值。

想要交互（按钮、列表、翻页）、自定义配色、状态保留等，请继续阅读：

---

## 文档导航

| 文档 | 适合你想…… |
|---|---|
| [快速入门](docs/快速入门.md) | 跟着教程把 SDK 接入到一个真实 Activity，覆盖按钮 / 文字 / 列表 / 主题 |
| [组件目录](docs/组件目录.md) | 查 SDK 全部内置 widget 目录与签名 |
| [主题系统](docs/主题系统.md) | 学怎么用 token + 主题做全局换肤 / 单点覆盖 |
| [状态管理](docs/状态管理.md) | 学怎么管理列表滚动 / 翻页 / 输入框等状态 |
| [扩展开发](docs/扩展开发.md) | 自己写 `RenderObject` / 手势策略 / 字体栅格器 / 帧调度 |
| [架构说明](docs/架构说明.md) | 了解内部三层模型与渲染管线（可选）|
| [接手任务清单](docs/接手任务清单.md) | 维护者接手后的迭代任务与优先级 |

---

## 设计取向

pixel-engine 选用 **Flutter 风格 retained build tree** 而非 Compose 风格 immediate mode，原因：

- **状态与渲染解耦**：widget 是不可变配置，element 树持有生命周期，render object 拥有 layout/paint。三层职责清晰
- **可预测的重建**：`setState` / `InheritedWidget` 触发的脏标记沿 element 树传播，重建范围明确
- **像素操作友好**：`RenderObject` 直接拿到 `PixelBuffer`，无任何中间抽象层，自定义渲染零损耗

---

## 模块边界

- `:pixel-engine` —— 本 SDK（你要 `implementation` 的模块）
- `:pixel-demo` —— SDK 自测应用（含 25+ 个完整场景示例，可作为用法参考）
- `:app` —— PixelLauncher 主程序（生产消费者）

---

## 反馈与贡献

issue / PR / 讨论欢迎到本仓库根目录提交。
