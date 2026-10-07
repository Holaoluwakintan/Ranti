package com.holaoluwakintan.ranti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holaoluwakintan.ranti.core.AskContacts
import com.holaoluwakintan.ranti.core.ContactEntry
import com.holaoluwakintan.ranti.core.DateMath

/** "2d ago" style for the asked marker. */
fun agoLabel(at: Long, now: Long = System.currentTimeMillis()): String {
    val mins = (now - at) / 60_000L
    return when {
        mins < 2 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        else -> "${mins / (24 * 60)}d ago"
    }
}

/**
 * v0.3: every contact with a number, a search box and multi-select, so Michael can ask people for their
 * birthday when none are saved in his contacts. Stateless so it can be screenshot-tested.
 */
@Composable
fun ColumnScope.AskList(
    contacts: List<ContactEntry>,
    noneSaved: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    picked: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    asked: Map<String, Long>,
    inRanti: Set<String>,
    onManual: (ContactEntry) -> Unit,
    onAsk: () -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val shown = remember(contacts, query) { AskContacts.filter(contacts, query) }
    val selectable = shown.filter { it.intl !in inRanti }
    val allOn = selectable.isNotEmpty() && selectable.all { picked[it.intl] == true }
    val count = picked.count { it.value }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.GoldSoft).padding(16.dp)) {
                Text(
                    if (noneSaved) "None of your contacts have a birthday saved, so ask them." else "Ask the rest for their birthday",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Tick people below. Ranti opens WhatsApp to each one with your birthday link ready, you just tap send. When they fill it, they appear in Ranti by themselves.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text("Search ${contacts.size} contacts") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = C.InkFaint) },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Coral, unfocusedBorderColor = C.Line, focusedContainerColor = C.Card, unfocusedContainerColor = C.Card),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (query.isBlank()) "${contacts.size} with a phone number" else "${shown.size} match",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                )
                if (selectable.isNotEmpty()) TextButton(onClick = { selectable.forEach { onToggle(it.intl, !allOn) } }) {
                    Text(if (allOn) "Clear" else if (query.isBlank()) "Select all" else "Select these", color = C.Coral, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (shown.isEmpty()) item {
            Text(
                if (contacts.isEmpty()) "No contacts with a phone number were found on this phone." else "No one matches “$query”.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        items(shown, key = { it.intl }) { c ->
            val known = c.intl in inRanti
            val on = !known && picked[c.intl] == true
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                    .clickable(enabled = !known) { onToggle(c.intl, !on) }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(c.name, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, color = if (known) C.InkFaint else C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val a = asked[c.intl]
                    Text(
                        when {
                            known -> "🎂 already in Ranti"
                            a != null -> "✓ asked ${agoLabel(a, now)}"
                            else -> c.phone
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when { known -> C.InkFaint; a != null -> C.Mint; else -> C.InkSoft },
                        maxLines = 1,
                    )
                }
                if (!known) TextButton(onClick = { onManual(c) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("+ date", color = C.InkSoft, style = MaterialTheme.typography.labelLarge)
                }
                Checkbox(checked = on, onCheckedChange = { if (!known) onToggle(c.intl, it) }, enabled = !known, colors = CheckboxDefaults.colors(checkedColor = C.Coral))
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 6.dp)) {
        GradientButton(
            if (count == 0) "Tick people to ask" else "Ask $count for ${if (count == 1) "their birthday" else "birthdays"}",
            Modifier.fillMaxWidth(), enabled = count > 0,
        ) { onAsk() }
    }
}

/** v0.3: one person at a time, because WhatsApp needs a tap to send each message. */
@Composable
fun AskFlow(
    current: ContactEntry,
    index: Int,
    total: Int,
    message: String,
    lastAsked: String?,
    onWhatsApp: () -> Unit,
    onSms: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.Bg).navigationBarsPadding()) {
        TopBar("Ask for birthdays", onBack = onStop)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(AskContacts.progress(index, total), style = MaterialTheme.typography.labelLarge, color = C.Coral, modifier = Modifier.weight(1f))
                if (lastAsked != null) Text("✓ $lastAsked asked", style = MaterialTheme.typography.labelLarge, color = C.Mint)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { index.toFloat() / total.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = C.Coral, trackColor = C.CoralSoft,
            )
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(current.name, 56.dp, strong = true)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(current.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("+" + current.intl, style = MaterialTheme.typography.bodyMedium)
                }
            }
            SectionLabel("Message they'll get")
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)).background(C.MintSoft).padding(14.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = C.Ink)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "WhatsApp opens with this ready. Tap send there, then come back here for the next person.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Column(Modifier.padding(20.dp)) {
            val first = AskContacts.firstName(current.name).ifEmpty { "them" }
            GradientButton(
                if (index == 0 && lastAsked == null) "WhatsApp $first (${AskContacts.progress(index, total)})"
                else "Next (${AskContacts.progress(index, total)}): WhatsApp $first",
                Modifier.fillMaxWidth(),
            ) { onWhatsApp() }
            Spacer(Modifier.height(10.dp))
            Row {
                SoftButton("Send by SMS", Modifier.weight(1f), bg = C.Card) { onSms() }
                Spacer(Modifier.width(10.dp))
                SoftButton("Skip", Modifier.weight(1f), bg = C.Card) { onSkip() }
            }
        }
    }
}

@Composable
fun AskDone(count: Int, onMore: () -> Unit, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg).navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(60.dp))
        Text("🎉", fontSize = 52.sp)
        Spacer(Modifier.height(8.dp))
        Text(if (count == 1) "1 friend asked" else "$count friends asked", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "When they fill your link, their birthday lands in Ranti by itself and you get a ping.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        GradientButton("Ask more people", Modifier.fillMaxWidth()) { onMore() }
        Spacer(Modifier.height(10.dp))
        SoftButton("Done", Modifier.fillMaxWidth(), bg = C.Card) { onHome() }
    }
}

/** v0.3: type a birthday in by hand for one contact. */
@Composable
fun ManualDateDialog(name: String, onSave: (month: Int, day: Int, year: Int?) -> Unit, onDismiss: () -> Unit) {
    var day by remember { mutableStateOf("") }
    var month by remember { mutableIntStateOf(0) }
    var year by remember { mutableStateOf("") }
    val d = day.toIntOrNull()
    val y = year.toIntOrNull()?.takeIf { it in 1900..2100 }
    val ok = d != null && month in 1..12 && DateMath.isValid(month, d, y) && (year.isBlank() || y != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.Card,
        title = { Text("$name's birthday", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Month", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..12).toList()) { m -> Pill(Fmt.months[m - 1].take(3), month == m, { month = m }) }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedTextField(
                        value = day, onValueChange = { day = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                        label = { Text("Day") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) }, singleLine = true,
                        label = { Text("Year (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (ok) onSave(month, d!!, y) }, enabled = ok) { Text("Save", color = if (ok) C.Coral else C.InkFaint) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.InkSoft) } },
    )
}
