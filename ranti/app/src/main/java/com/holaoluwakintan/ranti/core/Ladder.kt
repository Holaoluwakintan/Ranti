package com.holaoluwakintan.ranti.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The countdown ladder: 7, 5 and 1 day before, and the morning of the day (7:00). The day itself can't be switched off. */
enum class Stage(val daysBefore: Int, val short: String) {
    WEEK(7, "7 days"),
    FIVE(5, "5 days"),
    ONE(1, "1 day"),
    DAY(0, "The day");
}

data class LadderReminder(
    val occasionId: Long,
    val stage: Stage,
    val occurrence: LocalDate,
    val at: ZonedDateTime,
) {
    val key: String get() = "$occasionId|$occurrence|${stage.name}"
}

/** Minimal view of an occasion the ladder needs (keeps this file Android-free and testable). */
data class LadderInput(
    val id: Long,
    val month: Int,
    val day: Int,
    val year: Int?,
    val recurring: Boolean,
    val createdAtMillis: Long,
)

/** v1.0: the times the ladder rings (Settings → Reminder times). */
data class LadderTimes(val prep: LocalTime = Ladder.PREP_TIME, val day: LocalTime = Ladder.DAY_TIME)

object Ladder {
    val PREP_TIME: LocalTime = LocalTime.of(9, 0)
    val DAY_TIME: LocalTime = LocalTime.of(7, 0)

    fun timeFor(stage: Stage, times: LadderTimes = LadderTimes()): LocalTime = if (stage == Stage.DAY) times.day else times.prep

    /** All four rungs for one occurrence, in time order. */
    val ALL: Set<Stage> = Stage.entries.toSet()

    fun rungs(id: Long, occurrence: LocalDate, zone: ZoneId, enabled: Set<Stage> = ALL, times: LadderTimes = LadderTimes()): List<LadderReminder> =
        Stage.entries.filter { it in enabled || it == Stage.DAY }.map { st ->
            val date = occurrence.minusDays(st.daysBefore.toLong())
            LadderReminder(id, st, occurrence, ZonedDateTime.of(date, timeFor(st, times), zone))
        }

    /** Rungs for the next occurrence and (for recurring occasions) the one after, so there is always a future alarm. */
    fun upcoming(o: LadderInput, now: ZonedDateTime, enabled: Set<Stage> = ALL, times: LadderTimes = LadderTimes()): List<LadderReminder> {
        val today = now.toLocalDate()
        val next = DateMath.nextOccurrence(o.month, o.day, o.year, o.recurring, today) ?: return emptyList()
        val list = rungs(o.id, next, now.zone, enabled, times).toMutableList()
        if (o.recurring || o.year == null) list += rungs(o.id, DateMath.followingOccurrence(o.month, o.day, next), now.zone, enabled, times)
        // Never remind about a rung whose time passed before the person was even added.
        return list.filter { it.at.toInstant().toEpochMilli() >= o.createdAtMillis }
    }

    /**
     * How late a missed rung may still be shown (phone off, app killed, alarm delayed).
     * Prep rungs: up to 20 hours. The day itself: until the end of that day.
     */
    fun stillUseful(r: LadderReminder, now: ZonedDateTime): Boolean {
        if (now.isBefore(r.at)) return false
        return if (r.stage == Stage.DAY) now.toLocalDate() == r.occurrence
        else now.isBefore(r.at.plusHours(20))
    }

    /** Rungs that should be shown right now (due, still useful, not yet sent). */
    fun due(all: List<LadderInput>, now: ZonedDateTime, sent: Set<String>, enabled: Set<Stage> = ALL, times: LadderTimes = LadderTimes()): List<LadderReminder> =
        all.flatMap { upcoming(it, now, enabled, times) }
            .filter { stillUseful(it, now) && it.key !in sent }
            // If several rungs of the same occasion are due (e.g. phone was off), show only the latest.
            .groupBy { it.occasionId }
            .map { (_, rs) -> rs.maxBy { it.at } }

    /** The next moment an alarm must fire, or null if nothing is ahead. */
    fun nextAlarm(all: List<LadderInput>, now: ZonedDateTime, sent: Set<String>, enabled: Set<Stage> = ALL, times: LadderTimes = LadderTimes()): ZonedDateTime? =
        all.flatMap { upcoming(it, now, enabled, times) }
            .filter { it.at.isAfter(now) && it.key !in sent }
            .minByOrNull { it.at }?.at

    /** Which rung is "current" for the plan screen given days left. */
    fun currentStage(daysLeft: Long): Stage? = when {
        daysLeft < 0 -> null
        daysLeft == 0L -> Stage.DAY
        daysLeft <= 1 -> Stage.ONE
        daysLeft <= 5 -> Stage.FIVE
        daysLeft <= 7 -> Stage.WEEK
        else -> null
    }
}
