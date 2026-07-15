package com.purride.pixelui.host

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.RequiresApi
import com.purride.pixelui.PixelMotionSettings

/** Observes Android's animator duration preference behind an injectable internal interface. */
internal class AndroidPixelMotionSettingsSource(
    context: Context,
) : PixelMotionSettingsSource {
    /** Application resolver used to avoid retaining an Activity or View context. */
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    /** Main-thread handler required by the legacy Settings observer. */
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    /** API-specific observer hidden behind a version-neutral registration interface. */
    private val platformRegistration: PlatformMotionRegistration = createPlatformRegistration()

    /** Consumer retained only for the current attached interval. */
    private var callback: ((PixelMotionSettings) -> Unit)? = null

    /** Latest distinct snapshot emitted or read from Android. */
    private var cachedSettings: PixelMotionSettings = readPlatformSettings()

    /** Whether the platform registration is currently active. */
    private var attached: Boolean = false

    /** Whether terminal source destruction has completed. */
    private var destroyed: Boolean = false

    override val currentSettings: PixelMotionSettings
        get() {
            if (!destroyed) cachedSettings = readPlatformSettings()
            return cachedSettings
        }

    /** Registers exactly one API-appropriate listener and synchronizes the latest value. */
    override fun attach(onChanged: (PixelMotionSettings) -> Unit) {
        if (destroyed) return
        callback = onChanged
        val latest = readPlatformSettings()
        cachedSettings = latest
        if (!attached) {
            attached = true
            platformRegistration.register(::handlePlatformSignal)
        }
        onChanged(latest)
    }

    /** Removes the active listener and releases the attached callback. */
    override fun detach() {
        if (!attached) {
            callback = null
            return
        }
        attached = false
        platformRegistration.unregister()
        callback = null
    }

    /** Permanently unregisters this source exactly once. */
    override fun destroy() {
        if (destroyed) return
        detach()
        destroyed = true
    }

    /** Re-reads both duration scale and reduce-motion state after a platform signal. */
    private fun handlePlatformSignal() {
        if (!attached || destroyed) return
        val latest = readPlatformSettings()
        if (latest == cachedSettings) return
        cachedSettings = latest
        callback?.invoke(latest)
    }

    /**
     * Creates the version-specific observer after an explicit runtime SDK guard.
     *
     * Lint cannot propagate the guard through the private version-neutral interface, so the
     * suppression is deliberately limited to this guarded factory.
     */
    @SuppressLint("NewApi")
    private fun createPlatformRegistration(): PlatformMotionRegistration {
        return if (Build.VERSION.SDK_INT >= 33) {
            Api33AnimatorScaleRegistration()
        } else {
            LegacyAnimatorScaleRegistration(contentResolver, mainHandler)
        }
    }

    /**
     * Reads settings using only APIs available on the running Android version.
     *
     * Each newer call is isolated behind its matching SDK check; the narrow suppression handles
     * Lint's inability to infer that guarantee through the private reader objects.
     */
    @SuppressLint("NewApi")
    private fun readPlatformSettings(): PixelMotionSettings {
        val durationScale = when {
            Build.VERSION.SDK_INT >= 33 -> Api33AnimatorSettingsReader.durationScale()
            else -> Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                DEFAULT_DURATION_SCALE,
            )
        }.normalizedDurationScale()
        val animatorsEnabled = when {
            Build.VERSION.SDK_INT >= 26 -> Api26AnimatorSettingsReader.areAnimatorsEnabled()
            else -> durationScale > 0f
        }
        return PixelMotionSettings(
            animatorDurationScale = durationScale,
            reduceMotion = !animatorsEnabled,
        )
    }

    private companion object {
        /** Android's documented default animator duration multiplier. */
        const val DEFAULT_DURATION_SCALE: Float = 1f
    }
}

/** Version-neutral platform registration used by [AndroidPixelMotionSettingsSource]. */
private interface PlatformMotionRegistration {
    /** Starts forwarding platform changes to [onChanged]. */
    fun register(onChanged: () -> Unit)

    /** Stops forwarding changes; repeated calls are safe. */
    fun unregister()
}

/** API 24-32 registration backed by Settings.Global's animator scale URI. */
private class LegacyAnimatorScaleRegistration(
    /** Resolver that owns this observer registration. */
    private val contentResolver: ContentResolver,
    /** Handler defining observer callback affinity. */
    handler: Handler,
) : PlatformMotionRegistration {
    /** Current callback, cleared before observer unregistration. */
    private var callback: (() -> Unit)? = null

    /** Single reusable observer instance, avoiding one allocation per attach. */
    private val observer: ContentObserver = object : ContentObserver(handler) {
        /** Forwards any animator-scale URI mutation to the source. */
        override fun onChange(selfChange: Boolean) {
            callback?.invoke()
        }
    }

    /** Registers once for descendants=false because only one global key is relevant. */
    override fun register(onChanged: () -> Unit) {
        if (callback != null) return
        callback = onChanged
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
    }

    /** Unregisters exactly one active observer. */
    override fun unregister() {
        if (callback == null) return
        callback = null
        contentResolver.unregisterContentObserver(observer)
    }
}

/** API 33 duration listener isolated so older runtimes never resolve its nested listener type. */
@RequiresApi(33)
private class Api33AnimatorScaleRegistration : PlatformMotionRegistration {
    /** Version-specific listener retained only while registration succeeds. */
    private var listener: ValueAnimator.DurationScaleChangeListener? = null

    /** Registers Android's process-wide duration scale listener once. */
    override fun register(onChanged: () -> Unit) {
        if (listener != null) return
        val candidate = ValueAnimator.DurationScaleChangeListener { onChanged() }
        if (ValueAnimator.registerDurationScaleChangeListener(candidate)) {
            listener = candidate
        }
    }

    /** Unregisters the exact listener instance accepted by Android. */
    override fun unregister() {
        val current = listener ?: return
        listener = null
        ValueAnimator.unregisterDurationScaleChangeListener(current)
    }
}

/** API 26 animator-enabled query isolated from minSdk 24 class verification. */
@RequiresApi(26)
private object Api26AnimatorSettingsReader {
    /** Returns whether framework animators are currently enabled. */
    fun areAnimatorsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
}

/** API 33 scale getter isolated from minSdk 24 class verification. */
@RequiresApi(33)
private object Api33AnimatorSettingsReader {
    /** Returns Android's current process-wide animator duration scale. */
    fun durationScale(): Float = ValueAnimator.getDurationScale()
}

/** Converts invalid OEM settings values into Android's default without allowing NaN downstream. */
private fun Float.normalizedDurationScale(): Float {
    return if (isNaN() || this < 0f) 1f else this
}
