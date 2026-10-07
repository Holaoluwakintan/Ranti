package com.holaoluwakintan.ranti.core

import java.time.LocalDate
import java.time.Year
import java.time.temporal.ChronoUnit

enum class OccasionType(val label: String, val emoji: String, val recursByDefault: Boolean) {
    BIRTHDAY("Birthday", "🎂", true),
    ANNIVERSARY("Anniversary", "💍", true),
    WEDDING("Wedding", "💒", false),
    EVENT("Event", "📅", false),
    CUSTOM("Custom", "✨", true);

    companion object {
        fun from(name: String?): OccasionType = entries.firstOrNull { it.name == name } ?: BIRTHDAY
    }
}

/** Pure date math. Everything here is calendar-date based (no time zones). */
object DateMath {

    /** The date an (month, day) falls on in [year]. Feb 29 falls on Feb 28 in non-leap years. */
    fun occurrenceIn(year: Int, month: Int, day: Int): LocalDate =
        if (month == 2 && day == 29 && !Year.isLeap(year.toLong())) LocalDate.of(year, 2, 28)
        else LocalDate.of(year, month, day)

    /** Is this a real calendar date? Year may be unknown (null): then Feb 29 is allowed. */
    fun isValid(month: Int, day: Int, year: Int?): Boolean {
        if (month !in 1..12 || day < 1) return false
        if (year != null && (year < 1900 || year > 2100)) return false
        val max = when (month) {
            2 -> if (year == null || Year.isLeap(year.toLong())) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return day <= max
    }

    /**
     * The next date (today included) this occasion happens.
     * Recurring occasions repeat yearly. A one-time occasion (wedding/event with a known year)
     * happens once; it returns null after it has passed. A one-time occasion with unknown year
     * is treated as yearly, because we can't know which year is meant.
     */
    fun nextOccurrence(month: Int, day: Int, year: Int?, recurring: Boolean, today: LocalDate): LocalDate? {
        if (!recurring && year != null) {
            val d = occurrenceIn(year, month, day)
            return if (d.isBefore(today)) null else d
        }
        val thisYear = occurrenceIn(today.year, month, day)
        return if (thisYear.isBefore(today)) occurrenceIn(today.year + 1, month, day) else thisYear
    }

    /** The occurrence after [occurrence] for a recurring occasion. */
    fun followingOccurrence(month: Int, day: Int, occurrence: LocalDate): LocalDate =
        occurrenceIn(occurrence.year + 1, month, day)

    fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** Age (or years together) reached on [occurrence]; null when the start year is unknown or in the future. */
    fun yearsOn(startYear: Int?, occurrence: LocalDate): Int? {
        if (startYear == null) return null
        val n = occurrence.year - startYear
        return if (n >= 1) n else null
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
        return "$n$suffix"
    }

    fun countdownLabel(days: Long): String = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days < 0 -> "Passed"
        else -> "$days days"
    }
}
