package com.holaoluwakintan.ranti.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.temporal.TemporalAdjusters

/** What a one-line quick add turned into. */
data class QuickResult(
    val kind: Kind,
    /** Reminder title, or the person's name for an occasion. */
    val title: String,
    val type: OccasionType = OccasionType.BIRTHDAY,
    val month: Int = 0,
    val day: Int = 0,
    val year: Int? = null,
    val at: LocalDateTime? = null,
    val repeat: Repeat = Repeat.NONE,
    val anchorDay: Int = 0,
    val dateGiven: Boolean = false,
    val timeGiven: Boolean = false,
) {
    enum class Kind { EMPTY, OCCASION, REMINDER }

    val complete: Boolean
        get() = when (kind) {
            Kind.OCCASION -> title.isNotBlank() && DateMath.isValid(month, day, year)
            Kind.REMINDER -> title.isNotBlank() && at != null
            Kind.EMPTY -> false
        }
}

/**
 * Understands everyday phrasing, offline:
 *  "Tolu's birthday 12 March", "mum bday march 3 1960", "Ada & Kunle anniversary 25/10/2019",
 *  "call dad tomorrow 6pm", "pay rent every month on the 1st", "gym every monday 7am",
 *  "take medicine daily 8:30", "in 30 minutes check the rice", "meeting friday 2pm".
 * Dates are day-first (Nigerian style) when written with numbers.
 */
object QuickParse {
    private const val MONTH = "(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)"
    private const val WEEKDAY_FULL = "(monday|tuesday|wednesday|thursday|friday|saturday|sunday)"
    private const val WEEKDAY_ANY = "(monday|tuesday|wednesday|thursday|friday|saturday|sunday|mon|tues?|wed|thu(?:rs?)?|fri|sat|sun)"
    private val IC = setOf(RegexOption.IGNORE_CASE)

    private val occKeyword = Regex("""\b(wedding\s+anniversary|anniversary|annivesary|wedding|birthday|bday|b-day|b'day|born)\b""", IC)
    private val remindLead = Regex("""^\s*(please\s+)?(remind\s+me\s+(to\s+|about\s+|that\s+)?|reminder\s*:?\s*|todo\s*:?\s*|to\s+)""", IC)

    private val tAmPm = Regex("""(?:\b(?:at|by)\s+|@\s*)?\b(\d{1,2})(?:[:.](\d{2}))?\s*(a\.?m\.?|p\.?m\.?)(?![a-z])""", IC)
    private val t24 = Regex("""(?:\b(?:at|by)\s+|@\s*)?\b([01]?\d|2[0-3]):([0-5]\d)\b""", IC)
    private val tAtBare = Regex("""\b(?:at|by)\s+(\d{1,2})\b(?!\s*(?:st|nd|rd|th|/|-|\.\d|days?|weeks?|months?|minutes?|mins?|hours?|hrs?|$MONTH))""", IC)
    private val tWords = Regex("""\b(noon|midday|midnight|(?:this\s+|in\s+the\s+)?morning|(?:this\s+|in\s+the\s+)?afternoon|(?:this\s+|in\s+the\s+)?evening|tonight|at\s+night)\b""", IC)

    private val dDayMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s*(?:of\s+)?$MONTH\.?(?:,?\s+(\d{4}))?\b""", IC)
    private val dMonthDay = Regex("""\b$MONTH\.?\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""", IC)
    private val dNumeric = Regex("""\b(\d{1,2})[/.\-](\d{1,2})(?:[/.\-](\d{4}|\d{2}))?\b""")
    private val dOnThe = Regex("""\b(?:on\s+)?the\s+(\d{1,2})(?:st|nd|rd|th)\b|\bon\s+(\d{1,2})(?:st|nd|rd|th)\b""", IC)

    private val rIn = Regex("""\bin\s+(\d+|an?|one|two|three|four|five|six|ten|fifteen|twenty|thirty|half\s+an?)\s*(minutes?|mins?|hours?|hrs?|h|days?|weeks?|months?)\b""", IC)
    private val rDayAfter = Regex("""\b(?:the\s+)?day\s+after\s+tomorrow\b""", IC)
    private val rTomorrow = Regex("""\b(tomorrow|tomorow|tmrw|tmr|tmrrw)\b""", IC)
    private val rToday = Regex("""\b(today|tonite)\b""", IC)
    private val rWeekdayFull = Regex("""\b(?:(next|this|on|coming)\s+)?$WEEKDAY_FULL\b""", IC)
    private val rWeekdayAbbr = Regex("""\b(next|this|on|coming)\s+(mon|tues?|wed|thu(?:rs?)?|fri|sat|sun)\b""", IC)

    private val repDaily = Regex("""\b(every\s*day|everyday|daily|each\s+day|every\s+morning|every\s+night|every\s+evening)\b""", IC)
    private val repWeekdays = Regex("""\b(every\s+weekday|weekdays|every\s+work\s*day|on\s+weekdays)\b""", IC)
    private val repEveryWeekday = Regex("""\b(?:every|each)\s+$WEEKDAY_ANY(?:s)?\b""", IC)
    private val repWeekly = Regex("""\b(every\s+week|weekly|each\s+week)\b""", IC)
    private val repMonthly = Regex("""\b(every\s+month|monthly|each\s+month)\b""", IC)
    private val repYearly = Regex("""\b(every\s+year|yearly|annually|each\s+year)\b""", IC)

    fun monthOf(s: String): Int {
        val k = s.lowercase().take(3)
        return listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(k) + 1
    }

    fun weekdayOf(s: String): DayOfWeek? = when (s.lowercase().take(3)) {
        "mon" -> DayOfWeek.MONDAY; "tue" -> DayOfWeek.TUESDAY; "wed" -> DayOfWeek.WEDNESDAY; "thu" -> DayOfWeek.THURSDAY
        "fri" -> DayOfWeek.FRIDAY; "sat" -> DayOfWeek.SATURDAY; "sun" -> DayOfWeek.SUNDAY; else -> null
    }

    private fun num(s: String): Int? = s.toIntOrNull() ?: when (s.lowercase().trim()) {
        "a", "an", "one" -> 1; "two" -> 2; "three" -> 3; "four" -> 4; "five" -> 5; "six" -> 6; "ten" -> 10
        "fifteen" -> 15; "twenty" -> 20; "thirty" -> 30; else -> if (s.lowercase().startsWith("half")) 0 else null
    }

    private fun fullYear(y: String?, today: LocalDate): Int? {
        if (y.isNullOrBlank()) return null
        val n = y.toIntOrNull() ?: return null
        if (y.length == 4) return n.takeIf { it in 1900..2100 }
        val yy = today.year % 100
        return if (n <= yy + 1) 2000 + n else 1900 + n
    }

    /** Mutable text we cut matched pieces out of; what's left is the title or name. */
    private class Cut(input: String) {
        var s: String = " $input "
        fun take(r: Regex): MatchResult? {
            val m = r.find(s) ?: return null
            s = s.removeRange(m.range).let { it.substring(0, m.range.first) + " " + it.substring(m.range.first) }
            return m
        }
    }

    fun parse(input: String, now: LocalDateTime, defaultHour: Int = 9): QuickResult {
        val raw = input.trim()
        if (raw.isEmpty()) return QuickResult(QuickResult.Kind.EMPTY, "")
        val today = now.toLocalDate()
        val cut = Cut(raw)

        val startsRemind = remindLead.containsMatchIn(raw)
        val occ = if (startsRemind) null else occKeyword.find(raw)

        // ---- repeat ----
        var repeat = Repeat.NONE
        var repeatDefaultHour = defaultHour
        var repeatWeekday: DayOfWeek? = null
        if (occ == null) {
            when {
                cut.take(repWeekdays) != null -> repeat = Repeat.WEEKDAYS
                repDaily.containsMatchIn(cut.s) -> {
                    val m = cut.take(repDaily)!!.value.lowercase()
                    repeat = Repeat.DAILY
                    if ("night" in m) repeatDefaultHour = 20 else if ("evening" in m) repeatDefaultHour = 18
                }
                else -> {
                    val ew = cut.take(repEveryWeekday)
                    if (ew != null) { repeat = Repeat.WEEKLY; repeatWeekday = weekdayOf(ew.groupValues[1]) }
                    else if (cut.take(repWeekly) != null) repeat = Repeat.WEEKLY
                    else if (cut.take(repMonthly) != null) repeat = Repeat.MONTHLY
                    else if (cut.take(repYearly) != null) repeat = Repeat.YEARLY
                }
            }
        }

        // ---- time (before numeric dates, so 6.30pm is never read as a date) ----
        var time: LocalTime? = null
        var impliedTonight = false
        var offset: LocalDateTime? = null
        var offsetIsClock = false
        if (occ == null) {
            cut.take(tAmPm)?.let { m ->
                var h = m.groupValues[1].toInt(); val min = m.groupValues[2].toIntOrNull() ?: 0
                val pm = m.groupValues[3].lowercase().startsWith("p")
                if (h in 1..12 && min in 0..59) {
                    if (h == 12) h = 0
                    time = LocalTime.of(if (pm) h + 12 else h, min)
                }
            }
            if (time == null) cut.take(t24)?.let { m -> time = LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
            if (time == null) cut.take(tAtBare)?.let { m ->
                val h = m.groupValues[1].toInt()
                if (h in 0..23) time = LocalTime.of(if (h in 1..7) h + 12 else h, 0)
            }
            val w = cut.take(tWords)
            if (w != null) {
                val word = w.value.lowercase()
                val t = when {
                    "noon" in word || "midday" in word -> LocalTime.NOON
                    "midnight" in word -> LocalTime.of(23, 59)
                    "morning" in word -> LocalTime.of(9, 0)
                    "afternoon" in word -> LocalTime.of(14, 0)
                    "evening" in word -> LocalTime.of(18, 0)
                    else -> LocalTime.of(20, 0)
                }
                if (time == null) time = t
                if ("tonight" in word || "this" in word) impliedTonight = true
            }
            cut.take(rIn)?.let { m ->
                val n = num(m.groupValues[1]) ?: 1
                val unit = m.groupValues[2].lowercase()
                offset = when {
                    unit.startsWith("min") -> now.plusMinutes(n.toLong())
                    unit.startsWith("h") -> if (n == 0) now.plusMinutes(30) else now.plusHours(n.toLong())
                    unit.startsWith("d") -> now.plusDays(n.toLong())
                    unit.startsWith("w") -> now.plusWeeks(n.toLong())
                    else -> now.plusMonths(n.toLong())
                }
                if (unit.startsWith("min") || unit.startsWith("h")) { offsetIsClock = true; time = offset!!.toLocalTime().withSecond(0).withNano(0) }
            }
        }

        // ---- date ----
        var md: Pair<Int, Int>? = null
        var year: Int? = null
        var date: LocalDate? = null
        var anchorDay = 0
        var namedWeekday = false
        run {
            val a = cut.take(dDayMonth)
            if (a != null) {
                val d = a.groupValues[1].toInt(); val m = monthOf(a.groupValues[2])
                if (DateMath.isValid(m, d, null)) { md = m to d; year = fullYear(a.groupValues[3], today) }
                return@run
            }
            val b = cut.take(dMonthDay)
            if (b != null) {
                val m = monthOf(b.groupValues[1]); val d = b.groupValues[2].toInt()
                if (DateMath.isValid(m, d, null)) { md = m to d; year = fullYear(b.groupValues[3], today) }
                return@run
            }
            val c = cut.take(dNumeric)
            if (c != null) {
                val x = c.groupValues[1].toInt(); val y = c.groupValues[2].toInt()
                val yr = fullYear(c.groupValues[3], today)
                md = when {
                    DateMath.isValid(y, x, null) -> y to x   // day/month
                    DateMath.isValid(x, y, null) -> x to y   // month/day
                    else -> null
                }
                if (md != null) year = yr
                return@run
            }
        }
        if (year != null && md != null && !DateMath.isValid(md!!.first, md!!.second, year)) year = null

        if (md == null) {
            when {
                cut.take(rDayAfter) != null -> date = today.plusDays(2)
                cut.take(rTomorrow) != null -> date = today.plusDays(1)
                cut.take(rToday) != null -> date = today
                else -> {
                    val wd = cut.take(rWeekdayFull) ?: cut.take(rWeekdayAbbr)
                    if (wd != null) {
                        val dow = weekdayOf(wd.groupValues[2])
                        val next = wd.groupValues[1].equals("next", true)
                        if (dow != null) {
                            date = if (next) today.with(TemporalAdjusters.next(dow))
                            else today.with(TemporalAdjusters.nextOrSame(dow))
                            namedWeekday = true
                        }
                    } else {
                        val ot = cut.take(dOnThe)
                        if (ot != null) {
                            val d = (ot.groupValues[1].ifBlank { ot.groupValues[2] }).toInt()
                            if (d in 1..31) {
                                anchorDay = d
                                var ym = java.time.YearMonth.from(today)
                                var cand = ym.atDay(d.coerceAtMost(ym.lengthOfMonth()))
                                if (cand.isBefore(today)) { ym = ym.plusMonths(1); cand = ym.atDay(d.coerceAtMost(ym.lengthOfMonth())) }
                                date = cand
                            }
                        }
                    }
                }
            }
        }
        if (date == null && impliedTonight) date = today
        if (date == null && offset != null) date = offset!!.toLocalDate()

        // ---------- occasion ----------
        if (occ != null) {
            val kw = occ.value.lowercase()
            val type = when {
                "anniv" in kw -> OccasionType.ANNIVERSARY
                "wedding" in kw -> OccasionType.WEDDING
                else -> OccasionType.BIRTHDAY
            }
            cut.take(occKeyword)
            if (md == null && date != null) md = date!!.monthValue to date!!.dayOfMonth
            val name = cleanName(cut.s)
            val (m, d) = md ?: (0 to 0)
            return QuickResult(QuickResult.Kind.OCCASION, name, type, m, d, year, dateGiven = md != null)
        }

        // ---------- reminder ----------
        if (md != null) {
            val (m, d) = md!!
            date = if (year != null) DateMath.occurrenceIn(year!!, m, d)
            else {
                val c = DateMath.occurrenceIn(today.year, m, d)
                if (c.isBefore(today)) DateMath.occurrenceIn(today.year + 1, m, d) else c
            }
            if (anchorDay == 0) anchorDay = d
        }
        if (repeat == Repeat.WEEKLY && repeatWeekday != null && date == null) {
            date = today.with(TemporalAdjusters.nextOrSame(repeatWeekday))
        }
        if (repeat == Repeat.MONTHLY && anchorDay == 0 && date != null) anchorDay = date!!.dayOfMonth

        val dateGiven = date != null
        val timeGiven = time != null
        var at: LocalDateTime? = when {
            offsetIsClock && offset != null -> offset!!.withSecond(0).withNano(0)
            date != null && time != null -> date!!.atTime(time!!)
            date != null -> date!!.atTime(defaultHour, 0)
            time != null -> today.atTime(time!!).let { if (!it.isAfter(now)) it.plusDays(1) else it }
            repeat != Repeat.NONE -> today.atTime(repeatDefaultHour, 0).let { if (!it.isAfter(now)) it.plusDays(1) else it }
            else -> null
        }
        // A weekday named for today whose time has passed means next week.
        if (at != null && !at.isAfter(now) && date == today && repeat == Repeat.NONE && namedWeekday) at = at.plusWeeks(1)
        if (at != null && repeat != Repeat.NONE) {
            at = Repeats.firstValid(at, repeat)
            if (!at.isAfter(now)) at = Repeats.nextAfter(at, repeat, now, anchorDay)
        }
        val title = cleanTitle(cut.s)
        return QuickResult(QuickResult.Kind.REMINDER, title, at = at, repeat = repeat, anchorDay = anchorDay, dateGiven = dateGiven, timeGiven = timeGiven || offsetIsClock)
    }

    private val edgeWords = setOf("on", "at", "by", "the", "in", "for", "next", "this", "every", "each", "and", "to", "from", "of", "is", "it's", "its", "my", "a", "," , "-", "–", "@", "please", "remind", "me", "coming")

    private fun tidy(s: String): String = s.replace(Regex("""\s+"""), " ").replace(Regex("""\s+([,.!?])"""), "$1").trim()

    fun cleanTitle(s0: String): String {
        var s = tidy(remindLead.replace(tidy(s0), ""))
        var words = s.split(" ").filter { it.isNotBlank() }.toMutableList()
        while (words.isNotEmpty() && words.first().lowercase().trim(',', '.', ':') in edgeWords) words.removeAt(0)
        while (words.isNotEmpty() && words.last().lowercase().trim(',', '.', ':', '!') in edgeWords) words.removeAt(words.lastIndex)
        s = words.joinToString(" ").trim(' ', ',', '.', '-', ':')
        return s.replaceFirstChar { it.uppercaseChar() }
    }

    private val nameFiller = setOf("is", "on", "my", "the", "of", "date", "day", "falls", "it's", "its", "it", "was", "in", "at", "add", "save", "remember", "s", "'s", "’s", "-", "–", ",", ":", "and")

    fun cleanName(s0: String): String {
        val words = tidy(s0).split(" ").filter { it.isNotBlank() }.map { it.trim(',', '.', ':', '!', '?') }
            .map { it.replace(Regex("""['’]s$""", RegexOption.IGNORE_CASE), "") }
            .filter { it.isNotBlank() }
            .toMutableList()
        while (words.isNotEmpty() && words.first().lowercase() in nameFiller) words.removeAt(0)
        while (words.isNotEmpty() && words.last().lowercase() in nameFiller) words.removeAt(words.lastIndex)
        val kept = words.filter { w -> w.lowercase() !in setOf("is", "on", "my", "date", "falls") }
        return kept.joinToString(" ") { w -> if (w == w.lowercase() && w.length > 1 && w != "and") w.replaceFirstChar { it.uppercaseChar() } else w }
    }

    /** "Tue 14 Oct, 6:00 pm" style label used in previews. */
    fun describe(r: QuickResult, today: LocalDate): String = when (r.kind) {
        QuickResult.Kind.OCCASION -> if (r.month == 0) "Add a date, e.g. 12 March" else {
            val md = MonthDay.of(r.month, r.day)
            "${r.day} ${java.time.Month.of(r.month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)}" + (r.year?.let { " $it" } ?: "") + (if (md == MonthDay.from(today)) " · today!" else "")
        }
        QuickResult.Kind.REMINDER -> r.at?.let { at ->
            val d = at.toLocalDate()
            val day = when (d) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH))
            }
            val t = at.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)).lowercase()
            "$day, $t" + if (r.repeat != Repeat.NONE) " · ${r.repeat.short}" else ""
        } ?: "Pick a time"
        QuickResult.Kind.EMPTY -> ""
    }
}
