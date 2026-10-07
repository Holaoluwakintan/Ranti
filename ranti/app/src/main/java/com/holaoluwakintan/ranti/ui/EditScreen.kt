package com.holaoluwakintan.ranti.ui

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.system.ContactsImport

private val relationships = listOf("Friend", "Family", "Partner", "Colleague", "Church", "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditScreen(vm: AppViewModel, nav: Nav, id: Long, capture: Boolean) {
    val list by vm.occasions.collectAsStateWithLifecycle()
    val existing = remember(list, id) { if (id == 0L) null else list?.firstOrNull { it.id == id } }
    if (id != 0L && existing == null) return
    EditContent(existing, capture, nav) { o, done -> vm.save(o, done) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditContent(existing: Occasion?, capture: Boolean, nav: Nav, onSave: (Occasion, (Long) -> Unit) -> Unit) {
    val ctx = LocalContext.current

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var type by remember(existing) { mutableStateOf(existing?.kind ?: OccasionType.BIRTHDAY) }
    var custom by remember(existing) { mutableStateOf(existing?.customLabel ?: "") }
    var rel by remember(existing) { mutableStateOf(existing?.relationship ?: "") }
    var day by remember(existing) { mutableStateOf(existing?.day) }
    var month by remember(existing) { mutableStateOf(existing?.month) }
    var yearText by remember(existing) { mutableStateOf(existing?.year?.toString() ?: "") }
    var phone by remember(existing) { mutableStateOf(existing?.phone ?: "") }
    var notes by remember(existing) { mutableStateOf(existing?.notes ?: "") }
    var email by remember(existing) { mutableStateOf(existing?.email ?: "") }
    var autoEmail by remember(existing) { mutableStateOf(existing?.autoEmail ?: false) }
    var emailNote by remember(existing) { mutableStateOf(existing?.emailNote ?: "") }
    var tried by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data
        if (res.resultCode == Activity.RESULT_OK && uri != null) {
            ContactsImport.readPicked(ctx, uri)?.let { (n, p) -> if (n.isNotBlank()) name = n; if (p.isNotBlank()) phone = p }
        }
    }

    val year = yearText.trim().toIntOrNull()
    val yearOk = yearText.isBlank() || (year != null && year in 1900..2100)
    val dateOk = day != null && month != null && yearOk && DateMath.isValid(month!!, day!!, if (yearText.isBlank()) null else year)
    val emailOk = email.isBlank() || com.holaoluwakintan.ranti.core.FriendEntries.looksLikeEmail(email)
    val valid = name.isNotBlank() && dateOk && emailOk && (type != OccasionType.CUSTOM || custom.isNotBlank())

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        // Header: deep plum for the quick capture from the nightly question.
        Column(
            Modifier.fillMaxWidth().background(if (capture) NightSky else androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.Bg, C.Bg)))
        ) {
            TopBar(if (existing != null) "Edit" else "", onBack = { nav.back() }, dark = capture)
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp)) {
                if (capture) Text("TONIGHT'S QUESTION", style = MaterialTheme.typography.labelMedium, color = C.Gold)
                Text(
                    when {
                        existing != null -> "Edit ${existing.firstName}"
                        capture -> "Whose birthday should I remember?"
                        else -> "Who should Ranti remember?"
                    },
                    style = MaterialTheme.typography.headlineMedium, color = if (capture) Color.White else C.Ink,
                )
                if (existing == null) {
                    Text(
                        "Takes 10 seconds. You can skip the year if you don't know it.",
                        style = MaterialTheme.typography.bodyMedium, color = if (capture) Color.White.copy(alpha = 0.75f) else C.InkSoft,
                    )
                }
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true,
                isError = tried && name.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "📇  Pick from contacts",
                style = MaterialTheme.typography.titleSmall, color = C.Coral,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    try { pick.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)) } catch (_: Exception) {}
                }.padding(vertical = 6.dp, horizontal = 4.dp),
            )

            SectionLabel("What's the day?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OccasionType.entries.forEach { t -> Pill("${t.emoji} ${t.label}", type == t, { type = t }) }
            }
            if (type == OccasionType.CUSTOM || type == OccasionType.EVENT) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it },
                    label = { Text(if (type == OccasionType.EVENT) "Event name (optional)" else "What's the occasion?") },
                    singleLine = true, isError = tried && type == OccasionType.CUSTOM && custom.isBlank(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                )
            }

            SectionLabel("Date")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerField("Day", day?.toString() ?: "", (1..31).map { it.toString() }, Modifier.weight(0.8f), tried && day == null) { day = it + 1 }
                PickerField("Month", month?.let { Fmt.months[it - 1] } ?: "", Fmt.months, Modifier.weight(1.4f), tried && month == null) { month = it + 1 }
                OutlinedTextField(
                    value = yearText, onValueChange = { v -> yearText = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Year") }, placeholder = { Text("Optional") }, singleLine = true, isError = !yearOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                )
            }
            if (day != null && month != null && !dateOk && yearOk) {
                Text("That date doesn't exist" + (if (month == 2 && day == 29) " in $yearText" else ""), color = C.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            } else if (yearText.isBlank()) {
                Text("Year unknown is fine. Ranti just won't show the age.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            if (!type.recursByDefault && yearText.isNotBlank()) {
                Text("A ${type.label.lowercase()} with a year happens once. Leave the year empty to repeat it yearly.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }

            SectionLabel("Relationship")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                relationships.forEach { r -> Pill(r, rel == r, { rel = if (rel == r) "" else r }) }
            }

            SectionLabel("Extras")
            OutlinedTextField(
                value = phone, onValueChange = { phone = it }, label = { Text("WhatsApp number (optional)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim() }, label = { Text("Email (optional)") }, singleLine = true,
                isError = !emailOk,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
            )
            if (type == OccasionType.BIRTHDAY) {
                Spacer(Modifier.height(10.dp))
                AutoEmailCard(email.isNotBlank() && emailOk, autoEmail, emailNote, name.trim().split(" ").firstOrNull().orEmpty(), { autoEmail = it }, { emailNote = it })
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = notes, onValueChange = { notes = it }, label = { Text("Notes: favourite things, sizes, ideas") },
                minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(24.dp))
        }

        Box(Modifier.fillMaxWidth().background(C.Bg).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            GradientButton(if (existing != null) "Save changes" else "Remember ${name.trim().split(" ").firstOrNull().orEmpty().ifBlank { "them" }}", Modifier.fillMaxWidth()) {
                tried = true
                if (!valid) return@GradientButton
                val y = if (yearText.isBlank()) null else year
                val base = existing ?: Occasion(name = "", month = 1, day = 1)
                val o = base.copy(
                    name = name.trim(), type = type.name, customLabel = custom.trim(), relationship = rel,
                    month = month!!, day = day!!, year = y, recurring = type.recursByDefault || y == null,
                    phone = phone.trim(), notes = notes.trim(), email = email.trim(),
                    autoEmail = autoEmail && type == OccasionType.BIRTHDAY && email.isNotBlank() && emailOk,
                    emailNote = emailNote.trim().take(1500),
                )
                onSave(o) { newId -> if (existing == null) nav.replace(Route.Detail(newId)) else nav.back() }
            }
        }
    }
}

@Composable
fun PickerField(label: String, value: String, options: List<String>, modifier: Modifier, error: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().height(64.dp).padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                .border(1.dp, if (error) C.Coral else C.Line, RoundedCornerShape(16.dp))
                .clickable { open = true }.padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (error) C.Coral else C.InkSoft)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = if (value.isBlank()) C.InkFaint else C.Ink, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp).background(C.Card)) {
            options.forEachIndexed { i, s ->
                DropdownMenuItem(text = { Text(s, fontSize = 15.sp) }, onClick = { onPick(i); open = false })
            }
        }
    }
}

/** v0.2: opt-in automatic birthday email for one person. */
@Composable
fun AutoEmailCard(hasEmail: Boolean, on: Boolean, note: String, first: String, onToggle: (Boolean) -> Unit, onNote: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("✉️  Auto-email on their birthday", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (hasEmail) "Ranti sends a birthday email from you at about 7 AM (Lagos time), even if you forget. Their name, email and birthday are kept on Ranti's server for this; switch it off any time."
                    else "Add their email above to switch this on.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(
                checked = on && hasEmail, enabled = hasEmail, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = C.Coral, checkedThumbColor = Color.White, uncheckedTrackColor = C.Line, uncheckedBorderColor = C.Line),
            )
        }
        if (on && hasEmail) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = note, onValueChange = { onNote(it.take(1500)) },
                label = { Text("Your message (optional)") },
                placeholder = { Text("Leave empty for a warm default wish to ${first.ifBlank { "them" }}") },
                minLines = 3, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Text("Only people you switch this on for are sent to Ranti's server (name, date, email, message). Each email has an unsubscribe link.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
