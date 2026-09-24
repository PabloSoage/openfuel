package com.varuna.openfuel.core.search

import com.varuna.openfuel.core.net.HttpClient
import com.varuna.openfuel.core.parse.obj
import com.varuna.openfuel.core.parse.string
import com.varuna.openfuel.core.parse.withoutBom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import java.net.URLEncoder

/**
 * Addresses and places anywhere in Spain through OpenStreetMap's Nominatim.
 * Its usage policy asks for an identifying User-Agent, at most one request
 * per second and no autocomplete: the app calls it only when the user
 * submits a search. https://operations.osmfoundation.org/policies/nominatim/
 */
class Nominatim(private val http: HttpClient) {

    /** Null on failure, so the UI can tell "no results" from "no answer". */
    fun search(query: String, language: String): List<Place>? = runCatching {
        val r = http.get(url(query, language), mapOf("Accept" to "application/json"))
        if (r.isSuccess) parse(r.text()) else null
    }.getOrNull()

    companion object {
        const val BASE = "https://nominatim.openstreetmap.org/search"

        fun url(query: String, language: String): String =
            "$BASE?format=jsonv2&addressdetails=1&countrycodes=es&limit=6" +
                "&accept-language=${URLEncoder.encode(language, "UTF-8")}" +
                "&q=${URLEncoder.encode(query.trim(), "UTF-8")}"

        fun parse(body: String): List<Place> {
            val array = runCatching { Json.parseToJsonElement(body.withoutBom()).jsonArray }.getOrNull() ?: return emptyList()
            return array.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val lat = o.string("lat")?.toDoubleOrNull() ?: return@mapNotNull null
                val lon = o.string("lon")?.toDoubleOrNull() ?: return@mapNotNull null
                val display = o.string("display_name").orEmpty()
                val address = o.obj("address")
                Place(
                    name = o.string("name")?.takeIf { it.isNotBlank() } ?: display.substringBefore(','),
                    detail = display.substringAfter(", ", "").removeSuffix(", España").removeSuffix(", Spain"),
                    lat = lat,
                    lon = lon,
                    provinceId = Provinces.of(
                        address?.string("postcode"),
                        address?.string("ISO3166-2-lvl6"),
                        address?.string("ISO3166-2-lvl4"),
                    ),
                    kind = Place.Kind.ADDRESS,
                )
            }
        }
    }
}
