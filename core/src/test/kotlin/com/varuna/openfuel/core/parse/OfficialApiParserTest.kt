package com.varuna.openfuel.core.parse

import com.varuna.openfuel.core.Fixtures
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Territory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OfficialApiParserTest {
    private val snapshot = OfficialApiParser.parse(Fixtures.text("official-current.json"))

    @Test fun `reads through the BOM and the envelope`() {
        assertEquals(LocalDateTime.of(2026, 9, 23, 9, 32, 44), snapshot.publishedAt)
        assertEquals(2, snapshot.stations.size)
    }

    @Test fun `coordinates outside Spain are repaired when swapped and dropped otherwise`() {
        // Tui (16268) came with lat/lon swapped on 2026-09-23; three stations came at 0,0.
        assertEquals(42.037472 to -8.659472, OfficialApiParser.coordinates(-8.659472, 42.037472))
        assertEquals(null, OfficialApiParser.coordinates(0.0, 0.0))
        assertEquals(28.1 to -15.4, OfficialApiParser.coordinates(28.1, -15.4)) // Canarias stays
    }

    @Test fun `a station without coordinates is skipped and counted`() {
        assertEquals(1, snapshot.skipped)
        assertFalse(snapshot.stations.any { it.id == "9999" })
    }

    @Test fun `every field of station 4375 lands where it should`() {
        val s = snapshot.stations.first { it.id == "4375" }
        assertEquals("Nº 10.935", s.sign)
        assertEquals("AVENIDA CASTILLA LA MANCHA, 26", s.address)
        assertEquals("02250", s.postalCode)
        assertEquals("02", s.provinceId)
        assertEquals("07", s.ccaaId)
        assertEquals(39.211417, s.lat, 1e-9)
        assertEquals(-1.539167, s.lon, 1e-9)
        assertEquals("D", s.roadSide)
        assertEquals(Territory.PENINSULA_BALEARES, s.territory)
    }

    @Test fun `only fuels actually sold have a price`() {
        val s = snapshot.stations.first { it.id == "4375" }
        assertEquals(setOf(Fuel.GOA, Fuel.GOB, Fuel.G95E5), s.prices.keys)
        assertEquals(1.879, s.prices.getValue(Fuel.GOA), 1e-12)
    }

    @Test fun `sign is classified into a brand`() {
        assertEquals("repsol", snapshot.stations.first { it.id == "773" }.brand.key)
        assertTrue(snapshot.stations.first { it.id == "4375" }.brand.independent)
    }

    @Test fun `product-filtered history keeps id and price only, empty prices dropped`() {
        val prices = OfficialApiParser.parseProductPrices(Fixtures.text("official-history-product.json"))
        assertEquals(mapOf("773" to 1.859, "16055" to 1.799), prices)
    }

    @Test fun `Fecha without zero-padded hour`() {
        assertEquals(LocalDateTime.of(2026, 9, 20, 0, 0, 0), OfficialApiParser.parseFecha("20/09/2026 0:00:00"))
    }

    @Test(expected = OfficialApiException::class)
    fun `a failed query is an error, not an empty list`() {
        OfficialApiParser.parse("""{"ResultadoConsulta":"ERROR","ListaEESSPrecio":[]}""")
    }

    @Test(expected = OfficialApiException::class)
    fun `HTML instead of JSON is an error`() {
        OfficialApiParser.parse("<html>maintenance</html>")
    }
}
