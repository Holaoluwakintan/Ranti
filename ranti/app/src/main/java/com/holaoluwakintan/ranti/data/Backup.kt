package com.holaoluwakintan.ranti.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * v1.0: a plain JSON backup of everything Ranti keeps (people, dates, plans, reminders and settings),
 * so a lost phone or a reset never loses the list. Kept free of Android APIs so it is unit tested.
 */
object BackupCodec {
    const val FORMAT = "ranti-backup"
    const val VERSION = 1

    data class Settings(
        val userName: String = "",
        val tone: String = "WARM",
        val headsUp: Boolean = true,
        val nightlyEnabled: Boolean = true,
        val windowStart: Int = 20,
        val windowEnd: Int = 22,
        val dayTimeMin: Int = 7 * 60,
        val prepTimeMin: Int = 9 * 60,
        val themeMode: String = "SYSTEM",
        val ownerCode: String = "",
        val ownerKey: String = "",
    )

    data class Content(val occasions: List<Occasion>, val reminders: List<Reminder>, val settings: Settings?, val exportedAt: Long = 0L)

    fun encode(c: Content, now: Long = System.currentTimeMillis()): String {
        val occ = JSONArray()
        c.occasions.forEach { o ->
            occ.put(JSONObject().apply {
                put("name", o.name); put("relationship", o.relationship); put("type", o.type); put("customLabel", o.customLabel)
                put("month", o.month); put("day", o.day); put("year", o.year ?: JSONObject.NULL); put("recurring", o.recurring)
                put("phone", o.phone); put("notes", o.notes); put("email", o.email); put("autoEmail", o.autoEmail)
                put("emailNote", o.emailNote); put("source", o.source); put("giftIdea", o.giftIdea); put("planFor", o.planFor)
                put("giftDone", o.giftDone); put("messageDone", o.messageDone); put("callDone", o.callDone); put("createdAt", o.createdAt)
            })
        }
        val rem = JSONArray()
        c.reminders.forEach { r ->
            rem.put(JSONObject().apply {
                put("title", r.title); put("note", r.note); put("dueAt", r.dueAt); put("repeat", r.repeat); put("anchorDay", r.anchorDay)
                put("done", r.done); put("firedAt", r.firedAt); put("createdAt", r.createdAt)
            })
        }
        val root = JSONObject()
            .put("format", FORMAT).put("version", VERSION).put("exportedAt", now)
            .put("occasions", occ).put("reminders", rem)
        c.settings?.let { s ->
            root.put("settings", JSONObject().apply {
                put("userName", s.userName); put("tone", s.tone); put("headsUp", s.headsUp); put("nightlyEnabled", s.nightlyEnabled)
                put("windowStart", s.windowStart); put("windowEnd", s.windowEnd); put("dayTimeMin", s.dayTimeMin); put("prepTimeMin", s.prepTimeMin)
                put("themeMode", s.themeMode); put("ownerCode", s.ownerCode); put("ownerKey", s.ownerKey)
            })
        }
        return root.toString(1)
    }

    /** Throws IllegalArgumentException with a human message when the file isn't a Ranti backup. */
    fun decode(text: String): Content {
        val root = try { JSONObject(text.trim().removePrefix("\uFEFF")) } catch (e: Exception) { throw IllegalArgumentException("This file isn't a Ranti backup.") }
        if (root.optString("format") != FORMAT) throw IllegalArgumentException("This file isn't a Ranti backup.")
        if (root.optInt("version", 0) > VERSION) throw IllegalArgumentException("This backup is from a newer Ranti. Update the app first.")
        val occ = ArrayList<Occasion>()
        root.optJSONArray("occasions")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val m = o.optInt("month", 0); val d = o.optInt("day", 0)
                val y = if (o.isNull("year") || !o.has("year")) null else o.optInt("year").takeIf { it in 1900..2100 }
                if (name.isEmpty() || !com.holaoluwakintan.ranti.core.DateMath.isValid(m, d, y)) continue
                occ += Occasion(
                    name = name.take(80), relationship = o.optString("relationship"), type = o.optString("type", "BIRTHDAY"),
                    customLabel = o.optString("customLabel"), month = m, day = d, year = y, recurring = o.optBoolean("recurring", true),
                    phone = o.optString("phone"), notes = o.optString("notes"), email = o.optString("email"),
                    autoEmail = o.optBoolean("autoEmail", false), emailNote = o.optString("emailNote"), source = o.optString("source"),
                    giftIdea = o.optString("giftIdea"), planFor = o.optString("planFor"), giftDone = o.optBoolean("giftDone"),
                    messageDone = o.optBoolean("messageDone"), callDone = o.optBoolean("callDone"),
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        }
        val rem = ArrayList<Reminder>()
        root.optJSONArray("reminders")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val title = o.optString("title").trim()
                val due = o.optLong("dueAt", 0L)
                if (title.isEmpty() || due <= 0L) continue
                rem += Reminder(
                    title = title.take(200), note = o.optString("note"), dueAt = due, repeat = o.optString("repeat", "NONE"),
                    anchorDay = o.optInt("anchorDay", 0), done = o.optBoolean("done"), firedAt = o.optLong("firedAt", 0L),
                    createdAt = o.optLong("createdAt", 0L),
                )
            }
        }
        val s = root.optJSONObject("settings")?.let { o ->
            Settings(
                userName = o.optString("userName"), tone = o.optString("tone", "WARM"), headsUp = o.optBoolean("headsUp", true),
                nightlyEnabled = o.optBoolean("nightlyEnabled", true), windowStart = o.optInt("windowStart", 20), windowEnd = o.optInt("windowEnd", 22),
                dayTimeMin = o.optInt("dayTimeMin", 7 * 60), prepTimeMin = o.optInt("prepTimeMin", 9 * 60), themeMode = o.optString("themeMode", "SYSTEM"),
                ownerCode = o.optString("ownerCode"), ownerKey = o.optString("ownerKey"),
            )
        }
        return Content(occ, rem, s, root.optLong("exportedAt", 0L))
    }

    fun occKey(o: Occasion) = o.name.trim().lowercase() + "|" + o.type + "|" + o.month + "|" + o.day
    fun remKey(r: Reminder) = r.title.trim().lowercase() + "|" + r.dueAt + "|" + r.repeat

    /** What a restore adds: only entries this phone doesn't already have. */
    fun merge(existingOcc: List<Occasion>, existingRem: List<Reminder>, incoming: Content): Pair<List<Occasion>, List<Reminder>> {
        val haveO = existingOcc.map(::occKey).toHashSet()
        val haveR = existingRem.map(::remKey).toHashSet()
        val o = incoming.occasions.filter { haveO.add(occKey(it)) }.map { it.copy(id = 0) }
        val r = incoming.reminders.filter { haveR.add(remKey(it)) }.map { it.copy(id = 0, occasionId = 0) }
        return o to r
    }
}
