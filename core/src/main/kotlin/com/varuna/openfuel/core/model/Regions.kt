package com.varuna.openfuel.core.model

data class Community(val id: String, val officialName: String)

data class Province(val id: String, val officialName: String, val communityId: String)

/**
 * The Ministry's own lists, fetched on 2026-09-23 from
 * `/Listados/ComunidadesAutonomas/` and `/Listados/Provincias/` and copied
 * verbatim, so the region picker works offline on first launch. Official
 * spellings are kept as data (e.g. "Andalucia" without the accent); the app
 * shows localised names.
 */
object Regions {
    val communities: List<Community> = listOf(
        Community("01", "Andalucia"),
        Community("02", "Aragón"),
        Community("03", "Asturias"),
        Community("04", "Baleares"),
        Community("05", "Canarias"),
        Community("06", "Cantabria"),
        Community("07", "Castilla la Mancha"),
        Community("08", "Castilla y León"),
        Community("09", "Cataluña"),
        Community("10", "Comunidad Valenciana"),
        Community("11", "Extremadura"),
        Community("12", "Galicia"),
        Community("13", "Madrid"),
        Community("14", "Murcia"),
        Community("15", "Navarra"),
        Community("16", "País Vasco"),
        Community("17", "Rioja (La)"),
        Community("18", "Ceuta"),
        Community("19", "Melilla"),
    )

    val provinces: List<Province> = listOf(
        Province("01", "ARABA/ÁLAVA", "16"),
        Province("02", "ALBACETE", "07"),
        Province("03", "ALICANTE", "10"),
        Province("04", "ALMERÍA", "01"),
        Province("05", "ÁVILA", "08"),
        Province("06", "BADAJOZ", "11"),
        Province("07", "BALEARS (ILLES)", "04"),
        Province("08", "BARCELONA", "09"),
        Province("09", "BURGOS", "08"),
        Province("10", "CÁCERES", "11"),
        Province("11", "CÁDIZ", "01"),
        Province("12", "CASTELLÓN / CASTELLÓ", "10"),
        Province("13", "CIUDAD REAL", "07"),
        Province("14", "CÓRDOBA", "01"),
        Province("15", "CORUÑA (A)", "12"),
        Province("16", "CUENCA", "07"),
        Province("17", "GIRONA", "09"),
        Province("18", "GRANADA", "01"),
        Province("19", "GUADALAJARA", "07"),
        Province("20", "GIPUZKOA", "16"),
        Province("21", "HUELVA", "01"),
        Province("22", "HUESCA", "02"),
        Province("23", "JAÉN", "01"),
        Province("24", "LEÓN", "08"),
        Province("25", "LLEIDA", "09"),
        Province("26", "RIOJA (LA)", "17"),
        Province("27", "LUGO", "12"),
        Province("28", "MADRID", "13"),
        Province("29", "MÁLAGA", "01"),
        Province("30", "MURCIA", "14"),
        Province("31", "NAVARRA", "15"),
        Province("32", "OURENSE", "12"),
        Province("33", "ASTURIAS", "03"),
        Province("34", "PALENCIA", "08"),
        Province("35", "PALMAS (LAS)", "05"),
        Province("36", "PONTEVEDRA", "12"),
        Province("37", "SALAMANCA", "08"),
        Province("38", "SANTA CRUZ DE TENERIFE", "05"),
        Province("39", "CANTABRIA", "06"),
        Province("40", "SEGOVIA", "08"),
        Province("41", "SEVILLA", "01"),
        Province("42", "SORIA", "08"),
        Province("43", "TARRAGONA", "09"),
        Province("44", "TERUEL", "02"),
        Province("45", "TOLEDO", "07"),
        Province("46", "VALENCIA / VALÈNCIA", "10"),
        Province("47", "VALLADOLID", "08"),
        Province("48", "BIZKAIA", "16"),
        Province("49", "ZAMORA", "08"),
        Province("50", "ZARAGOZA", "02"),
        Province("51", "CEUTA", "18"),
        Province("52", "MELILLA", "19"),
    )

    fun community(id: String): Community? = communities.firstOrNull { it.id == pad(id) }

    fun province(id: String): Province? = provinces.firstOrNull { it.id == pad(id) }

    fun provincesOf(communityId: String): List<Province> = provinces.filter { it.communityId == pad(communityId) }

    fun pad(id: String): String = id.trim().padStart(2, '0')
}

/** What the user chose to download. */
sealed interface RegionSelection {
    data object AllSpain : RegionSelection
    data class Communities(val ids: Set<String>) : RegionSelection
    data class Provinces(val ids: Set<String>) : RegionSelection

    /** Every province covered, for history requests and database clean-up. */
    fun provinceIds(): Set<String> = when (this) {
        AllSpain -> Regions.provinces.map { it.id }.toSet()
        is Communities -> ids.flatMap { Regions.provincesOf(it) }.map { it.id }.toSet()
        is Provinces -> ids.map { Regions.pad(it) }.toSet()
    }
}
