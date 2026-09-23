package com.varuna.openfuel.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.varuna.openfuel.AppContainer
import com.varuna.openfuel.core.analysis.OwnAverage
import com.varuna.openfuel.core.analysis.RelativeMargin
import com.varuna.openfuel.core.discount.EffectivePriceCalculator
import com.varuna.openfuel.core.geo.Geo
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.model.Station
import com.varuna.openfuel.data.Settings
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val store = container.settings

    private val location = MutableStateFlow<LatLon?>(null)
    private val refreshState = MutableStateFlow(RefreshState())
    private val _detail = MutableStateFlow<StationDetail?>(null)
    val detail: StateFlow<StationDetail?> = _detail

    private data class RefreshState(val running: Boolean = false, val error: String? = null)

    private data class Core(
        val settings: Settings,
        val rows: List<StationRow>,
        val all: List<Station>,
        val brands: List<BrandFilterItem>,
    )

    private val core = combine(
        store.settings,
        repo.stations,
        repo.plansByStation,
        repo.favourites,
        location,
    ) { settings, stations, plans, favourites, here ->
        Core(settings, buildRows(settings, stations, plans, favourites, here), stations, buildBrands(settings, stations))
    }

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
            logos = logos,
            planCatalog = catalog,
            schedule = schedule,
            location = location.value,
            refreshing = refresh.running,
            error = refresh.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        viewModelScope.launch {
            container.taxSchedules.loadCached()
            refresh(force = false)
        }
    }

    // --- actions -----------------------------------------------------------

    fun refresh(force: Boolean = true) {
        if (refreshState.value.running) return
        viewModelScope.launch {
            val settings = store.current()
            val region = settings.region ?: return@launch
            val stale = System.currentTimeMillis() - settings.lastRefreshAt > STALE_MS
            if (!force && !stale) {
                enrich(settings)
                return@launch
            }
            refreshState.value = RefreshState(running = true)
            val outcome = runCatching { repo.refresh(region) }
            refreshState.value = RefreshState(running = false, error = outcome.exceptionOrNull()?.message)
            if (outcome.isSuccess) enrich(store.current())
        }
    }

    fun setRegion(selection: RegionSelection) {
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
            if (owned) repo.prefetchPlans(ui.value.allStations.map { it.id })
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
            val today = LocalDate.now()
            val relative = RelativeMargin.compute(
                station, fuel, ui.value.allStations, settings.radiusKm.toDouble(), today, schedule,
            )
            _detail.value = _detail.value?.copy(relative = relative)

            val plans = runCatching { repo.plansFor(station.id) }.getOrDefault(emptyList())
            _detail.value = _detail.value?.copy(plans = plans, plansLoading = false)

            if (settings.historyDays > 0) {
                runCatching { repo.ensureHistory(station.provinceId, fuel, settings.historyDays, today) }
            }
            val history = runCatching { repo.history(station.id, fuel, settings.historyDays, today) }.getOrDefault(emptyList())
            val todayPrice = station.prices[fuel]
            val past = history.filter { it.day.isBefore(today) }.map { it.price }
            val own = todayPrice?.let { OwnAverage.compare(it, past) }
            _detail.value = _detail.value?.copy(history = history, historyLoading = false, ownAverage = own)
        }
    }

    fun closeStation() {
        detailJob?.cancel()
        _detail.value = null
    }

    // --- derivations -------------------------------------------------------

    /** Logos, tax schedule and, if the user owns a plan, the region's plans — never blocking the map. */
    private fun enrich(settings: Settings) {
        viewModelScope.launch { runCatching { container.taxSchedules.refreshIfDue() } }
        viewModelScope.launch { runCatching { container.logos.resolveMissing() } }
        if (settings.ownedPlans.isNotEmpty()) {
            viewModelScope.launch { runCatching { repo.prefetchPlans(ui.value.allStations.map { it.id }) } }
        }
    }

    private fun buildRows(
        settings: Settings,
        stations: List<Station>,
        plans: Map<String, List<com.varuna.openfuel.core.discount.DiscountPlan>>,
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

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
        }
    }
}
