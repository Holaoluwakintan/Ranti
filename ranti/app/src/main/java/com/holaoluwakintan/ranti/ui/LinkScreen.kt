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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.holaoluwakintan.ranti.core.BirthdayLink
import com.holaoluwakintan.ranti.system.Share

/** v0.2: "Share my birthday link". Friends add their own birthdays; they land in Ranti automatically. */
@Composable
fun LinkScreen(vm: AppViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val code = remember { vm.linkCode() }
    var note by remember { mutableStateOf<String?>(null) }
    LinkContent(
        code = code, friendsAdded = vm.friendsAdded, syncing = vm.syncing, lastSyncAt = vm.lastSyncAt, note = note, nav = nav,
        onShare = { Share.shareSheet(ctx, BirthdayLink.shareText(code, vm.userName)) },
        onWhatsApp = { Share.whatsApp(ctx, BirthdayLink.shareText(code, vm.userName), "") },
        onCopy = { Share.copy(ctx, BirthdayLink.url(code)) },
        onCheck = {
            vm.syncFriends(force = true) { r ->
                note = when {
                    !r.ok -> "Couldn't reach Ranti's server. Check your internet and try again."
                    r.added.isEmpty() -> "No new birthdays yet. Share your link again on your status?"
                    r.added.size == 1 -> "🎉 ${r.added[0]} just added their birthday!"
                    else -> "🎉 ${r.added.size} friends just added their birthdays!"
                }
            }
        },
    )
}

@Composable
fun LinkContent(
    code: String, friendsAdded: Int, syncing: Boolean, lastSyncAt: Long, note: String?, nav: Nav,
    onShare: () -> Unit, onWhatsApp: () -> Unit, onCopy: () -> Unit, onCheck: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(C.Bg)) {
        Column(Modifier.fillMaxWidth().background(NightSky)) {
            TopBar("", onBack = { nav.back() }, dark = true)
            Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 26.dp)) {
                Text("YOUR BIRTHDAY LINK", style = MaterialTheme.typography.labelMedium, color = C.Gold)
                Spacer(Modifier.height(4.dp))
                Text("Let friends add their own birthdays", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Post this link on your WhatsApp status. Friends tap it, type their name and birthday, and they appear in your Ranti by themselves.",
                    style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.78f),
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            RCard(Modifier.fillMaxWidth()) {
                Text("🔗  Your link", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Text(BirthdayLink.url(code).removePrefix("https://"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), color = C.Coral)
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(C.WhatsApp)
                        .clickable(onClick = onWhatsApp),
                    contentAlignment = Alignment.Center,
                ) { Text("Share on WhatsApp / status", style = MaterialTheme.typography.labelLarge, color = Color.White) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("Share…", Modifier.weight(1f)) { onShare() }
                    SoftButton("Copy link", Modifier.weight(1f)) { onCopy() }
                }
            }
            Spacer(Modifier.height(14.dp))
            RCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎂", fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (friendsAdded) { 0 -> "No friends yet"; 1 -> "1 friend added so far"; else -> "$friendsAdded friends added so far" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (lastSyncAt > 0) "Last checked " + Fmt.ago(lastSyncAt) + ". Ranti checks when you open it and twice a day."
                            else "Ranti checks when you open it and twice a day.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                SoftButton(if (syncing) "Checking…" else "Check for new birthdays", Modifier.fillMaxWidth(), bg = C.Bg) { if (!syncing) onCheck() }
                if (note != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(note, style = MaterialTheme.typography.bodySmall, color = C.Ink)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.GoldSoft).padding(14.dp)) {
                Text("🔒", fontSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Only your Ranti can collect these entries: the link has a public code, and this phone keeps the secret key. What friends type goes only to you.",
                    style = MaterialTheme.typography.bodySmall, color = C.Ink,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
