package com.holaoluwakintan.ranti

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import com.holaoluwakintan.ranti.reminders.Notifier
import com.holaoluwakintan.ranti.ui.AppViewModel
import com.holaoluwakintan.ranti.ui.RantiRoot
import com.holaoluwakintan.ranti.ui.RantiTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    /** A deep link from a notification: (target, id). */
    val pendingOpen = mutableStateOf<Pair<String, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // v1.0: pick light/dark before the first frame (Settings → Appearance; Auto follows the phone).
        val systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        com.holaoluwakintan.ranti.ui.C.dark = com.holaoluwakintan.ranti.ui.C.resolve(com.holaoluwakintan.ranti.data.Prefs(this).themeMode, systemDark)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        readIntent(intent)
        setContent { RantiTheme { RantiRoot(vm, pendingOpen) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    private fun readIntent(i: Intent?) {
        val open = i?.getStringExtra(Notifier.EXTRA_OPEN) ?: return
        pendingOpen.value = open to i.getLongExtra(Notifier.EXTRA_ID, 0L)
        i.removeExtra(Notifier.EXTRA_OPEN)
    }
}
