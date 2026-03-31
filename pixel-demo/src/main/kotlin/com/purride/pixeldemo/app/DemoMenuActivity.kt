package com.purride.pixeldemo.app

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

/**
 * Demo 菜单页。
 *
 * 这里刻意使用原生 Android 控件做导航，只负责打开不同的框架验证页面。
 * 这样 demo 的复杂度不会反过来污染 `pixel-ui` 的首轮设计。
 */
class DemoMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Pixel Demo"

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        DemoSceneKind.entries.forEach { sceneKind ->
            container.addView(
                Button(this).apply {
                    text = sceneKind.menuLabel
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        bottomMargin = 24
                    }
                    setOnClickListener {
                        startActivity(
                            Intent(this@DemoMenuActivity, DemoSceneActivity::class.java)
                                .putExtra(DemoSceneActivity.EXTRA_SCENE_KIND, sceneKind.name),
                        )
                    }
                },
            )
        }

        setContentView(
            ScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                addView(
                    container,
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
                )
            },
        )
    }
}
