package com.varuna.openfuel.core.discount

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectivePriceTest {
    private fun plan(id: Int, amount: Double, kind: DiscountKind, audience: Int = 1) =
        DiscountPlan(id, "p$id", "", amount, kind, "", audience, "", 18, "Repsol")

    private val percent2 = plan(102, 2.0, DiscountKind.PERCENT)
    private val cents3 = plan(144, 3.0, DiscountKind.CENTS_PER_LITRE)

    @Test fun `2 percent of 1,899 is 3,798 cents, better than 3 cents`() {
        val e = EffectivePriceCalculator.best(1.899, listOf(percent2, cents3), setOf(102, 144))
        assertEquals(1.899 * 0.98, e.price, 1e-12)
        assertEquals(102, e.plan!!.id)
    }

    @Test fun `3 cents wins below 1,50 per litre`() {
        val e = EffectivePriceCalculator.best(1.40, listOf(percent2, cents3), setOf(102, 144))
        assertEquals(1.37, e.price, 1e-12)
        assertEquals(144, e.plan!!.id)
    }

    @Test fun `plans the user does not hold are ignored`() {
        val e = EffectivePriceCalculator.best(1.899, listOf(percent2, cents3), setOf(144))
        assertEquals(144, e.plan!!.id)
    }

    @Test fun `no owned plan leaves the pump price`() {
        val e = EffectivePriceCalculator.best(1.899, listOf(percent2), emptySet())
        assertEquals(1.899, e.price, 0.0)
        assertNull(e.plan)
    }

    @Test fun `restricted audiences and unknown kinds are never applied`() {
        val fleet = plan(900, 10.0, DiscountKind.CENTS_PER_LITRE, audience = 4)
        val weird = plan(901, 10.0, DiscountKind.OTHER)
        val e = EffectivePriceCalculator.best(1.899, listOf(fleet, weird), setOf(900, 901))
        assertNull(e.plan)
    }
}
