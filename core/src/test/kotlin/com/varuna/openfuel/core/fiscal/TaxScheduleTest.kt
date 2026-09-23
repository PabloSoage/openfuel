package com.varuna.openfuel.core.fiscal

import com.varuna.openfuel.core.model.TaxCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaxScheduleTest {
    private val schedule = TaxSchedule.embedded()

    @Test fun `embedded schedule parses and validates`() {
        assertEquals(7, schedule.periods.size)
        assertTrue(schedule.version >= 1)
    }

    @Test fun `September 2026 diesel is the reduced branch, 179 per 1000 litres`() {
        val sept = schedule.periodAt(LocalDate.of(2026, 9, 23))!!
        assertEquals(0.179, sept.rates.getValue(TaxCategory.DIESEL).perLitre, 1e-12)
        assertEquals(0.42269, sept.rates.getValue(TaxCategory.PETROL_95).perLitre, 1e-12)
        assertEquals(0.45392, sept.rates.getValue(TaxCategory.PETROL_98).perLitre, 1e-12)
        assertEquals(0.21, sept.vat, 0.0)
        assertTrue(sept.confirmed)
    }

    @Test fun `spring 2026 had 10 percent VAT`() {
        val april = schedule.periodAt(LocalDate.of(2026, 4, 15))!!
        assertEquals(0.10, april.vat, 0.0)
        assertEquals(0.330, april.rates.getValue(TaxCategory.DIESEL).perLitre, 1e-12)
    }

    @Test fun `June 2026 is flagged unconfirmed`() = assertFalse(schedule.periodAt(LocalDate.of(2026, 6, 10))!!.confirmed)

    @Test fun `from October the base law, unconfirmed`() {
        val oct = schedule.periodAt(LocalDate.of(2026, 10, 5))!!
        assertEquals(0.379, oct.rates.getValue(TaxCategory.DIESEL).perLitre, 1e-12)
        assertNull(oct.to)
        assertFalse(oct.confirmed)
    }

    @Test fun `before 2026 there is no period`() = assertNull(schedule.periodAt(LocalDate.of(2025, 12, 31)))

    @Test fun `period boundaries are inclusive`() {
        assertEquals(LocalDate.of(2026, 9, 1), schedule.periodAt(LocalDate.of(2026, 9, 30))!!.from)
        assertEquals(LocalDate.of(2026, 10, 1), schedule.periodAt(LocalDate.of(2026, 10, 1))!!.from)
    }

    private fun period(from: String, to: String?, vat: Double = 0.21, source: String = "test") = """
        {"from":"$from","to":${to?.let { "\"$it\"" } ?: "null"},"vat":$vat,"confirmed":true,"source":"$source",
         "rates":{"1.2.1":{"general":1,"especial":1},"1.2.2":{"general":1,"especial":1},
                  "1.3":{"general":1,"especial":1},"1.14":{"general":1,"especial":1}}}
    """.trimIndent()

    private fun schedule(vararg periods: String) = """{"version":2,"updated":"x","periods":[${periods.joinToString(",")}]}"""

    @Test fun `a valid minimal schedule parses`() {
        TaxSchedule.parse(schedule(period("2026-01-01", "2026-01-31"), period("2026-02-01", null)))
    }

    @Test(expected = InvalidTaxScheduleException::class)
    fun `a gap between periods is rejected`() {
        TaxSchedule.parse(schedule(period("2026-01-01", "2026-01-30"), period("2026-02-01", null)))
    }

    @Test(expected = InvalidTaxScheduleException::class)
    fun `overlapping periods are rejected`() {
        TaxSchedule.parse(schedule(period("2026-01-01", "2026-02-05"), period("2026-02-01", null)))
    }

    @Test(expected = InvalidTaxScheduleException::class)
    fun `an open period that is not last is rejected`() {
        TaxSchedule.parse(schedule(period("2026-01-01", null), period("2026-02-01", null)))
    }

    @Test(expected = InvalidTaxScheduleException::class)
    fun `an unusual VAT rate is rejected`() {
        TaxSchedule.parse(schedule(period("2026-01-01", null, vat = 0.19)))
    }

    @Test(expected = InvalidTaxScheduleException::class)
    fun `a period without source is rejected`() {
        TaxSchedule.parse(schedule(period("2026-01-01", null, source = " ")))
    }

    @Test(expected = InvalidTaxScheduleException::class)
    fun `a missing category is rejected`() {
        TaxSchedule.parse(
            """{"version":2,"periods":[{"from":"2026-01-01","to":null,"vat":0.21,"confirmed":true,"source":"x",
                "rates":{"1.3":{"general":1,"especial":1}}}]}""",
        )
    }
}
