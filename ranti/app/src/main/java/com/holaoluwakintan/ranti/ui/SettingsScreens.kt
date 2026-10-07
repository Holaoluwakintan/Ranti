package com.holaoluwakintan.ranti.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holaoluwakintan.ranti.BuildConfig
import com.holaoluwakintan.ranti.reminders.Notifier
import com.holaoluwakintan.ranti.reminders.ReminderEngine
import com.holaoluwakintan.ranti.system.ContactsImport
import com.holaoluwakintan.ranti.system.Share
import com.holaoluwakintan.ranti.core.AskContacts
import com.holaoluwakintan.ranti.core.BirthdayLink
import com.holaoluwakintan.ranti.core.ContactEntry
import com.holaoluwakintan.ranti.core.PhoneNorm
import androidx.activity.compose.BackHandler
import com.holaoluwakintan.ranti.system.FoundDate
import com.holaoluwakintan.ranti.system.Reliability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun hourLabel(h: Int): String = when {
    h == 0 || h == 24 -> "12am"
    h < 12 -> "${h}am"
    h == 12 -> "12pm"
    else -> "${h - 12}pm"
}

/** Status rows for the things Xiaomi needs before alarms are reliable. Shared by onboarding and settings. */
@Composable
fun ReliabilityChecklist(vm: AppViewModel) {
    val ctx = LocalContext.current
    val tick = vm.resumeTick
    var notifOk by remember(tick) { mutableStateOf(Notifier.canPost(ctx)) }
    val exactOk = remember(tick) { ReminderEngine.canExact(ctx) }
    val batteryOk = remember(tick) { Reliability.isIgnoringBatteryOptimizations(ctx) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifOk = granted && Notifier.canPost(ctx)
        if (!granted) Reliability.openNotificationSettings(ctx)
        vm.reschedule()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CheckRow("🔔", "Allow notifications", "So reminders can reach you", notifOk) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else Reliability.openNotificationSettings(ctx)
        }
        CheckRow("⏰", "Alarms & reminders", if (exactOk) "On time, to the minute" else "Allow so reminders ring on time", exactOk) {
            Reliability.openExactAlarmSettings(ctx)
        }
        CheckRow("🚀", "Autostart", if (Reliability.isXiaomi) "Xiaomi: switch Ranti ON" else "Let Ranti start after a restart", null) {
            Reliability.openAutostart(ctx)
        }
        CheckRow("🔋", "Battery: No restrictions", if (batteryOk) "Ranti won't be put to sleep" else "Choose \"No restrictions\" for Ranti", if (batteryOk) true else null) {
            Reliability.openBattery(ctx)
        }
    }
}

@Composable
private fun CheckRow(emoji: String, title: String, sub: String, ok: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Card).border(1.dp, C.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(C.Bg), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall)
        }
        when (ok) {
            true -> Box(Modifier.size(28.dp).clip(CircleShape).background(C.Mint), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontWeight = FontWeight.SemiBold) }
            false -> Text("Allow", style = MaterialTheme.typography.titleSmall, color = C.Coral)
            null -> Text("Open", style = MaterialTheme.typography.titleSmall, color = C.Coral)
        }
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    var nightly by remember { mutableStateOf(vm.prefs.nightlyEnabled) }
    var start by remember { mutableIntStateOf(vm.prefs.windowStart) }
    var end by remember { mutableIntStateOf(vm.prefs.windowEnd) }
    var name by remember { mutableStateOf(vm.userName) }

    fun applyWindow() {
        vm.prefs.windowStart = start; vm.prefs.windowEnd = end; vm.prefs.nightlyNextAt = 0L
        ReminderEngine.scheduleNightly(ctx, fromAlarm = false, forceNew = true)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 130.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionLabel("You")
        OutlinedTextField(
            value = name, onValueChange = { name = it; vm.setName(it) }, label = { Text("Your first name") }, singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = fieldColors(),
        )

        AppearanceSection(vm)

        SectionLabel("Nightly question")
        RCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ask me each evening", style = MaterialTheme.typography.titleMedium)
                    Text("\"Is there someone's birthday you'd like to remember?\" at a random time in your window.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = nightly,
                    onCheckedChange = { nightly = it; vm.prefs.nightlyEnabled = it; vm.prefs.nightlyNextAt = 0L; ReminderEngine.scheduleNightly(ctx, fromAlarm = false, forceNew = true) },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Coral, checkedThumbColor = Color.White, uncheckedTrackColor = C.Line, uncheckedBorderColor = C.Line),
                )
            }
            if (nightly) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField("From", hourLabel(start), (12..23).map { hourLabel(it) }, Modifier.weight(1f), false) { i ->
                        start = 12 + i; if (end <= start) end = (start + 1).coerceAtMost(24); applyWindow()
                    }
                    PickerField("Until", hourLabel(end), (13..24).map { hourLabel(it) }, Modifier.weight(1f), false) { i ->
                        end = 13 + i; if (start >= end) start = end - 1; applyWindow()
                    }
                }
            }
        }

        SectionLabel("Reminders")
        var headsUp by remember { mutableStateOf(vm.prefs.headsUp) }
        RCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("1-day-before heads-up", style = MaterialTheme.typography.titleMedium)
                    Text("\"Tomorrow is Tolu's birthday\" the day before. The ping on the day always comes.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = headsUp,
                    onCheckedChange = { headsUp = it; vm.prefs.headsUp = it; vm.reschedule() },
                    colors = SwitchDefaults.colors(checkedTrackColor = C.Coral, checkedThumbColor = Color.White, uncheckedTrackColor = C.Line, uncheckedBorderColor = C.Line),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        ReminderTimesSection(vm)

        SectionLabel("Make reminders reliable")
        Text("Xiaomi phones close apps to save battery. These keep Ranti's alarms alive, even offline or after a restart.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        ReliabilityChecklist(vm)
        Spacer(Modifier.height(10.dp))
        SoftButton("Send a test reminder", Modifier.fillMaxWidth()) { Notifier.postTest(ctx) }

        SectionLabel("Your dates")
        SoftButton("Import birthdays from contacts", Modifier.fillMaxWidth(), bg = C.Card) { nav.push(Route.Import) }
        Spacer(Modifier.height(10.dp))
        SoftButton("🔗  Share my birthday link", Modifier.fillMaxWidth(), bg = C.GoldSoft) { nav.push(Route.Link) }

        SectionLabel("Backup")
        DataSection(vm)

        Spacer(Modifier.height(28.dp))
        Text("Ranti ${BuildConfig.VERSION_NAME} · works offline · your dates stay on this phone (only auto-email people are synced)", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("rántí: “remember” in Yoruba", style = MaterialTheme.typography.bodySmall, color = C.InkFaint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(vm.userName) }
    Column(Modifier.fillMaxSize().background(if (step == 0) NightSky else androidx.compose.ui.graphics.Brush.linearGradient(listOf(C.Bg, C.Bg))).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i ->
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) C.Coral else if (step == 0) Color.White.copy(alpha = 0.2f) else C.Line))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(40.dp))
                    Box(Modifier.size(72.dp).clip(CircleShape).background(Sunset), contentAlignment = Alignment.Center) {
                        Text("R", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, color = C.Plum)
                    }
                    Spacer(Modifier.height(28.dp))
                    Text("Ranti", fontFamily = Fraunces, fontWeight = FontWeight.SemiBold, fontSize = 52.sp, color = Color.White, lineHeight = 56.sp)
                    Text("Never be the one who forgot.", fontFamily = Fraunces, fontSize = 24.sp, color = C.Gold, lineHeight = 30.sp)
                    Spacer(Modifier.height(16.dp))
                    Text("Ranti remembers the birthdays, anniversaries and big days of the people you love, and walks you to each one with time to plan.", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(32.dp))
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text("What should I call you?") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = C.Gold, unfocusedBorderColor = Color.White.copy(alpha = 0.3f), focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White, focusedLabelColor = C.Gold, unfocusedLabelColor = Color.White.copy(alpha = 0.7f), cursorColor = C.Gold,
                        ),
                    )
                }
                1 -> {
                    Spacer(Modifier.height(24.dp))
                    Text("How Ranti keeps you ready", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(20.dp))
                    HowRow("🌙", "A question each night", "Between 8 and 10pm: \"Is there someone's birthday you'd like to remember?\" One tap to add them.")
                    HowRow("🎁", "7 days before", "Pick a gift while there's time.")
                    HowRow("✍️", "5 days before", "Write your message. Ranti drafts it in your tone.")
                    HowRow("📞", "1 day before", "Plan the call.")
                    HowRow("🎉", "On the day, 7am", "Send your wishes on WhatsApp with one tap.")
                    HowRow("📴", "Works offline", "Your dates stay on your phone. No data needed.")
                }
                else -> {
                    Spacer(Modifier.height(24.dp))
                    Text("Let Ranti ring on time", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (Reliability.isXiaomi) "Your Xiaomi closes apps to save battery. Tap each one below and allow it, so your reminders are never missed."
                        else "Tap each one below and allow it, so your reminders are never missed.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(20.dp))
                    ReliabilityChecklist(vm)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            GradientButton(
                when (step) { 0 -> "Get started"; 1 -> "Next"; else -> "I'm ready" },
                Modifier.fillMaxWidth(),
            ) {
                if (step == 0) vm.setName(name)
                if (step < 2) step++ else { vm.prefs.reliabilitySeen = true; onDone() }
            }
            if (step == 2) {
                TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("I'll do this later", color = C.InkSoft) }
            }
        }
    }
}

@Composable
private fun HowRow(emoji: String, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(C.CoralSoft), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ImportScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val list by vm.occasions.collectAsStateWithLifecycle()
    var state by remember { mutableStateOf("ask") } // ask | loading | list | denied | done | asking | askdone
    var found by remember { mutableStateOf<List<FoundDate>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<ContactEntry>>(emptyList()) }
    val chosen = remember { mutableStateMapOf<Int, Boolean>() }
    val picked = remember { mutableStateMapOf<String, Boolean>() }
    var asked by remember { mutableStateOf(vm.prefs.asked()) }
    var tab by remember { mutableIntStateOf(0) } // 0 = saved birthdays, 1 = ask friends
    var query by remember { mutableStateOf("") }
    var queue by remember { mutableStateOf<List<ContactEntry>>(emptyList()) }
    var qi by remember { mutableIntStateOf(0) }
    var sentCount by remember { mutableIntStateOf(0) }
    var lastAsked by remember { mutableStateOf<String?>(null) }
    var manualFor by remember { mutableStateOf<ContactEntry?>(null) }
    var imported by remember { mutableIntStateOf(0) }
    var trigger by remember { mutableIntStateOf(0) }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { state = "loading"; trigger++ } else state = "denied"
    }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val res = withContext(Dispatchers.IO) { try { ContactsImport.read(ctx) } catch (_: Exception) { emptyList() } }
        val all = withContext(Dispatchers.IO) { try { ContactsImport.readAll(ctx) } catch (_: Exception) { emptyList() } }
        found = res
        contacts = all
        chosen.clear()
        res.forEachIndexed { i, f -> chosen[i] = !vm.isDuplicate(list.orEmpty(), f) }
        tab = if (res.isEmpty()) 1 else 0
        state = "list"
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) { state = "loading"; trigger++ }
        else perm.launch(Manifest.permission.READ_CONTACTS)
    }
    // People already in Ranti (same number, or same name) are greyed out in the ask list.
    val inRanti = remember(list, contacts) {
        val occ = list.orEmpty()
        val nums = occ.mapNotNull { o -> o.phone.takeIf { it.isNotBlank() }?.let { PhoneNorm.international(it) } }.toSet()
        val names = occ.map { it.name.trim().lowercase() }.toSet()
        contacts.filter { it.intl in nums || it.name.trim().lowercase() in names }.map { it.intl }.toSet()
    }
    val code = remember { vm.linkCode() }
    fun messageFor(c: ContactEntry) = AskContacts.message(c.name, BirthdayLink.url(code), vm.userName)
    fun advance(sent: Boolean) {
        val c = queue.getOrNull(qi) ?: return
        if (sent) {
            vm.prefs.markAsked(c.intl); asked = vm.prefs.asked(); sentCount++
            lastAsked = AskContacts.firstName(c.name).ifEmpty { c.name }
            picked.remove(c.intl)
        } else lastAsked = null
        qi++
        if (qi >= queue.size) state = "askdone"
    }
    BackHandler(enabled = state == "asking" || state == "askdone") { state = "list" }

    manualFor?.let { c ->
        ManualDateDialog(c.name, onSave = { m, d, y ->
            vm.addManual(c.name, c.phone, m, d, y)
            picked.remove(c.intl)
            manualFor = null
        }, onDismiss = { manualFor = null })
    }

    when (state) {
        "asking" -> {
            val c = queue.getOrNull(qi)
            if (c != null) AskFlow(
                current = c, index = qi, total = queue.size, message = messageFor(c), lastAsked = lastAsked,
                onWhatsApp = { Share.whatsApp(ctx, messageFor(c), "+" + c.intl); advance(true) },
                onSms = { Share.sms(ctx, messageFor(c), c.phone); advance(true) },
                onSkip = { advance(false) },
                onStop = { state = "list" },
            )
            return
        }
        "askdone" -> { AskDone(sentCount, onMore = { state = "list" }, onHome = { nav.tab(Route.Home) }); return }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar("Import from contacts", onBack = { nav.back() })
        when (state) {
            "ask", "denied" -> Column(Modifier.weight(1f).padding(24.dp)) {
                Text("📇", fontSize = 44.sp)
                Spacer(Modifier.height(12.dp))
                Text("Get birthdays from your contacts", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ranti adds any birthdays already saved in your contacts (including Google contacts). For everyone else, it helps you ask them on WhatsApp in a few taps. It all stays on this phone, and you choose who.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (state == "denied") {
                    Spacer(Modifier.height(12.dp))
                    Text("Contacts permission was declined. You can allow it in Settings, or add people by hand.", style = MaterialTheme.typography.bodySmall, color = C.Coral)
                    TextButton(onClick = { Reliability.openAppInfo(ctx) }) { Text("Open app settings", color = C.Coral) }
                }
                Spacer(Modifier.weight(1f))
                GradientButton("Open my contacts", Modifier.fillMaxWidth()) { start() }
            }
            "loading" -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Coral) }
            "done" -> Column(Modifier.weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                Text("🎉", fontSize = 52.sp)
                Text("$imported added", style = MaterialTheme.typography.headlineMedium)
                Text("Ranti will count down to each of them.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                GradientButton("Ask the rest for theirs", Modifier.fillMaxWidth()) { tab = 1; state = "list" }
                Spacer(Modifier.height(10.dp))
                SoftButton("See who's next", Modifier.fillMaxWidth(), bg = C.Card) { nav.tab(Route.Home) }
            }
            else -> {
                if (found.isNotEmpty()) ImportTabs(tab, found.size, contacts.size) { tab = it }
                if (tab == 0 && found.isNotEmpty()) {
                    val dups = found.map { vm.isDuplicate(list.orEmpty(), it) }
                    ImportList(found, dups, chosen, onToggle = { i, v -> chosen[i] = v }) {
                        val pickList = found.filterIndexed { i, _ -> chosen[i] == true }
                        vm.importAll(pickList) { n -> imported = n; state = "done" }
                    }
                } else {
                    AskList(
                        contacts = contacts, noneSaved = found.isEmpty(), query = query, onQuery = { query = it },
                        picked = picked, onToggle = { k, v -> picked[k] = v }, asked = asked, inRanti = inRanti,
                        onManual = { manualFor = it },
                        onAsk = {
                            queue = contacts.filter { picked[it.intl] == true && it.intl !in inRanti }
                            qi = 0; sentCount = 0; lastAsked = null
                            if (queue.isNotEmpty()) { vm.syncFriends(force = true); state = "asking" }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ImportTabs(tab: Int, saved: Int, all: Int, onTab: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill("🎂 Saved birthdays ($saved)", tab == 0, { onTab(0) })
        Pill("💬 Ask friends ($all)", tab == 1, { onTab(1) })
    }
}

/** The checklist preview before anything is saved (stateless, so it can be screenshot-tested). */
@Composable
fun ColumnScope.ImportList(found: List<FoundDate>, dups: List<Boolean>, chosen: Map<Int, Boolean>, onToggle: (Int, Boolean) -> Unit, onAdd: () -> Unit) {
    val count = chosen.count { it.value }
    val selectable = found.indices.filter { !dups[it] }
    val allOn = selectable.isNotEmpty() && selectable.all { chosen[it] == true }
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        item {
            Row(Modifier.padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Found ${found.size}" + (if (dups.any { it }) " (${dups.count { it }} already saved)" else "") + ". Tick who Ranti should remember.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                if (selectable.isNotEmpty()) TextButton(onClick = { selectable.forEach { onToggle(it, !allOn) } }) {
                    Text(if (allOn) "Clear" else "Select all", color = C.Coral, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        items(found.size) { i ->
            val f = found[i]
            val dup = dups[i]
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(C.Card)
                    .clickable(enabled = !dup) { onToggle(i, !(chosen[i] ?: false)) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(f.name, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, color = if (dup) C.InkFaint else C.Ink)
                    Text(
                        "${f.type.emoji} ${f.day} ${Fmt.months[f.month - 1]}" + (f.year?.let { " $it" } ?: "") +
                            (if (f.phone.isNotBlank()) "  ·  📱" else "") + (if (f.email.isNotBlank()) "  ·  ✉️" else "") +
                            if (dup) "  ·  already saved" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Checkbox(checked = !dup && chosen[i] == true, onCheckedChange = { if (!dup) onToggle(i, it) }, enabled = !dup, colors = CheckboxDefaults.colors(checkedColor = C.Coral))
            }
        }
        item {
            Text("📱 number and ✉️ email are saved too, so wishes are one tap.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
        }
    }
    Box(Modifier.padding(20.dp)) {
        GradientButton(if (count == 0) "Choose people" else "Add $count ${if (count == 1) "person" else "people"}", Modifier.fillMaxWidth(), enabled = count > 0) { onAdd() }
    }
}
