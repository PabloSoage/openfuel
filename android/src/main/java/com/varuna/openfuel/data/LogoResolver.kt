package com.varuna.openfuel.data

import android.util.Log
import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.net.FaviconFetcher
import com.varuna.openfuel.core.net.GeoportalApi
import com.varuna.openfuel.core.net.HttpClient
import com.varuna.openfuel.core.parse.GeoportalParser
import com.varuna.openfuel.data.db.BrandLogoEntity
import com.varuna.openfuel.data.db.OpenFuelDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The logo cascade, one brand at a time: geoportal `imagenEESS` of a few of
 * the brand's stations (after `logoUrl` when the brand prefers it: the
 * geoportal gives Petronor Repsol's logo) → the brand's `logoUrl` (Wikimedia Commons) → the
 * brand site's apple-touch-icon → nothing (the map draws a badge).
 *
 * The geoportal carries a logo on some stations of a brand and not others
 * (Carrefour on 1 in 12 sampled on 2026-09-23, Petroprix on 8), so several
 * stations are tried, spread over the list rather than the first ones. A miss
 * is retried the next day, a hit after 90.
 */
class LogoResolver(
    private val db: OpenFuelDatabase,
    private val geoportal: GeoportalApi,
    private val favicons: FaviconFetcher,
    private val http: HttpClient,
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
        val brand = catalog.fromKey(brandKey, "")
        // A brand whose geoportal logo is known to be wrong (Petronor gets Repsol's)
        // never falls back to it: if its own logo cannot be fetched, it gets a badge
        // today and another try tomorrow.
        if (brand.preferLogoUrl) {
            brand.logoUrl?.let { url -> png(url)?.let { return SOURCE_URL to it } }
            brand.website?.let { host -> favicons.pngFor(host)?.let { return SOURCE_FAVICON to it } }
            return SOURCE_NONE to null
        }
        val stations = db.stations().byBrand(brandKey, SAMPLE)
        val step = (stations.size / GEOPORTAL_ATTEMPTS).coerceAtLeast(1)
        for (station in stations.filterIndexed { i, _ -> i % step == 0 }.take(GEOPORTAL_ATTEMPTS)) {
            geoportal.logoPng(station.id)?.let { return SOURCE_GEOPORTAL to it }
        }
        brand.logoUrl?.let { url -> png(url)?.let { return SOURCE_URL to it } }
        brand.website?.let { host -> favicons.pngFor(host)?.let { return SOURCE_FAVICON to it } }
        return SOURCE_NONE to null
    }

    private fun png(url: String): ByteArray? = runCatching { http.get(url, mapOf("Accept" to "image/png")) }
        .getOrNull()?.takeIf { it.isSuccess && GeoportalParser.isPng(it.body) }?.body

    private fun isStale(logo: BrandLogoEntity, now: Long): Boolean {
        // A brand whose geoportal logo is known to be wrong drops a cached one at once.
        if (logo.source == SOURCE_GEOPORTAL && catalog.fromKey(logo.brandKey, "").preferLogoUrl) return true
        // Misses recorded before the cascade tried several stations and logoUrl
        // are retried at once, not a month later.
        if (logo.png == null && logo.checkedAt < CASCADE_V2_MS) return true
        val maxAge = if (logo.png == null) MISS_TTL_MS else HIT_TTL_MS
        return now - logo.checkedAt > maxAge
    }

    private companion object {
        const val TAG = "LogoResolver"
        const val SAMPLE = 200
        const val GEOPORTAL_ATTEMPTS = 8
        const val SOURCE_GEOPORTAL = "geoportal"
        const val SOURCE_URL = "url"
        const val SOURCE_FAVICON = "favicon"
        const val SOURCE_NONE = "none"
        const val MISS_TTL_MS = 24L * 60 * 60 * 1000
        const val HIT_TTL_MS = 90L * 24 * 60 * 60 * 1000
        const val CASCADE_V2_MS = 1_790_190_000_000L // 2026-09-23 21:00 CEST
    }
}
