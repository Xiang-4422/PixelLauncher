package com.purride.pixeldemo.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import com.purride.pixelui.PixelHapticType
import com.purride.pixelui.PixelHostBridge
import com.purride.pixelui.PixelHostView
import com.purride.pixelui.PixelSystemAction
import com.purride.pixelui.PixelTextInputRequest

/**
 * 单个 demo scene 的宿主 Activity。
 */
class DemoSceneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sceneKind = DemoSceneKind.valueOf(
            intent.getStringExtra(EXTRA_SCENE_KIND) ?: DemoSceneKind.TEXT.name,
        )
        title = sceneKind.menuLabel

        val hostView = PixelHostView(this)
        val hiddenInput = AppCompatEditText(this).apply {
            alpha = 0f
            isFocusable = true
            isFocusableInTouchMode = true
            setSingleLine()
        }
        var syncingFromHost = false
        hiddenInput.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(s: Editable?) {
                    if (syncingFromHost) {
                        return
                    }
                    val text = s?.toString().orEmpty()
                    hostView.updateFocusedTextInput(
                        text = text,
                        selectionStart = hiddenInput.selectionStart.coerceAtLeast(0),
                        selectionEnd = hiddenInput.selectionEnd.coerceAtLeast(0),
                    )
                }
            },
        )
        hostView.hostBridge = object : PixelHostBridge {
            override fun showTextInput(request: PixelTextInputRequest) {
                syncingFromHost = true
                try {
                    if (hiddenInput.text?.toString() != request.text) {
                        hiddenInput.setText(request.text)
                    }
                    val textLength = hiddenInput.text?.length ?: 0
                    val safeSelectionStart = request.selectionStart.coerceIn(0, textLength)
                    val safeSelectionEnd = request.selectionEnd.coerceIn(safeSelectionStart, textLength)
                    hiddenInput.setSelection(safeSelectionStart, safeSelectionEnd)
                } finally {
                    syncingFromHost = false
                }
                hiddenInput.requestFocus()
                val imm = getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(hiddenInput, InputMethodManager.SHOW_IMPLICIT)
            }

            override fun hideTextInput() {
                val imm = getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(hiddenInput.windowToken, 0)
                hiddenInput.clearFocus()
            }

            override fun performHapticFeedback(type: PixelHapticType) {
                hostView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }

            override fun requestFrame() {
                hostView.requestRender()
            }

            override fun dispatchSystemAction(action: PixelSystemAction) = Unit
        }
        val textRasterizers = DemoTextRasterizers(this)
        val scene = DemoScenes.create(sceneKind, hostView, textRasterizers)
        hostView.screenProfile = scene.initialProfile
        hostView.setPalette(scene.initialPalette)
        hostView.textRasterizer = scene.initialTextRasterizer
        hostView.setContent(scene.content)
        setContentView(
            FrameLayout(this).apply {
                addView(
                    hostView,
                    FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
                )
                addView(
                    hiddenInput,
                    FrameLayout.LayoutParams(1, WRAP_CONTENT),
                )
            },
        )
    }

    companion object {
        const val EXTRA_SCENE_KIND = "scene_kind"
    }
}
