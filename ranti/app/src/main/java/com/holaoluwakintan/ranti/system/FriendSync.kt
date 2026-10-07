package com.holaoluwakintan.ranti.system

import android.content.Context
import android.util.Log
import com.holaoluwakintan.ranti.core.BirthdayLink
import com.holaoluwakintan.ranti.core.FriendEntries
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.data.Prefs
import com.holaoluwakintan.ranti.data.RantiDb
import com.holaoluwakintan.ranti.reminders.Notifier
import com.holaoluwakintan.ranti.reminders.ReminderEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** Tiny HTTP client for ranti-ng.vercel.app. Plain HttpURLConnection, no extra libraries. */
object Cloud {
    private fun call(method: String, path: String, key: String?, body: String?): Pair<Int, String> {
        val c = URL(BirthdayLink.BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.setRequestProperty("Accept", "application/json")
            if (key != null) c.setRequestProperty("x-ranti-key", key)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            return code to text
        } finally {
            c.disconnect()
        }
    }

    fun register(code: String, key: String, name: String): Int =
        call("POST", "/api/register", null, JSONObject().put("c", code).put("key", key).put("name", name).toString()).first

    fun pull(code: String, key: String, after: Long): Pair<Int, String> = call("GET", "/api/pull?c=$code&after=$after", key, null)

    fun syncEmails(key: String, body: String): Int = call("POST", "/api/emails", key, body).first

    /** v1.0: deletes this phone's link, friends' entries and auto-emails from the server. */
    fun delete(code: String, key: String): Int = call("POST", "/api/delete", key, JSONObject().put("c", code).toString()).first
}

/** Collects birthdays friends added through the link, and keeps the auto-email list in sync. */
object FriendSync {
    private const val TAG = "Ranti"
    private val mutex = Mutex()

    /** Makes sure this phone has its own link code + secret key. Cheap; no network. */
    fun ensureIdentity(prefs: Prefs) {
        if (prefs.ownerCode.isBlank() || prefs.ownerKey.isBlank()) {
            val rnd = SecureRandom()
            prefs.ownerCode = BirthdayLink.newCode(rnd)
            prefs.ownerKey = BirthdayLink.newKey(rnd)
            prefs.registeredName = null
            prefs.lastPullId = 0L
        }
    }

    data class Result(val added: List<String>, val ok: Boolean)

    /** v1.0: "Delete my cloud data". True when the server confirmed (or nothing was ever uploaded). */
    suspend fun deleteCloud(ctx: Context): Boolean = mutex.withLock {
        val prefs = Prefs(ctx.applicationContext)
        if (prefs.ownerCode.isBlank() || prefs.ownerKey.isBlank()) return true
        val neverRegistered = prefs.registeredName == null && prefs.lastPullId == 0L && prefs.emailSyncSig.isBlank()
        val st = if (neverRegistered) 200 else Cloud.delete(prefs.ownerCode, prefs.ownerKey)
        if (st == 200 || st == 404) {
            prefs.ownerCode = ""; prefs.ownerKey = ""; prefs.registeredName = null
            prefs.lastPullId = 0L; prefs.emailSyncSig = ""; prefs.friendsAdded = 0
            return true
        }
        return false
    }

    /**
     * Safe to call any time off the main thread. Without [force] it does nothing if the last
     * successful check was under 15 minutes ago (so opening the app often doesn't hammer the network).
     */
    suspend fun run(ctx: Context, force: Boolean = false): Result = mutex.withLock {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        if (!force && System.currentTimeMillis() - prefs.lastSyncAt < 15 * 60_000L) return Result(emptyList(), true)
        ensureIdentity(prefs)
        val code = prefs.ownerCode
        val key = prefs.ownerKey
        try {
            val name = prefs.userName.trim()
            if (prefs.registeredName != name) {
                val st = Cloud.register(code, key, name)
                if (st == 409) {
                    // Someone else holds this code (practically impossible): start over with a fresh identity.
                    prefs.ownerCode = ""; ensureIdentity(prefs)
                    return Result(emptyList(), false)
                }
                if (st != 200) return Result(emptyList(), false)
                prefs.registeredName = name
            }
            val added = pullNew(app, prefs, code, key)
            syncEmails(app, prefs)
            prefs.lastSyncAt = System.currentTimeMillis()
            if (added.isNotEmpty()) {
                prefs.friendsAdded = prefs.friendsAdded + added.size
                Notifier.postFriends(app, added)
                ReminderEngine.runAndReschedule(app)
            }
            return Result(added, true)
        } catch (t: Throwable) {
            Log.w(TAG, "friend sync failed: ${t.message}")
            return Result(emptyList(), false)
        }
    }

    private suspend fun pullNew(app: Context, prefs: Prefs, code: String, key: String): List<String> {
        val dao = RantiDb.get(app).occasions()
        val names = ArrayList<String>()
        var after = prefs.lastPullId
        repeat(5) { // up to 1,000 entries per run
            val (st, body) = Cloud.pull(code, key, after)
            if (st == 401) { prefs.registeredName = null; return names }
            if (st != 200) return names
            val entries = FriendEntries.parse(body)
            val maxId = FriendEntries.maxId(body, after)
            if (maxId <= after) return names
            val existing = dao.all()
            val byKey = existing.associateBy { FriendEntries.sameKey(it.name, it.month, it.day) }
            for (e in entries) {
                val k = FriendEntries.sameKey(e.name, e.month, e.day)
                val old = byKey[k]
                if (old != null) {
                    // Already saved: just fill in a missing number or email.
                    val upd = old.copy(phone = old.phone.ifBlank { e.phone }, email = old.email.ifBlank { e.email })
                    if (upd != old) dao.update(upd)
                } else {
                    dao.insert(
                        Occasion(
                            name = e.name, relationship = "Friend", type = OccasionType.BIRTHDAY.name,
                            month = e.month, day = e.day, year = e.year, recurring = true,
                            phone = e.phone, email = e.email, source = "link",
                        )
                    )
                    names += e.name.split(" ").first()
                }
            }
            after = maxId
            prefs.lastPullId = maxId
            if (entries.size < 200) return names
        }
        return names
    }

    /** Uploads only the people with auto-email switched on (name, date, email, custom message). */
    suspend fun syncEmails(app: Context, prefs: Prefs) {
        val people = RantiDb.get(app).occasions().all()
            .filter { it.autoEmail && it.kind == OccasionType.BIRTHDAY && FriendEntries.looksLikeEmail(it.email) }
            .map { FriendEntries.AutoEmail(it.id, it.name.trim(), it.email.trim(), it.day, it.month, it.emailNote.trim()) }
        val ownerName = prefs.userName.trim()
        val sig = FriendEntries.signature(ownerName, people)
        if (sig == prefs.emailSyncSig) return
        if (people.isEmpty() && prefs.emailSyncSig.isBlank()) { prefs.emailSyncSig = sig; return }
        val st = Cloud.syncEmails(prefs.ownerKey, FriendEntries.emailsBody(prefs.ownerCode, ownerName, people))
        if (st == 200) prefs.emailSyncSig = sig
    }
}
