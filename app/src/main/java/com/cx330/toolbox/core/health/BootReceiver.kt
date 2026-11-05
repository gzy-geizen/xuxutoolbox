package com.cx330.toolbox.core.health

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 重启后（S9/S14）：
 * 1. 无障碍开关还开着的话，尝试把保活前台服务重新拉起来；
 * 2. 立刻跑一次健康检查（闹钟不会跨重启保留，等 15 分钟太慢）；
 * 3. 重排下一次周期检查。
 *
 * 开机瞬间系统可能还没重新连上无障碍服务，所以健康检查里的「疑似被冻结」判断会
 * 通过开机时间基线自动跳过上一轮进程的旧心跳（见 [ServiceHealth.heartbeatState]）。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        HealthChecks.ensureKeepAlive(context)
        HealthChecks.runNow(context)
        HealthCheckScheduler.schedule(context)
    }
}
