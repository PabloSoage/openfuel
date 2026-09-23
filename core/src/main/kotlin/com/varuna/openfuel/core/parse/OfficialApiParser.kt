package com.varuna.openfuel.core.parse

import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Station
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class OfficialApiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One response of the official API, already parsed. */
data class OfficialSnapshot(
    /** `Fecha` of the response: local Spanish time, no zone. */
    val publishedAt: LocalDateTime?,
    val stations: List<Station>,
    /** Station objects that could not be read (no id or no coordinates). */
    val skipped: Int,
)

/**
 * Reads `ServiciosRESTCarburantes` responses.
 *
 * Keys are looked up by their exact string — `Rótulo`, `Longitud (WGS84)`,
 * `C.P.` — because they carry accents, spaces and punctuation. See
 * openfuel-docs/research/01-api-oficial.md §3 for every field.
 */
object OfficialApiParser {
    // "23/09/2026 9:32:44": the hour is not zero-padded.
    private val FECHA: DateTimeFormatter = DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss")

    fun parse(body: String, catalog: BrandCatalog = BrandCatalog.default): OfficialSnapshot {
        val root = envelope(body)
        val list = root.array("ListaEESSPrecio") ?: throw OfficialApiException("ListaEESSPrecio missing")
        var skipped = 0
        val stations = list.mapNotNull { element ->
            val parsed = (element as? JsonObject)?.let { runCatching { readStation(it, catalog) }.getOrNull() }
            if (parsed == null) skipped++
            parsed
        }
        return OfficialSnapshot(parseFecha(root.string("Fecha")), stations, skipped)
    }

    /**
     * Reads a product-filtered response (`…FiltroProvinciaProducto…`), where the
     * price is the single field `PrecioProducto`. Only ids and prices are
     * needed for history, so nothing else is required to be present.
     */
    fun parseProductPrices(body: String): Map<String, Double> {
        val list = envelope(body).array("ListaEESSPrecio") ?: throw OfficialApiException("ListaEESSPrecio missing")
        return buildMap {
            for (element in list) {
                val o = element as? JsonObject ?: continue
                val id = o.string("IDEESS")?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val price = SpanishNumbers.parseOrNull(o.string("PrecioProducto"))?.takeIf { it > 0 } ?: continue
                put(id, price)
            }
        }
    }

    fun parseFecha(raw: String?): LocalDateTime? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDateTime.parse(it, FECHA) }.getOrNull() }

    private fun envelope(body: String): JsonObject {
        val root = try {
            Json.parseToJsonElement(body.withoutBom()).jsonObject
        } catch (e: Exception) {
            throw OfficialApiException("Response is not a JSON object", e)
        }
        val result = root.string("ResultadoConsulta")
        if (result != null && result != "OK") throw OfficialApiException("ResultadoConsulta=$result")
        return root
    }

    private fun readStation(o: JsonObject, catalog: BrandCatalog): Station {
        val id = requireNotNull(o.string("IDEESS")?.trim()?.takeIf { it.isNotEmpty() }) { "IDEESS" }
        val lat = requireNotNull(SpanishNumbers.parseOrNull(o.string("Latitud"))) { "Latitud" }
        val lon = requireNotNull(SpanishNumbers.parseOrNull(o.string("Longitud (WGS84)"))) { "Longitud" }
        val prices = buildMap {
            for (fuel in Fuel.entries) {
                val price = SpanishNumbers.parseOrNull(o.string(fuel.apiField)) ?: continue
                if (price > 0) put(fuel, price)
            }
        }
        val sign = o.string("Rótulo")?.trim().orEmpty()
        return Station(
            id = id,
            sign = sign,
            brand = catalog.classify(sign),
            address = o.string("Dirección")?.trim().orEmpty(),
            locality = o.string("Localidad")?.trim().orEmpty(),
            municipality = o.string("Municipio")?.trim().orEmpty(),
            province = o.string("Provincia")?.trim().orEmpty(),
            provinceId = o.string("IDProvincia")?.trim().orEmpty(),
            ccaaId = o.string("IDCCAA")?.trim().orEmpty(),
            postalCode = o.string("C.P.")?.trim().orEmpty(),
            lat = lat,
            lon = lon,
            schedule = o.string("Horario")?.trim().orEmpty(),
            roadSide = o.string("Margen")?.trim().orEmpty(),
            prices = prices,
        )
    }
}
