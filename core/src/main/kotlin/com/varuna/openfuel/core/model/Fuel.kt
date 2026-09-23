package com.varuna.openfuel.core.model

/**
 * Excise category of a fuel: the epígrafe of Ley 38/1992, art. 50, Tarifa 1.ª.
 * Only the categories the app can break down are listed; see
 * `tax-schedule.json` for their rates.
 */
enum class TaxCategory(val epigrafe: String) {
    PETROL_98("1.2.1"),
    PETROL_95("1.2.2"),
    DIESEL("1.3"),
    BIODIESEL("1.14");

    companion object {
        fun fromEpigrafe(code: String): TaxCategory? = entries.firstOrNull { it.epigrafe == code }
    }
}

/**
 * One entry per price field of the official API.
 *
 * [apiField] is the exact key in a station object, accents included.
 * [productId] is the `IDProducto` of `/Listados/ProductosPetroliferos/`, needed
 * for the product-filtered history endpoints; null where the catalogue has none.
 * The pairing is written by hand on purpose: the API offers no mapping.
 */
enum class Fuel(
    val apiField: String,
    val productId: Int?,
    val taxCategory: TaxCategory?,
    /** False for fuels sold at a handful of stations nationwide: parsed, not offered in the picker. */
    val pickable: Boolean,
) {
    GOA("Precio Gasoleo A", 4, TaxCategory.DIESEL, true),
    GOA_PREMIUM("Precio Gasoleo Premium", 5, TaxCategory.DIESEL, true),
    G95E5("Precio Gasolina 95 E5", 1, TaxCategory.PETROL_95, true),
    G95E10("Precio Gasolina 95 E10", 23, TaxCategory.PETROL_95, true),
    G95E5_PREMIUM("Precio Gasolina 95 E5 Premium", 20, TaxCategory.PETROL_95, true),
    G98E5("Precio Gasolina 98 E5", 3, TaxCategory.PETROL_98, true),
    G98E10("Precio Gasolina 98 E10", 21, TaxCategory.PETROL_98, true),
    BIODIESEL("Precio Biodiesel", 8, TaxCategory.BIODIESEL, true),
    GOB("Precio Gasoleo B", 6, null, true),
    GLP("Precio Gases licuados del petróleo", 17, null, true),
    GNC("Precio Gas Natural Comprimido", 18, null, true),
    GNL("Precio Gas Natural Licuado", 19, null, true),
    DIESEL_RENOVABLE("Precio Diésel Renovable", 27, null, true),
    ADBLUE("Precio Adblue", 26, null, true),
    G95E85("Precio Gasolina 95 E85", 25, null, true),
    BIOETANOL("Precio Bioetanol", 16, null, true),
    HIDROGENO("Precio Hidrogeno", 22, null, true),
    G95E25("Precio Gasolina 95 E25", 24, null, false),
    GASOLINA_RENOVABLE("Precio Gasolina Renovable", null, null, false),
    BIOGAS_GNC("Precio Biogas Natural Comprimido", null, null, false),
    BIOGAS_GNL("Precio Biogas Natural Licuado", null, null, false);

    companion object {
        val pickable: List<Fuel> = entries.filter { it.pickable }

        fun fromName(name: String?): Fuel? = entries.firstOrNull { it.name == name }
    }
}
