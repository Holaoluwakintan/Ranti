package com.holaoluwakintan.ranti

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.holaoluwakintan.ranti.core.OccasionType
import com.holaoluwakintan.ranti.core.Tone
import com.holaoluwakintan.ranti.data.Occasion
import com.holaoluwakintan.ranti.data.Reminder
import com.holaoluwakintan.ranti.ui.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * v1.0: Play Store phone screenshots at 1080x2160 (2:1). Paparazzi caps an image at 1000 px, so each screen
 * is rendered as a 2160 px tall layout and captured in two 1080x1080 halves, stitched afterwards.
 */
class PlayShots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 1080, screenWidth = 1080, softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 1.0,
    )

    private val today = LocalDate.of(2026, 10, 6)
    private val zone = java.time.ZoneId.systemDefault()
    private fun at(d: Int, h: Int, m: Int = 0) = java.time.LocalDateTime.of(2026, 10, d, h, m).atZone(zone).toInstant().toEpochMilli()
    private val nowMs = at(6, 15)
    private val people = listOf(
        Occasion(id = 1, name = "Tolu Adeyemi", relationship = "Friend", month = 10, day = 11, year = 1997, phone = "08031234567",
            notes = "Loves Afrobeats and suya. Wants to visit Zanzibar. Wears size 40 shoes.", giftIdea = "Burna Boy concert tickets", createdAt = 0),
        Occasion(id = 2, name = "Pastor Femi", relationship = "Church", month = 10, day = 14, year = null, createdAt = 0),
        Occasion(id = 3, name = "Dami Okafor", relationship = "Colleague", month = 10, day = 20, year = 1994, createdAt = 0),
        Occasion(id = 4, name = "Ada & Kunle", relationship = "Family", type = OccasionType.ANNIVERSARY.name, month = 10, day = 25, year = 2019, createdAt = 0),
        Occasion(id = 5, name = "Mummy", relationship = "Family", month = 11, day = 2, year = null, createdAt = 0),
        Occasion(id = 6, name = "Kemi's wedding", relationship = "Friend", type = OccasionType.WEDDING.name, month = 12, day = 12, year = 2026, recurring = false, createdAt = 0),
    )
    private val withToday = people + Occasion(id = 7, name = "Bisi Adebayo", relationship = "Sister", month = 10, day = 6, year = 1999, phone = "0803", createdAt = 0)
    private val reminders = listOf(
        Reminder(id = 1, title = "Call mum", dueAt = at(6, 18), createdAt = 0),
        Reminder(id = 2, title = "Pay rent", dueAt = at(6, 9), firedAt = at(6, 9), repeat = "MONTHLY", anchorDay = 6, createdAt = 0),
        Reminder(id = 3, title = "Choir rehearsal", dueAt = at(8, 17), repeat = "WEEKLY", createdAt = 0),
        Reminder(id = 4, title = "Take blood pressure meds", dueAt = at(7, 8), repeat = "DAILY", createdAt = 0),
        Reminder(id = 5, title = "Renew NEPA prepaid meter", dueAt = at(10, 10), note = "Token for 10k", createdAt = 0),
        Reminder(id = 6, title = "Wish Tolu a happy birthday", dueAt = at(11, 8), occasionId = 1, createdAt = 0),
        Reminder(id = 7, title = "Buy cake", dueAt = at(5, 12), done = true, createdAt = 0),
    )

    private fun nav(r: Route) = Nav(mutableStateListOf(r))

    private fun tall(name: String, dark: Boolean = false, content: @Composable BoxScope.() -> Unit) {
        for (part in 0..1) {
            C.dark = dark
            paparazzi.snapshot(name = "${name}_$part") {
                val d = LocalDensity.current
                val w = with(d) { 1080.toDp() }
                val h = with(d) { 2160.toDp() }
                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                            .offset { IntOffset(0, -1080 * part) }
                            .requiredSize(w, h)
                    ) {
                        RantiTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { content() } }
                    }
                }
            }
        }
        C.dark = false
    }

    @Test fun s1_home() = tall("s1_home") {
        HomeContent(withToday, today, "Michael", nav(Route.Home), reminders, nowMs = nowMs)
        Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Home, nav(Route.Home), Modifier) }
    }

    @Test fun s2_quick_birthday() = tall("s2_quick_birthday") {
        QuickAddContent("Tolu's birthday 11 October 1997", java.time.LocalDateTime.of(2026, 10, 6, 15, 0), nav(Route.Home), autoFocus = false,
            onSaveOccasion = { _, _ -> }, onSaveReminder = {})
    }

    @Test fun s3_quick_reminder() = tall("s3_quick_reminder") {
        QuickAddContent("Pay rent every month on the 1st", java.time.LocalDateTime.of(2026, 10, 6, 15, 0), nav(Route.Home), autoFocus = false,
            onSaveOccasion = { _, _ -> }, onSaveReminder = {})
    }

    @Test fun s4_reminders() = tall("s4_reminders") {
        RemindersContent(reminders.filter { !it.done }, today, nowMs, nav(Route.Reminders), { _, _ -> }, {}, { id -> if (id == 1L) "Tolu" else null })
        Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Reminders, nav(Route.Reminders), Modifier) }
    }

    @Test fun s5_plan() = tall("s5_plan") {
        DetailContent(people[0].scopedTo(LocalDate.of(2026, 10, 11)).copy(giftDone = true), today, nav(Route.Home), {}, {})
    }

    @Test fun s6_wish() = tall("s6_wish") {
        MessageContent(withToday.last().copy(email = "bisi.adebayo@gmail.com"), today, nav(Route.Home), Tone.PRAYERFUL, {}, {})
    }

    @Test fun s7_link() = tall("s7_link") {
        LinkContent(
            code = "k7m2xq9ahd", friendsAdded = 3, syncing = false, lastSyncAt = System.currentTimeMillis() - 2 * 3600_000L,
            note = "🎉 3 friends just added their birthdays!", nav = nav(Route.Home), onShare = {}, onWhatsApp = {}, onCopy = {}, onCheck = {},
        )
    }

    @Test fun s8_home_dark() = tall("s8_home_dark", dark = true) {
        HomeContent(withToday, today, "Michael", nav(Route.Home), reminders, nowMs = nowMs)
        Box(Modifier.align(Alignment.BottomCenter)) { BottomBar(Route.Home, nav(Route.Home), Modifier) }
    }
}
