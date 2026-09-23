package com.varuna.openfuel.core.net

import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.parse.OfficialApiException
import com.varuna.openfuel.core.parse.OfficialApiParser
import com.varuna.openfuel.core.parse.OfficialSnapshot
import java.time.LocalDate

/** The Ministry's open-data service: the source of truth for prices. Blocking. */
class OfficialApi(
    private val http: HttpClient,
    private val catalog: BrandCatalog = BrandCatalog.default,
) {
    /** Current prices of the selection; several regions are merged, newest `Fecha` kept. */
    fun current(selection: RegionSelection): OfficialSnapshot {
        val urls = when (selection) {
            RegionSelection.AllSpain -> listOf(Endpoints.currentAll())
            is RegionSelection.Communities -> selection.ids.sorted().map { Endpoints.currentCommunity(it) }
            is RegionSelection.Provinces -> selection.ids.sorted().map { Endpoints.currentProvince(it) }
        }
        val parts = urls.map { OfficialApiParser.parse(fetch(it), catalog) }
        return OfficialSnapshot(
            publishedAt = parts.mapNotNull { it.publishedAt }.maxOrNull(),
            stations = parts.flatMap { it.stations }.distinctBy { it.id },
            skipped = parts.sumOf { it.skipped },
        )
    }

    /** IDEESS → price of [fuel] on [day] in one province (~85 KB per call). */
    fun historyPrices(day: LocalDate, provinceId: String, fuel: Fuel): Map<String, Double> {
        val productId = fuel.productId ?: throw IllegalArgumentException("$fuel has no IDProducto")
        return OfficialApiParser.parseProductPrices(fetch(Endpoints.historyProvinceProduct(day, provinceId, productId)))
    }

    private fun fetch(url: String): String {
        val response = try {
            http.get(url, Endpoints.JSON_HEADERS)
        } catch (e: Exception) {
            throw OfficialApiException("Network error for $url: ${e.message}", e)
        }
        if (!response.isSuccess) throw OfficialApiException("HTTP ${response.code} for $url")
        return response.text()
    }
}
