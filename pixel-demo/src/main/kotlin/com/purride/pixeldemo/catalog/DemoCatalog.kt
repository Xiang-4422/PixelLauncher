package com.purride.pixeldemo.catalog

import com.purride.pixeldemo.showcase.foundation.AlignSizingScene
import com.purride.pixeldemo.showcase.foundation.ContainerDecorationScene
import com.purride.pixeldemo.showcase.foundation.HelloPixelScene
import com.purride.pixeldemo.showcase.foundation.LayoutPrimitivesScene
import com.purride.pixeldemo.showcase.foundation.RichTextScene
import com.purride.pixeldemo.showcase.foundation.TextMatrixScene

object DemoCatalog {
    val sections: List<DemoSection> = listOf(
        DemoSection(
            title = "Foundation",
            scenes = listOf(
                HelloPixelScene,
                LayoutPrimitivesScene,
                AlignSizingScene,
                TextMatrixScene,
                RichTextScene,
                ContainerDecorationScene,
            ),
        ),
    )

    fun findById(id: String): DemoScene? =
        sections.flatMap { it.scenes }.find { it.id == id }
}
