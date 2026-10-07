package com.holaoluwakintan.ranti

import com.holaoluwakintan.ranti.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class V02Test {
    private val zone = ZoneId.of("Africa/Lagos")

    @Test fun parsesFriendEntries() {
        val json = """{"entries":[
            {"id":3,"name":"Tolu Test","day":11,"month":10,"year":1997,"phone":"08031234567","email":"tolu@example.com","created_at":"x"},
            {"id":4,"name":"Ada","day":29,"month":2,"year":null,"phone":null,"email":null},
            {"id":5,"name":"Broken","day":31,"month":2,"year":null},
            {"id":6,"name":"  ","day":1,"month":1}
        ]}"""
        val e = FriendEntries.parse(json)
        assertEquals(2, e.size)
        assertEquals(FriendEntry(3, "Tolu Test", 11, 10, 1997, "08031234567", "tolu@example.com"), e[0])
        assertEquals(null, e[1].year)
        assertEquals("", e[1].phone)
        assertEquals("", e[1].email)
        // The cursor moves past skipped rows too.
        assertEquals(6L, FriendEntries.maxId(json, 0))
        assertEquals(9L, FriendEntries.maxId("{\"entries\":[]}", 9))
    }

    @Test fun emailsBodyAndSignature() {
        val p = listOf(FriendEntries.AutoEmail(7, "Tolu", "t@x.com", 11, 10, "Happy day!"))
        val body = org.json.JSONObject(FriendEntries.emailsBody("abcd2345ef", "Michael", p))
        assertEquals("abcd2345ef", body.getString("c"))
        assertEquals("Michael", body.getString("name"))
        assertEquals("t@x.com", body.getJSONArray("people").getJSONObject(0).getString("email"))
        val s1 = FriendEntries.signature("Michael", p)
        assertEquals(s1, FriendEntries.signature("Michael", p.toList()))
        assertNotEquals(s1, FriendEntries.signature("Michael", listOf(p[0].copy(message = "Changed"))))
        assertNotEquals(s1, FriendEntries.signature("Mike", p))
    }

    @Test fun emailCheck() {
        assertTrue(FriendEntries.looksLikeEmail("tolu.a@gmail.com"))
        assertFalse(FriendEntries.looksLikeEmail("tolu@gmail"))
        assertFalse(FriendEntries.looksLikeEmail("no at sign"))
    }

    @Test fun linkCodeAndKey() {
        val rnd = java.security.SecureRandom()
        val code = BirthdayLink.newCode(rnd)
        assertTrue(Regex("^[a-z0-9]{8,16}$").matches(code))
        assertEquals(10, code.length)
        assertTrue(BirthdayLink.newKey(rnd).length >= 40)
        assertEquals("https://ranti-ng.vercel.app/b/$code", BirthdayLink.url(code))
        assertTrue(BirthdayLink.shareText(code, "Michael").endsWith(BirthdayLink.url(code)))
    }

    @Test fun morningPingTitle() {
        assertEquals("🎂 Tolu's birthday today", Prompts.title(Stage.DAY, "Tolu", "birthday"))
        assertEquals("Today is Ada's anniversary 🎉", Prompts.title(Stage.DAY, "Ada", "anniversary"))
        assertEquals("Tomorrow is Tolu's birthday", Prompts.title(Stage.ONE, "Tolu", "birthday"))
    }

    @Test fun friendsNotification() {
        assertEquals("🎂 Tolu added their birthday", Prompts.friendsTitle(listOf("Tolu")))
        assertEquals("🎂 3 friends added their birthdays", Prompts.friendsTitle(listOf("A", "B", "C")))
        assertEquals("A, B, C and 2 more. Ranti will remind you before each day.", Prompts.friendsBody(listOf("A", "B", "C", "D", "E")))
    }

    @Test fun headsUpOffDropsOnlyTheDayBefore() {
        val o = LadderInput(1, 10, 11, null, true, 0)
        val now = ZonedDateTime.of(2026, 10, 6, 12, 0, 0, 0, zone)
        val all = Ladder.upcoming(o, now).filter { it.occurrence == LocalDate.of(2026, 10, 11) }.map { it.stage }
        assertEquals(listOf(Stage.WEEK, Stage.FIVE, Stage.ONE, Stage.DAY), all)
        val off = Ladder.upcoming(o, now, Ladder.ALL - Stage.ONE).filter { it.occurrence == LocalDate.of(2026, 10, 11) }.map { it.stage }
        assertEquals(listOf(Stage.WEEK, Stage.FIVE, Stage.DAY), off)
        // The day itself can never be switched off, and it fires at 7:00.
        val dayOnly = Ladder.upcoming(o, now, emptySet()).filter { it.occurrence == LocalDate.of(2026, 10, 11) }
        assertEquals(listOf(Stage.DAY), dayOnly.map { it.stage })
        assertEquals(7, dayOnly[0].at.hour)
        // Next alarm with heads-up off on Oct 10 at noon is the 7:00 morning ping of the 11th.
        val oct10 = ZonedDateTime.of(2026, 10, 10, 12, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 11, 7, 0, 0, 0, zone), Ladder.nextAlarm(listOf(o), oct10, emptySet(), Ladder.ALL - Stage.ONE))
    }

    @Test fun emailSubjects() {
        assertEquals("Happy birthday, Tolu! 🎂", Messages.subject(OccasionType.BIRTHDAY, "Tolu"))
        assertTrue(Messages.subject(OccasionType.ANNIVERSARY, "Ada").startsWith("Happy anniversary"))
    }

    @Test fun ageLineHasNoStrayComma() {
        val all = Tone.entries.flatMap { t -> Messages.options(OccasionType.BIRTHDAY, t).indices.map { Messages.draft(OccasionType.BIRTHDAY, t, it, "Tolu", years = 29) } }
        assertTrue(all.none { it.contains(",.") || it.contains(",!") })
        assertTrue(all.any { it.contains("this new year, your 29th.") })
    }

    @Test fun sameKeyIgnoresCaseAndSpaces() {
        assertEquals(FriendEntries.sameKey(" Tolu Adeyemi ", 10, 11), FriendEntries.sameKey("tolu adeyemi", 10, 11))
    }
}
