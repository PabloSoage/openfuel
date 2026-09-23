package com.varuna.openfuel.data

import android.util.Log
import com.varuna.openfuel.core.fiscal.TaxSchedule
import com.varuna.openfuel.core.net.Endpoints
import com.varuna.openfuel.core.net.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * Embedded schedule first; a newer one from the repository replaces it when it
 * parses, validates and has a higher version. A tax change is then a commit to
 * `core/src/main/resources/tax-schedule.json`, not an app release.
 */
class TaxScheduleProvider(
    private val http: HttpClient,
    private val settings: SettingsStore,
) {
    private val embedded = TaxSchedule.embedded()
    private val _schedule = MutableStateFlow(embedded)
    val schedule: StateFlow<TaxSchedule> = _schedule

    /** Applies the cached remote copy, if any. Call once at start-up. */
    suspend fun loadCached() {
        val (json, _) = settings.cachedTaxSchedule()
        json?.let { adopt(it) }
    }

    /** At most once a day. Failures leave the current schedule untouched. */
    suspend fun refreshIfDue(now: Long = System.currentTimeMillis()): Unit = withContext(Dispatchers.IO) {
        val (_, checkedAt) = settings.cachedTaxSchedule()
        if (now - checkedAt < DAY_MS) return@withContext
        val body = runCatching { http.get(Endpoints.TAX_SCHEDULE, emptyMap()) }.getOrNull()
            ?.takeIf { it.isSuccess }?.text()
        if (body != null && adopt(body)) {
            settings.storeTaxSchedule(body, now)
        } else {
            settings.storeTaxSchedule(null, now)
        }
    }

    private fun adopt(json: String): Boolean {
        val candidate = runCatching { TaxSchedule.parse(json) }
            .onFailure { Log.w(TAG, "Remote tax schedule rejected: ${it.message}") }
            .getOrNull() ?: return false
        if (candidate.version <= _schedule.value.version) return false
        _schedule.value = candidate
        return true
    }

    private companion object {
        const val TAG = "TaxSchedule"
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
