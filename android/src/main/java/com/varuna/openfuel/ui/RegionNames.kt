package com.varuna.openfuel.ui

import com.varuna.openfuel.core.model.Province
import com.varuna.openfuel.core.model.RegionSelection
import com.varuna.openfuel.core.model.Regions

/**
 * Display names. The API writes "Andalucia", "Rioja (La)" or "CORUÑA (A)";
 * these are proper nouns, so one spelling serves both languages.
 */
object RegionNames {
    private val COMMUNITIES = mapOf(
        "01" to "Andalucía", "02" to "Aragón", "03" to "Asturias", "04" to "Illes Balears",
        "05" to "Canarias", "06" to "Cantabria", "07" to "Castilla-La Mancha", "08" to "Castilla y León",
        "09" to "Cataluña", "10" to "Comunitat Valenciana", "11" to "Extremadura", "12" to "Galicia",
        "13" to "Comunidad de Madrid", "14" to "Región de Murcia", "15" to "Navarra", "16" to "País Vasco",
        "17" to "La Rioja", "18" to "Ceuta", "19" to "Melilla",
    )

    private val SMALL_WORDS = setOf("de", "del", "la", "las", "los", "y", "el")

    fun community(id: String): String = COMMUNITIES[Regions.pad(id)] ?: Regions.community(id)?.officialName ?: id

    fun province(province: Province): String {
        // "CORUÑA (A)" -> "A Coruña", "BALEARS (ILLES)" -> "Illes Balears", "PALMAS (LAS)" -> "Las Palmas".
        val raw = province.officialName
        val article = Regex("^(.*)\\(([^)]+)\\)\\s*$").find(raw)
        val reordered = if (article != null) "${article.groupValues[2]} ${article.groupValues[1].trim()}" else raw
        return reordered.lowercase().split(' ').mapIndexed { i, word ->
            if (i > 0 && word in SMALL_WORDS) word
            else word.split('/').joinToString("/") { part -> part.replaceFirstChar { it.titlecase() } }
        }.joinToString(" ")
    }

    fun province(id: String): String = Regions.province(id)?.let { province(it) } ?: id

    /** Short summary for the status line and settings. */
    fun summary(selection: RegionSelection, allSpain: String): String = when (selection) {
        RegionSelection.AllSpain -> allSpain
        is RegionSelection.Communities -> selection.ids.sorted().joinToString(", ") { community(it) }
        is RegionSelection.Provinces -> selection.ids.sorted().joinToString(", ") { province(it) }
    }
}
