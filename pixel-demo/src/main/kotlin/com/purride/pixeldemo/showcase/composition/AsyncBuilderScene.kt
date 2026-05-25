package com.purride.pixeldemo.showcase.composition

import com.purride.pixelcore.PixelColor
import com.purride.pixelui.AsyncBuilder
import com.purride.pixelui.BuildContext
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.MainAxisAlignment
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelAsyncSnapshot
import com.purride.pixelui.PixelAsyncSource
import com.purride.pixelui.Row
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixeldemo.catalog.DemoScene
import com.purride.pixeldemo.scaffold.DemoEnv

/**
 * 演示 [AsyncBuilder]：订阅一个手动可控的 [PixelAsyncSource]，
 * 通过按钮在 Loading / Success / Failure 三种 snapshot 之间切换。
 *
 * 真实使用中，source 通常包装 Coroutines `Flow` 或 java.util.concurrent.Future；
 * 本场景用一个简单的 listener-broadcast source 演示生命周期，不依赖 kotlinx.coroutines。
 */
object AsyncBuilderScene : DemoScene {
    override val id = "async_builder"
    override val title = "ASYNC BUILDER"
    override val description = "AsyncBuilder 订阅可控数据源切换 snapshot"

    override fun build(env: DemoEnv): Widget {
        val source = ManualSource
        val labelStyle = TextStyle(color = PixelColor.fromRgb(180, 180, 180))
        val valueStyle = TextStyle(color = PixelColor.fromRgb(0xFF, 0xC0, 0x40))
        return Center(
            child = Column(
                mainAxisAlignment = MainAxisAlignment.CENTER,
                crossAxisAlignment = CrossAxisAlignment.CENTER,
                children = listOf(
                    Text("SNAPSHOT", style = labelStyle),
                    SizedBox(height = 2),
                    AsyncBuilder(source = source) { _: BuildContext, snapshot ->
                        when (snapshot) {
                            is PixelAsyncSnapshot.Loading -> Text("LOADING…", style = valueStyle)
                            is PixelAsyncSnapshot.Success -> Text(snapshot.value, style = valueStyle)
                            is PixelAsyncSnapshot.Failure -> Text(
                                "ERR: ${snapshot.error.message}",
                                style = valueStyle,
                            )
                        }
                    },
                    SizedBox(height = 6),
                    Row(
                        mainAxisAlignment = MainAxisAlignment.CENTER,
                        children = listOf(
                            OutlinedButton(text = "LOAD", onPressed = { source.loading() }),
                            SizedBox(width = 2),
                            OutlinedButton(text = "OK", onPressed = { source.success() }),
                            SizedBox(width = 2),
                            OutlinedButton(text = "ERR", onPressed = { source.failure() }),
                        ),
                    ),
                ),
            ),
        )
    }
}

/**
 * 手动控制 snapshot 的 source。在 demo 中按按钮即触发 emit，
 * 真实使用中通常包装异步框架（Flow / Future）。
 */
private object ManualSource : PixelAsyncSource<String> {
    private val listeners = mutableSetOf<(PixelAsyncSnapshot<String>) -> Unit>()
    private var counter = 0

    override fun subscribe(listener: (PixelAsyncSnapshot<String>) -> Unit): () -> Unit {
        listeners += listener
        listener(PixelAsyncSnapshot.Loading)
        return { listeners -= listener }
    }

    fun loading() = emit(PixelAsyncSnapshot.Loading)

    fun success() {
        counter += 1
        emit(PixelAsyncSnapshot.Success("OK #$counter"))
    }

    fun failure() {
        emit(PixelAsyncSnapshot.Failure(IllegalStateException("simulated")))
    }

    private fun emit(snapshot: PixelAsyncSnapshot<String>) {
        // toList 避免 emit 期间 listener 修改集合
        listeners.toList().forEach { it.invoke(snapshot) }
    }
}
