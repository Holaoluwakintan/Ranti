package com.holaoluwakintan.ranti.core

data class ParsedDate(val month: Int, val day: Int, val year: Int?)

/** Contact apps store birthdays in many shapes. This understands the common ones. */
object ContactDates {
    private val noYear = Regex("^--(\\d{2})-?(\\d{2})")
    private val iso = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val compact = Regex("^(\\d{4})(\\d{2})(\\d{2})$")
    private val dmy = Regex("^(\\d{1,2})[./](\\d{1,2})[./](\\d{4})$")
    private val dmNoYear = Regex("^(\\d{1,2})[./](\\d{1,2})$")

    fun parse(raw: String): ParsedDate? {
        val s = raw.trim()
        fun mk(m: Int, d: Int, y: Int?): ParsedDate? {
            // Some apps use placeholder years (1604, 1900, 0000) for "no year".
            val year = y?.takeIf { it in 1901..2100 }
            return if (DateMath.isValid(m, d, year)) ParsedDate(m, d, year)
            else if (DateMath.isValid(m, d, null)) ParsedDate(m, d, null) else null
        }
        noYear.find(s)?.let { return mk(it.groupValues[1].toInt(), it.groupValues[2].toInt(), null) }
        iso.find(s)?.let { return mk(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[1].toInt()) }
        compact.find(s)?.let { return mk(it.groupValues[2].toInt(), it.groupValues[3].toInt(), it.groupValues[1].toInt()) }
        // Day first (Nigerian style); if that's impossible, try month first.
        dmy.find(s)?.let {
            val a = it.groupValues[1].toInt(); val b = it.groupValues[2].toInt(); val y = it.groupValues[3].toInt()
            return mk(b, a, y) ?: mk(a, b, y)
        }
        dmNoYear.find(s)?.let {
            val a = it.groupValues[1].toInt(); val b = it.groupValues[2].toInt()
            return mk(b, a, null) ?: mk(a, b, null)
        }
        return null
    }
}
