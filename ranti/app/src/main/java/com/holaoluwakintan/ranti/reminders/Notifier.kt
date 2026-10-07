package com.holaoluwakintan.ranti.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.holaoluwakintan.ranti.MainActivity
import com.holaoluwakintan.ranti.R
import com.holaoluwakintan.ranti.core.LadderReminder
import com.holaoluwakintan.ranti.core.Prompts
import com.holaoluwakintan.ranti.core.Stage
import com.holaoluwakintan.ranti.data.Occasion

object Notifier {
    const val CH_COUNTDOWN = "countdown"
    const val CH_NIGHTLY = "nightly"
    const val EXTRA_OPEN = "ranti.open"
    const val EXTRA_ID = "ranti.id"
    const val NIGHTLY_ID = 900_001
    const val CH_FRIENDS = "friends"
    const val FRIENDS_ID = 900_200
    const val CH_REMINDERS = "reminders"

    fun reminderNid(id: Long): Int = 600_000 + (id % 250_000).toInt()
    fun ladderNid(occasionId: Long, stageOrdinal: Int): Int = (occasionId * 10 + stageOrdinal).toInt()

    fun createChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_COUNTDOWN, "Countdown reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "7, 5 and 1 day before, and the morning of each date"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_NIGHTLY, "Nightly question", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "One gentle question each evening to help you capture dates"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set, like \"Call mum Friday 6pm\""
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_FRIENDS, "Friends' birthdays", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When friends add their birthday through your link"
            }
        )
    }

    fun canPost(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun openIntent(ctx: Context, open: String, id: Long, requestCode: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, open)
            putExtra(EXTRA_ID, id)
        }
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun whatForPublic(o: Occasion): String = whatFor(o)

    private fun whatFor(o: Occasion): String = when (o.kind) {
        com.holaoluwakintan.ranti.core.OccasionType.BIRTHDAY -> "birthday"
        com.holaoluwakintan.ranti.core.OccasionType.ANNIVERSARY -> "anniversary"
        com.holaoluwakintan.ranti.core.OccasionType.WEDDING -> "wedding"
        else -> o.title.lowercase()
    }

    fun postLadder(ctx: Context, o: Occasion, r: LadderReminder) {
        if (!canPost(ctx)) return
        val first = o.firstName
        val title = Prompts.title(r.stage, first, whatFor(o))
        val body = Prompts.body(r.stage, first) + (if (r.stage == Stage.WEEK && o.giftIdea.isNotBlank()) " Your idea: ${o.giftIdea}" else "")
        val nid = ladderNid(o.id, r.stage.ordinal)
        val b = NotificationCompat.Builder(ctx, CH_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_ranti)
            .setColor(0xFFFF6A3D.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, if (r.stage == Stage.DAY) "message" else "person", o.id, nid))
        val msgLabel = if (r.stage == Stage.DAY) "Send a wish" else "Write message"
        b.addAction(0, msgLabel, openIntent(ctx, "message", o.id, nid + 500_000))
        if (r.stage != Stage.DAY) b.addAction(0, "Open plan", openIntent(ctx, "person", o.id, nid + 1_000_000))
        // v1.0: snooze turns into a one-off reminder linked to this person.
        b.addAction(0, "Snooze 1 h", ActionReceiver.intent(ctx, ActionReceiver.ACT_SNOOZE_LADDER, o.id, nid, 60))
        try { NotificationManagerCompat.from(ctx).notify(nid, b.build()) } catch (_: SecurityException) {}
    }

    /** v1.0: a reminder the user set. [person] is set for a snoozed birthday wish. */
    fun postReminder(ctx: Context, r: com.holaoluwakintan.ranti.data.Reminder, person: Occasion?) {
        if (!canPost(ctx)) return
        val nid = reminderNid(r.id)
        val body = when {
            r.note.isNotBlank() -> r.note
            person != null -> "Tap to send ${person.firstName} your wishes."
            r.repeatKind != com.holaoluwakintan.ranti.core.Repeat.NONE -> "${r.repeatKind.label}. Tap Done when it's done."
            else -> "Tap Done when it's done, or snooze it."
        }
        val open = if (person != null) openIntent(ctx, "message", person.id, nid) else openIntent(ctx, "reminders", r.id, nid)
        val b = NotificationCompat.Builder(ctx, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_ranti)
            .setColor(0xFFFF6A3D.toInt())
            .setContentTitle((if (person != null) "🎂 " else "⏰ ") + r.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setWhen(r.dueAt).setShowWhen(true)
            .setContentIntent(open)
            .addAction(0, "Done ✓", ActionReceiver.intent(ctx, ActionReceiver.ACT_DONE, r.id, nid, 0))
            .addAction(0, "Snooze 1 h", ActionReceiver.intent(ctx, ActionReceiver.ACT_SNOOZE, r.id, nid, 60))
            .addAction(0, "Tomorrow", ActionReceiver.intent(ctx, ActionReceiver.ACT_SNOOZE, r.id, nid, -1))
        try { NotificationManagerCompat.from(ctx).notify(nid, b.build()) } catch (_: SecurityException) {}
    }

    fun cancel(ctx: Context, nid: Int) {
        try { NotificationManagerCompat.from(ctx).cancel(nid) } catch (_: Exception) {}
    }

    fun postNightly(ctx: Context, seed: Int) {
        if (!canPost(ctx)) return
        val body = Prompts.nightlyBody(seed)
        val b = NotificationCompat.Builder(ctx, CH_NIGHTLY)
            .setSmallIcon(R.drawable.ic_stat_ranti)
            .setColor(0xFFFF6A3D.toInt())
            .setContentTitle(Prompts.nightlyTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, "capture", 0, NIGHTLY_ID))
            .addAction(0, "Add someone", openIntent(ctx, "capture", 0, NIGHTLY_ID + 1))
            .addAction(0, "From contacts", openIntent(ctx, "import", 0, NIGHTLY_ID + 2))
        try { NotificationManagerCompat.from(ctx).notify(NIGHTLY_ID, b.build()) } catch (_: SecurityException) {}
    }

    fun postTest(ctx: Context) {
        if (!canPost(ctx)) return
        val b = NotificationCompat.Builder(ctx, CH_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_stat_ranti)
            .setColor(0xFFFF6A3D.toInt())
            .setContentTitle("Ranti is ready 🎉")
            .setContentText("This is how your reminders will look.")
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, "home", 0, 900_100))
        try { NotificationManagerCompat.from(ctx).notify(900_100, b.build()) } catch (_: SecurityException) {}
    }

    fun postFriends(ctx: Context, names: List<String>) {
        if (names.isEmpty() || !canPost(ctx)) return
        val body = Prompts.friendsBody(names)
        val b = NotificationCompat.Builder(ctx, CH_FRIENDS)
            .setSmallIcon(R.drawable.ic_stat_ranti)
            .setColor(0xFFFF6A3D.toInt())
            .setContentTitle(Prompts.friendsTitle(names))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openIntent(ctx, "people", 0, FRIENDS_ID))
        try { NotificationManagerCompat.from(ctx).notify(FRIENDS_ID, b.build()) } catch (_: SecurityException) {}
    }
}
