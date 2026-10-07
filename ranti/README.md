# Ranti 0.1 (MVP)

Birthday & event reminders that count down 7, 5 and 1 day before, plus 7am on the day. Native Kotlin + Jetpack Compose, fully offline (Room).

## Build
- JDK 17, Android SDK platform 35. `local.properties` → `sdk.dir=...`
- Release signing reads a properties file outside the repo: `RANTI_KEYSTORE_PROPS=/path/keystore.properties ./gradlew assembleRelease`
  (keys: storeFile, storePassword, keyAlias, keyPassword). Keep the SAME keystore for every update, or Android will refuse to update the app.
- Unit tests: `./gradlew testReleaseUnitTest` (date math, Feb 29, year unknown, Africa/Lagos ladder, nightly window, phone numbers, contact date formats).
- v0.3: Import screen = "Saved birthdays" + "Ask friends" (all contacts with a number, search, multi-select, one-at-a-time WhatsApp/SMS ask with the birthday link, "+ date" by hand). Logic in core/AskContacts.kt (PhoneNorm, ContactEvents, AskContacts); tests in V03Test (SQLite via sqlite-jdbc).
- Screenshots: `./gradlew -Pshots recordPaparazziDebug` → `app/src/test/snapshots/images/`.

## Map
- `core/` pure Kotlin: DateMath, Ladder (countdown + due/catch-up logic), Messages (offline templates), ContactDates.
- `reminders/` ReminderEngine (one exact alarm for the next rung + one for the nightly question; inexact fallback; WorkManager 3-hourly safety net), receivers (boot, time/time-zone change, package replaced), Notifier.
- `system/` Xiaomi autostart/battery intents with fallbacks, WhatsApp (wa.me) + share sheet, contacts import.
- `ui/` Compose screens: onboarding, home, people, person plan, add/edit (also the nightly quick-capture), message, settings, import.


## v0.2 (2026-10-06): automatic birthdays
- Import from contacts now also saves each contact's email (number was already saved); checklist with Select all, duplicates greyed.
- Morning ping on the day at 7:00 ("🎂 Tolu's birthday today") opens the wish screen: WhatsApp / SMS / Email / Share / Copy. 1-day-before heads-up is a setting (Settings → Reminders).
- Birthday link: each phone gets a public code + secret key (Prefs). Link https://ranti-ng.vercel.app/b/<code>. FriendSync pulls new entries on app open (15-min throttle) and every 12 h (WorkManager, network required), imports them (dedupe by name+day), notifies.
- Auto birthday email: per-person opt-in (birthdays with an email). Only opted-in people are uploaded (/api/emails). Vercel Cron 06:00 UTC sends via Brevo.
- Room v1 -> v2 migration adds email, autoEmail, emailNote, source.
- Backend lives in the site folder (ranti-site/api, Node serverless, no deps). Supabase tables ranti_* in the Blossom project.
