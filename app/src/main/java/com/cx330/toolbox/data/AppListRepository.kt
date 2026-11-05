package com.cx330.toolbox.data

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.Context

/** 应用列表里的一行。 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * 已安装应用列表。
 *
 * 用 `CATEGORY_LAUNCHER` 查询而不是 `getInstalledApplications()`：
 * - Android 11+ 包可见性限制下，带启动图标的应用刚好是开屏广告的目标集合；
 * - 不需要申请 `QUERY_ALL_PACKAGES` 这种敏感权限。
 */
object AppListRepository {

    fun loadInstalledApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        val seen = HashSet<String>()
        val apps = ArrayList<AppEntry>()
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val appInfo = info.activityInfo.applicationInfo
            val label = runCatching { appInfo?.loadLabel(pm)?.toString() }.getOrNull() ?: pkg
            val isSystem = appInfo != null &&
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            apps += AppEntry(pkg, label, isSystem)
        }
        return apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** 按名称或包名过滤（纯函数，可单测）。 */
    fun filter(apps: List<AppEntry>, query: String): List<AppEntry> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return apps
        return apps.filter { app ->
            app.label.contains(keyword, ignoreCase = true) ||
                app.packageName.contains(keyword, ignoreCase = true)
        }
    }
}
