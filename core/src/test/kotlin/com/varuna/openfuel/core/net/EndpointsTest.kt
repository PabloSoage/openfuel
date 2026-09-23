package com.varuna.openfuel.core.net

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EndpointsTest {
    private val base = "https://sedeaplicaciones.minetur.gob.es/ServiciosRESTCarburantes/PreciosCarburantes"

    @Test fun `current by community and province, zero-padded`() {
        assertEquals("$base/EstacionesTerrestres/FiltroCCAA/05", Endpoints.currentCommunity("5"))
        assertEquals("$base/EstacionesTerrestres/FiltroProvincia/36", Endpoints.currentProvince("36"))
        assertEquals("$base/EstacionesTerrestres/", Endpoints.currentAll())
    }

    @Test fun `history uses dd-MM-yyyy`() {
        assertEquals(
            "$base/EstacionesTerrestresHist/FiltroProvinciaProducto/20-09-2026/36/4",
            Endpoints.historyProvinceProduct(LocalDate.of(2026, 9, 20), "36", 4),
        )
        assertEquals("$base/EstacionesTerrestresHist/01-09-2026", Endpoints.historyAll(LocalDate.of(2026, 9, 1)))
    }

    @Test fun `geoportal endpoints`() {
        assertEquals("https://geoportalgasolineras.es/geoportal/rest/773/busquedaEstacion", Endpoints.geoportalStation("773"))
        assertEquals(
            "https://geoportalgasolineras.es/geoportal/rest/773/planesDescuentoEstacion",
            Endpoints.geoportalStationPlans(" 773 "),
        )
    }
}
