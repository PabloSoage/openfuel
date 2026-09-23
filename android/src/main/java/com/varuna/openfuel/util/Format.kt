package com.varuna.openfuel.util

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Numbers in the device (or per-app) locale: "1,879" in Spanish, "1.879" in English. */
object Format {
    private fun number(decimals: Int): NumberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
    }

    /** €/l with the three decimals stations display. */
    fun price(value: Double): String = number(3).format(value)

    fun euros(value: Double): String = number(3).format(value) + " €"

    fun cents(value: Double): String = number(1).format(value)

    fun percent(share: Double): String = number(1).format(share * 100) + " %"

    fun km(value: Double): String = if (value < 10) number(1).format(value) else number(0).format(value)

    private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")

    fun day(date: LocalDate): String = date.format(DAY)
}
