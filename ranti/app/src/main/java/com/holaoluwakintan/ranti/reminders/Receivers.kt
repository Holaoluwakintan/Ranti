package com.holaoluwakintan.ranti.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try { block() } catch (t: Throwable) { android.util.Log.e("Ranti", "receiver failed", t) } finally { pending.finish() }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ReminderEngine.ACTION_NIGHTLY -> runAsync { ReminderEngine.onNightlyAlarm(app); ReminderEngine.runAndReschedule(app) }
            else -> runAsync { ReminderEngine.runAndReschedule(app) }
        }
    }
}

/** After a reboot, an app update, or a clock/time-zone change, every alarm is set again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val timeChanged = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        runAsync {
            if (timeChanged) com.holaoluwakintan.ranti.data.Prefs(app).nightlyNextAt = 0L
            ReminderEngine.ensureSafetyNet(app)
            ReminderEngine.runAndReschedule(app)
        }
    }
}

class SafetyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        ReminderEngine.runAndReschedule(applicationContext)
        return Result.success()
    }
}

/** v0.2: pulls friends' birthdays from the link and syncs the auto-email list. */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        return try {
            com.holaoluwakintan.ranti.system.FriendSync.run(applicationContext, force = true)
            Result.success()
        } catch (t: Throwable) {
            android.util.Log.w("Ranti", "sync failed", t)
            Result.retry()
        }
    }
}

/** v1.0: notification buttons (Done, Snooze 1 h, Tomorrow, and snooze on birthday countdown pings). */
class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACT_DONE = "com.holaoluwakintan.ranti.action.DONE"
        const val ACT_SNOOZE = "com.holaoluwakintan.ranti.action.SNOOZE"
        const val ACT_SNOOZE_LADDER = "com.holaoluwakintan.ranti.action.SNOOZE_LADDER"
        private const val X_ID = "id"
        private const val X_NID = "nid"
        private const val X_MIN = "minutes"

        /** [minutes] = -1 means "tomorrow at the heads-up time". */
        fun intent(ctx: Context, action: String, id: Long, nid: Int, minutes: Int): android.app.PendingIntent {
            val i = Intent(ctx, ActionReceiver::class.java).setAction(action)
                .putExtra(X_ID, id).putExtra(X_NID, nid).putExtra(X_MIN, minutes)
            val rc = (nid * 4 + when (action) { ACT_DONE -> 0; ACT_SNOOZE -> if (minutes < 0) 1 else 2; else -> 3 }) and 0x7fffffff
            return android.app.PendingIntent.getBroadcast(ctx, rc, i, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun snoozeTime(prefs: com.holaoluwakintan.ranti.data.Prefs, minutes: Int, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): java.time.LocalDateTime =
            if (minutes >= 0) now.plusMinutes(minutes.toLong()).withSecond(0).withNano(0)
            else now.toLocalDate().plusDays(1).atTime(prefs.prepTimeMin / 60, prefs.prepTimeMin % 60)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val id = intent.getLongExtra(X_ID, 0L)
        val nid = intent.getIntExtra(X_NID, 0)
        val minutes = intent.getIntExtra(X_MIN, 60)
        Notifier.cancel(app, nid)
        runAsync {
            val db = com.holaoluwakintan.ranti.data.RantiDb.get(app)
            val prefs = com.holaoluwakintan.ranti.data.Prefs(app)
            val at = ReminderEngine.toMillis(snoozeTime(prefs, minutes))
            when (intent.action) {
                ACT_DONE -> db.reminders().byId(id)?.let { r ->
                    if (r.repeatKind == com.holaoluwakintan.ranti.core.Repeat.NONE) db.reminders().update(r.copy(done = true))
                }
                ACT_SNOOZE -> db.reminders().byId(id)?.let { r ->
                    if (r.repeatKind == com.holaoluwakintan.ranti.core.Repeat.NONE) db.reminders().update(r.copy(dueAt = at, firedAt = 0L, done = false))
                    else db.reminders().insert(com.holaoluwakintan.ranti.data.Reminder(title = r.title, note = r.note, dueAt = at, occasionId = r.occasionId))
                }
                ACT_SNOOZE_LADDER -> db.occasions().byId(id)?.let { o ->
                    db.reminders().insert(
                        com.holaoluwakintan.ranti.data.Reminder(title = "Wish ${o.firstName} a happy ${Notifier.whatForPublic(o)}", dueAt = at, occasionId = o.id)
                    )
                }
            }
            ReminderEngine.runAndReschedule(app)
        }
    }
}
