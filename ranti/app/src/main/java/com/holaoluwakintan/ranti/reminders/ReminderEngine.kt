package com.holaoluwakintan.ranti.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.holaoluwakintan.ranti.core.Ladder
import com.holaoluwakintan.ranti.data.Prefs
import com.holaoluwakintan.ranti.data.RantiDb
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * One engine for everything time-based. It keeps just two alarms set
 * (the next countdown rung and the next nightly question), which stays far below
 * Android's 500-alarm cap no matter how many people are saved.
 */
object ReminderEngine {
    private const val TAG = "Ranti"
    const val ACTION_LADDER = "com.holaoluwakintan.ranti.LADDER"
    const val ACTION_NIGHTLY = "com.holaoluwakintan.ranti.NIGHTLY"
    const val ACTION_REMINDER = "com.holaoluwakintan.ranti.REMINDER"
    private const val RC_LADDER = 1
    private const val RC_NIGHTLY = 2
    private const val RC_REMINDER = 3

    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun toLocal(millis: Long, zone: ZoneId = ZoneId.systemDefault()): java.time.LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(t: java.time.LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long = t.atZone(zone).toInstant().toEpochMilli()

    /** Show whatever is due now, then set the next alarms. Safe to call any time, from any thread (not main). */
    suspend fun runAndReschedule(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val db = RantiDb.get(app)
        mutex.lock()
        try {
            val occasions = db.occasions().all()
            val zone = ZoneId.systemDefault()
            val now = ZonedDateTime.now(zone)
            val times = prefs.ladderTimes()
            val enabled = if (prefs.headsUp) Ladder.ALL else Ladder.ALL - com.holaoluwakintan.ranti.core.Stage.ONE
            val sent = prefs.sentKeys()
            val due = Ladder.due(occasions.map { it.ladderInput() }, now, sent, enabled, times)
            val byId = occasions.associateBy { it.id }
            due.forEach { r -> byId[r.occasionId]?.let { Notifier.postLadder(app, it, r) } }
            // Mark every passed rung as handled, including older ones we chose not to show.
            val handled = occasions.flatMap { o -> Ladder.upcoming(o.ladderInput(), now, enabled, times) }
                .filter { !it.at.isAfter(now) }.map { it.key }
            prefs.markSent(handled + due.map { it.key })

            val next = Ladder.nextAlarm(occasions.map { it.ladderInput() }, now, prefs.sentKeys(), enabled, times)
            if (next != null) setAlarm(app, next.toInstant().toEpochMilli(), ACTION_LADDER, RC_LADDER)
            else cancel(app, ACTION_LADDER, RC_LADDER)

            // v1.0: plain reminders. Ring each due one once, move repeating ones on, arm the next.
            val rdao = db.reminders()
            val nowMs = now.toInstant().toEpochMilli()
            val nowLocal = now.toLocalDateTime()
            for (r in rdao.all()) {
                if (r.done || r.dueAt > nowMs || r.firedAt >= r.dueAt) continue
                val repeat = r.repeatKind
                // A repeating reminder missed by days (phone off) only rings for its latest time.
                val tooOld = repeat != com.holaoluwakintan.ranti.core.Repeat.NONE && nowMs - r.dueAt > 36 * 3_600_000L
                if (!tooOld) Notifier.postReminder(app, r, r.occasionId.takeIf { it > 0 }?.let { byId[it] })
                val following = com.holaoluwakintan.ranti.core.Repeats.nextAfter(toLocal(r.dueAt, zone), repeat, nowLocal, r.anchorDay)
                rdao.update(if (following != null) r.copy(dueAt = toMillis(following, zone), firedAt = 0L) else r.copy(firedAt = nowMs))
            }
            val nextRem = rdao.all().filter { !it.done && it.dueAt > nowMs && it.firedAt < it.dueAt }.minOfOrNull { it.dueAt }
            if (nextRem != null) setAlarm(app, nextRem, ACTION_REMINDER, RC_REMINDER) else cancel(app, ACTION_REMINDER, RC_REMINDER)
        } finally {
            mutex.unlock()
        }
        scheduleNightly(app, fromAlarm = false)
        try { com.holaoluwakintan.ranti.widget.RantiWidget.refresh(app) } catch (t: Throwable) { Log.w(TAG, "widget refresh failed", t) }
    }

    /** Called when the nightly alarm fires. */
    fun onNightlyAlarm(ctx: Context) {
        val app = ctx.applicationContext
        val prefs = Prefs(app)
        val today = LocalDate.now().toString()
        if (prefs.nightlyEnabled && prefs.nightlyLastDate != today) {
            val hour = LocalTime.now().hour
            // Only ask inside (or shortly after) the window; a very late alarm is skipped.
            if (hour >= prefs.windowStart && hour <= prefs.windowEnd) {
                Notifier.postNightly(app, LocalDate.now().dayOfYear)
            }
            prefs.nightlyLastDate = today
        }
        prefs.nightlyNextAt = 0L
        scheduleNightly(app, fromAlarm = true)
    }

    /** Pick a random moment in tonight's window (or tomorrow's) and keep it, so reboots don't reshuffle it. */
    fun scheduleNightly(ctx: Context, fromAlarm: Boolean, forceNew: Boolean = false) {
        val prefs = Prefs(ctx)
        if (!prefs.nightlyEnabled) {
            cancel(ctx, ACTION_NIGHTLY, RC_NIGHTLY); prefs.nightlyNextAt = 0L; return
        }
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val stored = prefs.nightlyNextAt
        if (!forceNew && stored > now.toInstant().toEpochMilli()) {
            setAlarm(ctx, stored, ACTION_NIGHTLY, RC_NIGHTLY); return
        }
        val at = pickNightly(now, prefs.windowStart, prefs.windowEnd, prefs.nightlyLastDate == now.toLocalDate().toString())
        prefs.nightlyNextAt = at.toInstant().toEpochMilli()
        setAlarm(ctx, prefs.nightlyNextAt, ACTION_NIGHTLY, RC_NIGHTLY)
    }

    fun pickNightly(now: ZonedDateTime, startHour: Int, endHour: Int, askedToday: Boolean, rnd: Random = Random.Default): ZonedDateTime {
        val s = startHour.coerceIn(0, 23)
        val e = endHour.coerceIn(s + 1, 24)
        fun windowOn(d: LocalDate): Pair<ZonedDateTime, ZonedDateTime> {
            val start = d.atTime(s, 0).atZone(now.zone)
            val end = if (e == 24) d.plusDays(1).atStartOfDay(now.zone) else d.atTime(e, 0).atZone(now.zone)
            return start to end
        }
        val today = now.toLocalDate()
        val (ts, te) = windowOn(today)
        val earliest = now.plusMinutes(2)
        if (!askedToday && earliest.isBefore(te.minusMinutes(5))) {
            val from = if (earliest.isAfter(ts)) earliest else ts
            val span = java.time.Duration.between(from, te).seconds.coerceAtLeast(60)
            return from.plusSeconds(rnd.nextLong(0, span))
        }
        val (ns, ne) = windowOn(today.plusDays(1))
        val span = java.time.Duration.between(ns, ne).seconds.coerceAtLeast(60)
        return ns.plusSeconds(rnd.nextLong(0, span))
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun pending(ctx: Context, action: String, rc: Int): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(ctx, rc, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setAlarm(ctx: Context, atMillis: Long, action: String, rc: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(ctx, action, rc)
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        } catch (se: SecurityException) {
            // Exact permission revoked between the check and the call: fall back to inexact.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
        }
        Log.i(TAG, "alarm $action at ${Instant.ofEpochMilli(atMillis)}")
    }

    private fun cancel(ctx: Context, action: String, rc: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx, action, rc))
    }

    /** WorkManager safety net: every 3 hours re-checks what's due and re-arms the alarms. */
    fun ensureSafetyNet(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<SafetyWorker>(3, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("ranti-safety-net", ExistingPeriodicWorkPolicy.KEEP, req)
        // v0.2: twice a day (when online) collect birthdays friends added through the link, and sync auto-emails.
        val sync = PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("ranti-friend-sync", ExistingPeriodicWorkPolicy.KEEP, sync)
    }
}
