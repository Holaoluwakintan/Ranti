package com.holaoluwakintan.ranti

import android.app.Application
import com.holaoluwakintan.ranti.reminders.Notifier
import com.holaoluwakintan.ranti.reminders.ReminderEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RantiApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        appScope.launch {
            try {
                ReminderEngine.ensureSafetyNet(this@RantiApp)
                ReminderEngine.runAndReschedule(this@RantiApp)
            } catch (t: Throwable) {
                android.util.Log.e("Ranti", "startup schedule failed", t)
            }
        }
    }
}
