package com.varuna.openfuel.core.analysis

import com.varuna.openfuel.core.geo.Geo
import com.varuna.openfuel.core.model.Station

/** The stations around one station, cheapest first: the list behind "the cheapest within 10 km". */
object Nearby {

    data class Entry(val station: Station, val price: Double, val distanceKm: Double)

    /**
     * Every candidate within [radiusKm] of [target] that has a [price], the
     * target included (at distance 0). Sorted by price, then distance.
     */
    fun around(target: Station, radiusKm: Double, candidates: List<Station>, price: (Station) -> Double?): List<Entry> =
        candidates.asSequence()
            .mapNotNull { s ->
                val p = price(s) ?: return@mapNotNull null
                val d = if (s.id == target.id) 0.0 else Geo.distanceKm(target.lat, target.lon, s.lat, s.lon)
                if (d <= radiusKm) Entry(s, p, d) else null
            }
            .sortedWith(compareBy<Entry>({ it.price }, { it.distanceKm }))
            .toList()
}
