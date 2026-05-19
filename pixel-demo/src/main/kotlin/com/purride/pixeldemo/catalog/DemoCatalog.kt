package com.purride.pixeldemo.catalog

import com.purride.pixeldemo.showcase.foundation.AlignSizingScene
import com.purride.pixeldemo.showcase.foundation.ContainerDecorationScene
import com.purride.pixeldemo.showcase.foundation.HelloPixelScene
import com.purride.pixeldemo.showcase.foundation.LayoutPrimitivesScene
import com.purride.pixeldemo.showcase.foundation.RichTextScene
import com.purride.pixeldemo.showcase.foundation.TextMatrixScene
import com.purride.pixeldemo.showcase.interaction.ButtonStatesScene
import com.purride.pixeldemo.showcase.interaction.GestureTapScene
import com.purride.pixeldemo.showcase.interaction.ImeTypesScene
import com.purride.pixeldemo.showcase.interaction.TextFieldBasicsScene
import com.purride.pixeldemo.showcase.interaction.TextInputHostCommandsScene
import com.purride.pixeldemo.showcase.scroll.ListEagerScene
import com.purride.pixeldemo.showcase.scroll.ListSeparatedScene
import com.purride.pixeldemo.showcase.scroll.ListVariableHeightScene
import com.purride.pixeldemo.showcase.scroll.ListVirtualFixedScene
import com.purride.pixeldemo.showcase.scroll.PageControllerCommandsScene
import com.purride.pixeldemo.showcase.scroll.PagerHorizontalScene
import com.purride.pixeldemo.showcase.scroll.PagerVerticalScene
import com.purride.pixeldemo.showcase.scroll.ScrollControllerCommandsScene
import com.purride.pixeldemo.showcase.scroll.SingleChildScrollScene

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
        DemoSection(
            title = "Interaction",
            scenes = listOf(
                GestureTapScene,
                ButtonStatesScene,
                TextFieldBasicsScene,
                ImeTypesScene,
                TextInputHostCommandsScene,
            ),
        ),
        DemoSection(
            title = "Scroll",
            scenes = listOf(
                SingleChildScrollScene,
                ListEagerScene,
                ListVirtualFixedScene,
                ListVariableHeightScene,
                ListSeparatedScene,
                PagerHorizontalScene,
                PagerVerticalScene,
                ScrollControllerCommandsScene,
                PageControllerCommandsScene,
            ),
        ),
    )

    fun findById(id: String): DemoScene? =
        sections.flatMap { it.scenes }.find { it.id == id }
}
