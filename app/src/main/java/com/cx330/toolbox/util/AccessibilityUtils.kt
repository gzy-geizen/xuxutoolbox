package com.cx330.toolbox.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.cx330.toolbox.service.MyAccessibilityService

/**
 * 无障碍服务的状态查询与跳转工具。
 * 注意：Android 不允许 App 自行开关无障碍服务，只能引导用户去系统设置手动开启。
 *
 * 2026-11-04（S14）：去掉硬编码包名/类名，统一用 context.packageName +
 * MyAccessibilityService::class.java.name，避免改包名/改类名后静默失配。
 */
object AccessibilityUtils {

    /** 当前应用里无障碍服务的 ComponentName（包名取运行时值）。 */
    fun serviceComponent(context: Context): ComponentName =
        ComponentName(context.packageName, MyAccessibilityService::class.java.name)

    /** 服务是否已被用户在系统设置中开启并连接。 */
    fun isServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false
        val expected = serviceComponent(context)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                val si = info.resolveInfo?.serviceInfo ?: return@any false
                si.packageName == expected.packageName && si.name == expected.className
            }
    }

    /** 跳转系统「无障碍」设置页，让用户手动开启服务。 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
