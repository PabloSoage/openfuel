package com.varuna.openfuel.core.search

import com.varuna.openfuel.core.Fixtures
import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {

    @Test fun `postcode first two digits are the province`() {
        assertEquals("36", Provinces.fromPostcode("36930"))
        assertEquals("02", Provinces.fromPostcode("02250"))
        assertNull(Provinces.fromPostcode("99999"))
        assertNull(Provinces.fromPostcode("3693"))
    }

    @Test fun `ISO province code, then single-province community`() {
        assertEquals("36", Provinces.fromIso("ES-PO", "ES-GA"))
        assertEquals("35", Provinces.fromIso("ES-GC", "ES-CN"))
        assertEquals("28", Provinces.fromIso(null, "ES-MD"))
        assertNull(Provinces.fromIso(null, "ES-GA")) // Galicia has four provinces
        assertEquals("15", Provinces.of("15001", "ES-PO", null)) // the postcode wins
    }

    /** A real answer for "Bueu" on 2026-09-24: one result without postcode, one with. */
    @Test fun `Nominatim results are parsed with their province`() {
        val places = Nominatim.parse(Fixtures.text("nominatim-bueu.json"))
        assertEquals(2, places.size)
        assertTrue(places.all { it.name == "Bueu" && it.provinceId == "36" && it.kind == Place.Kind.ADDRESS })
        assertTrue(places.first().lat in 42.0..43.0 && places.first().lon in -9.0..-8.0)
    }

    @Test fun `Nominatim garbage is an empty list`() = assertTrue(Nominatim.parse("<html/>").isEmpty())

    @Test fun `the URL is encoded and limited to Spain`() {
        val url = Nominatim.url("Rúa do Príncipe, Vigo", "es")
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?"))
        assertTrue("countrycodes=es" in url && "q=R%C3%BAa+do+Pr%C3%ADncipe%2C+Vigo" in url)
    }

    private fun station(
        id: String, sign: String, locality: String, postcode: String, lat: Double,
        address: String = "RÚA X, 1", municipality: String = locality,
    ) = Station(
        id, sign, BrandCatalog.default.classify(sign), address, locality, municipality, "PONTEVEDRA", "36", "12",
        postcode, lat, -8.7, "", "", mapOf(Fuel.GOA to 1.8),
    )

    private val stations = listOf(
        station("1", "REPSOL", "VIGO", "36201", 42.24),
        station("2", "GALP", "VIGO", "36204", 42.22),
        station("3", "PLENERGY", "BUEU", "36930", 42.32, address = "AVENIDA MONTERO RIOS, 5"),
        station("4", "SHELL", "A ESTRADA", "36680", 42.69),
    )

    @Test fun `a locality is placed at the centre of its stations`() {
        val vigo = LocalSearch.search("vigo", stations).first()
        assertEquals(Place.Kind.LOCALITY, vigo.kind)
        assertEquals("Vigo", vigo.name)
        assertEquals(42.23, vigo.lat, 1e-9)
        assertEquals("36", vigo.provinceId)
    }

    @Test fun `accents and case do not matter, and a word prefix is enough`() {
        assertEquals("A Estrada", LocalSearch.search("estrad", stations).first().name)
        assertEquals("Bueu", LocalSearch.search("BUÉU", stations).first().name)
    }

    @Test fun `a postcode finds its stations' area`() {
        val p = LocalSearch.search("3693", stations).first()
        assertEquals(Place.Kind.POSTCODE, p.kind)
        assertEquals("36930", p.name)
    }

    @Test fun `stations are found by address`() {
        val s = LocalSearch.search("montero rios", stations).single { it.kind == Place.Kind.STATION }
        assertEquals("3", s.stationId)
    }

    @Test fun `one letter is not a search`() = assertTrue(LocalSearch.search("v", stations).isEmpty())

    /** Real case of 2026-09-24: the only Bueu station has Localidad SABARIGO and Municipio Bueu. */
    @Test fun `a municipality is found even when the locality is a parish`() {
        val list = listOf(station("5", "SHELL", "SABARIGO", "36938", 42.33, municipality = "Bueu"))
        val bueu = LocalSearch.search("bueu", list).first()
        assertEquals(Place.Kind.LOCALITY, bueu.kind)
        assertEquals("Bueu", bueu.name)
        assertEquals("Sabarigo", LocalSearch.search("sabarigo", list).first().name)
    }

    @Test fun `the same town in capitals and mixed case is one place`() {
        val list = listOf(
            station("6", "REPSOL", "VIGO", "36201", 42.24, municipality = "Vigo"),
            station("7", "GALP", "VIGO", "36204", 42.22, municipality = "Vigo"),
        )
        val towns = LocalSearch.search("vigo", list).filter { it.kind == Place.Kind.LOCALITY }
        assertEquals(1, towns.size)
        assertTrue(towns.single().detail.endsWith("· 2"))
    }
}
