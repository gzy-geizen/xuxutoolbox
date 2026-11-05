package com.cx330.toolbox.core.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.cx330.toolbox.MainActivity
import com.cx330.toolbox.R

/**
 * 「疑似被冻结」提醒（S14）。
 *
 * 场景：无障碍开关在系统里还标着「已开启」，但已经超过 30 分钟没收到任何无障碍事件。
 * HyperOS 等系统冻结进程时就是这种表现：开关没关、服务却不在干活。
 *
 * 通知点按 → 打开 App（里面有保活指引）；操作按钮 → 直接跳系统无障碍设置页重新开启。
 * 通知 id 固定为 1004，与断线提醒（1003）分开：两者虽互斥，但独立 id 可以避免
 * 极端并发下互相覆盖，也方便用户分别清掉。
 */
object FrozenAlertNotifier {

    const val CHANNEL_ID = "cx330_frozen"

    /** 独立的「疑似冻结」告警槽位；不要与 [com.cx330.toolbox.keepalive.DisconnectNotifier] 复用。 */
    const val NOTIFICATION_ID = 1004

    fun show(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "服务疑似被冻结",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "无障碍开关还开着，但长时间没收到事件时提醒"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            context,
            10,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openSettings = PendingIntent.getActivity(
            context,
            11,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cx330)
            .setContentTitle("无障碍服务疑似被冻结")
            .setContentText("已超过 30 分钟没收到无障碍事件。点按打开工具箱查看修复指引。")
            .setContentIntent(openApp)
            .addAction(0, "打开无障碍设置", openSettings)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
