package com.holaoluwakintan.ranti.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.holaoluwakintan.ranti.core.DateMath
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.data.Prefs
import com.holaoluwakintan.ranti.data.RantiDb
import com.holaoluwakintan.ranti.data.Reminder
import com.holaoluwakintan.ranti.data.BackupCodec
import com.holaoluwakintan.ranti.reminders.ReminderEngine
import com.holaoluwakintan.ranti.system.FoundDate
import com.holaoluwakintan.ranti.system.FriendSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class Upcoming(val o: Occasion, val date: LocalDate, val days: Long)

fun upcomingOf(list: List<Occasion>, day: LocalDate): List<Upcoming> =
    list.mapNotNull { o -> o.next(day)?.let { Upcoming(o, it, DateMath.daysUntil(day, it)) } }
        .sortedWith(compareBy({ it.days }, { it.o.name.lowercase() }))

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = RantiDb.get(app).occasions()
    private val rdao = RantiDb.get(app).reminders()
    val prefs = Prefs(app)

    val occasions: StateFlow<List<Occasion>?> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** v1.0: plain reminders. */
    val reminders: StateFlow<List<Reminder>?> = rdao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var themeMode by mutableStateOf(prefs.themeMode)
        private set

    fun setTheme(mode: String, systemDark: Boolean) {
        themeMode = mode; prefs.themeMode = mode
        com.holaoluwakintan.ranti.ui.C.dark = com.holaoluwakintan.ranti.ui.C.resolve(mode, systemDark)
    }

    var dayTimeMin by mutableIntStateOf(prefs.dayTimeMin)
        private set
    var prepTimeMin by mutableIntStateOf(prefs.prepTimeMin)
        private set

    fun setTimes(day: Int = dayTimeMin, prep: Int = prepTimeMin) {
        dayTimeMin = day; prepTimeMin = prep
        prefs.dayTimeMin = day; prefs.prepTimeMin = prep
        reschedule()
    }

    fun saveReminder(r: Reminder, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = if (r.id == 0L) rdao.insert(r) else { rdao.update(r); r.id }
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
            withContext(Dispatchers.Main) { onDone(id) }
        }
    }

    fun setReminderDone(r: Reminder, done: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (r.repeatKind == com.holaoluwakintan.ranti.core.Repeat.NONE || !done) rdao.update(r.copy(done = done))
            com.holaoluwakintan.ranti.reminders.Notifier.cancel(getApplication(), com.holaoluwakintan.ranti.reminders.Notifier.reminderNid(r.id))
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
        }
    }

    fun deleteReminder(r: Reminder) {
        viewModelScope.launch(Dispatchers.IO) {
            rdao.delete(r)
            com.holaoluwakintan.ranti.reminders.Notifier.cancel(getApplication(), com.holaoluwakintan.ranti.reminders.Notifier.reminderNid(r.id))
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
        }
    }

    fun clearDoneReminders() { viewModelScope.launch(Dispatchers.IO) { rdao.clearDone() } }

    /** v1.0: undo for deleting a person. */
    fun restore(o: Occasion) = save(o.copy(id = 0))

    // ---------- v1.0 backup ----------
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val p = prefs
        BackupCodec.encode(
            BackupCodec.Content(
                dao.all(), rdao.all(),
                BackupCodec.Settings(
                    userName = p.userName, tone = p.tone, headsUp = p.headsUp, nightlyEnabled = p.nightlyEnabled,
                    windowStart = p.windowStart, windowEnd = p.windowEnd, dayTimeMin = p.dayTimeMin, prepTimeMin = p.prepTimeMin,
                    themeMode = p.themeMode, ownerCode = p.ownerCode, ownerKey = p.ownerKey,
                ),
            )
        )
    }

    fun markBackedUp() { prefs.lastBackupAt = System.currentTimeMillis(); lastBackupAt = prefs.lastBackupAt }

    var lastBackupAt by mutableStateOf(prefs.lastBackupAt)
        private set

    /** Merges a backup into this phone (never deletes). Returns (people added, reminders added) or an error message. */
    suspend fun importJson(text: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        try {
            val c = BackupCodec.decode(text)
            val (o, r) = BackupCodec.merge(dao.all(), rdao.all(), c)
            if (o.isNotEmpty()) dao.insertAll(o)
            if (r.isNotEmpty()) rdao.insertAll(r)
            c.settings?.let { s ->
                if (prefs.userName.isBlank() && s.userName.isNotBlank()) prefs.userName = s.userName
                // Keep the same birthday link on a new phone (only if this phone hasn't shared its own yet).
                if (s.ownerCode.isNotBlank() && s.ownerKey.isNotBlank() && prefs.friendsAdded == 0 && prefs.lastPullId == 0L) {
                    prefs.ownerCode = s.ownerCode; prefs.ownerKey = s.ownerKey; prefs.registeredName = null
                }
            }
            withContext(Dispatchers.Main) { userName = prefs.userName }
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
            Result.success(o.size to r.size)
        } catch (e: IllegalArgumentException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Couldn't read that file."))
        }
    }

    /** v1.0: removes everything Ranti's server holds for this phone (link, friends' entries, auto-emails). */
    fun deleteCloudData(onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try { com.holaoluwakintan.ranti.system.FriendSync.deleteCloud(getApplication()) } catch (_: Throwable) { false }
            if (ok) {
                // Stop auto-emails locally too, so nothing is re-uploaded.
                dao.all().filter { it.autoEmail }.forEach { dao.update(it.copy(autoEmail = false)) }
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    var today by mutableStateOf(LocalDate.now())
        private set
    var resumeTick by mutableIntStateOf(0)
        private set
    var userName by mutableStateOf(prefs.userName)
        private set

    fun onResume() {
        today = LocalDate.now()
        resumeTick++
        reschedule()
        syncFriends(force = false)
    }

    /** v0.2: birthday link state for the UI. */
    var syncing by mutableStateOf(false)
        private set
    var lastSyncAt by mutableStateOf(prefs.lastSyncAt)
        private set
    var friendsAdded by mutableIntStateOf(prefs.friendsAdded)
        private set
    var lastSyncOk by mutableStateOf<Boolean?>(null)
        private set

    fun linkCode(): String { FriendSync.ensureIdentity(prefs); return prefs.ownerCode }

    fun syncFriends(force: Boolean, onDone: (FriendSync.Result) -> Unit = {}) {
        if (syncing) return
        syncing = true
        viewModelScope.launch(Dispatchers.IO) {
            val r = try { FriendSync.run(getApplication(), force) } catch (_: Throwable) { FriendSync.Result(emptyList(), false) }
            withContext(Dispatchers.Main) {
                syncing = false; lastSyncOk = r.ok; lastSyncAt = prefs.lastSyncAt; friendsAdded = prefs.friendsAdded
                onDone(r)
            }
        }
    }

    fun setName(n: String) { userName = n.trim(); prefs.userName = n.trim() }

    fun upcoming(list: List<Occasion>, day: LocalDate = today): List<Upcoming> = upcomingOf(list, day)

    fun reschedule() {
        viewModelScope.launch(Dispatchers.IO) {
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
        }
    }

    fun save(o: Occasion, onDone: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val before = if (o.id == 0L) null else dao.byId(o.id)
            val id = if (o.id == 0L) dao.insert(o) else { dao.update(o); o.id }
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
            withContext(Dispatchers.Main) { onDone(id) }
            // Auto-email list changed? Push it now (only opted-in people ever leave the phone).
            if (o.autoEmail || before?.autoEmail == true) {
                try { FriendSync.run(getApplication(), force = true) } catch (_: Throwable) {}
            }
        }
    }

    fun update(o: Occasion) {
        viewModelScope.launch(Dispatchers.IO) { dao.update(o) }
    }

    fun delete(o: Occasion) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.delete(o)
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
            if (o.autoEmail) try { FriendSync.run(getApplication(), force = true) } catch (_: Throwable) {}
        }
    }

    fun importAll(found: List<FoundDate>, onDone: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = dao.all().map { key(it.name, it.month, it.day) }.toSet()
            val fresh = found.filter { key(it.name, it.month, it.day) !in existing }
                .distinctBy { key(it.name, it.month, it.day) }.map {
                Occasion(
                    name = it.name, phone = it.phone, email = it.email, type = it.type.name, month = it.month, day = it.day,
                    year = it.year, recurring = true, relationship = "", source = "contacts",
                )
            }
            if (fresh.isNotEmpty()) dao.insertAll(fresh)
            try { ReminderEngine.runAndReschedule(getApplication()) } catch (_: Throwable) {}
            withContext(Dispatchers.Main) { onDone(fresh.size) }
        }
    }

    /** v0.3: a birthday typed in by hand next to a contact in the "ask" list. */
    fun addManual(name: String, phone: String, month: Int, day: Int, year: Int?, onDone: () -> Unit = {}) {
        save(Occasion(name = name.trim(), phone = phone, type = OccasionType.BIRTHDAY.name, month = month, day = day, year = year,
            recurring = true, relationship = "", source = "manual")) { onDone() }
    }

    fun isDuplicate(list: List<Occasion>, f: FoundDate) = list.any { key(it.name, it.month, it.day) == key(f.name, f.month, f.day) }

    private fun key(name: String, m: Int, d: Int) = name.trim().lowercase() + "|" + m + "|" + d
}
