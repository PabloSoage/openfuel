package com.varuna.openfuel.core.search

/** Something the user can search for and jump to on the map. */
data class Place(
    val name: String,
    /** Second line: municipality, province, number of stations… */
    val detail: String,
    val lat: Double,
    val lon: Double,
    /** INE province id when known: lets the app offer to add it to the region. */
    val provinceId: String?,
    val kind: Kind,
    /** For [Kind.STATION], the station to open. */
    val stationId: String? = null,
) {
    enum class Kind { STATION, LOCALITY, POSTCODE, ADDRESS }
}
