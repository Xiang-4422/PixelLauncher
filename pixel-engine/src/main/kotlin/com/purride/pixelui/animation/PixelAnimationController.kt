package com.purride.pixelui.animation

import com.purride.pixelui.ChangeNotifier
import com.purride.pixelui.Listenable
import com.purride.pixelui.VoidCallback
import kotlin.time.Duration

public interface Animation<T> : Listenable {
    public val value: T
    public val status: PixelAnimationStatus
}

public class PixelAnimationController(
    public val duration: Duration,
    vsync: PixelTickerProvider,
    initialValue: Float = 0f,
) : ChangeNotifier(), Animation<Float> {

    private var _value: Float = initialValue.coerceIn(0f, 1f)
    private var _status: PixelAnimationStatus = PixelAnimationStatus.Dismissed
    private var repeatMode: Boolean? = null  // null=no repeat, false=forward, true=pingpong

    override val value: Float get() = _value
    override val status: PixelAnimationStatus get() = _status

    public val isAnimating: Boolean
        get() = _status == PixelAnimationStatus.Forward || _status == PixelAnimationStatus.Reverse

    private val ticker: PixelTicker = vsync.createTicker { elapsedNanos ->
        onTick(elapsedNanos)
    }

    private fun onTick(elapsedNanos: Long) {
        val t = (elapsedNanos.toDouble() / duration.inWholeNanoseconds.toDouble()).toFloat()
        when (_status) {
            PixelAnimationStatus.Forward -> {
                _value = t.coerceIn(0f, 1f)
                notifyListeners()
                if (t >= 1f) handleCompleted()
            }
            PixelAnimationStatus.Reverse -> {
                _value = (1f - t).coerceIn(0f, 1f)
                notifyListeners()
                if (t >= 1f) handleDismissed()
            }
            else -> Unit
        }
    }

    private fun handleCompleted() {
        ticker.stop()
        when (repeatMode) {
            false -> {
                ticker.start()
                _status = PixelAnimationStatus.Forward
                _value = 0f
                notifyListeners()
            }
            true -> {
                ticker.start()
                _status = PixelAnimationStatus.Reverse
                notifyListeners()
            }
            null -> {
                _status = PixelAnimationStatus.Completed
                _value = 1f
                notifyListeners()
            }
        }
    }

    private fun handleDismissed() {
        ticker.stop()
        if (repeatMode == true) {
            ticker.start()
            _status = PixelAnimationStatus.Forward
            _value = 0f
            notifyListeners()
        } else {
            _status = PixelAnimationStatus.Dismissed
            _value = 0f
            notifyListeners()
        }
    }

    public fun forward(from: Float? = null) {
        repeatMode = null
        if (from != null) _value = from.coerceIn(0f, 1f)
        ticker.stop()
        ticker.start()
        _status = PixelAnimationStatus.Forward
        notifyListeners()
    }

    public fun reverse(from: Float? = null) {
        repeatMode = null
        if (from != null) _value = from.coerceIn(0f, 1f)
        ticker.stop()
        ticker.start()
        _status = PixelAnimationStatus.Reverse
        notifyListeners()
    }

    public fun repeat(reverse: Boolean = false) {
        repeatMode = if (reverse) true else false
        ticker.stop()
        ticker.start()
        _status = PixelAnimationStatus.Forward
        _value = 0f
        notifyListeners()
    }

    public fun stop() {
        ticker.stop()
    }

    public fun reset() {
        ticker.stop()
        repeatMode = null
        _value = 0f
        _status = PixelAnimationStatus.Dismissed
        notifyListeners()
    }

    public fun setValue(v: Float) {
        ticker.stop()
        repeatMode = null
        _value = v.coerceIn(0f, 1f)
        _status = if (_value >= 1f) PixelAnimationStatus.Completed else PixelAnimationStatus.Dismissed
        notifyListeners()
    }

    public fun dispose() {
        ticker.stop()
        ticker.dispose()
    }
}
