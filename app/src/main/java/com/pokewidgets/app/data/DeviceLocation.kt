package com.pokewidgets.app.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * One coarse fix of where the phone is, turned into a [Place].
 *
 * Only ever run because the person tapped "Use my location", and only ever once per tap:
 * nothing here tracks the phone, and nothing runs in the background. City-level accuracy is
 * all the weather needs, so this asks for `ACCESS_COARSE_LOCATION` and nothing finer.
 *
 * Plain [LocationManager] rather than Play Services' fused provider, so it works on phones
 * without Google, and costs no new dependency. The label comes from the platform [Geocoder],
 * which is on-device on most phones; when it has nothing to say, the coordinates stand in.
 */
class DeviceLocation(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Where the phone is, or null with no permission, no provider, or no fix in time. */
    suspend fun currentPlace(): Place? {
        if (!hasPermission() || manager == null) return null
        val location = withTimeoutOrNull(FIX_TIMEOUT_MS) { freshFix() } ?: lastKnown() ?: return null
        return Place(label(location), location.latitude, location.longitude)
    }

    @SuppressLint("MissingPermission") // Checked in currentPlace.
    private suspend fun freshFix(): Location? {
        val provider = PROVIDERS.firstOrNull { runCatching { manager!!.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            runCatching {
                manager!!.getCurrentLocation(provider, signal, appContext.mainExecutor) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            }.onFailure {
                Log.w(TAG, "could not ask for a fix", it)
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    /** What the phone already knew, freshest first. Older Android has nothing better. */
    @SuppressLint("MissingPermission") // Checked in currentPlace.
    private fun lastKnown(): Location? =
        PROVIDERS.mapNotNull { runCatching { manager?.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

    @Suppress("DEPRECATION") // The listener form needs API 33; this runs off the main thread.
    private suspend fun label(location: Location): String = withContext(Dispatchers.IO) {
        val address = runCatching {
            if (!Geocoder.isPresent()) return@runCatching null
            Geocoder(appContext, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
        }.getOrNull()
        val parts = listOfNotNull(
            address?.locality ?: address?.subAdminArea,
            address?.adminArea,
            address?.countryName,
        ).filter { it.isNotBlank() }.distinct()
        if (parts.isNotEmpty()) {
            parts.joinToString(", ")
        } else {
            "Near %.2f°, %.2f°".format(Locale.ROOT, location.latitude, location.longitude)
        }
    }

    private companion object {
        const val TAG = "DeviceLocation"

        /** A city-level fix is the most anyone needs to know whether it is raining. */
        const val FIX_TIMEOUT_MS = 10_000L

        val PROVIDERS = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
    }
}
