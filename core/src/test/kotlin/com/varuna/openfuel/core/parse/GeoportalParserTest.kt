package com.varuna.openfuel.core.parse

import com.varuna.openfuel.core.Fixtures
import com.varuna.openfuel.core.discount.DiscountKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoportalParserTest {
    @Test fun `imagenEESS decodes to a PNG`() {
        val png = GeoportalParser.stationLogoPng(Fixtures.text("geoportal-station-with-logo.json"))
        assertNotNull(png)
        assertTrue(GeoportalParser.isPng(png!!))
    }

    @Test fun `null imagenEESS is no logo`() =
        assertNull(GeoportalParser.stationLogoPng(Fixtures.text("geoportal-station-without-logo.json")))

    @Test fun `XML or garbage is no logo, not a crash`() {
        assertNull(GeoportalParser.stationLogoPng("<EstacionVO><id>773</id></EstacionVO>"))
        assertNull(GeoportalParser.stationLogoPng("""{"imagenEESS":"bm90IGEgcG5n"}"""))
    }

    @Test fun `plans read with their kind, audience and operator`() {
        val plans = GeoportalParser.discountPlans(Fixtures.text("geoportal-plans.json"))
        assertEquals(listOf(102, 144, 145, 900), plans.map { it.id })
        val maxima = plans.first { it.id == 102 }
        assertEquals("Repsol Máxima", maxima.name)
        assertEquals(2.0, maxima.amount, 0.0)
        assertEquals(DiscountKind.PERCENT, maxima.kind)
        assertEquals(18, maxima.operatorId)
        assertTrue(maxima.appliesToEveryone)
        assertEquals(DiscountKind.CENTS_PER_LITRE, plans.first { it.id == 144 }.kind)
        val fleet = plans.first { it.id == 900 }
        assertEquals(DiscountKind.OTHER, fleet.kind)
        assertEquals(4, fleet.audienceId)
    }

    @Test fun `plans that are not a JSON array are an empty list`() =
        assertTrue(GeoportalParser.discountPlans("<List><item/></List>").isEmpty())
}
