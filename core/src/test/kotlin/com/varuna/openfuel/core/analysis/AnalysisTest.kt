package com.varuna.openfuel.core.analysis

import com.varuna.openfuel.core.brand.BrandCatalog
import com.varuna.openfuel.core.fiscal.TaxSchedule
import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AnalysisTest {
    private val schedule = TaxSchedule.embedded()
    private val day = LocalDate.of(2026, 9, 23)

    private fun station(id: String, price: Double, lat: Double = 42.5, lon: Double = -8.7, ccaa: String = "12") = Station(
        id, "X", BrandCatalog.independent("X"), "", "", "", "", "36", ccaa, "", lat, lon, "", "", mapOf(Fuel.GOA to price),
    )

    @Test fun `10 cents at the pump is 8,26 cents of margin`() {
        val cheap = station("a", 1.80)
        val dear = station("b", 1.90, lat = 42.51)
        val r = RelativeMargin.compute(dear, Fuel.GOA, listOf(cheap, dear), 10.0, day, schedule)!!
        assertEquals(10.0 / 1.21, r.centsAboveCheapest, 1e-9)
        assertEquals("a", r.cheapest.id)
        assertEquals(2, r.compared)
    }

    @Test fun `stations outside the radius are not compared`() {
        val far = station("far", 1.50, lat = 43.5)
        val target = station("t", 1.90)
        val r = RelativeMargin.compute(target, Fuel.GOA, listOf(far, target), 10.0, day, schedule)!!
        assertEquals(0.0, r.centsAboveCheapest, 1e-12)
        assertEquals("t", r.cheapest.id)
    }

    @Test fun `no breakdown, no comparison`() {
        val canarias = station("c", 1.3, ccaa = "05")
        assertNull(RelativeMargin.compute(canarias, Fuel.GOA, listOf(canarias), 10.0, day, schedule))
    }

    @Test fun `own average needs a week of history`() {
        assertNull(OwnAverage.compare(1.85, List(6) { 1.80 }))
        val r = OwnAverage.compare(1.85, List(10) { 1.80 })!!
        assertEquals(5.0, r.centsVsAverage, 1e-9)
        assertEquals(10, r.days)
    }
}
