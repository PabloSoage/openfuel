package com.varuna.openfuel.core.fiscal

import com.varuna.openfuel.core.model.TaxCategory
import com.varuna.openfuel.core.parse.array
import com.varuna.openfuel.core.parse.double
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

/** Rates of one excise category, in € per 1,000 litres, as the BOE writes them. */
data class ExciseRate(val general: Double, val especial: Double) {
    val perLitre: Double get() = (general + especial) / 1000.0
}

data class TaxPeriod(
    val from: LocalDate,
    /** Inclusive; null for the last, open-ended period. */
    val to: LocalDate?,
    val vat: Double,
    val confirmed: Boolean,
    val source: String,
    val note: String?,
    val rates: Map<TaxCategory, ExciseRate>,
) {
    operator fun contains(date: LocalDate): Boolean = !date.isBefore(from) && (to == null || !date.isAfter(to))
}

class InvalidTaxScheduleException(message: String) : Exception(message)

/**
 * The tax calendar for Peninsula + Baleares. It is data, not code: in 2026 the
 * excise changed every month and some months depended on CPI thresholds
 * (openfuel-docs/research/03-desglose-fiscal.md). The canonical file is
 * `core/src/main/resources/tax-schedule.json`; the app also fetches it from
 * the repository so a tax change is a commit, not a release.
 */
class TaxSchedule(val version: Int, val updated: String, val periods: List<TaxPeriod>) {

    fun periodAt(date: LocalDate): TaxPeriod? = periods.firstOrNull { date in it }

    companion object {
        private val ALLOWED_VAT = setOf(0.04, 0.05, 0.10, 0.21)
        private const val MAX_RATE = 1000.0

        fun embedded(): TaxSchedule {
            val text = TaxSchedule::class.java.getResourceAsStream("/tax-schedule.json")
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalStateException("tax-schedule.json missing from :core resources")
            return parse(text)
        }

        /** Parses and validates; any rule broken rejects the whole file. */
        fun parse(json: String): TaxSchedule {
            val root = try {
                Json.parseToJsonElement(json.withoutBom()).jsonObject
            } catch (e: Exception) {
                throw InvalidTaxScheduleException("not a JSON object: ${e.message}")
            }
            val version = root.int("version") ?: throw InvalidTaxScheduleException("version missing")
            val updated = root.string("updated").orEmpty()
            val periodsJson = root.array("periods") ?: throw InvalidTaxScheduleException("periods missing")
            val periods = periodsJson.mapIndexed { index, element ->
                val o = element as? JsonObject ?: throw InvalidTaxScheduleException("period $index is not an object")
                period(index, o)
            }
            validate(periods)
            return TaxSchedule(version, updated, periods)
        }

        private fun period(index: Int, o: JsonObject): TaxPeriod {
            fun fail(what: String): Nothing = throw InvalidTaxScheduleException("period $index: $what")
            val from = o.string("from")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: fail("bad 'from'")
            val toRaw = o.string("to")
            val to = toRaw?.let { runCatching { LocalDate.parse(it) }.getOrNull() ?: fail("bad 'to'") }
            val vat = o.double("vat") ?: fail("vat missing")
            val confirmed = (o["confirmed"] as? JsonPrimitive)?.booleanOrNull ?: fail("confirmed missing")
            val source = o.string("source")?.trim().orEmpty()
            if (source.isEmpty()) fail("source missing")
            val ratesJson = o.obj("rates") ?: fail("rates missing")
            val rates = TaxCategory.entries.associateWith { category ->
                val r = ratesJson.obj(category.epigrafe) ?: fail("rate ${category.epigrafe} missing")
                val general = r.double("general") ?: fail("rate ${category.epigrafe}.general missing")
                val especial = r.double("especial") ?: fail("rate ${category.epigrafe}.especial missing")
                if (general < 0 || especial < 0 || general + especial > MAX_RATE) fail("rate ${category.epigrafe} out of range")
                ExciseRate(general, especial)
            }
            if (ALLOWED_VAT.none { kotlin.math.abs(it - vat) < 1e-9 }) fail("vat $vat not allowed")
            return TaxPeriod(from, to, vat, confirmed, source, o.string("note"), rates)
        }

        private fun validate(periods: List<TaxPeriod>) {
            if (periods.isEmpty()) throw InvalidTaxScheduleException("no periods")
            periods.forEachIndexed { i, p ->
                val last = i == periods.lastIndex
                if (!last && p.to == null) throw InvalidTaxScheduleException("period $i is open-ended but not last")
                if (last && p.to != null) throw InvalidTaxScheduleException("last period must be open-ended")
                if (p.to != null && p.to.isBefore(p.from)) throw InvalidTaxScheduleException("period $i ends before it starts")
                if (i > 0) {
                    val previousEnd = periods[i - 1].to!!
                    if (p.from != previousEnd.plusDays(1)) {
                        throw InvalidTaxScheduleException("period $i does not start the day after period ${i - 1} ends")
                    }
                }
            }
        }
    }
}
