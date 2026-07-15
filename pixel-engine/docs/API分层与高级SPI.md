# pixel-engine API 分层与高级 SPI

本文定义 `pixel-engine` 对外 API 的分层、兼容边界，以及
`com.purride.pixelui.advanced` 中自定义 RenderObject 的扩展契约。版本、弃用周期和
migration guide 通用规则仍以 [发布与兼容策略](发布与兼容策略.md)
为准。

> 状态边界：本文记录当前源码中已经存在的真实 `Pixel*` 类型和目标兼容契约，
> 不代表 [1.0 Goal](1.0-GOAL.md) 的 M1 已验收。只有 API dump、独立 AAR 消费者、
> 旧二进制消费者和发布门禁都产生通过证据后，对应工作包才能标记完成。

## 1. 分类判定顺序

一个声明只属于一个主分类。按下列顺序判定，靠前的规则优先：

1. 位于 `com.purride.pixelui.internal.*` 或者具有 Kotlin `internal` / `private` 可见性：
   `internal`。
2. 声明或所在类标记 `@PixelExperimentalApi`：`experimental`。
3. 位于 `com.purride.pixelui.testing`：`testing`。
4. 文档明确列入诊断面，当前包括 `PixelDebugOverlay`、`PixelInspector*`、
   `PixelHostFrameStats` 及它们的诊断采样入口：`debug`。
5. 其余进入稳定 public API baseline 的公开声明：`stable`。

`public` 只表示语言可见性，不会自动把 API 升格为 `stable`。API dump 可以为审阅而
记录多个分类；声明“出现在 dump 中”同样不会改变它的分类。

## 2. 五类 API 的承诺

| 分类 | 面向场景 | 源码 / 二进制承诺 | 行为承诺 |
| --- | --- | --- | --- |
| `stable` | 生产代码和第三方组件 | `1.0.0` 起遵守 SemVer；破坏前按弃用周期保留旧入口 | 文档化的输入、输出、生命周期和错误语义受保护 |
| `experimental` | 需要尽早验证的低层能力 | 不保证跨 minor 源码或二进制兼容；不允许在 patch 版中无声破坏 | 只承诺当前文档与测试描述的行为，升级时必须复核 |
| `testing` | JVM / 离屏测试代码 | `1.0.0` 起，已入 baseline 的公开签名按稳定 API 流程变更 | 承诺可重复的 pump、输入和断言语义；不承诺内部树文本或生产环境性能 |
| `debug` | Inspector、帧统计和可视化诊断 | 已入 baseline 的公开签名按稳定 API 流程变更 | 字段含义受文档约束；精确计数、采样频率、文本排版和开销不是稳定业务契约 |
| `internal` | engine 实现 | 无兼容承诺，可在任意版本重命名、移动或删除 | 无对外行为承诺 |

兼容需要分开评估：

- **源码兼容**：消费者源码用新 SDK 重新编译仍然成功。
- **二进制兼容**：用旧 SDK 编译的 class，不重新编译就能在新 AAR 上加载和运行。
- **行为兼容**：文档化的结果、副作用、生命周期顺序与失败方式不变。

三者必须分别有证据。仅有 API dump 通过不能证明旧消费者二进制可运行，仅有
demo 能编译也不能证明行为兼容。

## 3. 高级 RenderObject SPI

### 3.1 当前稳定候选面

`com.purride.pixelui.advanced` 中的以下真实类型是稳定 SPI 候选：

- `PixelRenderObject`：定义 attach / detach 钩子和 protected 失效通知能力。
- `PixelRenderBox`：定义 `size`、`layout`和 `paint`。
- `PixelRenderObjectWidget`：定义 Widget 到 RenderObject 的 create / update 契约。
- `PixelLeafRenderObjectWidget`：为无 Widget 子节点的第三方组件提供入口。
- `PixelRenderSize` 与 `PixelRenderConstraints`：提供不包含 internal 类型的布局值对象。
- `PixelPaintContext`：提供稳定的像素写入操作；其 `bufferPool` 成员仍属实验能力。
- `PixelExperimentalApi`：实验 API 的编译期 opt-in 标记本身。

“稳定候选”表示源码已按目标边界设计，不是 M1 验收结论。它们只有在首个真实
SPI baseline 经审阅并通过本文第 6 节的证据检查后，才进入持续兼容保护。

### 3.2 当前实验面

以下声明已使用 `@PixelExperimentalApi` 表达其边界：

- `PixelRenderBox.hitTest` 和 `PixelHitTestResult`。
- `PixelPaintContext.bufferPool`。
- `PixelSingleChildRenderObjectWidget` 和 `PixelMultiChildRenderObjectWidget`。
- `PixelRenderObjectWithChild` 和 `PixelRenderObjectWithChildren`。
- `PixelSingleChildRenderObject` 和 `PixelMultiChildRenderObject`。

leaf 扩展是本阶段的最小稳定化目标。single-child / multi-child 还涉及子节点替换、
顺序、身份、detach 和命中路径等契约，在对应行为测试完整之前不作稳定承诺。

仅在使用实验声明的最小范围 opt in：

```kotlin
import com.purride.pixelui.advanced.PixelExperimentalApi
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderObjectWithChild

/**
 * 使用实验子节点协议替换一个直接子节点。
 *
 * @param parent 接收子节点的实验容器。
 * @param child 新的直接子节点，null 表示清空。
 */
@OptIn(PixelExperimentalApi::class)
private fun replaceExperimentalChild(
    parent: PixelRenderObjectWithChild,
    child: PixelRenderObject?,
) {
    parent.setRenderObjectChild(child)
}
```

不建议在整个消费者模块使用编译器全局 opt-in；否则新的实验依赖很难在评审中被发现。

### 3.3 实验 API 稳定化条件

移除某个声明的 `@PixelExperimentalApi` 之前，必须同时满足：

1. 输入、输出、所有权、生命周期、线程、失效传播和错误语义已写入 KDoc 与手册。
2. 有正向、边界、误用和更新路径的自动化行为测试。
3. 至少一个独立 consumer 只使用发布 AAR 就能编译、运行和通过测试。
4. 公开或 protected 签名、父类、泛型、注解和返回类型不包含
   `com.purride.*.internal`。
5. 源码 API、Java 可见 ABI、旧消费者二进制与行为门禁分别通过。
6. 文档、demo、migration note 和 changelog 不再使用已废弃的实验写法。

稳定化发布后，该声明立即按 `stable` 规则管理；“再加回实验标记”不能用来规避
SemVer。

### 3.4 实验 API 变更和移除策略

- 任何破坏性变更都必须进入 changelog，并在同一变更中提供 migration note。
- `0.x` 可在下一个明确标识的快照或 minor 重设实验 API，消费者必须重新编译。
- `1.x` 实验 API 可在 minor 版变更，但不应在 patch 版删除或破坏。
- 有可行替代方案时，先使用 `@Deprecated` 给出 `ReplaceWith`，至少经过一个已发布
  minor 后才移除。稳定 API 仍遵守更严格的两个 minor 窗口。
- 如果保留 API 会造成安全问题、数据损坏或必然崩溃，可以走紧急移除；必须在
  发布说明中记录原因、影响版本和替代方案。

## 4. `0.1.0-SNAPSHOT` typealias ABI 重置

旧高级入口是指向 `com.purride.pixelui.internal.*` 的 Kotlin `typealias`。`typealias` 不会在
JVM 中生成同名 class，所以消费者字节码实际记录的父类和方法类型是 internal 实现。
把 alias 替换为真实 `com.purride.pixelui.advanced.Pixel*` 类会改变 JVM 类名和父类描述符，
因此这是源码 / 二进制 ABI 的一次性重置，不能当作无影响的内部重构。

该重置发生在内部 `0.1.0-SNAPSHOT` 阶段：

- 使用重置前 snapshot 编译的消费者必须清理编译产物，并用重置后 AAR 重新编译。
- 当前版本暂时保留旧 internal JVM 类型作为尽力而为的过渡兼容壳，但它们不进入
  stable baseline，也不能成为新消费者的依赖；正式兼容证明从真实 SPI baseline 开始。
- 不得用“版本号字符串没变”宣称二进制兼容；`SNAPSHOT` 是可变产物。
- 验收 fixture 必须记录旧 AAR 的确切 SHA-256，避免同名 snapshot 覆盖证据。
- 首个经审阅的“真实 SPI”源码 / 二进制 baseline 是新兼容起点。从该起点起，
  `stable` 候选的变更必须由门禁发现并经明确审阅；对外长期 SemVer 承诺从
  `1.0.0` 正式发布开始。

baseline 更新 diff 应显示真实 `class` / `interface` 签名，而不再只有 `typealias`；在该
diff 和独立消费者证据被审阅前，不应宣布重置完成。

## 5. leaf RenderObject 最小完整示例

下面代码位于独立 consumer 模块，只需要发布 AAR，不访问 engine 源码或
`internal` 包。示例刻意只使用 leaf 稳定候选面。

```kotlin
package example.pixelplugin

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.BuildContext
import com.purride.pixelui.advanced.PixelLeafRenderObjectWidget
import com.purride.pixelui.advanced.PixelPaintContext
import com.purride.pixelui.advanced.PixelRenderBox
import com.purride.pixelui.advanced.PixelRenderConstraints
import com.purride.pixelui.advanced.PixelRenderObject
import com.purride.pixelui.advanced.PixelRenderSize

/**
 * 绘制一个可更新的单像素边框方形。
 *
 * @property side 方形目标边长，单位为逻辑像素。
 * @property color 边框颜色。
 * @param key retained tree 中用于匹配该 widget 的可选身份。
 */
public class OutlineSquareWidget(
    private val side: Int,
    private val color: PixelColor,
    key: Any? = null,
) : PixelLeafRenderObjectWidget(key = key) {
    /** 为首次 mount 创建与当前配置对应的 render object。 */
    override fun createRenderObject(context: BuildContext): PixelRenderObject {
        return OutlineSquareRenderObject(side = side, color = color)
    }

    /** 把重建后的边长和颜色同步到已保留的 render object。 */
    override fun updateRenderObject(
        context: BuildContext,
        renderObject: PixelRenderObject,
    ) {
        /** 当前 widget 在首次 mount 时创建的具体 render object。 */
        val square = renderObject as? OutlineSquareRenderObject
            ?: error("OutlineSquareWidget received an incompatible render object.")
        square.update(side = side, color = color)
    }
}

/**
 * 执行方形的布局、绘制和最小失效传播。
 *
 * @property side 当前目标边长。
 * @property color 当前边框颜色。
 */
private class OutlineSquareRenderObject(
    private var side: Int,
    private var color: PixelColor,
) : PixelRenderBox() {
    /** 将目标边长约束到父节点允许的宽高范围。 */
    override fun layout(constraints: PixelRenderConstraints) {
        size = PixelRenderSize(
            width = constraints.constrainWidth(side),
            height = constraints.constrainHeight(side),
        )
    }

    /** 在绝对逻辑坐标 [offsetX]、[offsetY] 处绘制单像素边框。 */
    override fun paint(
        context: PixelPaintContext,
        offsetX: Int,
        offsetY: Int,
    ) {
        if (size.width == 0 || size.height == 0) {
            return
        }
        context.drawRect(
            x = offsetX,
            y = offsetY,
            width = size.width,
            height = size.height,
            color = color,
        )
    }

    /** 更新绘制配置，并只标记必要的 pipeline 阶段。 */
    public fun update(side: Int, color: PixelColor) {
        /** 边长变化是否需要重新布局。 */
        val layoutChanged = this.side != side
        /** 颜色变化是否需要重新绘制。 */
        val paintChanged = this.color != color
        if (!layoutChanged && !paintChanged) {
            return
        }
        this.side = side
        this.color = color
        if (layoutChanged) {
            markNeedsLayout()
        } else {
            markNeedsPaint()
        }
    }
}
```

消费者测试可以只通过公开 `PixelTester` 验证布局和绘制：

```kotlin
package example.pixelplugin

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.testing.PixelTester
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证独立 leaf SPI 组件的离屏绘制契约。 */
public class OutlineSquareWidgetTest {
    /** 验证边界像素有色，方形内部保持透明。 */
    @Test
    public fun paintsOutlineWithoutFillingCenter() {
        /** 测试中可明确比较的边框颜色。 */
        val accent = PixelColor.fromRgb(r = 48, g = 220, b = 120)
        /** 不依赖 Android View 或 engine 内部类型的离屏测试宿主。 */
        val tester = PixelTester()

        tester.pumpWidget(
            widget = OutlineSquareWidget(side = 4, color = accent),
            logicalWidth = 4,
            logicalHeight = 4,
        )

        assertEquals(expected = accent, actual = tester.pixelAt(x = 0, y = 0))
        assertEquals(expected = PixelColor.Transparent, actual = tester.pixelAt(x = 1, y = 1))
        tester.dispose()
    }
}
```

完整验收还需要在独立 Gradle 消费者中实际编译和运行上述测试，并检查消费者
class 的常量池不包含 `/internal/`。把示例放入文档本身不等于这项验收已通过。

## 6. SPI 发布证据清单

从首个真实 SPI baseline 开始，每次发布至少要保留下列可复现证据：

- source API dump 已审阅，稳定签名不包含 internal 类型。
- release AAR 的 Java 可见 ABI 已用正式兼容工具比较。
- 独立 consumer 只依赖 Maven / AAR 产物，能够编译 leaf 自定义组件。
- consumer 的 layout、paint、update 和失效传播行为测试通过。
- 用上一个基线 AAR 编译的消费者 class，只换当前 AAR 仍能加载和运行。
- 实验 API 的新增、稳定化、弃用和删除已在 changelog 与 migration note 记录。

执行命令与证据路径以 [1.0 Goal](1.0-GOAL.md) 当前工作包为准，不在本文复制可能
尚未接入或会变化的门禁命令。
