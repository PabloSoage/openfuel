package com.varuna.openfuel.core.net

import com.varuna.openfuel.core.discount.DiscountPlan
import com.varuna.openfuel.core.parse.GeoportalParser

/**
 * Optional enrichment from the geoportal's internal API. Never throws: a
 * failure is null, so callers can tell "failed" from "no plans".
 */
class GeoportalApi(private val http: HttpClient) {

    fun logoPng(stationId: String): ByteArray? = runCatching {
        val r = http.get(Endpoints.geoportalStation(stationId), Endpoints.JSON_HEADERS)
        if (r.isSuccess) GeoportalParser.stationLogoPng(r.text()) else null
    }.getOrNull()

    fun plans(stationId: String): List<DiscountPlan>? = runCatching {
        val r = http.get(Endpoints.geoportalStationPlans(stationId), Endpoints.JSON_HEADERS)
        if (r.isSuccess) GeoportalParser.discountPlans(r.text()) else null
    }.getOrNull()
}
