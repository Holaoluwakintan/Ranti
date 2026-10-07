# Ranti v1.0 progress (worker 38b419f6, 2026-10-07)
DONE (source, not yet compiled): audit files/ranti/audit-v1.md; QuickParse + Repeat (core); Reminder entity + MIGRATION_2_3 (DB v3, exportSchema on);
BackupCodec (data/Backup.kt); ReminderEngine reminders + custom ladder times + mutex; Notifier reminders channel + snooze actions; ActionReceiver;
widget (widget/RantiWidget.kt + layout/widget_next.xml); dark mode (C getters + values-night); Root (Reminders tab, QuickAdd route, NotificationAsk);
ReminderScreens.kt (QuickAdd, Reminders, ReminderEdit); SettingsV1.kt (appearance, times, backup/restore, privacy, delete cloud data);
manifest (USE_EXACT_ALARM + REQUEST_IGNORE_BATTERY_OPTIMIZATIONS removed, SCHEDULE_EXACT_ALARM all versions, FileProvider, widget, backup rules);
compile/target 36, versionCode 4 / 1.0; V1Test.kt; site: api/delete.js, privacy.html, vercel.json /privacy rewrite; play kit md files + icon-512.
BUILD: /home/user/work/ranti-wait-build.sh 120 :app:testReleaseUnitTest :app:bundleRelease :app:assembleRelease (log /tmp/ranti-wait.log, /tmp/ranti-build.log). Blocked on MemAvailable (~1.45 GB < 2.3 GB) at 02:06Z.
TODO: compile fixes, Paparazzi shots (new screens + dark), feature graphic, Play screenshots 1080x2160, bundletool validate, apksigner verify, site index update + ONE deploy, memory/ranti.md, report.

## Worker b4e2239f (02:44Z+)
- 02:44Z low-mem build of HEAD 07977df (commit 02:38:50) SUCCEEDED: tests 54/54 pass (DateMath 10, Ladder 12, Nightly 4, Text 3, V02 10, V03 7, V1 8 incl. migrationAddsRemindersAndKeepsPeople).
- Verified: apksigner v2+v3, same cert as v0.3 (SHA-256 b58b889a…28e4) -> in-place upgrade; versionCode 4 / 1.0, target+compile 36; bundletool 1.18.1 validate rc 0; jarsigner AAB ok; native libs 16 KB aligned; zipalign -P 16 ok.
- Migration 2->3 only CREATE TABLE IF NOT EXISTS reminders; SQL identical to exported schema 3.json; occasions entity unchanged vs v0.3.
- Copied: files/ranti/android/ranti-1.0.aab (sha256 1c457dff…2b77), ranti-1.0.apk (8d6135f8…035f), SHA256SUMS.txt, ranti-1.0-mapping.txt.gz.
- Play shots stitched: files/ranti/play/screenshots/01..08 (1080x2160); feature-graphic-1024x500.png; app screens files/ranti/screenshots-v1/*.png.
- NEXT: site index -> v1.0 APK + ONE deploy, verify /privacy, source zip, memory, report.
- DONE 02:51Z: site deployed dpl_GNSqhkvSDmXcrJ697cqLg6hvJ1DE, /privacy 200, /ranti.apk = v1.0 verified; checklist file names fixed; source zip; memory/ranti.md updated. ALL TODO DONE; only the report remains.
