package com.holaoluwakintan.ranti.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.holaoluwakintan.ranti.MainActivity
import com.holaoluwakintan.ranti.R
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.data.RantiDb
import com.holaoluwakintan.ranti.reminders.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** v1.0: home-screen widget. Plain RemoteViews (no extra libraries), refreshed whenever reminders are re-armed. */
class RantiWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { refresh(context.applicationContext) } catch (_: Throwable) {} finally { pending.finish() }
        }
    }

    data class Line(val title: String, val sub: String, val right: String, val hot: Boolean)

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** The rows the widget shows; pure enough to unit test with plain lists. */
        fun lines(
            occasions: List<com.holaoluwakintan.ranti.data.Occasion>,
            reminders: List<com.holaoluwakintan.ranti.data.Reminder>,
            today: LocalDate,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<Line> {
            val fmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
            val tf = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
            data class Item(val key: Long, val line: Line)
            val items = ArrayList<Item>()
            occasions.forEach { o ->
                val d = o.next(today) ?: return@forEach
                val days = DateMath.daysUntil(today, d)
                if (days > 60) return@forEach
                val what = if (o.kind == com.holaoluwakintan.ranti.core.OccasionType.BIRTHDAY) "birthday" else o.title.lowercase()
                val title = if (days == 0L) "${o.kind.emoji} ${o.firstName}'s $what today!" else "${o.kind.emoji} ${o.firstName}'s $what"
                val years = DateMath.yearsOn(o.year, d)
                val sub = (if (years != null && o.kind == com.holaoluwakintan.ranti.core.OccasionType.BIRTHDAY) "Turns $years · " else "") + d.format(fmt)
                items += Item(d.toEpochDay() * 1440 + 7 * 60, Line(title, sub, DateMath.countdownLabel(days), days <= 1))
            }
            reminders.filter { !it.done }.forEach { r ->
                val t = java.time.Instant.ofEpochMilli(r.dueAt).atZone(zone).toLocalDateTime()
                val days = DateMath.daysUntil(today, t.toLocalDate())
                if (days > 7) return@forEach
                val overdue = r.firedAt >= r.dueAt || days < 0
                val right = when { overdue -> "Due"; days == 0L -> t.format(tf).lowercase(); days == 1L -> "Tomorrow"; else -> t.toLocalDate().format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) }
                items += Item(t.toLocalDate().toEpochDay() * 1440 + t.hour * 60 + t.minute, Line("⏰ ${r.title}", if (days == 0L || overdue) "Today" else t.format(fmt), right, overdue || days == 0L))
            }
            return items.sortedBy { it.key }.take(3).map { it.line }
        }

        suspend fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, RantiWidget::class.java))
            if (ids == null || ids.isEmpty()) return
            val db = RantiDb.get(ctx)
            val today = LocalDate.now()
            val rows = lines(db.occasions().all(), db.reminders().all(), today)
            val v = RemoteViews(ctx.packageName, R.layout.widget_next)
            v.setTextViewText(R.id.w_date, today.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase(Locale.getDefault()))
            if (rows.isEmpty()) {
                v.setViewVisibility(R.id.w_hero, View.GONE)
                v.setViewVisibility(R.id.w_row1, View.GONE)
                v.setViewVisibility(R.id.w_row2, View.GONE)
                v.setViewVisibility(R.id.w_empty, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.w_empty, View.GONE)
                v.setViewVisibility(R.id.w_hero, View.VISIBLE)
                val h = rows[0]
                v.setTextViewText(R.id.w_title, h.title)
                v.setTextViewText(R.id.w_sub, h.sub)
                v.setTextViewText(R.id.w_badge, h.right)
                listOf(R.id.w_row1 to Triple(R.id.w_r1_title, R.id.w_r1_right, 1), R.id.w_row2 to Triple(R.id.w_r2_title, R.id.w_r2_right, 2)).forEach { (row, t) ->
                    val line = rows.getOrNull(t.third)
                    if (line == null) v.setViewVisibility(row, View.GONE) else {
                        v.setViewVisibility(row, View.VISIBLE)
                        v.setTextViewText(t.first, line.title)
                        v.setTextViewText(t.second, line.right)
                    }
                }
            }
            val open = PendingIntent.getActivity(
                ctx, 900_300,
                Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            v.setOnClickPendingIntent(R.id.w_root, open)
            v.setOnClickPendingIntent(R.id.w_add, Notifier.openIntent(ctx, "quickadd", 0, 900_301))
            mgr.updateAppWidget(ids, v)
        }
    }
}
