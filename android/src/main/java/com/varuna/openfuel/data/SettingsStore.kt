package com.varuna.openfuel.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.RegionSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    /** Null until the first-run picker has been answered. */
    val region: RegionSelection?,
    val fuel: Fuel,
    val hiddenBrands: Set<String>,
    val ownedPlans: Set<Int>,
    val historyDays: Int,
    val radiusKm: Int,
    val lastRefreshAt: Long,
    /** `Fecha` of the last official response, as the API wrote it. */
    val publishedAt: String?,
    /** Look for a new release on GitHub when the app starts. */
    val checkUpdates: Boolean = true,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val REGION_KIND = stringPreferencesKey("region_kind")
        val REGION_IDS = stringSetPreferencesKey("region_ids")
        val FUEL = stringPreferencesKey("fuel")
        val HIDDEN_BRANDS = stringSetPreferencesKey("brands_hidden")
        val OWNED_PLANS = stringSetPreferencesKey("owned_plans")
        val HISTORY_DAYS = intPreferencesKey("history_days")
        val RADIUS_KM = intPreferencesKey("radius_km")
        val LAST_REFRESH = longPreferencesKey("last_refresh_at")
        val PUBLISHED_AT = stringPreferencesKey("published_at")
        val TAX_SCHEDULE_JSON = stringPreferencesKey("tax_schedule_json")
        val TAX_SCHEDULE_CHECKED = longPreferencesKey("tax_schedule_checked_at")
        val CHECK_UPDATES = booleanPreferencesKey("check_updates_on_start")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            region = region(p[Keys.REGION_KIND], p[Keys.REGION_IDS].orEmpty()),
            fuel = Fuel.fromName(p[Keys.FUEL]) ?: Fuel.GOA,
            hiddenBrands = p[Keys.HIDDEN_BRANDS].orEmpty(),
            ownedPlans = p[Keys.OWNED_PLANS].orEmpty().mapNotNull { it.toIntOrNull() }.toSet(),
            historyDays = p[Keys.HISTORY_DAYS] ?: DEFAULT_HISTORY_DAYS,
            radiusKm = p[Keys.RADIUS_KM] ?: DEFAULT_RADIUS_KM,
            lastRefreshAt = p[Keys.LAST_REFRESH] ?: 0L,
            publishedAt = p[Keys.PUBLISHED_AT],
            checkUpdates = p[Keys.CHECK_UPDATES] ?: true,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setRegion(selection: RegionSelection) = context.dataStore.edit { p ->
        when (selection) {
            RegionSelection.AllSpain -> {
                p[Keys.REGION_KIND] = KIND_ALL
                p[Keys.REGION_IDS] = emptySet()
            }
            is RegionSelection.Communities -> {
                p[Keys.REGION_KIND] = KIND_CCAA
                p[Keys.REGION_IDS] = selection.ids
            }
            is RegionSelection.Provinces -> {
                p[Keys.REGION_KIND] = KIND_PROVINCES
                p[Keys.REGION_IDS] = selection.ids
            }
        }
        // A new region invalidates the freshness of the data on screen.
        p[Keys.LAST_REFRESH] = 0L
    }

    suspend fun setFuel(fuel: Fuel) = context.dataStore.edit { it[Keys.FUEL] = fuel.name }

    suspend fun setHiddenBrands(keys: Set<String>) = context.dataStore.edit { it[Keys.HIDDEN_BRANDS] = keys }

    suspend fun setPlanOwned(planId: Int, owned: Boolean) = context.dataStore.edit { p ->
        val current = p[Keys.OWNED_PLANS].orEmpty()
        p[Keys.OWNED_PLANS] = if (owned) current + planId.toString() else current - planId.toString()
    }

    suspend fun setHistoryDays(days: Int) = context.dataStore.edit { it[Keys.HISTORY_DAYS] = days }

    suspend fun setRadiusKm(km: Int) = context.dataStore.edit { it[Keys.RADIUS_KM] = km }

    suspend fun setCheckUpdates(on: Boolean) = context.dataStore.edit { it[Keys.CHECK_UPDATES] = on }

    suspend fun markRefreshed(at: Long, publishedAt: String?) = context.dataStore.edit { p ->
        p[Keys.LAST_REFRESH] = at
        if (publishedAt != null) p[Keys.PUBLISHED_AT] = publishedAt else p.remove(Keys.PUBLISHED_AT)
    }

    suspend fun cachedTaxSchedule(): Pair<String?, Long> {
        val p = context.dataStore.data.first()
        return p[Keys.TAX_SCHEDULE_JSON] to (p[Keys.TAX_SCHEDULE_CHECKED] ?: 0L)
    }

    suspend fun storeTaxSchedule(json: String?, checkedAt: Long) = context.dataStore.edit { p ->
        if (json != null) p[Keys.TAX_SCHEDULE_JSON] = json
        p[Keys.TAX_SCHEDULE_CHECKED] = checkedAt
    }

    private fun region(kind: String?, ids: Set<String>): RegionSelection? = when (kind) {
        KIND_ALL -> RegionSelection.AllSpain
        KIND_CCAA -> ids.takeIf { it.isNotEmpty() }?.let { RegionSelection.Communities(it) }
        KIND_PROVINCES -> ids.takeIf { it.isNotEmpty() }?.let { RegionSelection.Provinces(it) }
        else -> null
    }

    companion object {
        const val DEFAULT_HISTORY_DAYS = 30
        const val DEFAULT_RADIUS_KM = 10
        val HISTORY_CHOICES = listOf(0, 7, 30, 90)
        val RADIUS_CHOICES = listOf(5, 10, 25)
        private const val KIND_ALL = "ALL"
        private const val KIND_CCAA = "CCAA"
        private const val KIND_PROVINCES = "PROVINCES"
    }
}
