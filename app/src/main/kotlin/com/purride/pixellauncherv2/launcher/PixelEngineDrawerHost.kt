package com.purride.pixellauncherv2.launcher

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import com.purride.pixellauncherv2.render.PixelShape
import com.purride.pixellauncherv2.render.ScreenProfile
import com.purride.pixelcore.PixelColor
import com.purride.pixelcore.PixelShape as EnginePixelShape
import com.purride.pixelui.Alignment
import com.purride.pixelui.ButtonStyle
import com.purride.pixelui.Center
import com.purride.pixelui.Column
import com.purride.pixelui.Container
import com.purride.pixelui.CrossAxisAlignment
import com.purride.pixelui.EdgeInsets
import com.purride.pixelui.Expanded
import com.purride.pixelui.ListViewBuilder
import com.purride.pixelui.OutlinedButton
import com.purride.pixelui.PixelHostProfilePreference
import com.purride.pixelui.PixelHostSetup
import com.purride.pixelui.PixelHostSetupConfig
import com.purride.pixelui.Row
import com.purride.pixelui.ScrollController
import com.purride.pixelui.SizedBox
import com.purride.pixelui.Text
import com.purride.pixelui.TextEditingController
import com.purride.pixelui.TextField
import com.purride.pixelui.TextInputAction
import com.purride.pixelui.TextOverflow
import com.purride.pixelui.TextStyle
import com.purride.pixelui.Widget
import com.purride.pixelui.createPixelHostSetup
import com.purride.pixelui.jumpToEnd
import com.purride.pixelui.jumpToStart
import com.purride.pixelui.showItem

/**
 * 首个由 pixel-engine 承接的 Launcher 页面宿主。
 *
 * 这层只负责 App Drawer 的 widget 组合和状态桥接；Home/SMS/Settings 仍保留
 * 旧渲染路径，避免把旧 renderer 作为 pixel-engine 的 fallback 接进去。
 */
internal class PixelEngineDrawerHost(
    context: Context,
    private val callbacks: Callbacks,
) {
    private val textController = TextEditingController()
    private val queryState = textController.create()
    private val listController = ScrollController()
    private val listState = listController.create()
    private var model = Model()
    private var lastSelectedIndex = -1

    val setup: PixelHostSetup = createPixelHostSetup(
        context = context,
        config = PixelHostSetupConfig(
            content = { buildDrawer() },
        ),
    )

    val rootView: FrameLayout
        get() = setup.rootView

    fun update(
        state: LauncherState,
        apps: List<AppEntry>,
        screenProfile: ScreenProfile,
        pixelGapEnabled: Boolean,
    ) {
        model = Model(
            active = state.mode == LauncherMode.APP_DRAWER,
            apps = apps,
            query = state.drawerQuery,
            searchFocused = state.isDrawerSearchFocused,
            selectedIndex = state.selectedIndex.coerceIn(0, apps.lastIndex.coerceAtLeast(0)),
            lowBattery = state.batteryLevel <= LOW_BATTERY_THRESHOLD && !state.isCharging,
        )
        rootView.visibility = if (model.active) View.VISIBLE else View.GONE
        setup.hostView.profilePreference = PixelHostProfilePreference(
            dotSizePx = screenProfile.dotSizePx,
            pixelShape = screenProfile.pixelShape.toEngineShape(),
        )
        setup.hostView.setPixelGapEnabled(pixelGapEnabled)
        syncQueryState()
        if (model.active && model.selectedIndex != lastSelectedIndex) {
            listController.showItem(listState, model.selectedIndex)
            lastSelectedIndex = model.selectedIndex
        }
        setup.hostView.invalidate()
    }

    private fun syncQueryState() {
        if (queryState.text != model.query) {
            textController.updateText(
                state = queryState,
                text = model.query,
                selectionStart = model.query.length,
            )
        }
        if (model.searchFocused) {
            textController.requestFocus(queryState)
        } else if (queryState.isFocused) {
            textController.requestBlur(queryState)
        }
    }

    private fun buildDrawer(): Widget {
        if (!model.active) {
            return Center(child = Text(""))
        }
        val apps = model.apps
        val selectedLabel = apps.getOrNull(model.selectedIndex)?.label ?: "NONE"
        return Container(
            fillColor = PixelColor.Transparent,
            borderColor = null,
            padding = EdgeInsets.all(4),
            child = Column(
                spacing = 3,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    sectionTitle(),
                    Row(
                        spacing = 2,
                        crossAxisAlignment = CrossAxisAlignment.STRETCH,
                        children = listOf(
                            Expanded(
                                child = infoCard(
                                    label = "RESULTS",
                                    value = apps.size.toString(),
                                    accent = apps.isEmpty(),
                                ),
                            ),
                            Expanded(
                                child = infoCard(
                                    label = "SELECTED",
                                    value = selectedLabel.uppercase(),
                                    accent = apps.isNotEmpty(),
                                ),
                            ),
                        ),
                    ),
                    SizedBox(
                        height = 16,
                        child = TextField(
                            state = queryState,
                            controller = textController,
                            placeholder = "SEARCH APP",
                            autofocus = model.searchFocused,
                            textInputAction = TextInputAction.SEARCH,
                            onChanged = callbacks.onQueryChanged,
                            onSubmitted = { callbacks.onSubmitSearch() },
                        ),
                    ),
                    drawerActions(apps),
                    alphaIndex(apps),
                    SizedBox(
                        height = 54,
                        child = ListViewBuilder(
                            itemCount = apps.size.coerceAtLeast(1),
                            state = listState,
                            controller = listController,
                            itemExtent = ROW_HEIGHT,
                            cacheExtent = 2,
                            spacing = 2,
                            itemBuilder = { index ->
                                val app = apps.getOrNull(index)
                                val isSelected = index == model.selectedIndex && app != null
                                SizedBox(
                                    height = ROW_HEIGHT,
                                    child = OutlinedButton(
                                        text = app?.label?.uppercase() ?: "NO RESULTS",
                                        onPressed = app?.let {
                                            { callbacks.onAppPressed(index) }
                                        },
                                        borderColor = if (isSelected) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                                        enabled = app != null,
                                    ),
                                )
                            },
                        ),
                    ),
                ),
            ),
        )
    }

    private fun drawerActions(apps: List<AppEntry>): Widget {
        return SizedBox(
            height = 14,
            child = Row(
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    Expanded(
                        child = OutlinedButton(
                            text = "TOP",
                            onPressed = { listController.jumpToStart(listState) },
                        ),
                    ),
                    Expanded(
                        child = OutlinedButton(
                            text = "MID",
                            onPressed = {
                                val index = (apps.size / 2).coerceAtLeast(0)
                                if (apps.isNotEmpty()) {
                                    listController.showItem(listState, index)
                                    callbacks.onShowIndex(index)
                                }
                            },
                            borderColor = PixelColor.fromRgb(200, 100, 0),
                        ),
                    ),
                    Expanded(
                        child = OutlinedButton(
                            text = "END",
                            onPressed = { listController.jumpToEnd(listState) },
                        ),
                    ),
                ),
            ),
        )
    }

    private fun alphaIndex(apps: List<AppEntry>): Widget {
        val selectedLetter = apps.getOrNull(model.selectedIndex)?.label?.firstOrNull()?.uppercaseChar()
        return SizedBox(
            height = 14,
            child = Row(
                spacing = 2,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = INDEX_LETTERS.map { letter ->
                    val isActive = selectedLetter?.let { letter.firstOrNull() == it } == true
                    Expanded(
                        child = OutlinedButton(
                            text = letter,
                            onPressed = {
                                val index = apps.indexOfFirst { app ->
                                    app.label.startsWith(letter, ignoreCase = true) ||
                                        app.englishLabel.startsWith(letter, ignoreCase = true)
                                }
                                if (index >= 0) {
                                    listController.showItem(listState, index)
                                    callbacks.onShowIndex(index)
                                }
                            },
                            borderColor = if (isActive) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
                        ),
                    )
                },
            ),
        )
    }

    private fun sectionTitle(): Widget {
        return Container(
            height = 18,
            fillColor = PixelColor.Transparent,
            borderColor = PixelColor.fromRgb(200, 100, 0),
            padding = EdgeInsets.all(3),
            child = Center(
                child = Text(
                    data = "APP DRAWER",
                    style = TextStyle(color = PixelColor.fromRgb(200, 100, 0)),
                    overflow = TextOverflow.ELLIPSIS,
                ),
            ),
        )
    }

    private fun infoCard(
        label: String,
        value: String,
        accent: Boolean,
    ): Widget {
        return Container(
            height = 20,
            fillColor = PixelColor.Transparent,
            borderColor = if (accent) PixelColor.fromRgb(200, 100, 0) else PixelColor.White,
            padding = EdgeInsets.all(2),
            alignment = Alignment.TOP_START,
            child = Column(
                spacing = 1,
                crossAxisAlignment = CrossAxisAlignment.STRETCH,
                children = listOf(
                    Text(
                        data = label,
                        style = TextStyle(color = PixelColor.fromRgb(200, 100, 0)),
                        overflow = TextOverflow.ELLIPSIS,
                    ),
                    Text(
                        data = value,
                        overflow = TextOverflow.ELLIPSIS,
                    ),
                ),
            ),
        )
    }

    data class Callbacks(
        val onQueryChanged: (String) -> Unit,
        val onSubmitSearch: () -> Unit,
        val onAppPressed: (Int) -> Unit,
        val onShowIndex: (Int) -> Unit,
    )

    private data class Model(
        val active: Boolean = false,
        val apps: List<AppEntry> = emptyList(),
        val query: String = "",
        val searchFocused: Boolean = false,
        val selectedIndex: Int = 0,
        val lowBattery: Boolean = false,
    )

    companion object {
        private const val ROW_HEIGHT = 13
        private const val LOW_BATTERY_THRESHOLD = 15
        private val INDEX_LETTERS = listOf("A", "M", "Z")

        private fun PixelShape.toEngineShape(): EnginePixelShape {
            return EnginePixelShape.valueOf(name)
        }
    }
}
