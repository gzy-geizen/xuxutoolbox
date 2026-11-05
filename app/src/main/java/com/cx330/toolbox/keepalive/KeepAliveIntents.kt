package com.cx330.toolbox.keepalive

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** 把保活指引里的「去设置」落到具体 Intent（带多级兜底）。 */
object KeepAliveIntents {

    fun open(context: Context, target: DeepLinkTarget): Boolean {
        val candidates = when (target) {
            DeepLinkTarget.APP_DETAILS -> listOf(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )

            DeepLinkTarget.ACCESSIBILITY -> listOf(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            )

            DeepLinkTarget.BATTERY_OPTIMIZATION -> listOf(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                Intent(Settings.ACTION_SETTINGS),
            )

            DeepLinkTarget.AUTOSTART -> listOf(
                // 小米 / Redmi
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    ),
                ),
                // OPPO / OnePlus
                Intent().setComponent(
                    ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    ),
                ),
                // vivo
                Intent().setComponent(
                    ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                    ),
                ),
                // 兜底：应用详情页
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }

        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return true
            } catch (ignored: ActivityNotFoundException) {
                // 试下一个
            } catch (ignored: SecurityException) {
                // 试下一个
            }
        }
        return false
    }
}
