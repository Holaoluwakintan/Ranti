package com.holaoluwakintan.ranti

import com.holaoluwakintan.ranti.core.*
import com.holaoluwakintan.ranti.reminders.ReminderEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

class DateMathTest {
    private val lagos: ZoneId = ZoneId.of("Africa/Lagos")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(lagos)

    @Test fun nextOccurrence_laterThisYear() {
        assertEquals(LocalDate.of(2026, 10, 11), DateMath.nextOccurrence(10, 11, null, true, LocalDate.of(2026, 10, 6)))
    }

    @Test fun nextOccurrence_todayCountsAsToday() {
        val d = DateMath.nextOccurrence(10, 6, 1990, true, LocalDate.of(2026, 10, 6))
        assertEquals(LocalDate.of(2026, 10, 6), d)
        assertEquals(0L, DateMath.daysUntil(LocalDate.of(2026, 10, 6), d!!))
    }

    @Test fun nextOccurrence_passedRollsToNextYear() {
        assertEquals(LocalDate.of(2027, 1, 3), DateMath.nextOccurrence(1, 3, null, true, LocalDate.of(2026, 10, 6)))
    }

    @Test fun yearEnd_wrap() {
        val today = LocalDate.of(2026, 12, 30)
        val d = DateMath.nextOccurrence(1, 2, null, true, today)!!
        assertEquals(LocalDate.of(2027, 1, 2), d)
        assertEquals(3L, DateMath.daysUntil(today, d))
    }

    @Test fun feb29_nonLeapYearFallsOnFeb28() {
        assertEquals(LocalDate.of(2027, 2, 28), DateMath.nextOccurrence(2, 29, 2000, true, LocalDate.of(2026, 10, 6)))
        assertEquals(LocalDate.of(2028, 2, 29), DateMath.nextOccurrence(2, 29, 2000, true, LocalDate.of(2027, 3, 1)))
        // On Feb 28 of a non-leap year, it is "today".
        assertEquals(LocalDate.of(2027, 2, 28), DateMath.nextOccurrence(2, 29, null, true, LocalDate.of(2027, 2, 28)))
    }

    @Test fun feb29_ageCountsCalendarYears() {
        assertEquals(27, DateMath.yearsOn(2000, LocalDate.of(2027, 2, 28)))
        assertEquals(28, DateMath.yearsOn(2000, LocalDate.of(2028, 2, 29)))
    }

    @Test fun yearUnknown_noAge() {
        assertNull(DateMath.yearsOn(null, LocalDate.of(2026, 10, 11)))
        assertNull(DateMath.yearsOn(2030, LocalDate.of(2026, 10, 11)))
    }

    @Test fun validation() {
        assertTrue(DateMath.isValid(2, 29, null))
        assertTrue(DateMath.isValid(2, 29, 2024))
        assertFalse(DateMath.isValid(2, 29, 2023))
        assertFalse(DateMath.isValid(2, 30, null))
        assertFalse(DateMath.isValid(4, 31, null))
        assertTrue(DateMath.isValid(12, 31, 1999))
        assertFalse(DateMath.isValid(13, 1, null))
        assertFalse(DateMath.isValid(1, 0, null))
    }

    @Test fun oneTimeEvent_passedReturnsNull_yearUnknownRepeats() {
        assertNull(DateMath.nextOccurrence(3, 1, 2026, false, LocalDate.of(2026, 10, 6)))
        assertEquals(LocalDate.of(2026, 12, 12), DateMath.nextOccurrence(12, 12, 2026, false, LocalDate.of(2026, 10, 6)))
        assertEquals(LocalDate.of(2027, 3, 1), DateMath.nextOccurrence(3, 1, null, false, LocalDate.of(2026, 10, 6)))
    }

    @Test fun ordinal() {
        assertEquals("1st", DateMath.ordinal(1)); assertEquals("2nd", DateMath.ordinal(2)); assertEquals("3rd", DateMath.ordinal(3))
        assertEquals("11th", DateMath.ordinal(11)); assertEquals("12th", DateMath.ordinal(12)); assertEquals("13th", DateMath.ordinal(13))
        assertEquals("21st", DateMath.ordinal(21)); assertEquals("29th", DateMath.ordinal(29)); assertEquals("102nd", DateMath.ordinal(102))
    }
}

class LadderTest {
    private val lagos: ZoneId = ZoneId.of("Africa/Lagos")
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(lagos)
    private val longAgo = 0L
    private fun tolu(created: Long = longAgo) = LadderInput(1, 10, 11, 1997, true, created)

    @Test fun rungsAt7_5_1_andDayMorning_inLagos() {
        val r = Ladder.rungs(1, LocalDate.of(2026, 10, 11), lagos)
        assertEquals(listOf(at("2026-10-04T09:00"), at("2026-10-06T09:00"), at("2026-10-10T09:00"), at("2026-10-11T07:00")), r.map { it.at })
        assertEquals("+01:00", r[0].at.offset.id) // Lagos is UTC+1 all year, no DST
    }

    @Test fun nextAlarm_isNextRung() {
        val next = Ladder.nextAlarm(listOf(tolu()), at("2026-10-06T10:00"), emptySet())
        assertEquals(at("2026-10-10T09:00"), next)
    }

    @Test fun due_showsRungAtItsTime_once() {
        val now = at("2026-10-06T09:00:30")
        val due = Ladder.due(listOf(tolu()), now, emptySet())
        assertEquals(1, due.size); assertEquals(Stage.FIVE, due[0].stage)
        assertTrue(Ladder.due(listOf(tolu()), now, setOf(due[0].key)).isEmpty())
    }

    @Test fun due_missedRungStillShown_withinGrace_thenDropped() {
        // phone was off at 9am; turned on at 6pm the same day -> still shown
        assertEquals(Stage.FIVE, Ladder.due(listOf(tolu()), at("2026-10-06T18:00"), emptySet()).single().stage)
        // next morning 7am (22h later) -> too late for the 5-day nudge
        assertTrue(Ladder.due(listOf(tolu()), at("2026-10-07T07:00"), emptySet()).isEmpty())
    }

    @Test fun dayRung_shownAllDay_notAfter() {
        assertEquals(Stage.DAY, Ladder.due(listOf(tolu()), at("2026-10-11T22:30"), emptySet()).single().stage)
        assertTrue(Ladder.due(listOf(tolu()), at("2026-10-12T00:10"), emptySet()).isEmpty())
    }

    @Test fun severalMissed_onlyLatestShown() {
        val due = Ladder.due(listOf(tolu()), at("2026-10-11T08:00"), emptySet())
        assertEquals(listOf(Stage.DAY), due.map { it.stage })
    }

    @Test fun addedLate_noBackfilledNudges() {
        // Added on Oct 6 at 10:00 -> the 9:00 five-day rung that morning is not shown
        val created = at("2026-10-06T10:00").toInstant().toEpochMilli()
        assertTrue(Ladder.due(listOf(tolu(created)), at("2026-10-06T10:01"), emptySet()).isEmpty())
        assertEquals(at("2026-10-10T09:00"), Ladder.nextAlarm(listOf(tolu(created)), at("2026-10-06T10:01"), emptySet()))
    }

    @Test fun afterTheDay_nextAlarmIsNextYear() {
        val sent = Ladder.rungs(1, LocalDate.of(2026, 10, 11), lagos).map { it.key }.toSet()
        val next = Ladder.nextAlarm(listOf(tolu()), at("2026-10-11T07:05"), sent)
        assertEquals(at("2027-10-04T09:00"), next)
    }

    @Test fun feb29Person_ladderInNonLeapYear() {
        val p = LadderInput(2, 2, 29, null, true, 0)
        val next = Ladder.nextAlarm(listOf(p), at("2027-02-20T12:00"), emptySet())
        assertEquals(at("2027-02-21T09:00"), next) // 7 days before Feb 28
        val day = Ladder.rungs(2, LocalDate.of(2027, 2, 28), lagos).last()
        assertEquals(at("2027-02-28T07:00"), day.at)
    }

    @Test fun ladderAcrossNewYear() {
        val p = LadderInput(3, 1, 2, null, true, 0)
        assertEquals(at("2026-12-26T09:00"), Ladder.nextAlarm(listOf(p), at("2026-12-20T12:00"), emptySet()))
    }

    @Test fun currentStage() {
        assertNull(Ladder.currentStage(12)); assertEquals(Stage.WEEK, Ladder.currentStage(7)); assertEquals(Stage.WEEK, Ladder.currentStage(6))
        assertEquals(Stage.FIVE, Ladder.currentStage(5)); assertEquals(Stage.FIVE, Ladder.currentStage(2)); assertEquals(Stage.ONE, Ladder.currentStage(1))
        assertEquals(Stage.DAY, Ladder.currentStage(0))
    }

    @Test fun oneTimePassedEvent_noReminders() {
        val e = LadderInput(4, 3, 1, 2026, false, 0)
        assertNull(Ladder.nextAlarm(listOf(e), at("2026-10-06T12:00"), emptySet()))
    }
}

class NightlyTest {
    private val lagos: ZoneId = ZoneId.of("Africa/Lagos")
    private fun at(s: String): ZonedDateTime = LocalDateTime.parse(s).atZone(lagos)

    @Test fun beforeWindow_pickTonightBetween8and10() {
        repeat(200) { i ->
            val t = ReminderEngine.pickNightly(at("2026-10-06T12:00"), 20, 22, false, Random(i))
            assertTrue(t.toString(), !t.isBefore(at("2026-10-06T20:00")) && t.isBefore(at("2026-10-06T22:00")))
        }
    }

    @Test fun insideWindow_pickRestOfTonight() {
        repeat(100) { i ->
            val t = ReminderEngine.pickNightly(at("2026-10-06T21:00"), 20, 22, false, Random(i))
            assertTrue(t.toString(), t.isAfter(at("2026-10-06T21:01")) && t.isBefore(at("2026-10-06T22:00")))
        }
    }

    @Test fun alreadyAskedOrAfterWindow_pickTomorrow() {
        val a = ReminderEngine.pickNightly(at("2026-10-06T20:30"), 20, 22, true, Random(1))
        assertEquals(LocalDate.of(2026, 10, 7), a.toLocalDate())
        val b = ReminderEngine.pickNightly(at("2026-10-06T23:00"), 20, 22, false, Random(1))
        assertEquals(LocalDate.of(2026, 10, 7), b.toLocalDate()); assertTrue(b.hour in 20..21)
    }

    @Test fun windowEndingAtMidnight() {
        repeat(50) { i ->
            val t = ReminderEngine.pickNightly(at("2026-10-06T12:00"), 22, 24, false, Random(i))
            assertTrue(t.hour in 22..23 && t.toLocalDate() == LocalDate.of(2026, 10, 6))
        }
    }
}

class TextTest {
    @Test fun whatsappNumbers() {
        assertEquals("2348031234567", Phone.forWhatsApp("0803 123 4567"))
        assertEquals("2348031234567", Phone.forWhatsApp("+234 803 123 4567"))
        assertEquals("2348031234567", Phone.forWhatsApp("8031234567"))
        assertEquals("447700900123", Phone.forWhatsApp("0044 7700 900123"))
        assertEquals("", Phone.forWhatsApp(""))
    }

    @Test fun templatesFillName_andAge() {
        val m = Messages.draft(OccasionType.BIRTHDAY, Tone.WARM, 0, "Tolu", years = 29)
        assertTrue(m, m.contains("Tolu") && m.contains("29th") && !m.contains("{"))
        Tone.entries.forEach { t ->
            for (i in 0..5) {
                val s = Messages.draft(OccasionType.ANNIVERSARY, t, i, "Ada")
                assertFalse(s, s.contains("{")); assertTrue(s.contains("Ada"))
                val b = Messages.draft(OccasionType.BIRTHDAY, t, i, "Ada")
                assertFalse(b, b.contains("{"))
            }
        }
    }

    @Test fun contactDateFormats() {
        assertEquals(ParsedDate(3, 14, null), ContactDates.parse("--03-14"))
        assertEquals(ParsedDate(3, 14, null), ContactDates.parse("--0314"))
        assertEquals(ParsedDate(3, 14, 1990), ContactDates.parse("1990-03-14"))
        assertEquals(ParsedDate(3, 14, 1990), ContactDates.parse("1990-03-14T00:00:00.000Z"))
        assertEquals(ParsedDate(3, 14, 1990), ContactDates.parse("19900314"))
        assertEquals(ParsedDate(3, 14, 1990), ContactDates.parse("14/03/1990"))
        assertEquals(ParsedDate(3, 14, null), ContactDates.parse("1604-03-14"))
        assertEquals(ParsedDate(2, 29, null), ContactDates.parse("2023-02-29"))
        assertNull(ContactDates.parse("tomorrow"))
    }
}
