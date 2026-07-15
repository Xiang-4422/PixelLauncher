package com.purride.pixelcomposesample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.purride.pixelcompose.PixelHost
import com.purride.pixelengine.PixelEngine
import com.purride.pixelui.Center
import com.purride.pixelui.PixelSemanticRole
import com.purride.pixelui.Semantics
import com.purride.pixelui.Text

/** 展示 Compose 页面单向托管完整 Pixel Host 的独立最小 sample。 */
class PixelComposeSampleActivity : ComponentActivity() {
    /** sample 生命周期内复用的独立 Engine。 */
    private val engine: PixelEngine = PixelEngine.Builder().build()

    /** 创建 Compose 根并把 Pixel retained Widget 树装入真实 Android Host。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelHost(
                engine = engine,
                modifier = Modifier.fillMaxSize(),
                stateKey = "compose-sample-host",
                content = {
                    Semantics(
                        label = "Pixel Compose sample",
                        role = PixelSemanticRole.TEXT,
                        child = Center(child = Text("COMPOSE HOST")),
                    )
                },
            )
        }
    }
}
