package com.cx330.toolbox.core.health

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.cx330.toolbox.keepalive.DisconnectNotifier
import com.cx330.toolbox.service.KeepAliveService
import com.cx330.toolbox.util.AccessibilityUtils

/**
 * 健康检查的公共实现（S14）：周期检查（[HealthCheckReceiver]）和开机检查
 * （[BootReceiver]）共用同一套判断，避免两边逻辑分叉。
 *
 * 判断分两层：
 * 1. 开关关着 → 「已断开」提醒（DisconnectNotifier，id 1003）；
 * 2. 开关开着，但墙上心跳超过 30 分钟 → 「疑似被冻结」提醒（FrozenAlertNotifier，id 1004）。
 */
object HealthChecks {

    private const val TAG = "CX330"

    fun runNow(context: Context) {
        val app = context.applicationContext
        if (!AccessibilityUtils.isServiceEnabled(app)) {
            // 开关关了：冻结提醒没意义，换成「已断开」提醒
            FrozenAlertNotifier.cancel(app)
            DisconnectNotifier.show(app)
            return
        }

        DisconnectNotifier.cancel(app)
        val nowWallClock = System.currentTimeMillis()
        // 本次开机的墙上时间；用来排除「上一轮进程留下的旧心跳」
        val bootWallClock = nowWallClock - SystemClock.elapsedRealtime()
        val lastWallClock = ServiceHealth.readWallClock(app)
        when (ServiceHealth.heartbeatState(lastWallClock, nowWallClock, bootWallClock)) {
            HeartbeatState.STALE -> {
                Log.w(TAG, "heartbeat stale: last=$lastWallClock now=$nowWallClock")
                FrozenAlertNotifier.show(app)
            }

            HeartbeatState.FRESH, HeartbeatState.NO_BASELINE -> FrozenAlertNotifier.cancel(app)
        }
    }

    /** 开关开着就尝试把保活前台服务拉起来；后台受限时 start 返回 false，只记日志。 */
    fun ensureKeepAlive(context: Context): Boolean {
        if (!AccessibilityUtils.isServiceEnabled(context)) return false
        return KeepAliveService.start(context)
    }
}
