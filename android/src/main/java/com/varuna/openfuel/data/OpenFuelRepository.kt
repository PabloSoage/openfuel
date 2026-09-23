package com.varuna.openfuel.data

import androidx.room.withTransaction
import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.discount.DiscountPlan
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.model.Station
import com.varuna.openfuel.core.net.GeoportalApi
import com.varuna.openfuel.core.net.OfficialApi
import com.varuna.openfuel.data.db.FavouriteEntity
import com.varuna.openfuel.data.db.HistoryFetchEntity
import com.varuna.openfuel.data.db.HistoryPriceEntity
import com.varuna.openfuel.data.db.OpenFuelDatabase
import com.varuna.openfuel.data.db.StationPlanEntity
import com.varuna.openfuel.data.db.StationPlansFetchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One point of a price chart. */
data class PricePoint(val day: LocalDate, val price: Double)

/**
 * Room is the single source for the UI; the network only ever writes into it.
 * See openfuel-docs/specs/2026-09-23-openfuel-design.md §7.
 */
class OpenFuelRepository(
    private val db: OpenFuelDatabase,
    private val settings: SettingsStore,
    private val official: OfficialApi,
    private val geoportal: GeoportalApi,
    private val catalog: BrandCatalog,
) {
    val stations: Flow<List<Station>> =
        combine(db.stations().observeAll(), db.prices().observeCurrent()) { stations, prices ->
            val byStation = prices.groupBy { it.stationId }
            stations.map { it.toStation(byStation[it.id].orEmpty(), catalog) }
        }

    val planCatalog: Flow<List<DiscountPlan>> = db.plans().observeCatalog().map { list -> list.map { it.toPlan() } }

    val plansByStation: Flow<Map<String, List<DiscountPlan>>> =
        combine(db.plans().observeStationPlans(), planCatalog) { links, plans ->
            val byId = plans.associateBy { it.id }
            links.groupBy({ it.stationId }, { byId[it.planId] }).mapValues { (_, v) -> v.filterNotNull() }
        }

    val logos: Flow<Map<String, ByteArray>> = db.logos().observeAll().map { list ->
        list.mapNotNull { e -> e.png?.let { e.brandKey to it } }.toMap()
    }

    val favourites: Flow<Set<String>> = db.favourites().observeIds().map { it.toSet() }

    /**
     * Downloads the selection and replaces what is stored for its provinces.
     * Stations outside the selection are dropped (history rows stay).
     */
    suspend fun refresh(selection: RegionSelection): RefreshOutcome = withContext(Dispatchers.IO) {
        val snapshot = official.current(selection)
        val provinces = selection.provinceIds().toList()
        db.withTransaction {
            db.stations().deleteOutsideProvinces(provinces)
            db.prices().deleteCurrentInProvinces(provinces)
            db.stations().deleteInProvinces(provinces)
            db.prices().deleteOrphanCurrent()
            db.stations().upsert(snapshot.stations.map { it.toEntity() })
            db.prices().insertCurrent(snapshot.stations.flatMap { it.priceEntities() })
        }
        val published = snapshot.publishedAt?.format(FECHA_OUT)
        settings.markRefreshed(System.currentTimeMillis(), published)
        RefreshOutcome(snapshot.stations.size, snapshot.skipped, snapshot.publishedAt)
    }

    /**
     * Makes sure the last [days] days of [fuel] in [provinceId] are stored, one
     * product-filtered request (~85 KB) per missing day, four at a time. Days
     * that fail are left missing and retried next time.
     */
    suspend fun ensureHistory(provinceId: String, fuel: Fuel, days: Int, today: LocalDate = LocalDate.now()): Int =
        withContext(Dispatchers.IO) {
            if (days <= 0 || fuel.productId == null) return@withContext 0
            val from = today.minusDays(days.toLong())
            val have = db.prices().fetchedDays(provinceId, fuel.name, from.toEpochDay()).toSet()
            val missing = (1..days).map { today.minusDays(it.toLong()) }.filter { it.toEpochDay() !in have }
            val gate = Semaphore(PARALLEL_REQUESTS)
            coroutineScope {
                missing.map { day ->
                    async {
                        gate.withPermit {
                            runCatching { official.historyPrices(day, provinceId, fuel) }.getOrNull()?.let { prices ->
                                db.prices().insertHistory(
                                    prices.map { (id, price) -> HistoryPriceEntity(id, fuel.name, day.toEpochDay(), price) },
                                )
                                db.prices().insertFetch(
                                    HistoryFetchEntity(provinceId, fuel.name, day.toEpochDay(), System.currentTimeMillis(), prices.size),
                                )
                                1
                            } ?: 0
                        }
                    }
                }.awaitAll().sum()
            }
        }

    /** Stored history, oldest first, today's price appended when known. */
    suspend fun history(stationId: String, fuel: Fuel, days: Int, today: LocalDate = LocalDate.now()): List<PricePoint> =
        withContext(Dispatchers.IO) {
            val past = db.prices().history(stationId, fuel.name, today.minusDays(days.toLong()).toEpochDay())
                .filter { it.epochDay < today.toEpochDay() }
                .map { PricePoint(LocalDate.ofEpochDay(it.epochDay), it.price) }
            val now = db.prices().currentFor(stationId).firstOrNull { it.fuel == fuel.name }?.let { PricePoint(today, it.price) }
            past + listOfNotNull(now)
        }

    /** Plans of one station, from cache when fetched in the last 7 days. */
    suspend fun plansFor(stationId: String, force: Boolean = false): List<DiscountPlan> = withContext(Dispatchers.IO) {
        val fetchedAt = db.plans().fetchedAt(stationId)
        val fresh = fetchedAt != null && System.currentTimeMillis() - fetchedAt < PLANS_TTL_MS
        if (force || !fresh) fetchPlans(stationId)
        db.plans().plansFor(stationId).map { it.toPlan() }
    }

    /** Background fill for ranking by effective price; only worth it if the user owns a plan. */
    suspend fun prefetchPlans(stationIds: List<String>): Unit = withContext(Dispatchers.IO) {
        val gate = Semaphore(PARALLEL_REQUESTS)
        coroutineScope {
            stationIds.map { id ->
                async {
                    gate.withPermit {
                        val fetchedAt = db.plans().fetchedAt(id)
                        if (fetchedAt == null || System.currentTimeMillis() - fetchedAt >= PLANS_TTL_MS) fetchPlans(id)
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun setFavourite(stationId: String, favourite: Boolean) = withContext(Dispatchers.IO) {
        if (favourite) db.favourites().add(FavouriteEntity(stationId, System.currentTimeMillis()))
        else db.favourites().remove(stationId)
    }

    private suspend fun fetchPlans(stationId: String) {
        val plans = geoportal.plans(stationId) ?: return // failure: keep whatever is cached
        db.withTransaction {
            db.plans().upsertPlans(plans.map { it.toEntity() })
            db.plans().clearStation(stationId)
            db.plans().insertStationPlans(plans.map { StationPlanEntity(stationId, it.id) })
            db.plans().markFetched(StationPlansFetchEntity(stationId, System.currentTimeMillis()))
        }
    }

    data class RefreshOutcome(val stations: Int, val skipped: Int, val publishedAt: LocalDateTime?)

    private companion object {
        const val PARALLEL_REQUESTS = 4
        const val PLANS_TTL_MS = 7L * 24 * 60 * 60 * 1000
        val FECHA_OUT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
    }
}
