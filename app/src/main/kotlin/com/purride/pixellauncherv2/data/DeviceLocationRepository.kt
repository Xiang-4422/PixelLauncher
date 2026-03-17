package com.purride.pixellauncherv2.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /** 返回当前点与另一点之间的大致米数距离。 */
    fun distanceToMeters(other: GeoPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            latitude,
            longitude,
            other.latitude,
            other.longitude,
            results,
        )
        return results[0]
    }
}

class DeviceLocationRepository(
    private val context: Context,
) {

    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val timeoutHandler = Handler(Looper.getMainLooper())

    /** 判断当前是否具备粗略或精确定位权限。 */
    fun hasLocationPermission(): Boolean {
        return hasFineLocationPermission() || hasCoarseLocationPermission()
    }

    /**
     * 从首选 provider 中读取最新的可用缓存位置。
     */
    fun readBestLastKnownLocation(): GeoPoint? {
        if (!hasLocationPermission()) {
            return null
        }
        return preferredProviders()
            .asSequence()
            .filter(::isProviderEnabledSafely)
            .mapNotNull(::readLastKnownLocation)
            .sortedWith(compareByDescending<Location> { it.time }.thenBy { it.accuracy })
            .firstOrNull()
            ?.toGeoPoint()
    }

    /**
     * 为天气刷新解析一个可用位置，优先使用缓存位置，不够时再请求一次实时位置。
     */
    fun requestBestLocation(onResult: (GeoPoint?) -> Unit) {
        if (!hasLocationPermission()) {
            onResult(null)
            return
        }

        val cachedLocation = readBestLastKnownLocation()
        if (cachedLocation != null) {
            onResult(cachedLocation)
            return
        }

        val provider = preferredProviders().firstOrNull(::isProviderEnabledSafely)
        if (provider == null) {
            onResult(null)
            return
        }

        requestCurrentLocation(provider, onResult)
    }

    @SuppressLint("MissingPermission")
    private fun requestCurrentLocation(provider: String, onResult: (GeoPoint?) -> Unit) {
        val manager = locationManager ?: run {
            onResult(null)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                onResult(location?.toGeoPoint())
            }
            return
        }

        requestSingleUpdateCompat(
            manager = manager,
            provider = provider,
            onResult = onResult,
        )
    }

    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission")
    private fun requestSingleUpdateCompat(
        manager: LocationManager,
        provider: String,
        onResult: (GeoPoint?) -> Unit,
    ) {
        var delivered = false
        lateinit var listener: LocationListener
        val timeoutRunnable = Runnable {
            if (delivered) {
                return@Runnable
            }
            delivered = true
            manager.removeUpdates(listener)
            onResult(null)
        }
        listener = LocationListener { location ->
            if (delivered) {
                return@LocationListener
            }
            delivered = true
            timeoutHandler.removeCallbacks(timeoutRunnable)
            manager.removeUpdates(listener)
            onResult(location?.toGeoPoint())
        }

        timeoutHandler.postDelayed(timeoutRunnable, currentLocationTimeoutMs)
        try {
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: Exception) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            if (!delivered) {
                delivered = true
                onResult(null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnownLocation(provider: String): Location? {
        return try {
            locationManager?.getLastKnownLocation(provider)
        } catch (_: SecurityException) {
            null
        }
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun preferredProviders(): List<String> {
        val providers = LinkedHashSet<String>()
        providers += LocationManager.NETWORK_PROVIDER
        if (hasFineLocationPermission()) {
            providers += LocationManager.GPS_PROVIDER
        }
        providers += LocationManager.PASSIVE_PROVIDER
        return providers.toList()
    }

    private fun isProviderEnabledSafely(provider: String): Boolean {
        return try {
            locationManager?.isProviderEnabled(provider) == true
        } catch (_: Exception) {
            false
        }
    }

    private fun Location.toGeoPoint(): GeoPoint {
        return GeoPoint(
            latitude = latitude,
            longitude = longitude,
        )
    }

    private companion object {
        const val currentLocationTimeoutMs = 10_000L
    }
}
