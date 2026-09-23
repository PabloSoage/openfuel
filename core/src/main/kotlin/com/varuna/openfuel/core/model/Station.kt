package com.varuna.openfuel.core.model

/**
 * A normalised brand. [key] is stable and used as a database key and as the
 * map icon id. Independent stations get `ind:XX`, XX being two letters of
 * their sign, so they share badges without sharing a brand filter entry.
 */
data class Brand(
    val key: String,
    val displayName: String,
    /** ARGB, e.g. 0xFFFF6A13. */
    val color: Long,
    val textColor: Long,
    val initials: String,
    /** Host used for the favicon fallback, or null when not confidently known. */
    val website: String?,
    val independent: Boolean,
) {
    /** Key used by the brand filter: every independent station is one entry. */
    val filterKey: String get() = if (independent) INDEPENDENT_FILTER_KEY else key

    companion object {
        const val INDEPENDENT_FILTER_KEY = "independent"
    }
}

/** Tax territory, decided by the autonomous community. */
enum class Territory {
    PENINSULA_BALEARES,
    CANARIAS,
    CEUTA,
    MELILLA;

    companion object {
        fun fromCommunity(ccaaId: String): Territory = when (ccaaId.trim().padStart(2, '0')) {
            "05" -> CANARIAS
            "18" -> CEUTA
            "19" -> MELILLA
            else -> PENINSULA_BALEARES
        }
    }
}

data class Station(
    /** `IDEESS`; the same id space as the geoportal. */
    val id: String,
    /** `Rótulo`, trimmed, as written by the station. */
    val sign: String,
    val brand: Brand,
    val address: String,
    val locality: String,
    val municipality: String,
    val province: String,
    val provinceId: String,
    val ccaaId: String,
    val postalCode: String,
    val lat: Double,
    val lon: Double,
    val schedule: String,
    /** `Margen`: D, I or N. */
    val roadSide: String,
    /** €/litre. A fuel that is absent is not sold here. */
    val prices: Map<Fuel, Double>,
) {
    val territory: Territory get() = Territory.fromCommunity(ccaaId)
}
