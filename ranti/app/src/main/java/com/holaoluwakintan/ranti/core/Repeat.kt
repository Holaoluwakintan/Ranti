package com.holaoluwakintan.ranti.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** How a reminder repeats. Pure date math, no Android, unit tested. */
enum class Repeat(val label: String, val short: String) {
    NONE("Once", "Once"),
    DAILY("Every day", "Daily"),
    WEEKDAYS("Every weekday (Mon–Fri)", "Weekdays"),
    WEEKLY("Every week", "Weekly"),
    MONTHLY("Every month", "Monthly"),
    YEARLY("Every year", "Yearly");

    companion object {
        fun from(s: String?): Repeat = entries.firstOrNull { it.name == s } ?: NONE
    }
}

object Repeats {
    private fun clampDay(ym: YearMonth, day: Int): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))

    /** The occurrence right after [from] (same time of day). [anchorDay] keeps "the 31st" across short months. */
    fun step(from: LocalDateTime, repeat: Repeat, anchorDay: Int = 0): LocalDateTime? {
        val t = from.toLocalTime()
        val anchor = if (anchorDay in 1..31) anchorDay else from.dayOfMonth
        return when (repeat) {
            Repeat.NONE -> null
            Repeat.DAILY -> from.plusDays(1)
            Repeat.WEEKLY -> from.plusWeeks(1)
            Repeat.WEEKDAYS -> {
                var d = from.toLocalDate().plusDays(1)
                while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
                d.atTime(t)
            }
            Repeat.MONTHLY -> clampDay(YearMonth.from(from).plusMonths(1), anchor).atTime(t)
            Repeat.YEARLY -> {
                val ym = YearMonth.of(from.year + 1, from.month)
                clampDay(ym, anchor).atTime(t)
            }
        }
    }

    /**
     * The first occurrence strictly after [now], starting from [due]. Returns [due] itself when it is
     * still ahead, and null for a one-off reminder whose time has passed.
     */
    fun nextAfter(due: LocalDateTime, repeat: Repeat, now: LocalDateTime, anchorDay: Int = 0): LocalDateTime? {
        if (due.isAfter(now)) return due
        if (repeat == Repeat.NONE) return null
        var d = due
        var guard = 0
        while (!d.isAfter(now) && guard < 4000) {
            d = step(d, repeat, anchorDay) ?: return null
            guard++
        }
        return d
    }

    /** Weekdays-only repeats should never start on a weekend. */
    fun firstValid(start: LocalDateTime, repeat: Repeat): LocalDateTime {
        if (repeat != Repeat.WEEKDAYS) return start
        var d = start
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
        return d
    }
}
