package com.varuna.openfuel.core.analysis

import com.varuna.openfuel.core.fiscal.FiscalBreakdown
import com.varuna.openfuel.core.fiscal.TaxSchedule
import com.varuna.openfuel.core.geo.Geo
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Station
import java.time.LocalDate

/**
 * How much more a station keeps per litre than the cheapest one nearby.
 *
 * Product cost and logistics are the same for two stations on the same day, so
 * the difference of what is left after taxes *is* the difference of margins.
 * This needs no outside data and is exact (openfuel-docs/research/04 §2).
 */
object RelativeMargin {

    data class Result(
        /** Euro cents per litre above the cheapest; 0 for the cheapest itself. */
        val centsAboveCheapest: Double,
        val cheapest: Station,
        /** Stations compared, the target included. */
        val compared: Int,
    )

    fun compute(
        target: Station,
        fuel: Fuel,
        candidates: List<Station>,
        radiusKm: Double,
        date: LocalDate,
        schedule: TaxSchedule,
    ): Result? {
        fun rest(s: Station): Double? {
            val price = s.prices[fuel] ?: return null
            return (FiscalBreakdown.of(price, fuel, date, s.territory, schedule) as? FiscalBreakdown.Available)?.rest
        }
        val targetRest = rest(target) ?: return null
        val nearby = candidates
            .filter { it.territory == target.territory }
            .filter { it.id == target.id || Geo.distanceKm(target.lat, target.lon, it.lat, it.lon) <= radiusKm }
            .mapNotNull { s -> rest(s)?.let { s to it } }
        val (cheapest, cheapestRest) = nearby.minByOrNull { it.second } ?: return null
        val compared = nearby.size + if (nearby.none { it.first.id == target.id }) 1 else 0
        return Result((targetRest - cheapestRest) * 100.0, cheapest, compared)
    }
}
