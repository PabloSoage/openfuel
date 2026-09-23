package com.varuna.openfuel.core.brand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandCatalogTest {
    private val catalog = BrandCatalog.default

    private fun key(sign: String) = catalog.classify(sign).key

    @Test fun `the fifteen most frequent signs of 2026-09-23`() {
        val expected = mapOf(
            "REPSOL" to "repsol", "MOEVE" to "moeve", "CEPSA" to "moeve", "GALP" to "galp",
            "BALLENOIL" to "ballenoil", "PLENERGY" to "plenergy", "SHELL" to "shell",
            "PETROPRIX" to "petroprix", "PETRONOR" to "petronor", "BP" to "bp",
            "CARREFOUR" to "carrefour", "AVIA" to "avia", "Q8" to "q8",
            "ESCLATOIL" to "esclatoil", "BONAREA" to "bonarea",
        )
        expected.forEach { (sign, brand) -> assertEquals(sign, brand, key(sign)) }
    }

    @Test fun `REPSOL variants seen in the data`() {
        listOf(
            "REPSOL LA COSTA", "ES DULANTZI REPSOL", "ESTACION DE SERVICIO REPSOL ** CASI **",
            "REPSOL BUTANO", "repsol", "Repsol Nº ESTACIÓN 97179",
        ).forEach { assertEquals(it, "repsol", key(it)) }
    }

    @Test fun `Cepsa and Moeve are one brand`() = assertEquals(key("E.S. CEPSA"), key("MOEVE"))

    @Test fun `Petronor wins over Repsol when both appear`() = assertEquals("petronor", key("PETRONOR - GRUPO REPSOL"))

    @Test fun `BP only as a word`() {
        assertEquals("bp", key("BP OIL"))
        assertTrue(catalog.classify("BPX GASOLINERA").independent)
    }

    @Test fun `a sign that is not a brand is independent with two initials`() {
        val b = catalog.classify("Nº 10.935")
        assertTrue(b.independent)
        // "º" is not a combining mark, so NFD keeps it and only "N" survives.
        assertEquals("ind:N", b.key)
        val c = catalog.classify("Gasolinera Ávila Sur")
        assertEquals("GA", c.initials)
        assertEquals("independent", c.filterKey)
    }

    @Test fun `normalisation strips accents, case and extra spaces`() =
        assertEquals("ESTACION NUMERO 1", BrandCatalog.normalise("  Estación   número 1 "))

    @Test fun `a stored key rebuilds the brand`() {
        assertEquals("Repsol", catalog.fromKey("repsol", "whatever").displayName)
        assertTrue(catalog.fromKey("ind:GA", "Gasolinera Ávila").independent)
    }
}
