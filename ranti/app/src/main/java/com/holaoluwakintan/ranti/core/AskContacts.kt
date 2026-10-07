package com.holaoluwakintan.ranti.core

/**
 * v0.3 "ask for birthdays": pure logic (no Android), so it is unit-tested on the JVM.
 * Phone normalisation, the contacts Event query and how its rows are classified,
 * the contact list clean-up, search, and the warm "when's your birthday?" message.
 */
object PhoneNorm {
    /**
     * International digits without '+', e.g. "2348031234567", or null when it doesn't look like a phone number.
     * Local numbers starting with a single 0 get [defaultCountry] (234, Nigeria). A trunk 0 written after the
     * country code ("+234 (0) 803…") is dropped.
     */
    fun international(raw: String, defaultCountry: String = "234"): String? {
        val t = raw.trim()
        if (t.isEmpty() || t.contains('*') || t.contains('#')) return null // USSD / short codes
        var d = t.filter { it.isDigit() }
        val plus = t.startsWith("+")
        d = when {
            plus -> d
            d.startsWith("00") -> d.drop(2)
            d.startsWith("0") -> defaultCountry + d.drop(1)
            defaultCountry == "234" && d.length == 10 && d[0] in "789" -> "234$d"
            else -> d
        }
        // "+234 0803…" / "2340803…": drop the trunk zero after the country code.
        if (d.startsWith(defaultCountry + "0") && d.length == defaultCountry.length + 11) d = defaultCountry + d.drop(defaultCountry.length + 1)
        if (d.startsWith("2340") && d.length == 14) d = "234" + d.drop(4)
        if (d.length < 8 || d.length > 15) return null
        return d
    }
}

/** The ContactsContract Event query, as plain column names so the SQL can be tested against SQLite. */
object ContactEvents {
    const val MIMETYPE_EVENT = "vnd.android.cursor.item/contact_event" // Event.CONTENT_ITEM_TYPE
    const val TYPE_CUSTOM = 0
    const val TYPE_ANNIVERSARY = 1
    const val TYPE_OTHER = 2
    const val TYPE_BIRTHDAY = 3

    /** v0.3: every Event row; types are sorted out in [classify] (v0.2 filtered to type 1/3 in SQL and missed custom-labelled ones). */
    const val SELECTION = "mimetype=?"
    val ARGS = arrayOf(MIMETYPE_EVENT)
    /** The v0.2 selection, kept for the regression test only. */
    const val SELECTION_V02 = "mimetype=? AND (data2=? OR data2=?)"
    val ARGS_V02 = arrayOf(MIMETYPE_EVENT, TYPE_BIRTHDAY.toString(), TYPE_ANNIVERSARY.toString())

    /** [type] = Event.TYPE (data2) as stored, [label] = Event.LABEL (data3). Null = not a date Ranti should import. */
    fun classify(type: String?, label: String?): OccasionType? {
        val l = label?.trim()?.lowercase().orEmpty()
        val byLabel = when {
            l.contains("birth") || l.contains("bday") || l.contains("b-day") || l.contains("born") -> OccasionType.BIRTHDAY
            l.contains("anniv") || l.contains("wedding") || l.contains("marri") -> OccasionType.ANNIVERSARY
            else -> null
        }
        return when (type?.trim()?.toIntOrNull()) {
            TYPE_BIRTHDAY -> OccasionType.BIRTHDAY
            TYPE_ANNIVERSARY -> OccasionType.ANNIVERSARY
            TYPE_CUSTOM, TYPE_OTHER -> byLabel
            null -> byLabel ?: OccasionType.BIRTHDAY // a date with no type at all is almost always a birthday
            else -> byLabel
        }
    }
}

data class PhoneRow(val contactId: Long, val name: String?, val number: String?, val isPrimary: Boolean = false, val isMobile: Boolean = false)

data class ContactEntry(val contactId: Long, val name: String, val phone: String, val intl: String)

object AskContacts {
    /** One row per person: drops rows without a usable number, picks primary/mobile numbers first, merges the same number saved twice. */
    fun clean(rows: List<PhoneRow>, defaultCountry: String = "234"): List<ContactEntry> {
        val best = LinkedHashMap<Long, Pair<PhoneRow, String>>()
        for (r in rows) {
            val num = r.number?.trim().orEmpty()
            val intl = PhoneNorm.international(num, defaultCountry) ?: continue
            val cur = best[r.contactId]
            fun score(x: PhoneRow) = (if (x.isPrimary) 2 else 0) + (if (x.isMobile) 1 else 0)
            if (cur == null || score(r) > score(cur.first)) best[r.contactId] = r to intl
        }
        return best.values
            .map { (r, intl) -> ContactEntry(r.contactId, r.name?.trim().takeUnless { it.isNullOrEmpty() } ?: ("+$intl"), r.number!!.trim(), intl) }
            .distinctBy { it.intl }
            .sortedWith(compareBy({ !it.name.first().isLetter() }, { fold(it.name) }))
    }

    /** Search by any part of the name (accents and case ignored) or by digits of the number. */
    fun filter(list: List<ContactEntry>, query: String): List<ContactEntry> {
        val q = fold(query.trim())
        if (q.isEmpty()) return list
        val qd = query.filter { it.isDigit() }
        return list.filter { c ->
            fold(c.name).contains(q) || (qd.length >= 3 && (c.intl.contains(qd) || c.phone.filter { it.isDigit() }.contains(qd)))
        }
    }

    private fun fold(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun firstName(name: String): String {
        val n = name.trim()
        if (n.isEmpty() || !n.first().isLetter()) return ""
        return n.split(Regex("\\s+")).first().trim(',', '.', '-')
    }

    /** The prefilled WhatsApp/SMS text. Warm, short, with the owner's birthday link. */
    fun message(contactName: String, link: String, ownerName: String): String {
        val first = firstName(contactName)
        val hi = if (first.isEmpty()) "Hi! 😊" else "Hi $first! 😊"
        val sign = ownerName.trim().takeIf { it.isNotEmpty() }?.let { "\n\nThank you! – $it" } ?: "\n\nThank you!"
        return "$hi I never want to miss your birthday again. Please pop it in here, it takes 10 seconds 🎂👇\n$link$sign"
    }

    /** "Next (3 of 12)" style label for one-at-a-time sending. [index] is 0-based. */
    fun progress(index: Int, total: Int): String = "${index + 1} of $total"
}
