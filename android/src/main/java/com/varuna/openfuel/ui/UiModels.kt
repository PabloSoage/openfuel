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
