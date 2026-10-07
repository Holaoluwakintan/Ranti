package com.holaoluwakintan.ranti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holaoluwakintan.ranti.core.Ladder
import com.holaoluwakintan.ranti.core.Prompts
import com.holaoluwakintan.ranti.core.Stage

/** v1.0: things on Home that need the user (reminders can't show, no backup yet). */
data class HomeAlerts(
    val notificationsOff: Boolean = false,
    val suggestBackup: Boolean = false,
    val onFixNotifications: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onDismissBackup: () -> Unit = {},
)

@Composable
fun HomeScreen(vm: AppViewModel, nav: Nav) {
    val list by vm.occasions.collectAsStateWithLifecycle()
    val rems by vm.reminders.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tick = vm.resumeTick
    val notifOff = remember(tick) { !com.holaoluwakintan.ranti.reminders.Notifier.canPost(ctx) }
    var backupDismissed by remember { mutableStateOf(false) }
    val count = list?.size ?: 0
    val suggestBackup = !backupDismissed && count >= 5 && vm.lastBackupAt == 0L && System.currentTimeMillis() > vm.prefs.backupNudgeUntil
    HomeContent(
        list, vm.today, vm.userName, nav, rems,
        HomeAlerts(
            notificationsOff = notifOff && vm.prefs.onboarded,
            suggestBackup = suggestBackup,
            onFixNotifications = { com.holaoluwakintan.ranti.system.Reliability.openNotificationSettings(ctx) },
            onBackup = { nav.tab(Route.Settings) },
            onDismissBackup = { backupDismissed = true; vm.prefs.backupNudgeUntil = System.currentTimeMillis() + 14L * 86_400_000L },
        ),
        onToggleReminder = { r, d -> vm.setReminderDone(r, d) },
    )
}

@Composable
fun HomeContent(
    list: List<com.holaoluwakintan.ranti.data.Occasion>?, today: java.time.LocalDate, userName: String, nav: Nav,
    reminders: List<com.holaoluwakintan.ranti.data.Reminder>? = null,
    alerts: HomeAlerts = HomeAlerts(),
    nowMs: Long = System.currentTimeMillis(),
    onToggleReminder: (com.holaoluwakintan.ranti.data.Reminder, Boolean) -> Unit = { _, _ -> },
) {
    val all = remember(list, today) { upcomingOf(list.orEmpty(), today) }
    val todays = all.filter { it.days == 0L }
    val next30 = all.filter { it.days in 1..30 }
    val later = all.filter { it.days > 30 }.take(8)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Fmt.header(today), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    val name = userName
                    Text(
                        Fmt.greeting() + if (name.isNotBlank()) ",\n$name" else "",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                Box(Modifier.clip(CircleShape).clickable(onClickLabel = "Settings") { nav.tab(Route.Settings) }) { Avatar(userName.ifBlank { "R" }, 46.dp, strong = true) }
            }
            Spacer(Modifier.height(16.dp))
            QuickAddBar { nav.push(Route.QuickAdd()) }
        }

        if (alerts.notificationsOff) {
            item {
                Spacer(Modifier.height(12.dp))
                AlertStrip("🔕", "Reminders are off", "Notifications are blocked, so Ranti can't remind you. Tap to fix.", C.DangerSoft, C.Danger, alerts.onFixNotifications)
            }
        }

        val dueRems = reminders.orEmpty().filter { !it.done && com.holaoluwakintan.ranti.reminders.ReminderEngine.toLocal(it.dueAt).toLocalDate() <= today }
            .sortedBy { it.dueAt }
        if (dueRems.isNotEmpty()) {
            item { SectionLabel("Reminders today") }
            items(dueRems.take(4), key = { "r" + it.id }) { r ->
                ReminderRow(r, today, r.firedAt >= r.dueAt && r.dueAt <= nowMs, null, Modifier.animateItem(), { onToggleReminder(r, it) }) { nav.push(Route.ReminderEdit(r.id)) }
            }
            if (dueRems.size > 4) item { TextButton(onClick = { nav.tab(Route.Reminders) }) { Text("See all ${dueRems.size}", color = C.Coral) } }
        }

        if (list != null && all.isEmpty()) {
            item { Spacer(Modifier.height(20.dp)); EmptyHome(nav) }
        }

        if (todays.isNotEmpty()) {
            item { SectionLabel("Today") }
            items(todays, key = { "t" + it.o.id }) { u -> Column(Modifier.animateItem()) { TodayCard(u, nav); Spacer(Modifier.height(12.dp)) } }
        } else if (all.isNotEmpty()) {
            item {
                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.GoldSoft).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("☀️", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text("No one to celebrate today. You're all caught up.", style = MaterialTheme.typography.bodyMedium, color = C.Ink)
                }
            }
        }

        if (list != null && all.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)); LinkPromo(nav) }
        }

        if (alerts.suggestBackup) {
            item {
                Spacer(Modifier.height(12.dp))
                AlertStrip("💾", "Keep your list safe", "Back up to WhatsApp, Drive or email in one tap, so a lost phone never loses a birthday.", C.MintSoft, C.Mint, alerts.onBackup, alerts.onDismissBackup)
            }
        }

        next30.firstOrNull()?.let { hero ->
            item { SectionLabel("Next up"); NextUpCard(hero, nav) }
        }

        if (next30.size > 1) {
            item { SectionLabel("Next 30 days") }
            items(next30.drop(1), key = { "n" + it.o.id }) { u -> UpcomingRow(u, Modifier.animateItem()) { nav.push(Route.Detail(u.o.id)) } }
        } else if (all.isNotEmpty() && next30.isEmpty()) {
            item {
                SectionLabel("Next 30 days")
                Text("Nothing in the next 30 days. Enjoy the quiet 🎈", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (later.isNotEmpty()) {
            item { SectionLabel("Later") }
            items(later, key = { "l" + it.o.id }) { u -> UpcomingRow(u, Modifier.animateItem()) { nav.push(Route.Detail(u.o.id)) } }
        }
    }
}

/** v0.2: invites the user to share their birthday link. */
@Composable
fun LinkPromo(nav: Nav) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NightSky).clickable { nav.push(Route.Link) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔗", fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Share my birthday link", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text("Friends add their own birthdays. Post it on your status.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
        }
        Text("›", fontSize = 26.sp, color = C.Gold)
    }
}

@Composable
private fun EmptyHome(nav: Nav) {
    RCard(Modifier.fillMaxWidth(), padding = 22.dp) {
        Text("🎂", fontSize = 40.sp)
        Spacer(Modifier.height(10.dp))
        Text("Who should Ranti remember for you?", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text("Tip: tap the bar above and just type “Tolu's birthday 12 March”.", style = MaterialTheme.typography.bodySmall, color = C.Coral)
        Spacer(Modifier.height(6.dp))
        Text(
            "Add the people you never want to forget. Ranti counts down 7, 5 and 1 day before, with a little job each time.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(18.dp))
        GradientButton("Add someone", Modifier.fillMaxWidth()) { nav.push(Route.Edit()) }
        Spacer(Modifier.height(10.dp))
        SoftButton("Import birthdays from contacts", Modifier.fillMaxWidth()) { nav.push(Route.Import) }
        Spacer(Modifier.height(10.dp))
        SoftButton("Share my birthday link", Modifier.fillMaxWidth(), bg = C.GoldSoft) { nav.push(Route.Link) }
    }
}

@Composable
private fun TodayCard(u: Upcoming, nav: Nav) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(Sunset).clickable { nav.push(Route.Detail(u.o.id)) }.padding(20.dp)
    ) {
        Text("🎉  TODAY", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.9f))
        Spacer(Modifier.height(8.dp))
        Text("It's ${u.o.firstName}'s ${u.o.title.lowercase()}!", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(Fmt.subtitle(u.o, u.date), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.92f))
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).clickable { nav.push(Route.Message(u.o.id)) },
                contentAlignment = Alignment.Center,
            ) { Text("Send wishes", style = MaterialTheme.typography.labelLarge, color = C.Coral) }
            Box(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp)).border(1.5.dp, Color.White, RoundedCornerShape(14.dp)).clickable { nav.push(Route.Detail(u.o.id)) },
                contentAlignment = Alignment.Center,
            ) { Text("Open plan", style = MaterialTheme.typography.labelLarge, color = Color.White) }
        }
    }
}

@Composable
private fun NextUpCard(u: Upcoming, nav: Nav) {
    val stage = Ladder.currentStage(u.days)
    RCard(Modifier.fillMaxWidth(), padding = 20.dp, onClick = { nav.push(Route.Detail(u.o.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(u.o.name, 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(u.o.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Fmt.subtitle(u.o, u.date), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.width(8.dp))
            CountBadge(u.days, 64.dp)
        }
        Spacer(Modifier.height(18.dp))
        LadderRow(u.days)
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.CoralSoft)
                .clickable { if (stage == Stage.FIVE || stage == Stage.DAY) nav.push(Route.Message(u.o.id)) else nav.push(Route.Detail(u.o.id)) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (stage != null) "TODAY'S JOB" else "COMING UP", style = MaterialTheme.typography.labelSmall, color = C.Coral)
                Text(
                    if (stage != null) Prompts.job(stage) else "First nudge in ${u.days - 7} days",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Text("→", fontSize = 20.sp, color = C.Coral)
        }
    }
}

/** The 7 · 5 · 1 · Day ladder with passed rungs filled. */
@Composable
fun LadderRow(daysLeft: Long) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Stage.entries.forEachIndexed { i, st ->
            val reached = daysLeft <= st.daysBefore
            val current = Ladder.currentStage(daysLeft) == st
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape)
                        .then(
                            when {
                                current -> Modifier.background(Sunset)
                                reached -> Modifier.background(C.CoralSoft)
                                else -> Modifier.background(C.Bg).border(1.dp, C.Line, CircleShape)
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (st == Stage.DAY) "🎂" else "${st.daysBefore}",
                        fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = if (st == Stage.DAY) 13.sp else 12.sp,
                        color = when { current -> Color.White; reached -> C.Coral; else -> C.InkFaint },
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(if (st == Stage.DAY) "Day" else "${st.daysBefore}d", style = MaterialTheme.typography.labelSmall, color = if (current) C.Ink else C.InkFaint)
            }
            if (i < Stage.entries.lastIndex) {
                val nextReached = daysLeft <= Stage.entries[i + 1].daysBefore
                Box(Modifier.weight(1f).padding(bottom = 18.dp).height(2.dp).background(if (nextReached) C.Coral.copy(alpha = 0.45f) else C.Line))
            }
        }
    }
}

@Composable
fun UpcomingRow(u: Upcoming, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(C.Card)
            .then(if (C.dark) Modifier.border(1.dp, C.Line, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(u.o.name, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(u.o.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${u.o.kind.emoji} ${u.o.title} · ${Fmt.short(u.date)}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        val hot = u.days <= 7
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (hot) C.CoralSoft else C.Bg).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                when (u.days) { 0L -> "Today"; 1L -> "1 day"; else -> "${u.days} days" },
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = if (hot) C.Coral else C.InkSoft,
            )
        }
    }
}

/** v1.0: a one-line banner (notifications off, backup nudge). */
@Composable
fun AlertStrip(emoji: String, title: String, sub: String, bg: Color, accent: Color, onClick: () -> Unit, onDismiss: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).clickable(onClick = onClick).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = accent)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = C.Ink)
        }
        if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Later", color = C.InkSoft) }
        else Text("›", fontSize = 26.sp, color = accent, modifier = Modifier.padding(horizontal = 8.dp))
    }
}
