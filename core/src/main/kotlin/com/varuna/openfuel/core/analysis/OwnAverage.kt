package com.varuna.openfuel.core.analysis

/** Today's price against the same station's own recent history. */
object OwnAverage {

    data class Result(
        /** Euro cents per litre; negative means cheaper than usual. */
        val centsVsAverage: Double,
        val average: Double,
        val days: Int,
    )

    /** Null with fewer than [minDays] days of history: too little to call it "usual". */
    fun compare(today: Double, history: List<Double>, minDays: Int = 7): Result? {
        if (history.size < minDays) return null
        val average = history.average()
        return Result((today - average) * 100.0, average, history.size)
    }
}
