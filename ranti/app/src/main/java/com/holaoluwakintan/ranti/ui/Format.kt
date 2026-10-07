package com.holaoluwakintan.ranti.ui

import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.data.Occasion
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    private val loc: Locale get() = Locale.getDefault()
    fun long(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", loc))
    fun short(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEE d MMM", loc))
    fun dayMonth(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", loc))
    fun header(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", loc)).uppercase(loc)

    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 0..4 -> "Good night"
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    /** "Turns 29 · Sunday, 11 October" or "Birthday · Sunday, 11 October". */
    fun subtitle(o: Occasion, occ: LocalDate): String {
        val years = DateMath.yearsOn(o.year, occ)
        val lead = when {
            years != null && o.kind == OccasionType.BIRTHDAY -> "Turns $years"
            years != null && (o.kind == OccasionType.ANNIVERSARY || o.kind == OccasionType.WEDDING) && o.recurring -> "${DateMath.ordinal(years)} ${o.title.lowercase()}"
            else -> o.title
        }
        return "$lead · ${long(occ)}"
    }

    fun savedDate(o: Occasion): String = "${o.day} ${months[o.month - 1]}" + (o.year?.let { " $it" } ?: "")

    fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
        val mins = ((now - millis) / 60_000L).coerceAtLeast(0)
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "$mins min ago"
            mins < 24 * 60 -> "${mins / 60} h ago"
            else -> "${mins / (24 * 60)} days ago"
        }
    }

    fun countdown(days: Long): String = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> "In $days days"
    }
}
