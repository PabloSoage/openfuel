package com.varuna.openfuel.core.discount

enum class DiscountKind {
    /** `tipoDescuento.id` 1: percentage of the total. */
    PERCENT,

    /** `tipoDescuento.id` 2: euro cents per litre. */
    CENTS_PER_LITRE,

    /** Any other type: shown, never applied. */
    OTHER;

    companion object {
        fun fromTypeId(id: Int?): DiscountKind = when (id) {
            1 -> PERCENT
            2 -> CENTS_PER_LITRE
            else -> OTHER
        }
    }
}

/** A loyalty or payment-card discount, as the geoportal publishes it per station. */
data class DiscountPlan(
    val id: Int,
    val name: String,
    val description: String,
    val amount: Double,
    val kind: DiscountKind,
    val kindLabel: String,
    /** `tipoDestinatario.id`; 1 is "Todos los consumidores". */
    val audienceId: Int?,
    val audienceLabel: String,
    val operatorId: Int?,
    val operatorName: String,
) {
    val appliesToEveryone: Boolean get() = audienceId == EVERYONE

    /** Price per litre after this plan, or null when the kind is unknown. */
    fun applyTo(price: Double): Double? = when (kind) {
        DiscountKind.PERCENT -> price * (1 - amount / 100.0)
        DiscountKind.CENTS_PER_LITRE -> price - amount / 100.0
        DiscountKind.OTHER -> null
    }

    companion object {
        const val EVERYONE = 1
    }
}

data class EffectivePrice(val price: Double, val plan: DiscountPlan?)

/**
 * The best price the user gets with the plans they hold. One plan at a time:
 * nothing in the data says plans stack, so they are assumed not to. Plans for
 * restricted audiences (fleets, professionals) are never applied on their own.
 */
object EffectivePriceCalculator {
    fun best(price: Double, plansAtStation: List<DiscountPlan>, ownedPlanIds: Set<Int>): EffectivePrice {
        val best = plansAtStation
            .asSequence()
            .filter { it.id in ownedPlanIds && it.appliesToEveryone }
            .mapNotNull { plan -> plan.applyTo(price)?.let { it to plan } }
            .minByOrNull { it.first }
        return if (best != null && best.first < price) EffectivePrice(best.first, best.second) else EffectivePrice(price, null)
    }
}
