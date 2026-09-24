package com.varuna.openfuel.core.search

import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.model.Station

/**
 * Search among the stations already on the device: no network, results as
 * the user types. Matches postcodes, localities and municipalities (placed at
 * the centre of their stations) and stations by sign or address.
 */
object LocalSearch {

    /**
     * [provinceName] turns an INE province id into a display name; the app
     * passes its own ("A Coruña" rather than the API's "CORUÑA (A)").
     */
    fun search(
        query: String,
        stations: List<Station>,
        limit: Int = 8,
        provinceName: (String) -> String = { id -> title(stations.firstOrNull { it.provinceId == id }?.province ?: id) },
    ): List<Place> {
        val q = BrandCatalog.normalise(query)
        if (q.length < 2) return emptyList()
        val words = q.split(' ')
        val results = mutableListOf<Place>()

        if (q.all(Char::isDigit)) {
            stations.filter { it.postalCode.startsWith(q) }.groupBy { it.postalCode }.entries
                .sortedByDescending { it.value.size }
                .forEach { (code, list) -> results += centre(code, title(list.first().locality), list, Place.Kind.POSTCODE) }
        }

        // Localities and municipalities: the name starts with the query, or every word appears in it.
        val byLocality = stations.groupBy { it.locality.ifBlank { it.municipality } to it.provinceId }
        byLocality.entries
            .filter { (key, _) -> matches(BrandCatalog.normalise(key.first), q, words) }
            .sortedWith(compareBy({ !BrandCatalog.normalise(it.key.first).startsWith(q) }, { -it.value.size }))
            .forEach { (key, list) -> results += centre(title(key.first), provinceName(list.first().provinceId), list, Place.Kind.LOCALITY) }

        // Stations by sign or address.
        stations.asSequence()
            .filter { s -> matches(BrandCatalog.normalise(s.sign + " " + s.address), q, words) }
            .take(limit)
            .forEach { s ->
                results += Place(
                    name = s.sign.ifBlank { s.brand.displayName },
                    detail = listOf(s.address, s.locality).filter { it.isNotBlank() }.joinToString(", ") { title(it) },
                    lat = s.lat, lon = s.lon, provinceId = s.provinceId, kind = Place.Kind.STATION, stationId = s.id,
                )
            }
        return results.distinctBy { it.kind to it.name to it.detail }.take(limit)
    }

    private fun matches(text: String, q: String, words: List<String>): Boolean =
        text.contains(q) || words.all { w -> text.split(' ', ',', '.', '-', '/').any { it.startsWith(w) } }

    private fun centre(name: String, detail: String, stations: List<Station>, kind: Place.Kind) = Place(
        name = name,
        detail = if (detail.isBlank()) "${stations.size}" else "$detail · ${stations.size}",
        lat = stations.map { it.lat }.average(),
        lon = stations.map { it.lon }.average(),
        provinceId = stations.first().provinceId,
        kind = kind,
    )

    /** The API writes places in capitals: "VIGO" → "Vigo", "A ESTRADA" → "A Estrada". */
    fun title(text: String): String =
        if (text.any { it.isLowerCase() }) text
        else text.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }
}
