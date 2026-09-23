package com.varuna.openfuel.core.net

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Every URL the project talks to, verified on 2026-09-23 (openfuel-docs/research/01, 02). */
object Endpoints {
    const val OFFICIAL_BASE = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes"
    const val GEOPORTAL_BASE = "https://geoportalgasolineras.es/geoportal/rest"
    const val TAX_SCHEDULE =
        "https://raw.githubusercontent.com/PabloSoage/openfuel/main/core/src/main/resources/tax-schedule.json"
    const val MAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun currentAll(): String = "$OFFICIAL_BASE/EstacionesTerrestres/"

    fun currentCommunity(id: String): String = "$OFFICIAL_BASE/EstacionesTerrestres/FiltroCCAA/${pad(id)}"

    fun currentProvince(id: String): String = "$OFFICIAL_BASE/EstacionesTerrestres/FiltroProvincia/${pad(id)}"

    fun historyAll(day: LocalDate): String = "$OFFICIAL_BASE/EstacionesTerrestresHist/${day.format(DAY)}"

    fun historyProvinceProduct(day: LocalDate, provinceId: String, productId: Int): String =
        "$OFFICIAL_BASE/EstacionesTerrestresHist/FiltroProvinciaProducto/${day.format(DAY)}/${pad(provinceId)}/$productId"

    fun geoportalStation(stationId: String): String = "$GEOPORTAL_BASE/${stationId.trim()}/busquedaEstacion"

    fun geoportalStationPlans(stationId: String): String = "$GEOPORTAL_BASE/${stationId.trim()}/planesDescuentoEstacion"

    fun pad(id: String): String = id.trim().padStart(2, '0')

    val JSON_HEADERS: Map<String, String> = mapOf("Accept" to "application/json")
}
