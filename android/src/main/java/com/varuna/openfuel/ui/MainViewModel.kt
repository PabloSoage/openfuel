package com.varuna.openfuel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.varuna.openfuel.AppContainer
import com.varuna.openfuel.core.analysis.OwnAverage
import com.varuna.openfuel.core.analysis.RelativeMargin
import com.varuna.openfuel.core.discount.DiscountPlan
import com.varuna.openfuel.core.discount.EffectivePriceCalculator
import com.varuna.openfuel.core.geo.Geo
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.model.Station
import com.varuna.openfuel.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val store = container.settings

    private val location = MutableStateFlow<LatLon?>(null)
    private val refreshState = MutableStateFlow(RefreshState())
    private val _detail = MutableStateFlow<StationDetail?>(null)
    val detail: StateFlow<StationDetail?> = _detail

    private data class RefreshState(val running: Boolean = false, val failed: Boolean = false)

    private data class Core(
        val settings: Settings,
        val rows: List<StationRow>,
        val all: List<Station>,
        val brands: List<BrandFilterItem>,
        val favourites: Set<String>,
    )

    // Ranking thousands of stations is not work for the main thread.
    private val core = combine(
        store.settings,
        repo.stations,
        repo.plansByStation,
        repo.favourites,
        location,
    ) { settings, stations, plans, favourites, here ->
        Core(settings, buildRows(settings, stations, plans, favourites, here), stations, buildBrands(settings, stations), favourites)
    }.flowOn(Dispatchers.Default)

    val ui: StateFlow<UiState> = combine(
        core,
        repo.logos,
        repo.planCatalog,
        container.taxSchedules.schedule,
        refreshState,
    ) { c, logos, catalog, schedule, refresh ->
        UiState(
            settings = c.settings,
            rows = c.rows,
            allStations = c.all,
            brands = c.brands,
            favourites = c.favourites,
            logos = logos,
            planCatalog = catalog,
            schedule = schedule,
            location = location.value,
            refreshing = refresh.running,
            refreshFailed = refresh.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            container.taxSchedules.loadCached()
            refresh(force = false)
        }
    }

    // --- actions -----------------------------------------------------------

    private var refreshJob: Job? = null

    fun refresh(force: Boolean = true) {
        // Both callers run on the main thread, so checking the job is enough to
        // stop two downloads (start-up and a quick tap) from overlapping.
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val settings = store.current()
            val region = settings.region ?: return@launch
            val stale = System.currentTimeMillis() - settings.lastRefreshAt > STALE_MS
            if (!force && !stale) {
                enrich(settings)
                return@launch
            }
            refreshState.value = RefreshState(running = true)
            try {
                val outcome = runCatching { repo.refresh(region) }
                refreshState.value = RefreshState(failed = outcome.exceptionOrNull()?.let { it !is CancellationException } ?: false)
                if (outcome.isSuccess) enrich(store.current())
            } finally {
                refreshState.update { it.copy(running = false) }
            }
        }
    }

    fun setRegion(selection: RegionSelection) {
        // A download of the old region finishing late would overwrite the new one.
        refreshJob?.cancel()
        viewModelScope.launch {
            store.setRegion(selection)
            refresh(force = true)
        }
    }

    fun setFuel(fuel: Fuel) {
        viewModelScope.launch { store.setFuel(fuel) }
        _detail.value?.station?.id?.let { openStation(it, fuel) }
    }

    fun setHiddenBrands(keys: Set<String>) {
        viewModelScope.launch { store.setHiddenBrands(keys) }
    }

    fun setPlanOwned(planId: Int, owned: Boolean) {
        viewModelScope.launch {
            store.setPlanOwned(planId, owned)
            if (owned) runCatching { repo.prefetchPlans(prefetchOrder(store.current())) }
        }
    }

    fun setHistoryDays(days: Int) {
        viewModelScope.launch { store.setHistoryDays(days) }
    }

    fun setRadiusKm(km: Int) {
        viewModelScope.launch { store.setRadiusKm(km) }
    }

    fun setLocation(lat: Double, lon: Double) {
        location.value = LatLon(lat, lon)
    }

    fun setFavourite(stationId: String, favourite: Boolean) {
        viewModelScope.launch { repo.setFavourite(stationId, favourite) }
    }

    private var detailJob: Job? = null

    fun openStation(stationId: String, fuelOverride: Fuel? = null) {
        val station = ui.value.allStations.firstOrNull { it.id == stationId } ?: return
        _detail.value = StationDetail(station)
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val settings = store.current()
            val fuel = fuelOverride ?: settings.fuel
            val schedule = container.taxSchedules.schedule.value
            val today = today()
            val relative = RelativeMargin.compute(
                station, fuel, ui.value.allStations, settings.radiusKm.toDouble(), today, schedule,
            )
            _detail.update { it?.copy(relative = relative) }

            // Plans and history are independent: neither waits for the other.
            val plans = async { loadPlans(station.id) }
            val history = async {
                if (settings.historyDays > 0) {
                    runCatching { repo.ensureHistory(station.provinceId, fuel, settings.historyDays, today) }
                }
                runCatching { repo.history(station.id, fuel, settings.historyDays, today) }.getOrDefault(emptyList())
            }
            plans.await()
            val points = history.await()
            val todayPrice = station.prices[fuel]
            val past = points.filter { it.day.isBefore(today) }.map { it.price }
            val own = todayPrice?.let { OwnAverage.compare(it, past) }
            _detail.update { it?.copy(history = points, historyLoading = false, ownAverage = own) }
        }
    }

    fun retryPlans() {
        val id = _detail.value?.station?.id ?: return
        _detail.update { it?.copy(plansLoading = true, plansFailed = false) }
        viewModelScope.launch { loadPlans(id, force = true) }
    }

    private suspend fun loadPlans(stationId: String, force: Boolean = false) {
        val result = runCatching { repo.plansFor(stationId, force) }.getOrNull()
        _detail.update { d ->
            if (d?.station?.id != stationId) d
            else d.copy(plans = result?.plans.orEmpty(), plansLoading = false, plansFailed = result?.failed ?: true)
        }
    }

    fun closeStation() {
        detailJob?.cancel()
        _detail.value = null
    }

    // --- derivations -------------------------------------------------------

    /** Logos, tax schedule and, if the user owns a plan, nearby plans — never blocking the map. */
    private fun enrich(settings: Settings) {
        viewModelScope.launch { runCatching { container.taxSchedules.refreshIfDue() } }
        viewModelScope.launch { runCatching { container.logos.resolveMissing() } }
        if (settings.ownedPlans.isNotEmpty()) {
            viewModelScope.launch { runCatching { repo.prefetchPlans(prefetchOrder(settings)) } }
        }
    }

    /**
     * Which stations are worth asking plans for: the nearest when the location
     * is known, otherwise the cheapest at the pump — a discount of a few cents
     * only changes the ranking near the top.
     */
    private fun prefetchOrder(settings: Settings): List<String> {
        val stations = ui.value.allStations.filter { settings.fuel in it.prices }
        val here = location.value
        val ordered = if (here != null) {
            stations.sortedBy { Geo.distanceKm(here.lat, here.lon, it.lat, it.lon) }
        } else {
            stations.sortedBy { it.prices.getValue(settings.fuel) }
        }
        val favourites = ui.value.favourites
        return (ordered.filter { it.id in favourites } + ordered).map { it.id }.distinct()
    }

    private fun buildRows(
        settings: Settings,
        stations: List<Station>,
        plans: Map<String, List<DiscountPlan>>,
        favourites: Set<String>,
        here: LatLon?,
    ): List<StationRow> {
        val fuel = settings.fuel
        val priced = stations
            .filter { it.brand.filterKey !in settings.hiddenBrands }
            .mapNotNull { station ->
                val price = station.prices[fuel] ?: return@mapNotNull null
                val effective = EffectivePriceCalculator.best(price, plans[station.id].orEmpty(), settings.ownedPlans)
                Triple(station, price, effective)
            }
            .sortedBy { it.third.price }
        val n = priced.size
        return priced.mapIndexed { index, (station, price, effective) ->
            val band = when {
                n < 3 -> PriceBand.MID
                index < n / 3 -> PriceBand.CHEAP
                index >= n - n / 3 -> PriceBand.DEAR
                else -> PriceBand.MID
            }
            StationRow(
                station = station,
                price = price,
                effective = effective,
                band = band,
                rank = index,
                distanceKm = here?.let { Geo.distanceKm(it.lat, it.lon, station.lat, station.lon) },
                favourite = station.id in favourites,
            )
        }
    }

    private fun buildBrands(settings: Settings, stations: List<Station>): List<BrandFilterItem> =
        stations
            .filter { settings.fuel in it.prices }
            .groupBy { it.brand.filterKey }
            .map { (key, list) ->
                val name = if (list.first().brand.independent) "" else list.first().brand.displayName
                BrandFilterItem(key, name, list.size, key in settings.hiddenBrands)
            }
            .sortedByDescending { it.stations }

    companion object {
        private const val STALE_MS = 30L * 60 * 1000

        /** The official prices are Spanish days, whatever the device's zone. */
        fun today(): LocalDate = LocalDate.now(ZoneId.of("Europe/Madrid"))

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
        }
    }
}
