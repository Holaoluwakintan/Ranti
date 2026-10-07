package com.holaoluwakintan.ranti.system

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Opens the phone settings that stop Xiaomi/HyperOS (and others) from killing reminders. Every call has fallbacks. */
object Reliability {

    val isXiaomi: Boolean
        get() = Build.MANUFACTURER.equals("Xiaomi", true) || Build.BRAND.equals("Redmi", true) ||
            Build.BRAND.equals("POCO", true) || Build.BRAND.equals("Xiaomi", true)

    private fun tryStart(ctx: Context, intents: List<Intent>): Boolean {
        for (i in intents) {
            try {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return true
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun appDetails(ctx: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))

    fun openAutostart(ctx: Context): Boolean = tryStart(
        ctx, listOf(
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.securityscan.MainActivity")),
            // Other brands, just in case
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            Intent().setComponent(ComponentName("com.transsion.phonemaster", "com.cyin.himgr.autostart.AutoStartActivity")),
            appDetails(ctx),
        )
    )

    /**
     * Xiaomi's per-app battery saver ("No restrictions"), then Android's battery-optimisation list.
     * v1.0: no direct "ignore optimisations" dialog (needs a Play-restricted permission).
     */
    fun openBattery(ctx: Context): Boolean = tryStart(
        ctx, listOf(
            Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY")
                .setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                .putExtra("package_name", ctx.packageName)
                .putExtra("package_label", "Ranti"),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetails(ctx),
        )
    )

    fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun openExactAlarmSettings(ctx: Context): Boolean {
        val list = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= 31) list += Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
        list += appDetails(ctx)
        return tryStart(ctx, list)
    }

    fun openNotificationSettings(ctx: Context): Boolean {
        val list = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= 26) list += Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        list += appDetails(ctx)
        return tryStart(ctx, list)
    }

    fun openAppInfo(ctx: Context) = tryStart(ctx, listOf(appDetails(ctx)))
}
