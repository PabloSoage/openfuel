package com.varuna.openfuel.core.net

import com.varuna.openfuel.core.Fixtures
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.parse.OfficialApiException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OfficialApiTest {
    private val requested = mutableListOf<Pair<String, Map<String, String>>>()

    private fun api(code: Int = 200, body: String = Fixtures.text("official-current.json")) = OfficialApi(
        HttpClient { url, headers ->
            requested += url to headers
            HttpResponse(code, body.toByteArray(), "application/json")
        },
    )

    @Test fun `two provinces are two calls, merged without duplicates`() {
        val snapshot = api().current(RegionSelection.Provinces(setOf("36", "15")))
        assertEquals(2, requested.size)
        assertEquals(listOf("15", "36"), requested.map { it.first.substringAfterLast('/') })
        assertEquals(2, snapshot.stations.size)
        assertEquals(2, snapshot.skipped)
    }

    @Test fun `always asks for JSON`() {
        api().current(RegionSelection.AllSpain)
        assertEquals("application/json", requested.single().second["Accept"])
    }

    @Test(expected = OfficialApiException::class)
    fun `non-200 is an error`() {
        api(code = 503, body = "").current(RegionSelection.AllSpain)
    }

    @Test fun `history of one province and product`() {
        val prices = api(body = Fixtures.text("official-history-product.json"))
            .historyPrices(LocalDate.of(2026, 9, 20), "36", Fuel.GOA)
        assertEquals(1.859, prices.getValue("773"), 0.0)
        org.junit.Assert.assertTrue(requested.single().first.endsWith("/FiltroProvinciaProducto/20-09-2026/36/4"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a fuel without IDProducto has no history`() {
        api().historyPrices(LocalDate.of(2026, 9, 20), "36", Fuel.GASOLINA_RENOVABLE)
    }
}
