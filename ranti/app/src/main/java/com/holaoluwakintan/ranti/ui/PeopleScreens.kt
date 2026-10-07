package com.holaoluwakintan.ranti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.core.Ladder
import com.holaoluwakintan.ranti.core.Stage
import com.holaoluwakintan.ranti.system.Share

@Composable
fun PeopleScreen(vm: AppViewModel, nav: Nav) {
    val list by vm.occasions.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val all = remember(list, vm.today) { vm.upcoming(list.orEmpty()) }
    val shown = all.filter { q.isBlank() || it.o.name.contains(q.trim(), true) || it.o.relationship.contains(q.trim(), true) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp),
    ) {
        item {
            Text("People", style = MaterialTheme.typography.headlineMedium)
            Text("${all.size} ${if (all.size == 1) "date" else "dates"} Ranti is keeping for you", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = C.InkFaint) },
                placeholder = { Text("Search by name or relationship") },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (all.isEmpty() && list != null) {
            item {
                Spacer(Modifier.height(24.dp))
                Text("No one yet. Tap + to add someone, or import birthdays from your contacts.", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                SoftButton("Import from contacts", Modifier.fillMaxWidth()) { nav.push(Route.Import) }
            }
        }
        items(shown, key = { it.o.id }) { u -> UpcomingRow(u) { nav.push(Route.Detail(u.o.id)) } }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Coral, unfocusedBorderColor = C.Line, focusedContainerColor = C.Card, unfocusedContainerColor = C.Card,
    cursorColor = C.Coral, focusedLabelColor = C.Coral,
)

@Composable
fun DetailScreen(vm: AppViewModel, nav: Nav, id: Long) {
    val list by vm.occasions.collectAsStateWithLifecycle()
    val o = list?.firstOrNull { it.id == id }
    if (list != null && o == null) { LaunchedEffect(Unit) { nav.back() }; return }
    if (o == null) return
    DetailContent(o, vm.today, nav, onUpdate = { vm.update(it) }, onDelete = { vm.delete(it) })
}

@Composable
fun DetailContent(
    o: com.holaoluwakintan.ranti.data.Occasion, today: java.time.LocalDate, nav: Nav,
    onUpdate: (com.holaoluwakintan.ranti.data.Occasion) -> Unit, onDelete: (com.holaoluwakintan.ranti.data.Occasion) -> Unit,
) {
    val ctx = LocalContext.current
    val occ = o.next(today)
    val days = occ?.let { DateMath.daysUntil(today, it) }
    var confirmDelete by remember { mutableStateOf(false) }
    var gift by remember(o.id) { mutableStateOf(o.giftIdea) }

    fun toggle(which: String) {
        if (occ == null) return
        val s = o.scopedTo(occ)
        onUpdate(
            when (which) {
                "gift" -> s.copy(giftDone = !s.giftDone, giftIdea = gift)
                "message" -> s.copy(messageDone = !s.messageDone)
                else -> s.copy(callDone = !s.callDone)
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()) {
        TopBar("", onBack = { nav.back() }, action = {
            IconButton(onClick = { nav.push(Route.Edit(o.id)) }) { Icon(Icons.Filled.Edit, "Edit", tint = C.Ink) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete", tint = C.InkSoft) }
        })
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp).clip(CircleShape).background(Sunset).padding(4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(C.Bg).padding(4.dp)) { Avatar(o.name, 96.dp) }
            }
            Spacer(Modifier.height(14.dp))
            Text(o.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (o.relationship.isNotBlank()) Text(o.relationship, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(if (occ != null) Fmt.subtitle(o, occ) else "${o.title} · ${Fmt.savedDate(o)} (passed)", style = MaterialTheme.typography.bodyMedium, color = C.Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            if (days != null) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(Sunset).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        when (days) { 0L -> "It's today! 🎉"; 1L -> "Tomorrow"; else -> "$days days to go" },
                        style = MaterialTheme.typography.labelLarge, color = Color.White,
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            if (occ != null && days != null) {
                SectionLabel("Your countdown plan")
                RCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                    PlanRow(Stage.WEEK, occ, days, "Pick a gift", if (gift.isNotBlank()) gift else "Something they'll love", o.gift(occ)) { toggle("gift") }
                    OutlinedTextField(
                        value = gift, onValueChange = { gift = it },
                        placeholder = { Text("Gift idea (e.g. perfume, ₦15k)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), colors = fieldColors(),
                    )
                    if (gift != o.giftIdea) {
                        TextButton(onClick = { onUpdate(o.copy(giftIdea = gift)) }, modifier = Modifier.padding(start = 4.dp)) { Text("Save gift idea", color = C.Coral) }
                    }
                    HorizontalDivider(color = C.Line, modifier = Modifier.padding(horizontal = 12.dp))
                    PlanRow(Stage.FIVE, occ, days, "Write your message", "Drafts ready in 4 tones", o.message(occ), onAction = { nav.push(Route.Message(o.id)) }) { toggle("message") }
                    HorizontalDivider(color = C.Line, modifier = Modifier.padding(horizontal = 12.dp))
                    PlanRow(Stage.ONE, occ, days, "Plan the call", if (o.phone.isNotBlank()) o.phone else "Pick a time to ring them", o.call(occ),
                        onAction = if (o.phone.isNotBlank()) ({ Share.dial(ctx, o.phone) }) else null) { toggle("call") }
                    HorizontalDivider(color = C.Line, modifier = Modifier.padding(horizontal = 12.dp))
                    PlanRow(Stage.DAY, occ, days, "Send your wishes", "Reminder at 7am on the day", false, onAction = { nav.push(Route.Message(o.id)) }, onToggle = null)
                }
            }
            SectionLabel("What I know about ${o.firstName}")
            RCard(Modifier.fillMaxWidth()) {
                Text(o.notes.ifBlank { "No notes yet. Add favourite things, sizes, kids' names, anything that helps." }, style = MaterialTheme.typography.bodyLarge, color = if (o.notes.isBlank()) C.InkSoft else C.Ink)
                Spacer(Modifier.height(10.dp))
                Text("Saved date: ${Fmt.savedDate(o)}" + (if (o.year == null) " (year unknown)" else ""), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
            GradientButton("Write ${o.firstName} a message", Modifier.fillMaxWidth()) { nav.push(Route.Message(o.id)) }
            if (o.phone.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                SoftButton("Call ${o.firstName}", Modifier.fillMaxWidth()) { Share.dial(ctx, o.phone) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Remove ${o.firstName}?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Ranti will stop reminding you about this date.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(o); nav.back() }) { Text("Remove", color = C.Coral) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = C.Ink) } },
            containerColor = C.Card,
        )
    }
}

@Composable
private fun PlanRow(
    stage: Stage, occ: java.time.LocalDate, daysLeft: Long, title: String, sub: String, done: Boolean,
    onAction: (() -> Unit)? = null, onToggle: (() -> Unit)?,
) {
    val date = occ.minusDays(stage.daysBefore.toLong())
    val current = Ladder.currentStage(daysLeft) == stage
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (current) C.CoralSoft.copy(alpha = 0.6f) else Color.Transparent)
            .let { m -> if (onAction != null) m.clickable(onClick = onAction) else m }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(58.dp)) {
            Text(if (stage == Stage.DAY) "THE DAY" else "${stage.daysBefore} DAYS", style = MaterialTheme.typography.labelSmall, color = if (current) C.Coral else C.InkFaint)
            Text(Fmt.dayMonth(date), style = MaterialTheme.typography.bodySmall)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (onToggle != null) {
            Box(
                Modifier.size(30.dp).clip(CircleShape)
                    .then(if (done) Modifier.background(C.Mint) else Modifier.border(2.dp, C.Line, CircleShape))
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) { if (done) Text("✓", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        } else if (onAction != null) {
            Text("→", fontSize = 18.sp, color = C.Coral)
        }
    }
}
