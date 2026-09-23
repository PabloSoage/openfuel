package com.varuna.openfuel.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The platform LocationManager, as in Rustify: no Play Services dependency.
 * The last known fix is enough to centre a map of petrol stations; when the
 * device has none (fresh boot, emulator) one fix is asked for.
 */
object DeviceLocation {
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private const val MAX_AGE_MS = 10L * 60 * 1000
    private const val FIX_TIMEOUT_MS = 15_000L

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        return manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** A recent last-known fix, or a fresh one within 15 s, or null. */
    suspend fun current(context: Context): Location? {
        val last = lastKnown(context)
        if (last != null && System.currentTimeMillis() - last.time < MAX_AGE_MS) return last
        return withTimeoutOrNull(FIX_TIMEOUT_MS) { freshFix(context) } ?: last
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshFix(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val enabled = manager.getProviders(true)
        val provider = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstOrNull { it in enabled } ?: return null
        return suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(manager, provider, cancel, ContextCompat.getMainExecutor(context)) { fix ->
                    if (cont.isActive) cont.resume(fix)
                }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}
