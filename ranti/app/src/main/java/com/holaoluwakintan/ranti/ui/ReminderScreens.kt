package com.holaoluwakintan.ranti.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.core.QuickParse
import com.holaoluwakintan.ranti.core.QuickResult
import com.holaoluwakintan.ranti.core.Repeat
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.data.Reminder
import com.holaoluwakintan.ranti.reminders.ReminderEngine
import com.holaoluwakintan.ranti.system.Reliability
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val QUICK_EXAMPLES = listOf(
    "Tolu's birthday 12 March",
    "Call mum tomorrow 6pm",
    "Pay rent every month on the 1st",
    "Gym every Monday 7am",
    "Mum & Dad anniversary 3 June 1990",
    "In 30 minutes check the rice",
)

object RFmt {
    private val tf = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    fun time(t: LocalTime): String = t.format(tf).lowercase(Locale.ENGLISH)
    fun minutes(m: Int): String = time(LocalTime.of(m / 60, m % 60))
    fun day(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> if (d.year == today.year) d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        else d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
    }
    fun whenLabel(t: LocalDateTime, today: LocalDate): String = day(t.toLocalDate(), today) + ", " + time(t.toLocalTime())
}

/** v1.0: one line in, a saved birthday or reminder out. */
@Composable
fun QuickAddScreen(vm: AppViewModel, nav: Nav, initial: String) {
    val ctx = LocalContext.current
    var askExact by remember { mutableStateOf(false) }
    QuickAddContent(
        initial = initial,
        now = LocalDateTime.now(),
        nav = nav,
        defaultHour = vm.prepTimeMin / 60,
        onSaveOccasion = { o, edit ->
            vm.save(o) { id -> if (edit) nav.replace(Route.Edit(id)) else nav.back() }
        },
        onSaveReminder = { r ->
            vm.saveReminder(r) {
                if (!ReminderEngine.canExact(ctx) && !vm.prefs.exactAsked) { vm.prefs.exactAsked = true; askExact = true } else nav.back()
            }
        },
    )
    if (askExact) {
        AlertDialog(
            onDismissRequest = { askExact = false; nav.back() },
            title = { Text("Ring right on time?") },
            text = { Text("Allow \"Alarms & reminders\" for Ranti so your reminders ring on the dot, even when the phone is resting. You can change this any time in Settings.") },
            confirmButton = { TextButton(onClick = { askExact = false; Reliability.openExactAlarmSettings(ctx); nav.back() }) { Text("Allow", color = C.Coral) } },
            dismissButton = { TextButton(onClick = { askExact = false; nav.back() }) { Text("Not now", color = C.InkSoft) } },
            containerColor = C.Card,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickAddContent(
    initial: String,
    now: LocalDateTime,
    nav: Nav,
    defaultHour: Int = 9,
    autoFocus: Boolean = true,
    onSaveOccasion: (Occasion, Boolean) -> Unit,
    onSaveReminder: (Reminder) -> Unit,
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var text by remember { mutableStateOf(initial) }
    val parsed = remember(text) { QuickParse.parse(text, now, defaultHour) }
    var atOverride by remember(parsed.kind, parsed.title.isBlank()) { mutableStateOf<LocalDateTime?>(null) }
    var repeatOverride by remember(parsed.kind) { mutableStateOf<Repeat?>(null) }
    LaunchedEffect(text) { atOverride = null; repeatOverride = null }
    val at = atOverride ?: parsed.at
    val repeat = repeatOverride ?: parsed.repeat
    val today = now.toLocalDate()
    val focus = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { try { focus.requestFocus() } catch (_: Exception) {} }

    fun save(edit: Boolean = false) {
        when (parsed.kind) {
            QuickResult.Kind.OCCASION -> if (parsed.complete) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSaveOccasion(
                    Occasion(
                        name = parsed.title, type = parsed.type.name, month = parsed.month, day = parsed.day, year = parsed.year,
                        recurring = parsed.type.recursByDefault || parsed.year == null, source = "quick",
                    ), edit
                )
            }
            QuickResult.Kind.REMINDER -> if (parsed.title.isNotBlank() && at != null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val anchor = if (repeat == Repeat.MONTHLY || repeat == Repeat.YEARLY) (parsed.anchorDay.takeIf { it > 0 } ?: at.dayOfMonth) else 0
                onSaveReminder(Reminder(title = parsed.title, dueAt = ReminderEngine.toMillis(at), repeat = repeat.name, anchorDay = anchor))
            }
            else -> {}
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).navigationBarsPadding().imePadding()) {
        TopBar("Quick add", onBack = { nav.back() }, action = {
            TextButton(onClick = { nav.replace(Route.Edit()) }) { Text("Full form", color = C.Coral, style = MaterialTheme.typography.titleSmall) }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("Just type it", style = MaterialTheme.typography.headlineMedium)
            Text("A birthday, an anniversary, or anything you need to remember.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                placeholder = { Text("e.g. Tolu's birthday 12 March", color = C.InkFaint) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 26.sp),
                shape = RoundedCornerShape(20.dp), colors = fieldColors(), minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = parsed.kind,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                label = "quick",
            ) { kind ->
                when (kind) {
                    QuickResult.Kind.EMPTY -> Column {
                        SectionLabel("Try")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            QUICK_EXAMPLES.forEach { ex -> Pill(ex, false, { text = ex }) }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Ranti understands dates like 12/3, March 12, tomorrow, Friday, in 2 hours, and repeats like every day, every Monday or every month on the 1st.", style = MaterialTheme.typography.bodySmall)
                    }
                    QuickResult.Kind.OCCASION -> OccasionPreview(parsed, today)
                    QuickResult.Kind.REMINDER -> ReminderPreview(
                        parsed, at, repeat, today,
                        onPickDate = {
                            val base = at ?: today.atTime(defaultHour, 0)
                            DatePickerDialog(ctx, { _, y, m, d -> atOverride = LocalDate.of(y, m + 1, d).atTime(base.toLocalTime()) }, base.year, base.monthValue - 1, base.dayOfMonth).show()
                        },
                        onPickTime = {
                            val base = at ?: today.atTime(defaultHour, 0)
                            TimePickerDialog(ctx, { _, h, mi -> atOverride = base.toLocalDate().atTime(h, mi).let { if (!it.isAfter(LocalDateTime.now()) && repeat == Repeat.NONE && atOverride == null && !parsed.dateGiven) it.plusDays(1) else it } }, base.hour, base.minute, false).show()
                        },
                        onQuick = { atOverride = it },
                        onRepeat = { repeatOverride = it },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            val ok = when (parsed.kind) {
                QuickResult.Kind.OCCASION -> parsed.complete
                QuickResult.Kind.REMINDER -> parsed.title.isNotBlank() && at != null
                else -> false
            }
            GradientButton(
                when (parsed.kind) {
                    QuickResult.Kind.OCCASION -> if (parsed.complete) "Save ${parsed.type.label.lowercase()}" else "Add a date to save"
                    QuickResult.Kind.REMINDER -> if (at == null) "Pick a time to save" else "Save reminder"
                    else -> "Save"
                },
                Modifier.fillMaxWidth(), enabled = ok,
            ) { save() }
            if (parsed.kind == QuickResult.Kind.OCCASION && parsed.complete) {
                TextButton(onClick = { save(edit = true) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Save and add phone, notes, gift idea", color = C.InkSoft)
                }
            }
        }
    }
}

@Composable
private fun OccasionPreview(r: QuickResult, today: LocalDate) {
    RCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(r.title.ifBlank { "?" }, 52.dp, strong = true)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title.ifBlank { "Whose day is it?" }, style = MaterialTheme.typography.titleLarge, color = if (r.title.isBlank()) C.InkFaint else C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${r.type.emoji} ${r.type.label} · ${QuickParse.describe(r, today)}", style = MaterialTheme.typography.bodyMedium, color = if (r.month == 0) C.Coral else C.InkSoft)
            }
        }
        if (r.complete) {
            val next = DateMath.nextOccurrence(r.month, r.day, r.year, r.type.recursByDefault || r.year == null, today)
            if (next != null) {
                val days = DateMath.daysUntil(today, next)
                val years = DateMath.yearsOn(r.year, next)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.CoralSoft).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (days == 0L) "🎉" else "⏳", fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        (if (days == 0L) "It's today! " else "${Fmt.countdown(days)} · ${Fmt.long(next)}. ") +
                            (if (years != null && r.type == OccasionType.BIRTHDAY) "Turns $years. " else "") +
                            "Ranti will remind you 7, 5 and 1 day before.",
                        style = MaterialTheme.typography.bodySmall, color = C.Ink,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderPreview(
    r: QuickResult, at: LocalDateTime?, repeat: Repeat, today: LocalDate,
    onPickDate: () -> Unit, onPickTime: () -> Unit, onQuick: (LocalDateTime) -> Unit, onRepeat: (Repeat) -> Unit,
) {
    RCard(Modifier.fillMaxWidth(), padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Sunset), contentAlignment = Alignment.Center) { Text("⏰", fontSize = 24.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title.ifBlank { "What should I remind you?" }, style = MaterialTheme.typography.titleLarge, color = if (r.title.isBlank()) C.InkFaint else C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (at == null) "When? Pick a time below" else RFmt.whenLabel(at, today) + if (repeat != Repeat.NONE) " · ${repeat.short}" else "",
                    style = MaterialTheme.typography.bodyMedium, color = if (at == null) C.Coral else C.InkSoft,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SoftButton("📅  " + (at?.let { RFmt.day(it.toLocalDate(), today) } ?: "Date"), Modifier.weight(1f), bg = C.Bg) { onPickDate() }
            SoftButton("🕐  " + (at?.let { RFmt.time(it.toLocalTime()) } ?: "Time"), Modifier.weight(1f), bg = C.Bg) { onPickTime() }
        }
        Spacer(Modifier.height(12.dp))
        val now = LocalDateTime.now()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val inHour = now.plusHours(1).withSecond(0).withNano(0)
            val tonight = today.atTime(20, 0).let { if (it.isAfter(now)) it else it.plusDays(1) }
            val tmrw = today.plusDays(1).atTime(9, 0)
            Pill("In 1 hour", at == inHour, { onQuick(inHour) })
            Pill("Tonight 8pm", at == tonight, { onQuick(tonight) })
            Pill("Tomorrow 9am", at == tmrw, { onQuick(tmrw) })
        }
        SectionLabel("Repeat")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Repeat.entries.forEach { rp -> Pill(rp.short, repeat == rp, { onRepeat(rp) }) }
        }
        if (at != null && !at.isAfter(now) && repeat == Repeat.NONE) {
            Spacer(Modifier.height(10.dp))
            Text("That time has already passed today.", style = MaterialTheme.typography.bodySmall, color = C.Coral)
        }
    }
}

/** v1.0: the Reminders tab. */
@Composable
fun RemindersScreen(vm: AppViewModel, nav: Nav) {
    val list by vm.reminders.collectAsStateWithLifecycle()
    val people by vm.occasions.collectAsStateWithLifecycle()
    RemindersContent(
        list, vm.today, System.currentTimeMillis(), nav,
        onToggle = { r, d -> vm.setReminderDone(r, d) },
        onClearDone = { vm.clearDoneReminders() },
        personName = { id -> people?.firstOrNull { it.id == id }?.firstName },
    )
}

@Composable
fun RemindersContent(
    list: List<Reminder>?, today: LocalDate, nowMs: Long, nav: Nav,
    onToggle: (Reminder, Boolean) -> Unit, onClearDone: () -> Unit, personName: (Long) -> String? = { null },
) {
    val all = list.orEmpty()
    val open = all.filter { !it.done }
    val overdue = open.filter { it.firedAt >= it.dueAt && it.dueAt <= nowMs }
    val rest = open - overdue.toSet()
    val todayList = rest.filter { ReminderEngine.toLocal(it.dueAt).toLocalDate() <= today }
    val upcoming = rest - todayList.toSet()
    val done = all.filter { it.done }
    var showDone by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
    ) {
        item {
            Text("Reminders", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (open.isEmpty()) "Nothing pending. Nice." else "${open.size} pending" + if (overdue.isNotEmpty()) " · ${overdue.size} due" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            QuickAddBar(placeholder = "Add a reminder… e.g. Call mum Friday 6pm") { nav.push(Route.QuickAdd()) }
        }
        if (list != null && all.isEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                RCard(Modifier.fillMaxWidth(), padding = 22.dp) {
                    Text("⏰", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Remember anything", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(6.dp))
                    Text("Bills, calls, medicine, church meetings. Type it like a text and Ranti handles the time and the repeat.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    listOf("Pay rent every month on the 1st", "Drink water every day 10am", "Call dad Sunday 5pm").forEach { ex ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(C.Bg)
                                .clickable { nav.push(Route.QuickAdd(ex)) }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("“$ex”", style = MaterialTheme.typography.bodyMedium, color = C.Ink, modifier = Modifier.weight(1f))
                            Text("+", color = C.Coral, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (overdue.isNotEmpty()) {
            item { SectionLabel("Due now") }
            items(overdue, key = { "o" + it.id }) { r -> ReminderRow(r, today, true, personName(r.occasionId), Modifier.animateItem(), { onToggle(r, it) }) { nav.push(Route.ReminderEdit(r.id)) } }
        }
        if (todayList.isNotEmpty()) {
            item { SectionLabel("Today") }
            items(todayList, key = { "t" + it.id }) { r -> ReminderRow(r, today, false, personName(r.occasionId), Modifier.animateItem(), { onToggle(r, it) }) { nav.push(Route.ReminderEdit(r.id)) } }
        }
        if (upcoming.isNotEmpty()) {
            item { SectionLabel("Coming up") }
            items(upcoming, key = { "u" + it.id }) { r -> ReminderRow(r, today, false, personName(r.occasionId), Modifier.animateItem(), { onToggle(r, it) }) { nav.push(Route.ReminderEdit(r.id)) } }
        }
        if (done.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDone = !showDone }) { Text((if (showDone) "Hide" else "Show") + " done (${done.size})", color = C.InkSoft) }
                    Spacer(Modifier.weight(1f))
                    if (showDone) TextButton(onClick = onClearDone) { Text("Clear done", color = C.Coral) }
                }
            }
            if (showDone) items(done, key = { "d" + it.id }) { r -> ReminderRow(r, today, false, personName(r.occasionId), Modifier.animateItem(), { onToggle(r, it) }) { nav.push(Route.ReminderEdit(r.id)) } }
        }
    }
}

@Composable
fun ReminderRow(r: Reminder, today: LocalDate, overdue: Boolean, person: String?, modifier: Modifier = Modifier, onCheck: (Boolean) -> Unit, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val t = ReminderEngine.toLocal(r.dueAt)
    val ring by animateColorAsState(if (r.done) C.Mint else if (overdue) C.Coral else C.Line, label = "chk")
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(C.Card)
            .then(if (C.dark) Modifier.border(1.dp, C.Line, RoundedCornerShape(18.dp)) else Modifier)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = if (r.done) "Mark not done" else "Mark done") {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); onCheck(!r.done)
            }.semantics { contentDescription = if (r.done) "Done" else "Not done" },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(if (r.done) C.Mint else Color.Transparent).border(2.dp, ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (r.done) Text("✓", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (r.occasionId > 0) "🎂 " else "") + r.title, style = MaterialTheme.typography.titleMedium,
                color = if (r.done) C.InkFaint else C.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (r.done) TextDecoration.LineThrough else null,
            )
            Text(
                RFmt.whenLabel(t, today) + (if (r.repeatKind != Repeat.NONE) "  ·  🔁 ${r.repeatKind.short}" else "") + (if (r.note.isNotBlank()) "  ·  ${r.note}" else ""),
                style = MaterialTheme.typography.bodySmall, color = if (overdue && !r.done) C.Coral else C.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A tappable "type to add" bar used on Home and Reminders. */
@Composable
fun QuickAddBar(placeholder: String = "Quick add… e.g. Tolu's birthday 12 March", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(C.Card)
            .border(1.dp, C.Line, RoundedCornerShape(18.dp)).clickable(onClickLabel = "Quick add", onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Sunset), contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = C.InkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** v1.0: edit one reminder. */
@Composable
fun ReminderEditScreen(vm: AppViewModel, nav: Nav, id: Long) {
    val list by vm.reminders.collectAsStateWithLifecycle()
    if (list == null) return
    val existing = list!!.firstOrNull { it.id == id }
    ReminderEditContent(existing, vm.today, nav, onSave = { vm.saveReminder(it) { nav.back() } }, onDelete = { vm.deleteReminder(it); nav.back() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderEditContent(existing: Reminder?, today: LocalDate, nav: Nav, onSave: (Reminder) -> Unit, onDelete: (Reminder) -> Unit) {
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var at by remember { mutableStateOf(existing?.let { ReminderEngine.toLocal(it.dueAt) } ?: today.plusDays(1).atTime(9, 0)) }
    var repeat by remember { mutableStateOf(existing?.repeatKind ?: Repeat.NONE) }
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.Bg).navigationBarsPadding().imePadding()) {
        TopBar(if (existing == null) "New reminder" else "Edit reminder", onBack = { nav.back() }, action = {
            if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = C.Danger) }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it }, label = { Text("What") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            SectionLabel("When")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("📅  " + RFmt.day(at.toLocalDate(), today), Modifier.weight(1f), bg = C.Card) {
                    DatePickerDialog(ctx, { _, y, m, d -> at = LocalDate.of(y, m + 1, d).atTime(at.toLocalTime()) }, at.year, at.monthValue - 1, at.dayOfMonth).show()
                }
                SoftButton("🕐  " + RFmt.time(at.toLocalTime()), Modifier.weight(1f), bg = C.Card) {
                    TimePickerDialog(ctx, { _, h, mi -> at = at.toLocalDate().atTime(h, mi) }, at.hour, at.minute, false).show()
                }
            }
            SectionLabel("Repeat")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Repeat.entries.forEach { rp -> Pill(rp.label, repeat == rp, { repeat = rp }) }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            GradientButton("Save", Modifier.fillMaxWidth(), enabled = title.isNotBlank()) {
                val anchor = if (repeat == Repeat.MONTHLY || repeat == Repeat.YEARLY) at.dayOfMonth else 0
                val base = (existing ?: Reminder(title = "", dueAt = 0L))
                onSave(base.copy(title = title.trim(), note = note.trim(), dueAt = ReminderEngine.toMillis(at), repeat = repeat.name, anchorDay = anchor, firedAt = 0L, done = false))
            }
        }
    }
    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this reminder?") },
            text = { Text("\"${existing.title}\" will be removed.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(existing) }) { Text("Delete", color = C.Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = C.InkSoft) } },
            containerColor = C.Card,
        )
    }
}
