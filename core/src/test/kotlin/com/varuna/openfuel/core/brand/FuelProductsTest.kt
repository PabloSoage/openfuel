package com.varuna.openfuel.core.brand

import com.varuna.openfuel.core.model.Fuel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelProductsTest {
    private val products = FuelProducts.default

    @Test fun `the embedded file parses and every brand key exists in the catalog`() {
        val keys = BrandCatalog.default.brands.map { it.key }.toSet()
        assertTrue(products.products.isNotEmpty())
        products.products.forEach { assertTrue("${it.brandKey} is not a catalog brand", it.brandKey in keys) }
    }

    @Test fun `every product has Spanish and English claims of the same length`() {
        products.products.forEach {
            assertEquals(it.name, it.claims.getValue("en").size, it.claims.getValue("es").size)
        }
    }

    @Test fun `Repsol standard and premium diesel are different products`() {
        val standard = products.find("repsol", Fuel.GOA)!!
        val premium = products.find("repsol", Fuel.GOA_PREMIUM)!!
        assertFalse(standard.premium)
        assertTrue(premium.premium)
        assertTrue(standard.name != premium.name)
    }

    @Test fun `a brand without research has no product`() = assertNull(products.find("plenergy", Fuel.GOA))

    @Test fun `claims fall back to English`() =
        assertEquals(products.find("bp", Fuel.GOA)!!.claimsIn("en"), products.find("bp", Fuel.GOA)!!.claimsIn("fr"))

    @Test fun `baseline text exists in both languages`() {
        assertNotNull(products.baseline("es").takeIf { it.contains("EN 590") })
        assertTrue(products.baseline("en").contains("EN 590"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the same brand and fuel twice is rejected`() {
        val p = """{"brand":"x","fuels":["GOA"],"name":"n","claims":{"en":["a"]},"source":"https://a","checked":"2026-01-01"}"""
        FuelProducts.parse("""{"version":1,"products":[$p,$p]}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an unknown fuel is rejected`() {
        FuelProducts.parse(
            """{"version":1,"products":[{"brand":"x","fuels":["DIESEL"],"name":"n","claims":{"en":["a"]},"source":"https://a","checked":"2026-01-01"}]}""",
        )
    }
}
