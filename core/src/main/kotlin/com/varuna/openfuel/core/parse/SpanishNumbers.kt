package com.varuna.openfuel.core.parse

/**
 * The official API writes every number with a decimal comma ("1,879",
 * "-8,024778") and an empty string for "not sold". No thousands separator
 * appears in any field observed on 2026-09-23.
 */
object SpanishNumbers {
    fun parseOrNull(raw: String?): Double? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        return s.replace(',', '.').toDoubleOrNull()
    }
}
