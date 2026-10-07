package com.holaoluwakintan.ranti.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.core.Messages
import com.holaoluwakintan.ranti.core.Tone
import com.holaoluwakintan.ranti.system.Share

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageScreen(vm: AppViewModel, nav: Nav, id: Long) {
    val list by vm.occasions.collectAsStateWithLifecycle()
    val o = list?.firstOrNull { it.id == id } ?: return
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        MessageContent(o, vm.today, nav, Tone.from(vm.prefs.tone), onTone = { vm.prefs.tone = it.name }, onUpdate = { vm.update(it) })
        // v1.0: it's their day: a little celebration.
        if (o.next(vm.today) == vm.today) Confetti()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageContent(
    o: com.holaoluwakintan.ranti.data.Occasion, today: java.time.LocalDate, nav: Nav, initialTone: Tone,
    onTone: (Tone) -> Unit, onUpdate: (com.holaoluwakintan.ranti.data.Occasion) -> Unit,
) {
    val ctx = LocalContext.current
    val occ = o.next(today)
    val years = occ?.let { DateMath.yearsOn(o.year, it) }
    var tone by remember { mutableStateOf(initialTone) }
    var index by remember { mutableIntStateOf(0) }
    var text by remember(tone, index) { mutableStateOf(Messages.draft(o.kind, tone, index, o.firstName, o.customLabel, years)) }

    fun markDone() { if (occ != null) onUpdate(o.scopedTo(occ).copy(messageDone = true)) }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        TopBar("", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Text("A message for ${o.firstName}", style = MaterialTheme.typography.headlineMedium)
            Text(
                if (occ != null) Fmt.subtitle(o, occ) else o.title,
                style = MaterialTheme.typography.bodyMedium,
            )
            SectionLabel("Tone")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tone.entries.forEach { t ->
                    Pill("${t.emoji} ${t.label}", tone == t, { tone = t; index = 0; onTone(t) })
                }
            }
            Spacer(Modifier.height(18.dp))
            RCard(Modifier.fillMaxWidth(), padding = 6.dp) {
                Row(Modifier.padding(start = 12.dp, top = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("DRAFT ${index % Messages.options(o.kind, tone).size + 1} OF ${Messages.options(o.kind, tone).size}", style = MaterialTheme.typography.labelSmall, color = C.Coral, modifier = Modifier.weight(1f))
                    TextButton(onClick = { index++ }) { Text("↻  Another one", color = C.Coral, style = MaterialTheme.typography.titleSmall) }
                }
                TextField(
                    value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = C.Coral,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Tip: edit it to sound like you. Add a memory only the two of you share.", style = MaterialTheme.typography.bodySmall)
            val to = listOfNotNull(
                o.phone.takeIf { it.isNotBlank() }?.let { "📱 $it" },
                o.email.takeIf { it.isNotBlank() }?.let { "✉️ $it" },
            )
            if (to.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Goes to: " + to.joinToString("   "), style = MaterialTheme.typography.bodySmall, color = C.InkSoft)
            } else {
                Spacer(Modifier.height(6.dp))
                Text("No number saved, so WhatsApp lets you pick the chat.", style = MaterialTheme.typography.bodySmall, color = C.InkSoft)
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(C.WhatsApp)
                    .clickable { markDone(); Share.whatsApp(ctx, text, o.phone) },
                contentAlignment = Alignment.Center,
            ) { Text("Open in WhatsApp", style = MaterialTheme.typography.labelLarge, color = Color.White) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("💬 SMS", Modifier.weight(1f), bg = C.Card) { markDone(); Share.sms(ctx, text, o.phone) }
                SoftButton("✉️ Email", Modifier.weight(1f), bg = C.Card) {
                    markDone(); Share.email(ctx, text, o.email, Messages.subject(o.kind, o.firstName, o.customLabel))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton("Share…", Modifier.weight(1f), bg = C.Card) { markDone(); Share.shareSheet(ctx, text) }
                SoftButton("Copy", Modifier.weight(1f), bg = C.Card) { Share.copy(ctx, text) }
            }
        }
    }
}
