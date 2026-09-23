package com.varuna.openfuel.core.fiscal

import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Territory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class FiscalBreakdownTest {
    private val schedule = TaxSchedule.embedded()
    private val today = LocalDate.of(2026, 9, 23)

    private fun available(price: Double, fuel: Fuel, date: LocalDate = today) =
        FiscalBreakdown.of(price, fuel, date, Territory.PENINSULA_BALEARES, schedule) as FiscalBreakdown.Available

    /** The worked example of openfuel-docs/research/03-desglose-fiscal.md §1. */
    @Test fun `diesel at 1,879 on 2026-09-23`() {
        val b = available(1.879, Fuel.GOA)
        assertEquals(0.32611, b.vat, 5e-6)
        assertEquals(0.17900, b.excise, 1e-12)
        assertEquals(1.37389, b.rest, 5e-6)
        assertEquals(0.269, b.taxShare, 5e-4)
        assertEquals(b.price, b.vat + b.excise + b.rest, 1e-12)
    }

    @Test fun `premium diesel shares the diesel rate`() =
        assertEquals(available(1.9, Fuel.GOA).excise, available(1.9, Fuel.GOA_PREMIUM).excise, 0.0)

    @Test fun `98 and 95 have different excise`() =
        assertNotEquals(available(1.9, Fuel.G95E5).excise, available(1.9, Fuel.G98E5).excise, 0.0)

    @Test fun `VAT at 10 percent in April 2026`() {
        val b = available(1.65, Fuel.GOA, LocalDate.of(2026, 4, 10))
        assertEquals(1.65 - 1.65 / 1.10, b.vat, 1e-12)
        assertEquals(0.10, b.vatRate, 0.0)
    }

    @Test fun `June 2026 breakdown carries the unconfirmed flag`() =
        assertFalse(available(1.7, Fuel.GOA, LocalDate.of(2026, 6, 15)).period.confirmed)

    @Test fun `Canarias, Ceuta and Melilla have no breakdown`() {
        listOf(Territory.CANARIAS, Territory.CEUTA, Territory.MELILLA).forEach {
            assertEquals(
                FiscalBreakdown.Unavailable(FiscalBreakdown.Reason.TERRITORY),
                FiscalBreakdown.of(1.5, Fuel.GOA, today, it, schedule),
            )
        }
    }

    @Test fun `LPG has no breakdown (excise per tonne)`() =
        assertEquals(
            FiscalBreakdown.Unavailable(FiscalBreakdown.Reason.PRODUCT),
            FiscalBreakdown.of(0.99, Fuel.GLP, today, Territory.PENINSULA_BALEARES, schedule),
        )

    @Test fun `dates before the schedule have no breakdown`() =
        assertEquals(
            FiscalBreakdown.Unavailable(FiscalBreakdown.Reason.NO_PERIOD),
            FiscalBreakdown.of(1.5, Fuel.GOA, LocalDate.of(2025, 6, 1), Territory.PENINSULA_BALEARES, schedule),
        )
}
