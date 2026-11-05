package com.cx330.toolbox.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.cx330.toolbox.MainActivity
import com.cx330.toolbox.R
import com.cx330.toolbox.core.inspect.CaptureActionReceiver

/**
 * 常驻通知。
 *
 * 注意：S4 实测发现 HyperOS 会在第三方 App 前台时把本进程冻结（cgroup.freeze=1），
 * 无障碍事件因此中断。所以现在同时启动前台服务（KeepAliveService）提高进程优先级，
 * 这个通知既是服务通知，也是「运行状态」提示。
 *
 * 2026-11-01：新增「捕获窗口」通知按钮，用户可在广告页直接下拉通知点按抓取节点树。
 * 2026-11-04（S14）：通知 id 拆开，避免互相覆盖：
 * - 1000 前台服务通知（KeepAliveService.startForeground 专用，只有服务能撤）；
 * - 1001 运行状态通知（界面里点「显示常驻通知」时用，onUnbind 只撤这个）；
 * - 1002 抓取结果通知；
 * - 1003 断线/冻结告警（见 DisconnectNotifier / FrozenAlertNotifier）。
 */
object NotificationHelper {

    const val CHANNEL_ID = "cx330_status"

    /** 前台服务通知：startForeground 用，取消它会导致前台服务被系统判定异常。 */
    const val FGS_NOTIFICATION_ID = 1000

    /** 运行状态通知：界面里主动显示/刷新，与前台服务通知互不覆盖。 */
    const val STATUS_NOTIFICATION_ID = 1001

    const val CAPTURE_RESULT_ID = 1002

    const val ACTION_CAPTURE = "com.cx330.toolbox.action.CAPTURE_WINDOW"

    fun ensureChannel(context: Context): NotificationManager {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
        return manager
    }

    fun buildOngoing(context: Context): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val capture = PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, CaptureActionReceiver::class.java).setAction(ACTION_CAPTURE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cx330)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(context.getString(R.string.notification_text))
            .setContentIntent(openApp)
            .addAction(0, "捕获窗口", capture)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 显示「运行状态」通知（id 1001）。
     * 前台服务通知（1000）已经存在时不重复发，避免通知栏出现两条一模一样的常驻通知。
     */
    fun showOngoing(context: Context) {
        val manager = ensureChannel(context)
        val fgsActive = manager.activeNotifications.any { it.id == FGS_NOTIFICATION_ID }
        if (fgsActive) return
        manager.notify(STATUS_NOTIFICATION_ID, buildOngoing(context))
    }

    /** 捕获完成后弹一条可点开 App 的结果通知。 */
    fun notifyCaptureResult(context: Context, text: String) {
        val manager = ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            3,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cx330)
            .setContentTitle("窗口捕获")
            .setContentText(text)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(CAPTURE_RESULT_ID, notification)
    }

    /** 只撤「运行状态」通知（1001），不碰前台服务通知（1000）。 */
    fun cancelStatus(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(STATUS_NOTIFICATION_ID)
    }
}
