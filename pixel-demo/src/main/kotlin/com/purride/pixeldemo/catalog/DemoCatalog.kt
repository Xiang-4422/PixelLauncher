package com.purride.pixeldemo.catalog

import com.purride.pixeldemo.showcase.composition.AppScaffoldScene
import com.purride.pixeldemo.showcase.composition.AsyncBuilderScene
import com.purride.pixeldemo.showcase.composition.MasterDetailScene
import com.purride.pixeldemo.showcase.composition.ModalOverlayScene
import com.purride.pixeldemo.showcase.composition.NavigatorStackScene
import com.purride.pixeldemo.showcase.composition.NestedPagerInListScene
import com.purride.pixeldemo.showcase.composition.OverlayFeedbackScene
import com.purride.pixeldemo.showcase.composition.StickyBottomBarScene
import com.purride.pixeldemo.showcase.extension.CustomPagerPolicyScene
import com.purride.pixeldemo.showcase.extension.CustomRasterizerScene
import com.purride.pixeldemo.showcase.extension.CustomRenderObjectScene
import com.purride.pixeldemo.showcase.extension.CustomScrollPhysicsScene
import com.purride.pixeldemo.showcase.extension.ManualFrameStepperScene
import com.purride.pixeldemo.showcase.extension.NestedScrollPolicyScene
import com.purride.pixeldemo.showcase.stress.StressAnimationFloodScene
import com.purride.pixeldemo.showcase.templates.TplCalculatorScene
import com.purride.pixeldemo.showcase.templates.TplChatScene
import com.purride.pixeldemo.showcase.templates.TplFileBrowserScene
import com.purride.pixeldemo.showcase.templates.TplPlayerHudScene
import com.purride.pixeldemo.showcase.templates.TplSettingsScene
import com.purride.pixeldemo.showcase.stress.StressDeepTreeScene
import com.purride.pixeldemo.showcase.stress.StressGestureStormScene
import com.purride.pixeldemo.showcase.stress.StressListScaleScene
import com.purride.pixeldemo.showcase.stress.StressRebuildStormScene
import com.purride.pixeldemo.showcase.stress.StressTextHeavyScene
import com.purride.pixeldemo.showcase.foundation.AlignSizingScene
import com.purride.pixeldemo.showcase.foundation.ContainerDecorationScene
import com.purride.pixeldemo.showcase.foundation.CustomPaintScene
import com.purride.pixeldemo.showcase.foundation.DirectionalVariantsScene
import com.purride.pixeldemo.showcase.foundation.HelloPixelScene
import com.purride.pixeldemo.showcase.foundation.ImageBlitScene
import com.purride.pixeldemo.showcase.foundation.LayoutPrimitivesScene
import com.purride.pixeldemo.showcase.foundation.PolygonPathScene
import com.purride.pixeldemo.showcase.foundation.RichTextScene
import com.purride.pixeldemo.showcase.foundation.SafeAreaScene
import com.purride.pixeldemo.showcase.foundation.ShapePrimitivesScene
import com.purride.pixeldemo.showcase.foundation.StackPositionedScene
import com.purride.pixeldemo.showcase.foundation.TextMatrixScene
import com.purride.pixeldemo.showcase.interaction.ButtonStatesScene
import com.purride.pixeldemo.showcase.interaction.FocusKeyScene
import com.purride.pixeldemo.showcase.interaction.GestureTapScene
import com.purride.pixeldemo.showcase.interaction.ImeTypesScene
import com.purride.pixeldemo.showcase.interaction.SelectionControlsScene
import com.purride.pixeldemo.showcase.interaction.TabsProgressScene
import com.purride.pixeldemo.showcase.interaction.TextFieldBasicsScene
import com.purride.pixeldemo.showcase.interaction.TextInputHostCommandsScene
import com.purride.pixeldemo.showcase.scroll.GridViewScene
import com.purride.pixeldemo.showcase.scroll.ListEagerScene
import com.purride.pixeldemo.showcase.scroll.ListSeparatedScene
import com.purride.pixeldemo.showcase.scroll.ListVariableHeightScene
import com.purride.pixeldemo.showcase.scroll.ListVirtualFixedScene
import com.purride.pixeldemo.showcase.scroll.PageControllerCommandsScene
import com.purride.pixeldemo.showcase.scroll.PagerHorizontalScene
import com.purride.pixeldemo.showcase.scroll.PagerVerticalScene
import com.purride.pixeldemo.showcase.scroll.PullRefreshScene
import com.purride.pixeldemo.showcase.scroll.ScrollControllerCommandsScene
import com.purride.pixeldemo.showcase.scroll.SingleChildScrollScene
import com.purride.pixeldemo.showcase.integration.ConfigChangePreserveScene
import com.purride.pixeldemo.showcase.integration.DebugOverlayScene
import com.purride.pixeldemo.showcase.integration.EmptyLoadingErrorScene
import com.purride.pixeldemo.showcase.integration.HapticFeedbackScene
import com.purride.pixeldemo.showcase.integration.HostHotSwapScene
import com.purride.pixeldemo.showcase.integration.SystemActionDispatchScene
import com.purride.pixeldemo.showcase.animation.AnimationCoreScene
import com.purride.pixeldemo.showcase.animation.AnimationLayer3Scene
import com.purride.pixeldemo.showcase.animation.AnimatedSpriteScene
import com.purride.pixeldemo.showcase.animation.ImplicitAnimationsScene
import com.purride.pixeldemo.header.HeaderPreviewScene
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
                StackPositionedScene,
                DirectionalVariantsScene,
                SafeAreaScene,
                TextMatrixScene,
                RichTextScene,
                ContainerDecorationScene,
                ImageBlitScene,
                ShapePrimitivesScene,
                PolygonPathScene,
                CustomPaintScene,
            ),
        ),
        DemoSection(
            title = "Interaction",
            scenes = listOf(
                GestureTapScene,
                ButtonStatesScene,
                SelectionControlsScene,
                TabsProgressScene,
                FocusKeyScene,
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
                GridViewScene,
                ListSeparatedScene,
                PullRefreshScene,
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
            title = "Composition",
            scenes = listOf(
                NestedPagerInListScene,
                StickyBottomBarScene,
                AppScaffoldScene,
                MasterDetailScene,
                ModalOverlayScene,
                OverlayFeedbackScene,
                AsyncBuilderScene,
                NavigatorStackScene,
            ),
        ),
        DemoSection(
            title = "Extension",
            scenes = listOf(
                CustomRenderObjectScene,
                CustomPagerPolicyScene,
                CustomRasterizerScene,
                ManualFrameStepperScene,
                CustomScrollPhysicsScene,
                NestedScrollPolicyScene,
            ),
        ),
        DemoSection(
            title = "Templates",
            scenes = listOf(
                TplSettingsScene,
                TplCalculatorScene,
                TplFileBrowserScene,
                TplChatScene,
                TplPlayerHudScene,
            ),
        ),
        DemoSection(
            title = "Stress",
            scenes = listOf(
                StressListScaleScene,
                StressRebuildStormScene,
                StressDeepTreeScene,
                StressAnimationFloodScene,
                StressGestureStormScene,
                StressTextHeavyScene,
            ),
        ),
        DemoSection(
            title = "Integration",
            scenes = listOf(
                HostHotSwapScene,
                ConfigChangePreserveScene,
                EmptyLoadingErrorScene,
                HapticFeedbackScene,
                SystemActionDispatchScene,
                DebugOverlayScene,
            ),
        ),
        DemoSection(
            title = "Animation",
            scenes = listOf(
                AnimationCoreScene,
                ImplicitAnimationsScene,
                AnimationLayer3Scene,
                AnimatedSpriteScene,
            ),
        ),
        DemoSection(
            title = "Launcher UI",
            scenes = listOf(
                HeaderPreviewScene,
            ),
        ),
    )

    fun findById(id: String): DemoScene? =
        sections.flatMap { it.scenes }.find { it.id == id }
}
