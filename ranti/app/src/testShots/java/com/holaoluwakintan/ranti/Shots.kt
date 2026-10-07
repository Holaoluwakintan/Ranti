package com.holaoluwakintan.ranti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.core.Tone
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class Shots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private val today = LocalDate.of(2026, 10, 6)
    private val people = listOf(
        Occasion(id = 1, name = "Tolu Adeyemi", relationship = "Friend", month = 10, day = 11, year = 1997, phone = "08031234567",
            notes = "Loves Afrobeats and suya. Wants to visit Zanzibar. Wears size 40 shoes.", giftIdea = "Burna Boy concert tickets", createdAt = 0),
        Occasion(id = 2, name = "Pastor Femi", relationship = "Church", month = 10, day = 14, year = null, createdAt = 0),
        Occasion(id = 3, name = "Dami Okafor", relationship = "Colleague", month = 10, day = 20, year = 1994, createdAt = 0),
        Occasion(id = 4, name = "Ada & Kunle", relationship = "Family", type = OccasionType.ANNIVERSARY.name, month = 10, day = 25, year = 2019, createdAt = 0),
        Occasion(id = 5, name = "Mummy", relationship = "Family", month = 11, day = 2, year = null, createdAt = 0),
        Occasion(id = 6, name = "Kemi's wedding", relationship = "Friend", type = OccasionType.WEDDING.name, month = 12, day = 12, year = 2026, recurring = false, createdAt = 0),
    )

    private fun nav(r: Route) = Nav(mutableStateListOf(r))

    @Composable
    private fun Frame(content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
        // C.dark is set by the test before snapshot() when a dark shot is wanted.
        RantiTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { content() } }
    }

    @Test fun a_home() = paparazzi.snapshot {
        Frame {
            HomeContent(people, today, "Michael", nav(Route.Home))
            Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Home, nav(Route.Home), Modifier) }
        }
    }

    @Test fun b_plan() = paparazzi.snapshot {
        Frame { DetailContent(people[0].scopedTo(LocalDate.of(2026, 10, 11)).copy(giftDone = true), today, nav(Route.Home), {}, {}) }
    }

    @Test fun c_message() = paparazzi.snapshot {
        Frame { MessageContent(people[0], today, nav(Route.Home), Tone.WARM, {}, {}) }
    }

    private val fakeRegistryOwner = object : androidx.activity.result.ActivityResultRegistryOwner {
        override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I,
                options: androidx.core.app.ActivityOptionsCompat?,
            ) {}
        }
    }

    @Test fun d_capture() = paparazzi.snapshot {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.activity.compose.LocalActivityResultRegistryOwner provides fakeRegistryOwner
        ) {
            Frame { EditContent(null, true, nav(Route.Home)) { _, _ -> } }
        }
    }

    // ---------- v0.2 ----------

    @Test fun e_wish_today() = paparazzi.snapshot {
        val tolu = people[0].copy(month = 10, day = 6, email = "tolu.adeyemi@gmail.com")
        Frame { MessageContent(tolu, today, nav(Route.Home), Tone.PRAYERFUL, {}, {}) }
    }

    @Test fun f_import_preview() = paparazzi.snapshot {
        val found = listOf(
            com.holaoluwakintan.ranti.system.FoundDate("Adaeze Nwosu", "08031112222", OccasionType.BIRTHDAY, 3, 14, 1995, "ada@gmail.com"),
            com.holaoluwakintan.ranti.system.FoundDate("Bayo Ogunleye", "", OccasionType.BIRTHDAY, 7, 2, null, "bayo.o@yahoo.com"),
            com.holaoluwakintan.ranti.system.FoundDate("Chioma & Emeka", "", OccasionType.ANNIVERSARY, 12, 20, 2018),
            com.holaoluwakintan.ranti.system.FoundDate("Dami Okafor", "08098887777", OccasionType.BIRTHDAY, 10, 20, 1994),
            com.holaoluwakintan.ranti.system.FoundDate("Funke Akindele", "08123456789", OccasionType.BIRTHDAY, 8, 24, null),
            com.holaoluwakintan.ranti.system.FoundDate("Pastor Femi", "08055554444", OccasionType.BIRTHDAY, 10, 14, null),
        )
        val dups = listOf(false, false, false, true, false, true)
        val chosen = mapOf(0 to true, 1 to true, 2 to false, 4 to true)
        Frame {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                TopBar("Import from contacts", onBack = {})
                ImportList(found, dups, chosen, { _, _ -> }) {}
            }
        }
    }

    @Test fun g_birthday_link() = paparazzi.snapshot {
        Frame {
            LinkContent(
                code = "k7m2xq9ahd", friendsAdded = 3, syncing = false, lastSyncAt = System.currentTimeMillis() - 2 * 3600_000L,
                note = "🎉 3 friends just added their birthdays!", nav = nav(Route.Home), onShare = {}, onWhatsApp = {}, onCopy = {}, onCheck = {},
            )
        }
    }

    @Test fun h_edit_auto_email() = paparazzi.snapshot {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.activity.compose.LocalActivityResultRegistryOwner provides fakeRegistryOwner
        ) {
            val p = people[0].copy(email = "tolu.adeyemi@gmail.com", autoEmail = true,
                emailNote = "Happy birthday Tolu! 🎉 God bless your new year with joy and big wins. Have the best day!")
            Frame { AutoEmailPreview(p) }
        }
    }

    // ---------- v0.3: ask for birthdays ----------
    private val askContacts = com.holaoluwakintan.ranti.core.AskContacts.clean(listOf(
        com.holaoluwakintan.ranti.core.PhoneRow(1, "Adaeze Nwosu", "0803 111 2222"),
        com.holaoluwakintan.ranti.core.PhoneRow(2, "Bayo Ogunleye", "0705 333 4444"),
        com.holaoluwakintan.ranti.core.PhoneRow(3, "Chioma Eze", "+234 809 555 6666"),
        com.holaoluwakintan.ranti.core.PhoneRow(4, "Dami Okafor", "0809 888 7777"),
        com.holaoluwakintan.ranti.core.PhoneRow(5, "Emeka Obi", "0816 123 9876"),
        com.holaoluwakintan.ranti.core.PhoneRow(6, "Funke Akindele", "0812 345 6789"),
        com.holaoluwakintan.ranti.core.PhoneRow(7, "Gbenga Adeyemi", "0703 222 1111"),
        com.holaoluwakintan.ranti.core.PhoneRow(8, "Halima Bello", "0906 777 1212"),
        com.holaoluwakintan.ranti.core.PhoneRow(9, "Ifeoma Nnaji", "0805 404 5050"),
        com.holaoluwakintan.ranti.core.PhoneRow(10, "Pastor Femi", "0805 555 4444"),
    ))
    private val now = 1_791_000_000_000L

    @Test fun i_ask_none_saved() = paparazzi.snapshot {
        val picked = mapOf("2348031112222" to true, "2347053334444" to true, "2348123456789" to true)
        val asked = mapOf("2348098887777" to now - 2 * 86_400_000L)
        Frame {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                TopBar("Import from contacts", onBack = {})
                AskList(askContacts, noneSaved = true, query = "", onQuery = {}, picked = picked, onToggle = { _, _ -> },
                    asked = asked, inRanti = setOf("2348055554444"), onManual = {}, onAsk = {}, now = now)
            }
        }
    }

    @Test fun j_ask_search() = paparazzi.snapshot {
        Frame {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                TopBar("Import from contacts", onBack = {})
                ImportTabs(1, 2, askContacts.size) {}
                AskList(askContacts, noneSaved = false, query = "ade", onQuery = {}, picked = mapOf("2348031112222" to true), onToggle = { _, _ -> },
                    asked = emptyMap(), inRanti = emptySet(), onManual = {}, onAsk = {}, now = now)
            }
        }
    }

    @Test fun k_ask_flow() = paparazzi.snapshot {
        val c = askContacts[1]
        Frame {
            AskFlow(c, index = 2, total = 12,
                message = com.holaoluwakintan.ranti.core.AskContacts.message(c.name, "https://ranti-ng.vercel.app/b/k7m2xq9ahd", "Michael"),
                lastAsked = "Adaeze", onWhatsApp = {}, onSms = {}, onSkip = {}, onStop = {})
        }
    }

    @Test fun l_ask_done() = paparazzi.snapshot { Frame { AskDone(12, {}, {}) } }

    @Test fun m_manual_date() = paparazzi.snapshot {
        Frame { ManualDateDialog("Bayo Ogunleye", onSave = { _, _, _ -> }, onDismiss = {}) }
    }

    // ---------- v1.0 ----------
    private val zone1 = java.time.ZoneId.systemDefault()
    private fun at1(d: Int, h: Int, m: Int = 0) = java.time.LocalDateTime.of(2026, 10, d, h, m).atZone(zone1).toInstant().toEpochMilli()
    private val rems1 = listOf(
        com.holaoluwakintan.ranti.data.Reminder(id = 1, title = "Call mum", dueAt = at1(6, 18), createdAt = 0),
        com.holaoluwakintan.ranti.data.Reminder(id = 2, title = "Pay rent", dueAt = at1(6, 9), firedAt = at1(6, 9), repeat = "MONTHLY", anchorDay = 6, createdAt = 0),
        com.holaoluwakintan.ranti.data.Reminder(id = 3, title = "Choir rehearsal", dueAt = at1(8, 17), repeat = "WEEKLY", createdAt = 0),
        com.holaoluwakintan.ranti.data.Reminder(id = 4, title = "Take blood pressure meds", dueAt = at1(7, 8), repeat = "DAILY", createdAt = 0),
        com.holaoluwakintan.ranti.data.Reminder(id = 7, title = "Buy cake", dueAt = at1(5, 12), done = true, createdAt = 0),
    )

    @Test fun n_home_v1() = paparazzi.snapshot {
        Frame {
            HomeContent(people, today, "Michael", nav(Route.Home), rems1, HomeAlerts(notificationsOff = true), nowMs = at1(6, 15))
            Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Home, nav(Route.Home), Modifier) }
        }
    }

    @Test fun o_quick_add_empty() = paparazzi.snapshot {
        Frame { QuickAddContent("", java.time.LocalDateTime.of(2026, 10, 6, 15, 0), nav(Route.Home), autoFocus = false, onSaveOccasion = { _, _ -> }, onSaveReminder = {}) }
    }

    @Test fun p_quick_add_reminder() = paparazzi.snapshot {
        Frame { QuickAddContent("Call mum Friday 6pm", java.time.LocalDateTime.of(2026, 10, 6, 15, 0), nav(Route.Home), autoFocus = false, onSaveOccasion = { _, _ -> }, onSaveReminder = {}) }
    }

    @Test fun q_reminders() = paparazzi.snapshot {
        Frame {
            RemindersContent(rems1, today, at1(6, 15), nav(Route.Reminders), { _, _ -> }, {})
            Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Reminders, nav(Route.Reminders), Modifier) }
        }
    }

    @Test fun r_reminder_edit() = paparazzi.snapshot {
        Frame { ReminderEditContent(rems1[2], today, nav(Route.Home), {}, {}) }
    }

    @Test fun s_home_dark() { C.dark = true; paparazzi.snapshot {
        Frame {
            HomeContent(people, today, "Michael", nav(Route.Home), rems1, nowMs = at1(6, 15))
            Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Home, nav(Route.Home), Modifier) }
        }
    } }

    @Test fun t_plan_dark() { C.dark = true; paparazzi.snapshot {
        Frame { DetailContent(people[0].scopedTo(LocalDate.of(2026, 10, 11)).copy(giftDone = true), today, nav(Route.Home), {}, {}) }
    } }

    @Test fun u_widget() = paparazzi.snapshot {
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF7FA7C9), androidx.compose.ui.graphics.Color(0xFF2F4A6B))))) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> android.view.LayoutInflater.from(ctx).inflate(R.layout.widget_next, null) },
                modifier = Modifier.align(Alignment.Center).padding(16.dp).fillMaxWidth().height(150.dp),
            )
        }
    }

    @org.junit.After fun resetTheme() { C.dark = false }
}

/** Shows the bottom of the edit form (extras + auto-email), where the v0.2 fields live. */
@Composable
private fun AutoEmailPreview(p: Occasion) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp)
    ) {
        TopBar("Edit", onBack = {})
        Text("Edit ${p.firstName}", style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
        SectionLabel("Extras")
        androidx.compose.material3.OutlinedTextField(value = p.phone, onValueChange = {}, label = { Text("WhatsApp number (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = fieldColors())
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedTextField(value = p.email, onValueChange = {}, label = { Text("Email (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), colors = fieldColors())
        androidx.compose.foundation.layout.Spacer(Modifier.height(10.dp))
        AutoEmailCard(true, p.autoEmail, p.emailNote, p.firstName, {}, {})
    }

}
