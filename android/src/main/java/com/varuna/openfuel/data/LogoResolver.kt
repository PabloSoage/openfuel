package com.varuna.openfuel.data

import android.util.Log
import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.net.FaviconFetcher
import com.varuna.openfuel.core.net.GeoportalApi
import com.varuna.openfuel.data.db.BrandLogoEntity
import com.varuna.openfuel.data.db.OpenFuelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The logo cascade, one brand at a time: geoportal `imagenEESS` → the brand
 * site's apple-touch-icon → nothing (the map draws a badge). Results are kept
 * per brand, never per station; a miss is retried after 30 days, a hit after 90.
 */
class LogoResolver(
    private val db: OpenFuelDatabase,
    private val geoportal: GeoportalApi,
    private val favicons: FaviconFetcher,
    private val catalog: BrandCatalog,
) {
    suspend fun resolveMissing(now: Long = System.currentTimeMillis()): Unit = withContext(Dispatchers.IO) {
        val keys = db.stations().brandKeys().filterNot { it.startsWith("ind:") }
        for (key in keys) {
            val known = db.logos().byKey(key)
            if (known != null && !isStale(known, now)) continue
            val result = resolve(key)
            db.logos().upsert(BrandLogoEntity(key, result.first, result.second, now))
            Log.i(TAG, "logo $key -> ${result.first} (${result.second?.size ?: 0} bytes)")
        }
    }

    private suspend fun resolve(brandKey: String): Pair<String, ByteArray?> {
        for (station in db.stations().byBrand(brandKey, GEOPORTAL_ATTEMPTS)) {
            geoportal.logoPng(station.id)?.let { return SOURCE_GEOPORTAL to it }
        }
        val host = catalog.fromKey(brandKey, "").website
        if (host != null) favicons.pngFor(host)?.let { return SOURCE_FAVICON to it }
        return SOURCE_NONE to null
    }

    private fun isStale(logo: BrandLogoEntity, now: Long): Boolean {
        val maxAge = if (logo.png == null) MISS_TTL_MS else HIT_TTL_MS
        return now - logo.checkedAt > maxAge
    }

    private companion object {
        const val TAG = "LogoResolver"
        const val GEOPORTAL_ATTEMPTS = 3
        const val SOURCE_GEOPORTAL = "geoportal"
        const val SOURCE_FAVICON = "favicon"
        const val SOURCE_NONE = "none"
        const val MISS_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val HIT_TTL_MS = 90L * 24 * 60 * 60 * 1000
    }
}
