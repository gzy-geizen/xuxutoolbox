package com.cx330.toolbox.core.health

import android.content.Context
import android.os.SystemClock

/** 心跳状态：用于「疑似被冻结」判断。 */
enum class HeartbeatState {
    /** 没有可用基线（从未收到事件 / 基线早于本次开机 / 墙上时钟回拨）：无法判断，不告警。 */
    NO_BASELINE,

    /** 最近 [ServiceHealth.STALE_THRESHOLD_MS] 内收到过事件。 */
    FRESH,

    /** 超过 [ServiceHealth.STALE_THRESHOLD_MS] 没有收到事件，疑似被冻结/假死。 */
    STALE,
}

/**
 * 服务健康度的轻量记录：
 * - 每次无障碍事件被收到就刷新一次时间戳；
 * - 界面与告警可以据此判断「服务是真的在干活」而不只是「开关还开着」。
 *
 * 2026-11-04（S14）：新增墙钟心跳 `last_event_wall_clock` 持久化到 SharedPreferences。
 * 原来只有 `elapsedRealtime`，进程被杀后归零，跨进程重启无法比较；墙上时间可以跨进程、
 * 跨开机保存，健康检查（BroadcastReceiver）在别的进程状态里也能据此判断是否「假死」。
 *
 * 写盘按 60 秒节流：无障碍事件可能每秒几十条，不能每条都写 SharedPreferences。
 */
object ServiceHealth {

    const val PREFS_NAME = "cx330_health"
    const val KEY_LAST_EVENT_WALL_CLOCK = "last_event_wall_clock"

    /** 写盘节流窗口：同一个窗口内只保留第一条心跳。 */
    const val THROTTLE_MS = 60_000L

    /** 超过这个时长没有事件 → 疑似被冻结（需求定的 30 分钟）。 */
    const val STALE_THRESHOLD_MS = 30 * 60_000L

    private const val NEVER = -1L

    @Volatile
    var lastEventAtElapsed: Long = 0L
        private set

    @Volatile
    var eventCount: Long = 0L
        private set

    /** 无障碍事件入口：内存计数 + 按 60 秒节流写一次墙上时间。 */
    fun onEvent(context: Context) {
        lastEventAtElapsed = SystemClock.elapsedRealtime()
        eventCount++
        val nowWallClock = System.currentTimeMillis()
        if (shouldPersist(nowWallClock, readWallClock(context))) {
            writeWallClock(context, nowWallClock)
        }
    }

    /** 服务重新连上也是一次「活着」的证据：刷新墙钟基线，但不清空事件计数。 */
    fun onServiceConnected(context: Context) {
        writeWallClock(context, System.currentTimeMillis())
    }

    fun reset() {
        lastEventAtElapsed = 0L
        eventCount = 0L
    }

    /** 距离上次事件过去多少毫秒；从未收到过事件返回 -1。 */
    fun lastEventAgoMs(now: Long = SystemClock.elapsedRealtime()): Long =
        if (lastEventAtElapsed == 0L) NEVER else now - lastEventAtElapsed

    fun neverReceived(): Boolean = lastEventAtElapsed == 0L

    /** 读取上次写盘的墙上时间；从未写过返回 -1。 */
    fun readWallClock(context: Context): Long =
        prefs(context).getLong(KEY_LAST_EVENT_WALL_CLOCK, NEVER)

    fun writeWallClock(context: Context, wallClock: Long) {
        prefs(context).edit().putLong(KEY_LAST_EVENT_WALL_CLOCK, wallClock).apply()
    }

    /**
     * 纯逻辑：是否需要把这次心跳写盘。
     * - 第一次（没有旧值）必须写；
     * - 时钟回拨（现在比上次还早）也写，避免旧值永远压住新值；
     * - 其余情况每 [THROTTLE_MS] 才写一次。
     */
    fun shouldPersist(nowWallClock: Long, lastWallClock: Long): Boolean = when {
        lastWallClock <= 0L -> true
        nowWallClock < lastWallClock -> true
        else -> nowWallClock - lastWallClock >= THROTTLE_MS
    }

    /**
     * 纯逻辑：判断心跳状态。
     *
     * [bootWallClock] 传本次开机的墙上时间（>0 时生效）。早于本次开机的旧心跳不算数：
     * 重启后系统还没重新连上无障碍服务，拿上一轮进程的旧时间戳会误报「被冻结」。
     */
    fun heartbeatState(
        lastWallClock: Long,
        nowWallClock: Long,
        bootWallClock: Long = 0L,
    ): HeartbeatState {
        if (lastWallClock <= 0L) return HeartbeatState.NO_BASELINE
        if (bootWallClock > 0L && lastWallClock < bootWallClock) return HeartbeatState.NO_BASELINE
        val age = nowWallClock - lastWallClock
        if (age < 0L) return HeartbeatState.NO_BASELINE
        return if (age >= STALE_THRESHOLD_MS) HeartbeatState.STALE else HeartbeatState.FRESH
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
