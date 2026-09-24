package com.varuna.openfuel.core.search

/**
 * Which province a searched place is in, from what Nominatim returns:
 * the postcode (its first two digits are the INE province id), else the
 * ISO 3166-2 province code (`ISO3166-2-lvl6`, e.g. ES-PO), else the
 * community code (`ISO3166-2-lvl4`) for the single-province communities.
 */
object Provinces {

    /** ISO 3166-2:ES province subdivision → INE province id. */
    private val ISO_PROVINCE = mapOf(
        "ES-VI" to "01", "ES-AB" to "02", "ES-A" to "03", "ES-AL" to "04", "ES-AV" to "05", "ES-BA" to "06",
        "ES-PM" to "07", "ES-B" to "08", "ES-BU" to "09", "ES-CC" to "10", "ES-CA" to "11", "ES-CS" to "12",
        "ES-CR" to "13", "ES-CO" to "14", "ES-C" to "15", "ES-CU" to "16", "ES-GI" to "17", "ES-GR" to "18",
        "ES-GU" to "19", "ES-SS" to "20", "ES-H" to "21", "ES-HU" to "22", "ES-J" to "23", "ES-LE" to "24",
        "ES-L" to "25", "ES-LO" to "26", "ES-LU" to "27", "ES-M" to "28", "ES-MA" to "29", "ES-MU" to "30",
        "ES-NA" to "31", "ES-OR" to "32", "ES-O" to "33", "ES-P" to "34", "ES-GC" to "35", "ES-PO" to "36",
        "ES-SA" to "37", "ES-TF" to "38", "ES-S" to "39", "ES-SG" to "40", "ES-SE" to "41", "ES-SO" to "42",
        "ES-T" to "43", "ES-TE" to "44", "ES-TO" to "45", "ES-V" to "46", "ES-VA" to "47", "ES-BI" to "48",
        "ES-ZA" to "49", "ES-Z" to "50", "ES-CE" to "51", "ES-ML" to "52",
    )

    /** Communities with a single province, which Nominatim may give without a province code. */
    private val ISO_SINGLE_PROVINCE_COMMUNITY = mapOf(
        "ES-AS" to "33", "ES-CB" to "39", "ES-MD" to "28", "ES-MC" to "30", "ES-NC" to "31",
        "ES-RI" to "26", "ES-IB" to "07", "ES-CE" to "51", "ES-ML" to "52",
    )

    fun fromPostcode(postcode: String?): String? {
        val digits = postcode?.trim()?.takeIf { it.length == 5 && it.all(Char::isDigit) } ?: return null
        return digits.take(2).takeIf { it.toInt() in 1..52 }
    }

    fun fromIso(provinceCode: String?, communityCode: String?): String? =
        provinceCode?.let { ISO_PROVINCE[it.trim().uppercase()] }
            ?: communityCode?.let { ISO_SINGLE_PROVINCE_COMMUNITY[it.trim().uppercase()] }

    fun of(postcode: String?, provinceCode: String?, communityCode: String?): String? =
        fromPostcode(postcode) ?: fromIso(provinceCode, communityCode)
}
