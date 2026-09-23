package com.varuna.openfuel.core.parse

import com.varuna.openfuel.core.discount.DiscountKind
import com.varuna.openfuel.core.discount.DiscountPlan
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.util.Base64

/**
 * Reads the geoportal's internal API (geoportalgasolineras.es/geoportal/rest),
 * requested with `Accept: application/json`. Undocumented: every function here
 * returns null or empty rather than throwing, because the app treats this
 * source as optional enrichment. See openfuel-docs/research/02-api-interna-geoportal.md.
 */
object GeoportalParser {

    /** The brand logo carried by `busquedaEstacion` as `imagenEESS`, or null. */
    fun stationLogoPng(body: String): ByteArray? {
        val o = runCatching { Json.parseToJsonElement(body.withoutBom()).jsonObject }.getOrNull() ?: return null
        val b64 = o.string("imagenEESS")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val bytes = runCatching { Base64.getMimeDecoder().decode(b64) }.getOrNull() ?: return null
        return bytes.takeIf { isPng(it) }
    }

    /** Plans of `planesDescuentoEstacion`; items that cannot be read are skipped. */
    fun discountPlans(body: String): List<DiscountPlan> {
        val array = runCatching { Json.parseToJsonElement(body.withoutBom()).jsonArray }.getOrNull() ?: return emptyList()
        return array.mapNotNull { element -> (element as? JsonObject)?.let { runCatching { plan(it) }.getOrNull() } }
    }

    fun isPng(bytes: ByteArray): Boolean =
        bytes.size > 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()

    private fun plan(o: JsonObject): DiscountPlan {
        val type = o.obj("tipoDescuento")
        val audience = o.obj("tipoDestinatario")
        val operator = o.obj("operador")
        return DiscountPlan(
            id = requireNotNull(o.int("id")) { "id" },
            name = o.string("nombre")?.trim().orEmpty(),
            description = o.string("descripcion")?.trim().orEmpty(),
            amount = o.double("cifraDescuento") ?: 0.0,
            kind = DiscountKind.fromTypeId(type?.int("id")),
            kindLabel = type?.string("tipo").orEmpty(),
            audienceId = audience?.int("id"),
            audienceLabel = audience?.string("tipo").orEmpty(),
            operatorId = operator?.int("id"),
            operatorName = operator?.string("nombre")?.trim().orEmpty(),
        )
    }
}
