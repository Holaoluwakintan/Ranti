package com.holaoluwakintan.ranti.core

import org.json.JSONArray
import org.json.JSONObject

/** One birthday a friend typed into the "add your birthday" page. */
data class FriendEntry(
    val id: Long,
    val name: String,
    val day: Int,
    val month: Int,
    val year: Int?,
    val phone: String,
    val email: String,
)

/** Data shapes for Ranti's tiny backend (kept Android-free so it can be unit tested). */
object FriendEntries {
    fun parse(json: String): List<FriendEntry> {
        val arr = JSONObject(json).optJSONArray("entries") ?: return emptyList()
        val out = ArrayList<FriendEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim().take(60)
            val day = o.optInt("day", 0)
            val month = o.optInt("month", 0)
            val year = if (o.isNull("year")) null else o.optInt("year").takeIf { it in 1900..2100 }
            if (name.isEmpty() || !DateMath.isValid(month, day, year)) continue
            out += FriendEntry(
                id = o.optLong("id"), name = name, day = day, month = month, year = year,
                phone = if (o.isNull("phone")) "" else o.optString("phone").trim(),
                email = if (o.isNull("email")) "" else o.optString("email").trim(),
            )
        }
        return out
    }

    /** Highest entry id in a pull response (including entries we skipped), so the cursor always moves on. */
    fun maxId(json: String, fallback: Long): Long {
        val arr = JSONObject(json).optJSONArray("entries") ?: return fallback
        var m = fallback
        for (i in 0 until arr.length()) m = maxOf(m, arr.optJSONObject(i)?.optLong("id") ?: 0L)
        return m
    }

    data class AutoEmail(val id: Long, val name: String, val email: String, val day: Int, val month: Int, val message: String)

    fun emailsBody(code: String, ownerName: String, people: List<AutoEmail>): String {
        val arr = JSONArray()
        people.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("email", p.email).put("day", p.day).put("month", p.month).put("message", p.message))
        }
        return JSONObject().put("c", code).put("name", ownerName).put("people", arr).toString()
    }

    /** A stable fingerprint of what the server should hold, so we only upload when something changed. */
    fun signature(ownerName: String, people: List<AutoEmail>): String =
        (listOf(ownerName) + people.sortedBy { it.id }.map { "${it.id}|${it.name}|${it.email}|${it.day}|${it.month}|${it.message}" })
            .joinToString("\n").hashCode().toString(16) + ":" + people.size

    private val emailRe = Regex("^[^\\s@<>]{1,64}@[^\\s@<>]+\\.[^\\s@<>]{2,}$")
    fun looksLikeEmail(s: String) = emailRe.matches(s.trim())

    /** Same person = same name (any case) and same day. */
    fun sameKey(name: String, month: Int, day: Int) = name.trim().lowercase() + "|" + month + "|" + day
}
