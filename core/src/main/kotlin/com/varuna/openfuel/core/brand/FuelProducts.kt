package com.varuna.openfuel.core.brand

import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.parse.array
import com.varuna.openfuel.core.parse.int
import com.varuna.openfuel.core.parse.obj
import com.varuna.openfuel.core.parse.string
import com.varuna.openfuel.core.parse.withoutBom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/**
 * What a brand says about one of its fuels: commercial name and additive
 * claims, with the page they come from. Marketing statements, not verified.
 */
data class FuelProduct(
    val brandKey: String,
    val fuels: Set<Fuel>,
    val name: String,
    val premium: Boolean,
    /** Language tag ("en", "es") → claims. */
    val claims: Map<String, List<String>>,
    val source: String,
    val checked: LocalDate,
) {
    /** Claims in [language], falling back to English. */
    fun claimsIn(language: String): List<String> = claims[language] ?: claims["en"].orEmpty()
}

/** `core/src/main/resources/fuel-products.json`, shared with the web. */
class FuelProducts(
    val version: Int,
    val products: List<FuelProduct>,
    private val baseline: Map<String, String>,
) {
    private val index: Map<Pair<String, Fuel>, FuelProduct> =
        products.flatMap { p -> p.fuels.map { (p.brandKey to it) to p } }.toMap()

    fun find(brandKey: String, fuel: Fuel): FuelProduct? = index[brandKey to fuel]

    fun baseline(language: String): String = baseline[language] ?: baseline["en"].orEmpty()

    companion object {
        fun parse(json: String): FuelProducts {
            val root = Json.parseToJsonElement(json.withoutBom()).jsonObject
            val version = requireNotNull(root.int("version")) { "version missing" }
            val baseline = root.obj("baseline")?.mapValues { (_, v) -> (v as JsonPrimitive).content }.orEmpty()
            val seen = mutableSetOf<Pair<String, Fuel>>()
            val products = requireNotNull(root.array("products")) { "products missing" }.map { element ->
                val o = element as JsonObject
                fun req(key: String) = requireNotNull(o.string(key)?.takeIf { it.isNotBlank() }) { "$key missing in ${o["name"]}" }
                val brand = req("brand")
                val fuels = requireNotNull(o.array("fuels")) { "fuels missing" }.map { f ->
                    val name = (f as JsonPrimitive).content
                    requireNotNull(Fuel.fromName(name)) { "unknown fuel $name" }
                }.toSet()
                fuels.forEach { require(seen.add(brand to it)) { "$brand/$it listed twice" } }
                val claims = requireNotNull(o.obj("claims")) { "claims missing" }
                    .mapValues { (_, v) -> (v as kotlinx.serialization.json.JsonArray).map { (it as JsonPrimitive).content } }
                require(claims["en"].orEmpty().isNotEmpty()) { "${req("name")}: English claims missing" }
                FuelProduct(
                    brandKey = brand,
                    fuels = fuels,
                    name = req("name"),
                    premium = (o["premium"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    claims = claims,
                    source = req("source").also { require(it.startsWith("https://")) { "source must be https: $it" } },
                    checked = LocalDate.parse(req("checked")),
                )
            }
            return FuelProducts(version, products, baseline)
        }

        val default: FuelProducts by lazy {
            val text = FuelProducts::class.java.getResourceAsStream("/fuel-products.json")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("fuel-products.json missing from :core resources")
            parse(text)
        }
    }
}
