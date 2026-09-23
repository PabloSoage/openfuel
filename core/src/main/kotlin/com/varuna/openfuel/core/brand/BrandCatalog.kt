package com.varuna.openfuel.core.brand

import com.varuna.openfuel.core.model.Brand
import java.text.Normalizer
import java.util.Locale

/**
 * Turns the free-text `Rótulo` into a brand.
 *
 * On 2026-09-23 there were 3,538 distinct signs for 11,486 stations, 55 of
 * them containing REPSOL. Matching is done on a normalised sign (upper case,
 * no accents, single spaces); the first entry whose pattern matches wins, so
 * order matters. Anything unmatched is an independent station.
 *
 * Websites are only filled in where the host is known with confidence: a wrong
 * host would put someone else's logo on the map. The rest fall back to a badge.
 */
class BrandCatalog(private val entries: List<Entry>) {

    data class Entry(val brand: Brand, val patterns: List<Regex>)

    private val byKey: Map<String, Brand> = entries.associate { it.brand.key to it.brand }

    val brands: List<Brand> get() = entries.map { it.brand }

    fun classify(sign: String): Brand {
        val normalised = normalise(sign)
        return entries.firstOrNull { entry -> entry.patterns.any { it.containsMatchIn(normalised) } }?.brand
            ?: independent(sign)
    }

    /** Rebuilds a brand from its stored key; independents need their sign. */
    fun fromKey(key: String, sign: String): Brand = byKey[key] ?: independent(sign)

    companion object {
        fun normalise(sign: String): String =
            Normalizer.normalize(sign, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .uppercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
                .trim()

        fun independent(sign: String): Brand {
            val letters = normalise(sign).filter { it in 'A'..'Z' }
            val initials = letters.take(2).ifEmpty { "?" }
            return Brand(
                key = "ind:$initials",
                displayName = sign.ifBlank { "?" },
                color = 0xFF6B7280,
                textColor = 0xFFFFFFFF,
                initials = initials,
                website = null,
                independent = true,
            )
        }

        private fun brand(
            key: String,
            name: String,
            color: Long,
            initials: String,
            website: String?,
            vararg patterns: String,
            textColor: Long = 0xFFFFFFFF,
        ) = Entry(
            Brand(key, name, color, textColor, initials, website, independent = false),
            patterns.map { Regex(it) },
        )

        val default = BrandCatalog(
            listOf(
                // Before REPSOL: some Petronor signs mention Repsol, the group it belongs to.
                brand("petronor", "Petronor", 0xFF00843D, "PN", "www.petronor.com", "\\bPETRONOR\\b"),
                brand("repsol", "Repsol", 0xFFFF6A13, "R", "www.repsol.es", "\\bREPSOL\\b", "\\bCAMPSA\\b"),
                // Cepsa renamed itself Moeve; both signs coexist mid-rebrand.
                brand("moeve", "Moeve (Cepsa)", 0xFF1D4F91, "M", null, "\\bMOEVE\\b", "\\bCEPSA\\b"),
                brand("galp", "Galp", 0xFFFF5F00, "G", "www.galp.com", "\\bGALP\\b"),
                brand("ballenoil", "Ballenoil", 0xFF0069B4, "B", null, "\\bBALLENOIL\\b"),
                brand("plenergy", "Plenergy", 0xFF00A0DF, "PL", null, "\\bPLENERGY\\b"),
                brand("shell", "Shell", 0xFFFFD500, "S", "www.shell.es", "\\bSHELL\\b", textColor = 0xFFDD1D21),
                brand("petroprix", "Petroprix", 0xFFE30613, "PP", null, "\\bPETROPRIX\\b"),
                brand("bp", "BP", 0xFF009A3E, "BP", "www.bp.com", "\\bBP\\b"),
                brand("carrefour", "Carrefour", 0xFF004E9F, "C", "www.carrefour.es", "\\bCARREFOUR\\b"),
                brand("avia", "Avia", 0xFFE3001B, "A", null, "\\bAVIA\\b"),
                brand("q8", "Q8", 0xFF0055A5, "Q8", null, "\\bQ8\\b"),
                brand("esclatoil", "Esclatoil", 0xFF00833E, "E", null, "\\bESCLATOIL\\b"),
                brand("bonarea", "bonÀrea", 0xFF8BC53F, "bA", null, "\\bBON ?AREA\\b"),
                brand("alcampo", "Alcampo", 0xFFE2001A, "AL", null, "\\bALCAMPO\\b"),
                brand("eroski", "Eroski", 0xFFE30613, "ER", null, "\\bEROSKI\\b"),
                brand("disa", "DISA", 0xFF003E7E, "D", null, "\\bDISA\\b"),
            ),
        )
    }
}
