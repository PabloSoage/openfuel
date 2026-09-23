package com.varuna.openfuel.core.brand

import com.varuna.openfuel.core.model.Brand
import com.varuna.openfuel.core.parse.array
import com.varuna.openfuel.core.parse.string
import com.varuna.openfuel.core.parse.withoutBom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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
 * The entries are data (`core/src/main/resources/brands.json`), shared with
 * the web and the enrichment script so the three classify signs alike.
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

        /** Parses `brands.json`; a malformed entry fails loudly, it is our own file. */
        fun parse(json: String): BrandCatalog {
            val root = Json.parseToJsonElement(json.withoutBom()).jsonObject
            val brands = requireNotNull(root.array("brands")) { "brands missing" }
            return BrandCatalog(
                brands.map { element ->
                    val o = element as JsonObject
                    fun req(key: String) = requireNotNull(o.string(key)) { "$key missing in $o" }
                    Entry(
                        Brand(
                            key = req("key"),
                            displayName = req("name"),
                            color = argb(req("color")),
                            textColor = argb(req("textColor")),
                            initials = req("initials"),
                            website = (o["website"] as? JsonPrimitive)?.contentOrNull,
                            independent = false,
                            logoUrl = (o["logoUrl"] as? JsonPrimitive)?.contentOrNull,
                            preferLogoUrl = (o["preferLogoUrl"] as? JsonPrimitive)?.booleanOrNull ?: false,
                        ),
                        requireNotNull(o.array("patterns")) { "patterns missing in $o" }
                            .map { Regex((it as JsonPrimitive).content) },
                    )
                },
            )
        }

        /** "#RRGGBB" → opaque ARGB. */
        private fun argb(hex: String): Long = 0xFF000000 or hex.removePrefix("#").toLong(16)

        val default: BrandCatalog by lazy {
            val text = BrandCatalog::class.java.getResourceAsStream("/brands.json")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("brands.json missing from :core resources")
            parse(text)
        }
    }
}
