package com.varuna.openfuel.ui

import com.varuna.openfuel.core.analysis.OwnAverage
import com.varuna.openfuel.core.analysis.RelativeMargin
import com.varuna.openfuel.core.discount.DiscountPlan
import com.varuna.openfuel.core.discount.EffectivePrice
import com.varuna.openfuel.core.fiscal.TaxSchedule
import com.varuna.openfuel.core.model.Station
import com.varuna.openfuel.data.PricePoint
import com.varuna.openfuel.data.Settings

/** Price band within the loaded region, by effective price: thirds. */
enum class PriceBand { CHEAP, MID, DEAR }

data class StationRow(
    val station: Station,
    val price: Double,
    val effective: EffectivePrice,
    val band: PriceBand,
    /** 0 = cheapest. Used as the map's symbol sort key: cheaper wins collisions. */
    val rank: Int,
    val distanceKm: Double?,
    val favourite: Boolean,
)

data class BrandFilterItem(val filterKey: String, val name: String, val stations: Int, val hidden: Boolean)

data class LatLon(val lat: Double, val lon: Double)

data class UiState(
    val settings: Settings? = null,
    val rows: List<StationRow> = emptyList(),
    /** Every station of the region, whatever the filters: phase-2 comparisons need them. */
    val allStations: List<Station> = emptyList(),
    val brands: List<BrandFilterItem> = emptyList(),
    val favourites: Set<String> = emptySet(),
    val logos: Map<String, ByteArray> = emptyMap(),
    val planCatalog: List<DiscountPlan> = emptyList(),
    val schedule: TaxSchedule? = null,
    val location: LatLon? = null,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
)

data class StationDetail(
    val station: Station,
    val plans: List<DiscountPlan> = emptyList(),
    val plansLoading: Boolean = true,
    /** The geoportal did not answer and nothing was cached: not the same as "no plans". */
    val plansFailed: Boolean = false,
    val history: List<PricePoint> = emptyList(),
    val historyLoading: Boolean = true,
    val relative: RelativeMargin.Result? = null,
    val ownAverage: OwnAverage.Result? = null,
)

/** Where the map should go. [key] changes on every request, so the same place can be asked for twice. */
data class MapFocus(val lat: Double, val lon: Double, val zoom: Double, val key: Long = System.nanoTime())

data class SearchState(
    val query: String = "",
    /** Among the downloaded stations, as the user types. */
    val local: List<com.varuna.openfuel.core.search.Place> = emptyList(),
    /** From OpenStreetMap, only after the user submits; null until then. */
    val remote: List<com.varuna.openfuel.core.search.Place>? = null,
    val remoteLoading: Boolean = false,
    val remoteFailed: Boolean = false,
)

/** The GitHub release check, shown in Settings. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data object Failed : UpdateStatus
    data class Available(val update: com.varuna.openfuel.core.update.Releases.Update) : UpdateStatus
}
