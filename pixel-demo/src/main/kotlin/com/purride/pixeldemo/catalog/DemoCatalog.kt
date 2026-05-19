package com.purride.pixeldemo.catalog

import com.purride.pixeldemo.showcase.extension.CustomPagerPolicyScene
import com.purride.pixeldemo.showcase.extension.CustomRasterizerScene
import com.purride.pixeldemo.showcase.extension.CustomRenderObjectScene
import com.purride.pixeldemo.showcase.extension.PerfOverlayScene
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
import com.purride.pixeldemo.showcase.theme.PaletteToggleScene
import com.purride.pixeldemo.showcase.theme.RtlMirrorScene
import com.purride.pixeldemo.showcase.theme.ThemeStateMatrixScene
import com.purride.pixeldemo.showcase.theme.ThemeTokensScene

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
        DemoSection(
            title = "Theme",
            scenes = listOf(
                PaletteToggleScene,
                ThemeTokensScene,
                ThemeStateMatrixScene,
                RtlMirrorScene,
            ),
        ),
        DemoSection(
            title = "Extension",
            scenes = listOf(
                CustomRenderObjectScene,
                CustomPagerPolicyScene,
                CustomRasterizerScene,
                PerfOverlayScene,
            ),
        ),
    )

    fun findById(id: String): DemoScene? =
        sections.flatMap { it.scenes }.find { it.id == id }
}
