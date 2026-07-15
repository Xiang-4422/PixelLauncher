package com.purride.pixelui.host

import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityManager
import androidx.annotation.RequiresApi
import com.purride.pixelui.HostCapabilitiesData
import com.purride.pixelui.PixelLocale
import com.purride.pixelui.TextDirection
import java.util.concurrent.Executor

/**
 * Android-backed source for configuration, contrast, density and display refresh capabilities.
 *
 * The source retains only the application context, registers listeners during an attached Host
 * interval and caches one immutable platform-neutral snapshot. Display features and motion are
 * merged by `PixelHostView` because they depend on current viewport geometry and the existing
 * independently testable motion observer.
 */
internal class AndroidPixelHostCapabilitiesSource(
    context: Context,
) : PixelHostCapabilitiesSource {
    /** Application context avoids retaining an Activity across recreation. */
    private val applicationContext: Context = context.applicationContext

    /** Main handler defining every listener and callback thread. */
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    /** Main-thread executor accepted by API 34+ contrast listeners. */
    private val mainExecutor: Executor = Executor { command -> mainHandler.post(command) }

    /** Display service used to read and observe the Host display refresh rate. */
    private val displayManager: DisplayManager =
        checkNotNull(applicationContext.getSystemService(DisplayManager::class.java))

    /** API-specific contrast observer isolated behind a version-neutral interface. */
    private val contrastRegistration: PlatformContrastRegistration =
        createContrastRegistration(applicationContext, mainExecutor)

    /** Configuration callback covering locale, direction, font scale and density changes. */
    private val componentCallbacks: ComponentCallbacks = object : ComponentCallbacks {
        /** Re-reads every field because one Configuration event can change multiple values. */
        override fun onConfigurationChanged(newConfig: Configuration) {
            dispatchOnMainThread(::emitLatest)
        }

        /** No cached resources are owned by this source. */
        override fun onLowMemory() = Unit
    }

    /** Display listener emits only when the Host's selected display may have changed. */
    private val displayListener: DisplayManager.DisplayListener =
        object : DisplayManager.DisplayListener {
            /** New display can satisfy a previously unknown Host display id. */
            override fun onDisplayAdded(displayId: Int) {
                if (selectedDisplayId == displayId) emitLatest()
            }

            /** Refresh-rate or mode changes require a fresh complete snapshot. */
            override fun onDisplayChanged(displayId: Int) {
                if (selectedDisplayId == null || selectedDisplayId == displayId) emitLatest()
            }

            /** Removed selected display becomes an explicit unknown refresh rate. */
            override fun onDisplayRemoved(displayId: Int) {
                if (selectedDisplayId == displayId) emitLatest()
            }
        }

    /** Consumer retained only during the current attached interval. */
    private var callback: ((HostCapabilitiesData) -> Unit)? = null

    /** Android display id currently associated with the Host, or default display when absent. */
    private var selectedDisplayId: Int? = null

    /** Latest immutable snapshot emitted or synchronously read from Android. */
    private var cachedCapabilities: HostCapabilitiesData = readPlatformCapabilities()

    /** Whether Android listeners are currently registered. */
    private var attached: Boolean = false

    /** Whether terminal destruction has completed. */
    private var destroyed: Boolean = false

    override val currentCapabilities: HostCapabilitiesData
        get() {
            requireMainThread()
            if (!destroyed) cachedCapabilities = readPlatformCapabilities()
            return cachedCapabilities
        }

    /** Registers every observer exactly once and immediately synchronizes the attached Host. */
    override fun attach(onChanged: (HostCapabilitiesData) -> Unit, displayId: Int?) {
        requireMainThread()
        if (destroyed) return
        callback = onChanged
        selectedDisplayId = displayId
        cachedCapabilities = readPlatformCapabilities()
        if (!attached) {
            attached = true
            applicationContext.registerComponentCallbacks(componentCallbacks)
            displayManager.registerDisplayListener(displayListener, mainHandler)
            contrastRegistration.register(::emitLatest)
        }
        onChanged(cachedCapabilities)
    }

    /** Rebinds refresh-rate observation to the Host's current display. */
    override fun updateDisplay(displayId: Int?) {
        requireMainThread()
        if (destroyed || selectedDisplayId == displayId) return
        selectedDisplayId = displayId
        emitLatest()
    }

    /** Re-reads all platform values and emits only a distinct complete snapshot. */
    override fun refresh() {
        requireMainThread()
        if (destroyed) return
        emitLatest()
    }

    /** Unregisters every active listener while retaining the last immutable snapshot. */
    override fun detach() {
        requireMainThread()
        if (!attached) {
            callback = null
            return
        }
        attached = false
        contrastRegistration.unregister()
        displayManager.unregisterDisplayListener(displayListener)
        applicationContext.unregisterComponentCallbacks(componentCallbacks)
        callback = null
    }

    /** Permanently detaches and rejects future registration exactly once. */
    override fun destroy() {
        requireMainThread()
        if (destroyed) return
        detach()
        destroyed = true
    }

    /** Re-reads Android and publishes only a value-distinct snapshot while attached. */
    private fun emitLatest() {
        requireMainThread()
        if (destroyed) return
        /** Complete new platform snapshot from a single main-thread read. */
        val latest = readPlatformCapabilities()
        if (latest == cachedCapabilities) return
        cachedCapabilities = latest
        if (attached) callback?.invoke(latest)
    }

    /** Ensures callbacks received from OEM services converge onto the documented main thread. */
    private fun dispatchOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    /** Reads every supported capability without retaining mutable Configuration or Locale objects. */
    private fun readPlatformCapabilities(): HostCapabilitiesData {
        /** Current application resource configuration. */
        val configuration = applicationContext.resources.configuration
        /** Ordered, deduplicated platform locale preferences. */
        val locales = buildList {
            for (index in 0 until configuration.locales.size()) {
                /** Canonical platform locale copied into the engine model. */
                val locale = PixelLocale(configuration.locales[index].toLanguageTag())
                if (none { existing -> existing == locale }) add(locale)
            }
        }.ifEmpty { listOf(PixelLocale.Default) }
        /** Positive text scale with an explicit fallback for malformed OEM configuration values. */
        val textScale = configuration.fontScale.takeIf { value -> value.isFinite() && value > 0f } ?: 1f
        /** Positive physical-pixels-per-dp ratio from the current resource display metrics. */
        val density = applicationContext.resources.displayMetrics.density
            .takeIf { value -> value.isFinite() && value > 0f }
            ?: 1f
        /** Current selected-display refresh rate, or null when the display is absent/invalid. */
        val refreshRate = readRefreshRateHz()
        return HostCapabilitiesData(
            locales = locales,
            layoutDirection = when (configuration.layoutDirection) {
                android.view.View.LAYOUT_DIRECTION_RTL -> TextDirection.RTL
                else -> TextDirection.LTR
            },
            textScaleFactor = textScale,
            highContrast = readHighContrastPreference(),
            density = density,
            refreshRateHz = refreshRate,
        )
    }

    /** Reads the selected display and rejects non-positive or non-finite OEM values. */
    private fun readRefreshRateHz(): Float? {
        /** Host-selected display, falling back to Android's default display before attachment. */
        val platformDisplay = displayManager.getDisplay(selectedDisplayId ?: Display.DEFAULT_DISPLAY)
            ?: return null
        return platformDisplay.refreshRate.takeIf { value -> value.isFinite() && value > 0f }
    }

    /** Reads the newest public contrast signal available on the running Android version. */
    @SuppressLint("NewApi")
    private fun readHighContrastPreference(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= 36 -> Api36HighContrastReader.isEnabled(applicationContext)
            Build.VERSION.SDK_INT >= 34 -> Api34HighContrastReader.isEnabled(applicationContext)
            else -> false
        }
    }

    /** Fails fast when a custom Host drives lifecycle methods from the wrong thread. */
    private fun requireMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "PixelHostCapabilitiesSource lifecycle must run on the Android main thread"
        }
    }
}

/** Version-neutral contrast registration used by [AndroidPixelHostCapabilitiesSource]. */
private interface PlatformContrastRegistration {
    /** Starts forwarding contrast preference changes to [onChanged]. */
    fun register(onChanged: () -> Unit)

    /** Stops forwarding changes; repeated calls are safe. */
    fun unregister()
}

/** No-op registration used when Android exposes no public contrast change API. */
private object NoOpContrastRegistration : PlatformContrastRegistration {
    /** Older Android releases have no observable public contrast preference. */
    override fun register(onChanged: () -> Unit) = Unit

    /** No platform listener was registered. */
    override fun unregister() = Unit
}

/** Creates an API-isolated contrast observer without resolving new nested listener types on API 24. */
@SuppressLint("NewApi")
private fun createContrastRegistration(
    /** Application context used to resolve the platform contrast service. */
    context: Context,
    /** Main-thread executor defining callback affinity. */
    executor: Executor,
): PlatformContrastRegistration {
    return when {
        Build.VERSION.SDK_INT >= 36 -> Api36ContrastRegistration(context, executor)
        Build.VERSION.SDK_INT >= 34 -> Api34ContrastRegistration(context, executor)
        else -> NoOpContrastRegistration
    }
}

/** API 34–35 contrast listener isolated from older runtime class verification. */
@RequiresApi(34)
private class Api34ContrastRegistration(
    context: Context,
    /** Executor required by [UiModeManager.addContrastChangeListener]. */
    private val executor: Executor,
) : PlatformContrastRegistration {
    /** UI mode manager owning the listener registration. */
    private val manager: UiModeManager = checkNotNull(context.getSystemService(UiModeManager::class.java))

    /** Exact listener instance removed during detach. */
    private var listener: UiModeManager.ContrastChangeListener? = null

    /** Registers one listener and forwards only its signal; the source performs the fresh read. */
    override fun register(onChanged: () -> Unit) {
        if (listener != null) return
        /** Candidate listener retained before platform registration. */
        val candidate = UiModeManager.ContrastChangeListener { onChanged() }
        listener = candidate
        manager.addContrastChangeListener(executor, candidate)
    }

    /** Removes the exact listener registered during the attached interval. */
    override fun unregister() {
        /** Active listener, or null when already detached. */
        val current = listener ?: return
        listener = null
        manager.removeContrastChangeListener(current)
    }
}

/** API 36 high-contrast-text listener isolated from older runtime class verification. */
@RequiresApi(36)
private class Api36ContrastRegistration(
    context: Context,
    /** Executor required by the accessibility contrast API. */
    private val executor: Executor,
) : PlatformContrastRegistration {
    /** Accessibility manager owning the high-contrast listener. */
    private val manager: AccessibilityManager =
        checkNotNull(context.getSystemService(AccessibilityManager::class.java))

    /** Exact listener instance removed during detach. */
    private var listener: AccessibilityManager.HighContrastTextStateChangeListener? = null

    /** Registers one listener and forwards its signal to the complete snapshot reader. */
    override fun register(onChanged: () -> Unit) {
        if (listener != null) return
        /** Candidate listener retained before platform registration. */
        val candidate = AccessibilityManager.HighContrastTextStateChangeListener { onChanged() }
        listener = candidate
        manager.addHighContrastTextStateChangeListener(executor, candidate)
    }

    /** Removes the exact listener registered during the attached interval. */
    override fun unregister() {
        /** Active listener, or null when already detached. */
        val current = listener ?: return
        listener = null
        manager.removeHighContrastTextStateChangeListener(current)
    }
}

/** API 34 UI contrast reader isolated from API 24 class verification. */
@RequiresApi(34)
private object Api34HighContrastReader {
    /** Treats any positive user contrast preference as an enhanced-contrast request. */
    fun isEnabled(context: Context): Boolean {
        /** Platform UI mode service containing the normalized `-1..1` contrast preference. */
        val manager = checkNotNull(context.getSystemService(UiModeManager::class.java))
        return manager.contrast > 0f
    }
}

/** API 36 accessibility high-contrast reader isolated from older runtime verification. */
@RequiresApi(36)
private object Api36HighContrastReader {
    /** Returns Android's explicit high-contrast-text accessibility preference. */
    fun isEnabled(context: Context): Boolean {
        /** Platform accessibility service containing the API 36 preference. */
        val manager = checkNotNull(context.getSystemService(AccessibilityManager::class.java))
        return manager.isHighContrastTextEnabled
    }
}
