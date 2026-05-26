package com.purride.pixelminimal

import android.app.Activity
import android.os.Bundle
import com.purride.pixelcore.PixelColor
import com.purride.pixelui.Center
import com.purride.pixelui.Container
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Text
import com.purride.pixelui.createPixelHostSetup

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setup = createPixelHostSetup(
            context = this,
            config = PixelHostSetupConfig(
                content = {
                    Center(
                        child = Container(
                            padding = EdgeInsets.all(3),
                            borderColor = PixelColor.White,
                            child = Text("PIXEL"),
                        ),
                    )
                },
            ),
        )
        setContentView(setup.rootView)
    }
}
