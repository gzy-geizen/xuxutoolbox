package com.cx330.toolbox.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cx330.toolbox.core.health.HealthCheckScheduler
import com.cx330.toolbox.notify.NotificationHelper

/**
 * 保活前台服务（S9-lite，S4 实测需要）。
 *
 * 背景：HyperOS 会在第三方 App 前台时冻结普通后台进程（cgroup.freeze=1），
 * 导致无障碍服务收不到事件。前台服务进程不会被当作 cached 进程冻结，
 * 因此用它把进程优先级抬起来。
 *
 * Android 14+ 必须声明 foregroundServiceType；这里用 specialUse
 * （个人自用、不上架商店，避免 dataSync 的 6 小时限制）。
 *
 * 通知 id 约定（S14）：前台服务通知固定 1000，和界面里的「运行状态」通知（1001）
 * 分开，这样 onUnbind 取消状态通知时不会误删前台服务通知。
 */
class KeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        val notification = NotificationHelper.buildOngoing(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationHelper.FGS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationHelper.FGS_NOTIFICATION_ID, notification)
        }
        Log.i(TAG, "foreground service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /**
     * 用户从最近任务里把 App 划掉时调用。
     * 这里只「尽力」重排一次健康检查闹钟；不宣称服务能自愈（系统仍可能整进程回收）。
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        HealthCheckScheduler.schedule(this)
        Log.i(TAG, "task removed: health check re-armed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CX330"

        /**
         * 安全启动：调用方可能处于后台，Android 12+ 会抛
         * ForegroundServiceStartNotAllowedException；启动成功返回 true，失败只记日志返回 false。
         */
        fun start(context: Context): Boolean = try {
            val intent = Intent(context, KeepAliveService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "start keep-alive service failed: ${e.message}")
            false
        }
    }
}
