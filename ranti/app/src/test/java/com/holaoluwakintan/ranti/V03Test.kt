package com.holaoluwakintan.ranti

import com.holaoluwakintan.ranti.core.*
import org.junit.Assert.*
import org.junit.Test
import java.sql.DriverManager

/** v0.3: ask-for-birthdays logic, phone normalisation, and the contacts Event query run against real SQLite. */
class V03Test {

    // ---------- phone normalisation ----------
    @Test fun normalisesNigerianNumbers() {
        assertEquals("2348031234567", PhoneNorm.international("0803 123 4567"))
        assertEquals("2348031234567", PhoneNorm.international("08031234567"))
        assertEquals("2348031234567", PhoneNorm.international("+234 803 123 4567"))
        assertEquals("2348031234567", PhoneNorm.international("+234 (0) 803-123-4567"))
        assertEquals("2348031234567", PhoneNorm.international("2348031234567"))
        assertEquals("2348031234567", PhoneNorm.international("002348031234567"))
        assertEquals("2348031234567", PhoneNorm.international("8031234567"))
        assertEquals("2349061234567", PhoneNorm.international("0906-123-4567"))
    }

    @Test fun keepsForeignNumbersAndRejectsJunk() {
        assertEquals("447700900123", PhoneNorm.international("+44 7700 900123"))
        assertEquals("447700900123", PhoneNorm.international("0044 7700 900123"))
        assertEquals("14155550123", PhoneNorm.international("+1 (415) 555-0123"))
        assertEquals("447700900123", PhoneNorm.international("07700 900123", defaultCountry = "44"))
        assertNull(PhoneNorm.international(""))
        assertNull(PhoneNorm.international("   "))
        assertNull(PhoneNorm.international("*556#"))
        assertNull(PhoneNorm.international("123"))
        assertNull(PhoneNorm.international("Mum"))
        // the old helper still works for wa.me links
        assertEquals("2348031234567", Phone.forWhatsApp("0803 123 4567"))
    }

    // ---------- contact list ----------
    @Test fun cleansContactList() {
        val rows = listOf(
            PhoneRow(1, "Tolu Adeyemi", "0803 123 4567"),
            PhoneRow(1, "Tolu Adeyemi", "+234 803 123 4567"),          // same number twice
            PhoneRow(2, "Ade", "01 234 5678", isMobile = false),
            PhoneRow(2, "Ade", "0809 999 0000", isMobile = true),          // mobile wins
            PhoneRow(3, "MTN Balance", "*556#"),                           // USSD: dropped
            PhoneRow(4, null, "0701 000 1111"),                            // no name: shown as the number
            PhoneRow(5, "Ìyá Bùkọ́lá", "0802 222 3333"),
            PhoneRow(6, "Tolu (work)", "08031234567"),                     // same number as contact 1: merged
        )
        val list = AskContacts.clean(rows)
        assertEquals(listOf("Ade", "Ìyá Bùkọ́lá", "Tolu Adeyemi", "+2347010001111"), list.map { it.name })
        assertEquals("2348099990000", list.first { it.name == "Ade" }.intl)
        // search by plain letters (accents ignored), by part of the name, and by digits
        assertEquals(listOf("Ìyá Bùkọ́lá"), AskContacts.filter(list, "iya buk").map { it.name })
        assertEquals(listOf("Tolu Adeyemi"), AskContacts.filter(list, "ADEY").map { it.name })
        assertEquals(listOf("Ade"), AskContacts.filter(list, "0809 999").map { it.name })
        assertEquals(4, AskContacts.filter(list, "  ").size)
    }

    @Test fun emptyContactsPath() {
        assertTrue(AskContacts.clean(emptyList()).isEmpty())
        assertTrue(AskContacts.clean(listOf(PhoneRow(1, "No number", null), PhoneRow(2, "Short", "112"))).isEmpty())
        assertTrue(AskContacts.filter(emptyList(), "tolu").isEmpty())
    }

    @Test fun warmMessageCarriesTheLink() {
        val link = BirthdayLink.url("k7m2xq9ahd")
        val m = AskContacts.message("Tolu Adeyemi", link, "Michael")
        assertTrue(m.startsWith("Hi Tolu! 😊"))
        assertTrue(m.contains("https://ranti-ng.vercel.app/b/k7m2xq9ahd"))
        assertTrue(m.endsWith("– Michael"))
        val anon = AskContacts.message("+2347010001111", link, "")
        assertTrue(anon.startsWith("Hi! 😊"))
        assertFalse(anon.contains("– "))
        assertEquals("3 of 12", AskContacts.progress(2, 12))
    }

    // ---------- the contacts Event query ----------
    @Test fun classifiesEventRows() {
        assertEquals(OccasionType.BIRTHDAY, ContactEvents.classify("3", null))
        assertEquals(OccasionType.ANNIVERSARY, ContactEvents.classify("1", null))
        assertEquals(OccasionType.BIRTHDAY, ContactEvents.classify("0", "Birthday"))
        assertEquals(OccasionType.BIRTHDAY, ContactEvents.classify("0", "bday"))
        assertEquals(OccasionType.ANNIVERSARY, ContactEvents.classify("0", "Wedding anniversary"))
        assertEquals(OccasionType.BIRTHDAY, ContactEvents.classify(null, null))
        assertNull(ContactEvents.classify("2", null))          // "Other" with no label
        assertNull(ContactEvents.classify("0", "Graduation"))
    }

    /**
     * Mirrors the Contacts provider's data view (data1..data3 are TEXT columns) with the rows Google Contacts sync and
     * common phone apps write, then runs the app's exact selection + selectionArgs on real SQLite.
     */
    @Test fun eventQueryFindsSavedBirthdays() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { db ->
            db.createStatement().execute(
                "CREATE TABLE view_data (contact_id INTEGER, display_name TEXT, mimetype TEXT, data1 TEXT, data2 TEXT, data3 TEXT)"
            )
            val ins = db.prepareStatement("INSERT INTO view_data VALUES (?,?,?,?,?,?)")
            fun row(id: Long, name: String, mime: String, d1: String?, type: Int?, label: String?) {
                ins.setLong(1, id); ins.setString(2, name); ins.setString(3, mime); ins.setString(4, d1)
                if (type == null) ins.setNull(5, java.sql.Types.INTEGER) else ins.setInt(5, type) // stored like ContentValues.put(TYPE, int)
                ins.setString(6, label); ins.executeUpdate()
            }
            val ev = ContactEvents.MIMETYPE_EVENT
            row(1, "Google Tolu", ev, "1997-10-11", 3, null)                    // Google-synced birthday with year
            row(2, "Google Ada", ev, "--02-29", 3, null)                        // Google-synced, no year
            row(3, "Ada & Kunle", ev, "2019-10-25", 1, null)                    // anniversary
            row(4, "Custom Bayo", ev, "1990-07-02", 0, "Birthday")              // custom-labelled birthday
            row(5, "Untyped Femi", ev, "14/10/1985", null, null)                // saved without a type
            row(6, "Other Kemi", ev, "2020-01-01", 2, null)                     // "Other" date: not imported
            row(7, "Phone Row", "vnd.android.cursor.item/phone_v2", "08031234567", 2, null)

            fun run(sel: String, args: Array<String>): List<Pair<String, OccasionType>> {
                val sql = "SELECT contact_id, display_name, data1, data2, data3 FROM view_data WHERE $sel"
                val st = db.prepareStatement(sql)
                args.forEachIndexed { i, a -> st.setString(i + 1, a) }
                val out = ArrayList<Pair<String, OccasionType>>()
                st.executeQuery().use { rs ->
                    while (rs.next()) {
                        val type = ContactEvents.classify(rs.getString(4), rs.getString(5)) ?: continue
                        ContactDates.parse(rs.getString(3)) ?: continue
                        out += rs.getString(2) to type
                    }
                }
                return out
            }

            val v03 = run(ContactEvents.SELECTION, ContactEvents.ARGS).toMap()
            assertEquals(
                mapOf(
                    "Google Tolu" to OccasionType.BIRTHDAY, "Google Ada" to OccasionType.BIRTHDAY, "Ada & Kunle" to OccasionType.ANNIVERSARY,
                    "Custom Bayo" to OccasionType.BIRTHDAY, "Untyped Femi" to OccasionType.BIRTHDAY,
                ), v03,
            )
            // v0.2's selection: standard birthday/anniversary rows were found (the string-vs-int type compare is fine),
            // only the custom-labelled and untyped rows were missed.
            val v02 = run(ContactEvents.SELECTION_V02, ContactEvents.ARGS_V02).map { it.first }.toSet()
            assertEquals(setOf("Google Tolu", "Google Ada", "Ada & Kunle"), v02)
        }
    }
}
