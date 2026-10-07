package com.holaoluwakintan.ranti.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.holaoluwakintan.ranti.core.BirthdayLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

const val PRIVACY_URL = BirthdayLink.BASE + "/privacy"

@Composable
fun AppearanceSection(vm: AppViewModel) {
    val sys = isSystemInDarkTheme()
    SectionLabel("Appearance")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("SYSTEM" to "📱 Auto", "LIGHT" to "☀️ Light", "DARK" to "🌙 Dark").forEach { (k, label) ->
            Pill(label, vm.themeMode == k, { vm.setTheme(k, sys) })
        }
    }
}

@Composable
fun SettingRow(emoji: String, title: String, sub: String, trailing: String? = null, onClick: () -> Unit) {
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
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.titleSmall, color = C.Coral)
    }
}

@Composable
fun ReminderTimesSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    fun pick(cur: Int, onPick: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, cur / 60, cur % 60, false).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow("🎉", "On the day", "The \"It's Tolu's birthday\" ping", RFmt.minutes(vm.dayTimeMin)) { pick(vm.dayTimeMin) { vm.setTimes(day = it) } }
        SettingRow("⏳", "Countdown pings", "7, 5 and 1 day before, and snoozes to tomorrow", RFmt.minutes(vm.prepTimeMin)) { pick(vm.prepTimeMin) { vm.setTimes(prep = it) } }
    }
}

/** v1.0: backup, restore, privacy, delete cloud data. */
@Composable
fun DataSection(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var restoreMsg by remember { mutableStateOf<String?>(null) }
    val fileName = "ranti-backup-${LocalDate.now()}.json"

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = try {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null }
            } catch (_: Exception) { false }
            if (ok) vm.markBackedUp()
            Toast.makeText(ctx, if (ok) "Backup saved ✓" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val text = try { withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } } } catch (_: Exception) { null }
            val r = if (text == null) Result.failure(IllegalArgumentException("Couldn't read that file.")) else vm.importJson(text)
            busy = false
            restoreMsg = r.fold(
                onSuccess = { (o, rm) -> if (o + rm == 0) "Everything in that backup is already on this phone." else "Restored $o ${if (o == 1) "date" else "dates"} and $rm ${if (rm == 1) "reminder" else "reminders"}." },
                onFailure = { it.message ?: "Couldn't read that file." },
            )
        }
    }

    Text(
        if (vm.lastBackupAt == 0L) "Your dates live only on this phone. A backup file keeps them safe if the phone is lost or reset."
        else "Last backup: ${Fmt.ago(vm.lastBackupAt)}.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow("📤", "Send backup to myself", "WhatsApp it to yourself, or save to Drive or email") {
            scope.launch {
                try {
                    val json = vm.exportJson()
                    val file = withContext(Dispatchers.IO) {
                        val dir = File(ctx.cacheDir, "backup").apply { mkdirs() }
                        dir.listFiles()?.forEach { it.delete() }
                        File(dir, fileName).apply { writeText(json, Charsets.UTF_8) }
                    }
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
                    val send = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                        .putExtra(Intent.EXTRA_SUBJECT, "My Ranti backup").putExtra(Intent.EXTRA_TEXT, "My Ranti backup (open Ranti → Settings → Restore from file).")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(send, "Save your backup…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    vm.markBackedUp()
                } catch (_: Exception) {
                    Toast.makeText(ctx, "Couldn't share the backup", Toast.LENGTH_SHORT).show()
                }
            }
        }
        SettingRow("💾", "Save backup file", "Choose a folder on this phone or Drive") { saver.launch(fileName) }
        SettingRow("📥", "Restore from file", if (busy) "Reading…" else "Adds what's missing. Nothing is deleted.") {
            opener.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
        }
    }
    restoreMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { restoreMsg = null },
            title = { Text("Restore") }, text = { Text(msg) },
            confirmButton = { TextButton(onClick = { restoreMsg = null }) { Text("OK", color = C.Coral) } },
            containerColor = C.Card,
        )
    }

    SectionLabel("Privacy")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingRow("🔒", "Privacy policy", "What stays on your phone and what doesn't") {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
        SettingRow("🗑️", "Delete my cloud data", "Removes your birthday link, friends' entries and auto-emails from Ranti's server") { confirmDelete = true }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete your cloud data?") },
            text = { Text("Your birthday link stops working, friends' entries waiting on the server are deleted, and automatic birthday emails are switched off. People already saved on this phone stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteCloudData { ok -> Toast.makeText(ctx, if (ok) "Cloud data deleted ✓" else "No connection. Try again online.", Toast.LENGTH_LONG).show() }
                }) { Text("Delete", color = C.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = C.InkSoft) } },
            containerColor = C.Card,
        )
    }
}
