package com.varuna.openfuel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionsTest {
    @Test fun `the official lists, verbatim`() {
        assertEquals(19, Regions.communities.size)
        assertEquals(52, Regions.provinces.size)
    }

    @Test fun `Galicia is A Coruña, Lugo, Ourense and Pontevedra`() =
        assertEquals(listOf("15", "27", "32", "36"), Regions.provincesOf("12").map { it.id })

    @Test fun `a community selection expands to its provinces`() =
        assertEquals(setOf("15", "27", "32", "36"), RegionSelection.Communities(setOf("12")).provinceIds())

    @Test fun `territory by community`() {
        assertEquals(Territory.CANARIAS, Territory.fromCommunity("5"))
        assertEquals(Territory.PENINSULA_BALEARES, Territory.fromCommunity("04"))
    }

    @Test fun `every province belongs to a known community`() =
        Regions.provinces.forEach { org.junit.Assert.assertNotNull(it.toString(), Regions.community(it.communityId)) }
}
