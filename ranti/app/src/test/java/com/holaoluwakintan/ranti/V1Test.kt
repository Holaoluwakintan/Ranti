package com.holaoluwakintan.ranti

import com.holaoluwakintan.ranti.core.*
import com.holaoluwakintan.ranti.data.BackupCodec
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.data.REMINDERS_CREATE_SQL
import com.holaoluwakintan.ranti.data.Reminder
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** v1.0: quick add, repeats, backup, the reminders migration, custom ladder times, the widget rows. */
class V1Test {
    private val now = LocalDateTime.of(2026, 10, 6, 15, 0) // Tuesday 3pm

    private fun p(s: String) = QuickParse.parse(s, now)

    // ---------- quick add: people's days ----------
    @Test fun quickBirthdays() {
        p("Tolu's birthday 12 March").let {
            assertEquals(QuickResult.Kind.OCCASION, it.kind); assertEquals("Tolu", it.title)
            assertEquals(OccasionType.BIRTHDAY, it.type); assertEquals(3, it.month); assertEquals(12, it.day); assertNull(it.year); assertTrue(it.complete)
        }
        p("mum bday march 3 1960").let { assertEquals("Mum", it.title); assertEquals(3, it.month); assertEquals(3, it.day); assertEquals(1960, it.year) }
        p("Ada & Kunle anniversary 25/10/2019").let {
            assertEquals("Ada & Kunle", it.title); assertEquals(OccasionType.ANNIVERSARY, it.type)
            assertEquals(10, it.month); assertEquals(25, it.day); assertEquals(2019, it.year)
        }
        p("Kemi's wedding 12 Dec 2026").let { assertEquals("Kemi", it.title); assertEquals(OccasionType.WEDDING, it.type); assertEquals(12, it.month); assertEquals(2026, it.year) }
        p("my sister's birthday is tomorrow").let { assertEquals("Sister", it.title); assertEquals(10, it.month); assertEquals(7, it.day) }
        p("Pastor Femi birthday 14th of October").let { assertEquals("Pastor Femi", it.title); assertEquals(10, it.month); assertEquals(14, it.day) }
        p("Dami's wedding anniversary 3/6").let { assertEquals(OccasionType.ANNIVERSARY, it.type); assertEquals(6, it.month); assertEquals(3, it.day) }
        p("Tolu birthday").let { assertEquals(QuickResult.Kind.OCCASION, it.kind); assertFalse(it.complete); assertEquals(0, it.month) }
        p("   ").let { assertEquals(QuickResult.Kind.EMPTY, it.kind) }
    }

    // ---------- quick add: reminders ----------
    @Test fun quickReminders() {
        p("call dad tomorrow 6pm").let {
            assertEquals(QuickResult.Kind.REMINDER, it.kind); assertEquals("Call dad", it.title)
            assertEquals(LocalDateTime.of(2026, 10, 7, 18, 0), it.at); assertEquals(Repeat.NONE, it.repeat)
        }
        p("Pay rent every month on the 1st").let {
            assertEquals("Pay rent", it.title); assertEquals(Repeat.MONTHLY, it.repeat); assertEquals(1, it.anchorDay)
            assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), it.at)
        }
        p("gym every monday 7am").let { assertEquals("Gym", it.title); assertEquals(Repeat.WEEKLY, it.repeat); assertEquals(LocalDateTime.of(2026, 10, 12, 7, 0), it.at) }
        p("take medicine daily 8:30").let { assertEquals("Take medicine", it.title); assertEquals(Repeat.DAILY, it.repeat); assertEquals(LocalDateTime.of(2026, 10, 7, 8, 30), it.at) }
        p("in 30 minutes check the rice").let { assertEquals("Check the rice", it.title); assertEquals(LocalDateTime.of(2026, 10, 6, 15, 30), it.at); assertTrue(it.timeGiven) }
        p("meeting friday 2pm").let { assertEquals("Meeting", it.title); assertEquals(LocalDateTime.of(2026, 10, 9, 14, 0), it.at) }
        p("remind me to buy cake for Tolu's birthday on Friday").let {
            assertEquals(QuickResult.Kind.REMINDER, it.kind); assertEquals("Buy cake for Tolu's birthday", it.title)
            assertEquals(LocalDateTime.of(2026, 10, 9, 9, 0), it.at)
        }
        p("church meeting tonight").let { assertEquals("Church meeting", it.title); assertEquals(LocalDateTime.of(2026, 10, 6, 20, 0), it.at) }
        p("drink water every day 10am").let { assertEquals(Repeat.DAILY, it.repeat); assertEquals(LocalDateTime.of(2026, 10, 7, 10, 0), it.at) }
        p("standup weekdays 9am").let { assertEquals(Repeat.WEEKDAYS, it.repeat); assertEquals(LocalDateTime.of(2026, 10, 7, 9, 0), it.at) }
        p("doctor 14/10 at 11").let { assertEquals("Doctor", it.title); assertEquals(LocalDateTime.of(2026, 10, 14, 11, 0), it.at) }
        p("6.30pm dinner with Ada").let { assertEquals("Dinner with Ada", it.title); assertEquals(LocalDateTime.of(2026, 10, 6, 18, 30), it.at) }
        p("Pay NEPA bill on 25th").let { assertEquals("Pay NEPA bill", it.title); assertEquals(LocalDateTime.of(2026, 10, 25, 9, 0), it.at) }
        p("buy bread").let { assertEquals("Buy bread", it.title); assertNull(it.at); assertFalse(it.complete) }
        p("tuesday 9am team call").let { assertEquals("Team call", it.title); assertEquals(LocalDateTime.of(2026, 10, 13, 9, 0), it.at) } // today's 9am passed -> next week
    }

    // ---------- repeats ----------
    @Test fun repeatMath() {
        val jan31 = LocalDateTime.of(2026, 1, 31, 10, 0)
        val feb = Repeats.step(jan31, Repeat.MONTHLY, 31)!!
        assertEquals(LocalDateTime.of(2026, 2, 28, 10, 0), feb)
        assertEquals(LocalDateTime.of(2026, 3, 31, 10, 0), Repeats.step(feb, Repeat.MONTHLY, 31))
        val fri = LocalDateTime.of(2026, 10, 9, 8, 0)
        assertEquals(LocalDateTime.of(2026, 10, 12, 8, 0), Repeats.step(fri, Repeat.WEEKDAYS))
        assertEquals(LocalDateTime.of(2026, 10, 7, 8, 0), Repeats.nextAfter(LocalDateTime.of(2026, 10, 1, 8, 0), Repeat.DAILY, now))
        assertNull(Repeats.nextAfter(LocalDateTime.of(2026, 10, 1, 8, 0), Repeat.NONE, now))
        val leap = LocalDateTime.of(2028, 2, 29, 9, 0)
        val y1 = Repeats.step(leap, Repeat.YEARLY, 29)!!
        assertEquals(LocalDate.of(2029, 2, 28), y1.toLocalDate())
        assertEquals(LocalDate.of(2032, 2, 29), Repeats.step(Repeats.step(Repeats.step(y1, Repeat.YEARLY, 29)!!, Repeat.YEARLY, 29)!!, Repeat.YEARLY, 29)!!.toLocalDate())
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0), Repeats.firstValid(LocalDateTime.of(2026, 10, 10, 9, 0), Repeat.WEEKDAYS))
    }

    // ---------- backup ----------
    @Test fun backupRoundTripAndMerge() {
        val occ = listOf(
            Occasion(id = 5, name = "Tolu Adeyemi", month = 10, day = 11, year = 1997, phone = "0803", email = "t@x.com", autoEmail = true, giftIdea = "Shoes", createdAt = 1),
            Occasion(id = 6, name = "Ada & Kunle", type = OccasionType.ANNIVERSARY.name, month = 6, day = 3, year = null, createdAt = 2),
        )
        val rem = listOf(Reminder(id = 9, title = "Pay rent", dueAt = 1_800_000_000_000L, repeat = "MONTHLY", anchorDay = 1))
        val json = BackupCodec.encode(BackupCodec.Content(occ, rem, BackupCodec.Settings(userName = "Michael", dayTimeMin = 480)), now = 42L)
        val back = BackupCodec.decode(json)
        assertEquals(2, back.occasions.size); assertEquals(1, back.reminders.size)
        assertEquals("Tolu Adeyemi", back.occasions[0].name); assertEquals(1997, back.occasions[0].year); assertTrue(back.occasions[0].autoEmail)
        assertNull(back.occasions[1].year); assertEquals("ANNIVERSARY", back.occasions[1].type)
        assertEquals("MONTHLY", back.reminders[0].repeat); assertEquals(1, back.reminders[0].anchorDay)
        assertEquals("Michael", back.settings!!.userName); assertEquals(480, back.settings!!.dayTimeMin); assertEquals(42L, back.exportedAt)
        // restoring onto a phone that already has Tolu adds only Ada & Kunle and the reminder, with fresh ids
        val (o, r) = BackupCodec.merge(listOf(occ[0].copy(id = 1)), emptyList(), back)
        assertEquals(listOf("Ada & Kunle"), o.map { it.name }); assertEquals(0L, o[0].id); assertEquals(1, r.size)
        // a second restore adds nothing
        val (o2, r2) = BackupCodec.merge(occ, rem, back)
        assertTrue(o2.isEmpty()); assertTrue(r2.isEmpty())
    }

    @Test fun backupRejectsOtherFiles() {
        val e1 = runCatching { BackupCodec.decode("hello") }.exceptionOrNull()
        assertTrue(e1 is IllegalArgumentException)
        val e2 = runCatching { BackupCodec.decode("{\"format\":\"something\"}") }.exceptionOrNull()
        assertTrue(e2 is IllegalArgumentException)
        val e3 = runCatching { BackupCodec.decode("{\"format\":\"ranti-backup\",\"version\":99}") }.exceptionOrNull()
        assertTrue(e3!!.message!!.contains("newer"))
        // bad rows are skipped, not fatal
        val ok = BackupCodec.decode("{\"format\":\"ranti-backup\",\"version\":1,\"occasions\":[{\"name\":\"\",\"month\":1,\"day\":1},{\"name\":\"X\",\"month\":2,\"day\":30},{\"name\":\"Y\",\"month\":2,\"day\":29}]}")
        assertEquals(listOf("Y"), ok.occasions.map { it.name })
    }

    // ---------- database upgrade v0.3 (schema 2) -> v1.0 (schema 3) ----------
    @Test fun migrationAddsRemindersAndKeepsPeople() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { c ->
            c.createStatement().use { s ->
                s.execute("CREATE TABLE `occasions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL)")
                s.execute("INSERT INTO occasions(name, month, day) VALUES ('Tolu', 10, 11)")
                s.execute(REMINDERS_CREATE_SQL)
                s.execute(REMINDERS_CREATE_SQL) // IF NOT EXISTS: safe to run twice
                val cols = ArrayList<Triple<String, String, Int>>()
                s.executeQuery("PRAGMA table_info(reminders)").use { rs -> while (rs.next()) cols += Triple(rs.getString("name"), rs.getString("type"), rs.getInt("notnull")) }
                assertEquals(listOf("id", "title", "note", "dueAt", "repeat", "anchorDay", "done", "firedAt", "occasionId", "createdAt"), cols.map { it.first })
                assertTrue(cols.all { it.third == 1 })
                assertEquals(setOf("INTEGER", "TEXT"), cols.map { it.second }.toSet())
                s.executeQuery("SELECT name FROM occasions").use { rs -> assertTrue(rs.next()); assertEquals("Tolu", rs.getString(1)) }
                s.execute("INSERT INTO reminders(title, note, dueAt, `repeat`, anchorDay, done, firedAt, occasionId, createdAt) VALUES ('Pay rent', '', 1, 'MONTHLY', 1, 0, 0, 0, 0)")
                s.executeQuery("SELECT COUNT(*) FROM reminders").use { rs -> rs.next(); assertEquals(1, rs.getInt(1)) }
            }
        }
    }

    // ---------- custom ladder times ----------
    @Test fun ladderUsesCustomTimes() {
        val zone = ZoneId.of("Africa/Lagos")
        val times = LadderTimes(prep = LocalTime.of(18, 0), day = LocalTime.of(6, 30))
        val rs = Ladder.rungs(1, LocalDate.of(2026, 10, 11), zone, Ladder.ALL, times)
        assertEquals(LocalTime.of(6, 30), rs.first { it.stage == Stage.DAY }.at.toLocalTime())
        assertEquals(LocalTime.of(18, 0), rs.first { it.stage == Stage.WEEK }.at.toLocalTime())
        // default stays 9:00 / 7:00 for existing users
        val d = Ladder.rungs(1, LocalDate.of(2026, 10, 11), zone)
        assertEquals(LocalTime.of(7, 0), d.first { it.stage == Stage.DAY }.at.toLocalTime())
        val input = LadderInput(1, 10, 11, null, true, 0)
        val next = Ladder.nextAlarm(listOf(input), ZonedDateTime.of(now, zone), emptySet(), Ladder.ALL, times)
        assertEquals(ZonedDateTime.of(2026, 10, 6, 18, 0, 0, 0, zone), next) // 5 days before at 6pm
    }

    // ---------- widget rows ----------
    @Test fun widgetRows() {
        val zone = ZoneId.of("Africa/Lagos")
        val today = LocalDate.of(2026, 10, 6)
        val occ = listOf(
            Occasion(id = 1, name = "Tolu Adeyemi", month = 10, day = 11, year = 1997, createdAt = 0),
            Occasion(id = 2, name = "Mummy", month = 10, day = 6, createdAt = 0),
            Occasion(id = 3, name = "Far Away", month = 3, day = 1, createdAt = 0),
        )
        val rem = listOf(Reminder(id = 1, title = "Pay rent", dueAt = LocalDateTime.of(2026, 10, 6, 18, 0).atZone(zone).toInstant().toEpochMilli()))
        val lines = com.holaoluwakintan.ranti.widget.RantiWidget.lines(occ, rem, today, zone)
        assertEquals(3, lines.size)
        assertTrue(lines[0].title.contains("Mummy") && lines[0].title.contains("today"))
        assertEquals("⏰ Pay rent", lines[1].title); assertEquals("6:00 pm", lines[1].right)
        assertTrue(lines[2].title.contains("Tolu")); assertEquals("Turns 29 · Sun 11 Oct", lines[2].sub); assertEquals("5 days", lines[2].right)
    }
}
