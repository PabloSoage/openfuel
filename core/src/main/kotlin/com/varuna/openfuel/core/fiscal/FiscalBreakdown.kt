package com.varuna.openfuel.core.fiscal

import com.varuna.openfuel.core.model.Fuel
import com.varuna.openfuel.core.model.Territory
import java.time.LocalDate

/**
 * What one litre at the pump is made of, Peninsula + Baleares:
 *
 * ```
 * base  = price / (1 + VAT)       excise sits inside the VAT base
 * VAT   = price − base
 * rest  = base − excise           product + logistics + margin
 * ```
 */
sealed interface FiscalBreakdown {

    data class Available(
        val price: Double,
        val vat: Double,
        val excise: Double,
        val rest: Double,
        val vatRate: Double,
        val period: TaxPeriod,
    ) : FiscalBreakdown {
        val taxes: Double get() = vat + excise
        val taxShare: Double get() = taxes / price
    }

    data class Unavailable(val reason: Reason) : FiscalBreakdown

    enum class Reason {
        /** Canarias (IGIC), Ceuta and Melilla (IPSI): no VAT, not modelled. */
        TERRITORY,

        /** A fuel whose excise is per tonne or per GJ, or not researched. */
        PRODUCT,

        /** The date falls outside the schedule. */
        NO_PERIOD,
    }

    companion object {
        fun of(price: Double, fuel: Fuel, date: LocalDate, territory: Territory, schedule: TaxSchedule): FiscalBreakdown {
            if (territory != Territory.PENINSULA_BALEARES) return Unavailable(Reason.TERRITORY)
            val category = fuel.taxCategory ?: return Unavailable(Reason.PRODUCT)
            val period = schedule.periodAt(date) ?: return Unavailable(Reason.NO_PERIOD)
            val rate = period.rates[category] ?: return Unavailable(Reason.PRODUCT)
            val base = price / (1 + period.vat)
            val excise = rate.perLitre
            return Available(
                price = price,
                vat = price - base,
                excise = excise,
                rest = base - excise,
                vatRate = period.vat,
                period = period,
            )
        }
    }
}
