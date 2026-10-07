package com.holaoluwakintan.ranti.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("ranti", Context.MODE_PRIVATE)

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var userName: String
        get() = sp.getString("userName", "") ?: ""
        set(v) = sp.edit().putString("userName", v).apply()

    var nightlyEnabled: Boolean
        get() = sp.getBoolean("nightlyEnabled", true)
        set(v) = sp.edit().putBoolean("nightlyEnabled", v).apply()

    /** Nightly window start/end hour (24h). Default 20:00–22:00. */
    var windowStart: Int
        get() = sp.getInt("windowStart", 20)
        set(v) = sp.edit().putInt("windowStart", v).apply()

    var windowEnd: Int
        get() = sp.getInt("windowEnd", 22)
        set(v) = sp.edit().putInt("windowEnd", v).apply()

    var tone: String
        get() = sp.getString("tone", "WARM") ?: "WARM"
        set(v) = sp.edit().putString("tone", v).apply()

    var nightlyNextAt: Long
        get() = sp.getLong("nightlyNextAt", 0L)
        set(v) = sp.edit().putLong("nightlyNextAt", v).apply()

    var nightlyLastDate: String
        get() = sp.getString("nightlyLastDate", "") ?: ""
        set(v) = sp.edit().putString("nightlyLastDate", v).apply()

    var lastAlarmAt: Long
        get() = sp.getLong("lastAlarmAt", 0L)
        set(v) = sp.edit().putLong("lastAlarmAt", v).apply()

    var reliabilitySeen: Boolean
        get() = sp.getBoolean("reliabilitySeen", false)
        set(v) = sp.edit().putBoolean("reliabilitySeen", v).apply()

    /** v0.2: "1 day before" heads-up (the evening-before style nudge). */
    var headsUp: Boolean
        get() = sp.getBoolean("headsUp", true)
        set(v) = sp.edit().putBoolean("headsUp", v).apply()

    /** v0.2: the birthday link. The code is public (it's in the link); the key never leaves this phone except to Ranti's server. */
    var ownerCode: String
        get() = sp.getString("ownerCode", "") ?: ""
        set(v) = sp.edit().putString("ownerCode", v).apply()

    var ownerKey: String
        get() = sp.getString("ownerKey", "") ?: ""
        set(v) = sp.edit().putString("ownerKey", v).apply()

    /** The name last sent to the server (re-registers when it changes). */
    var registeredName: String?
        get() = sp.getString("registeredName", null)
        set(v) = sp.edit().putString("registeredName", v).apply()

    var lastPullId: Long
        get() = sp.getLong("lastPullId", 0L)
        set(v) = sp.edit().putLong("lastPullId", v).apply()

    var lastSyncAt: Long
        get() = sp.getLong("lastSyncAt", 0L)
        set(v) = sp.edit().putLong("lastSyncAt", v).apply()

    var friendsAdded: Int
        get() = sp.getInt("friendsAdded", 0)
        set(v) = sp.edit().putInt("friendsAdded", v).apply()

    var emailSyncSig: String
        get() = sp.getString("emailSyncSig", "") ?: ""
        set(v) = sp.edit().putString("emailSyncSig", v).apply()

    // ---------- v1.0 ----------

    /** "SYSTEM", "LIGHT" or "DARK". */
    var themeMode: String
        get() = sp.getString("themeMode", "SYSTEM") ?: "SYSTEM"
        set(v) = sp.edit().putString("themeMode", v).apply()

    /** Minutes after midnight for the morning-of ping (default 7:00). */
    var dayTimeMin: Int
        get() = sp.getInt("dayTimeMin", 7 * 60)
        set(v) = sp.edit().putInt("dayTimeMin", v.coerceIn(0, 23 * 60 + 59)).apply()

    /** Minutes after midnight for the 7/5/1-day heads-ups (default 9:00). */
    var prepTimeMin: Int
        get() = sp.getInt("prepTimeMin", 9 * 60)
        set(v) = sp.edit().putInt("prepTimeMin", v.coerceIn(0, 23 * 60 + 59)).apply()

    fun ladderTimes() = com.holaoluwakintan.ranti.core.LadderTimes(
        prep = java.time.LocalTime.of(prepTimeMin / 60, prepTimeMin % 60),
        day = java.time.LocalTime.of(dayTimeMin / 60, dayTimeMin % 60),
    )

    /** We asked for the notification permission at least once (Android 13+). */
    var notifAsked: Boolean
        get() = sp.getBoolean("notifAsked", false)
        set(v) = sp.edit().putBoolean("notifAsked", v).apply()

    var lastBackupAt: Long
        get() = sp.getLong("lastBackupAt", 0L)
        set(v) = sp.edit().putLong("lastBackupAt", v).apply()

    /** Home banner "Back up your list" dismissed until this time. */
    var backupNudgeUntil: Long
        get() = sp.getLong("backupNudgeUntil", 0L)
        set(v) = sp.edit().putLong("backupNudgeUntil", v).apply()

    var exactAsked: Boolean
        get() = sp.getBoolean("exactAsked", false)
        set(v) = sp.edit().putBoolean("exactAsked", v).apply()

    var quickTipsSeen: Boolean
        get() = sp.getBoolean("quickTipsSeen", false)
        set(v) = sp.edit().putBoolean("quickTipsSeen", v).apply()

    fun sentKeys(): Set<String> = sp.getStringSet("sent", emptySet())?.toSet() ?: emptySet()

    fun markSent(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val now = sentKeys().toMutableSet()
        now += keys
        // Keep the set small: drop keys for occurrences more than 40 days old.
        val cutoff = java.time.LocalDate.now().minusDays(40).toString()
        val pruned = now.filter { k -> k.split("|").getOrNull(1)?.let { it >= cutoff } ?: false }.toSet()
        sp.edit().putStringSet("sent", pruned).apply()
    }

    /** v0.3: contacts asked for their birthday, as "intlNumber|epochMillis". */
    fun asked(): Map<String, Long> = (sp.getStringSet("asked", emptySet()) ?: emptySet()).mapNotNull { e ->
        val i = e.lastIndexOf('|'); if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toLongOrNull() ?: 0L)
    }.toMap()

    fun markAsked(intl: String, at: Long = System.currentTimeMillis()) {
        val m = asked().toMutableMap(); m[intl] = at
        sp.edit().putStringSet("asked", m.map { "${it.key}|${it.value}" }.toSet()).apply()
    }
}
