package com.cx330.toolbox.core.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 心跳判断的纯逻辑测试（不依赖 Android 运行时）：
 * 时间阈值、节流窗口、开机基线过滤、时钟回拨兜底。
 */
class ServiceHealthTest {

    // 开机 1 小时后：保证 now - 30 分钟 仍是正数、且晚于开机时间
    private val bootWallClock = 1_000_000L
    private val nowWallClock = bootWallClock + 60 * 60_000L

    // ---------- heartbeatState：无基线 ----------

    @Test
    fun `从未收到事件时没有基线`() {
        assertEquals(HeartbeatState.NO_BASELINE, ServiceHealth.heartbeatState(-1L, nowWallClock, bootWallClock))
        assertEquals(HeartbeatState.NO_BASELINE, ServiceHealth.heartbeatState(0L, nowWallClock, bootWallClock))
    }

    @Test
    fun `基线早于本次开机时不算数`() {
        assertEquals(
            HeartbeatState.NO_BASELINE,
            ServiceHealth.heartbeatState(bootWallClock - 1L, nowWallClock, bootWallClock),
        )
    }

    @Test
    fun `墙上时钟回拨时按无基线处理不误报`() {
        assertEquals(
            HeartbeatState.NO_BASELINE,
            ServiceHealth.heartbeatState(nowWallClock + 1_000L, nowWallClock, bootWallClock),
        )
    }

    @Test
    fun `不传开机时间时不做开机基线过滤`() {
        val stale = nowWallClock - ServiceHealth.STALE_THRESHOLD_MS - 1L
        assertEquals(HeartbeatState.STALE, ServiceHealth.heartbeatState(stale, nowWallClock, 0L))
        val fresh = nowWallClock - 1_000L
        assertEquals(HeartbeatState.FRESH, ServiceHealth.heartbeatState(fresh, nowWallClock, 0L))
    }

    // ---------- heartbeatState：新鲜 / 冻结边界 ----------

    @Test
    fun `刚收到事件判为新鲜`() {
        assertEquals(HeartbeatState.FRESH, ServiceHealth.heartbeatState(nowWallClock - 1_000L, nowWallClock, bootWallClock))
    }

    @Test
    fun `阈值前一毫秒仍判为新鲜`() {
        val last = nowWallClock - ServiceHealth.STALE_THRESHOLD_MS + 1L
        assertEquals(HeartbeatState.FRESH, ServiceHealth.heartbeatState(last, nowWallClock, bootWallClock))
    }

    @Test
    fun `刚好达到 30 分钟阈值判为冻结`() {
        val last = nowWallClock - ServiceHealth.STALE_THRESHOLD_MS
        assertEquals(HeartbeatState.STALE, ServiceHealth.heartbeatState(last, nowWallClock, bootWallClock))
    }

    @Test
    fun `超过 30 分钟判为冻结`() {
        val last = nowWallClock - ServiceHealth.STALE_THRESHOLD_MS - 60_000L
        assertEquals(HeartbeatState.STALE, ServiceHealth.heartbeatState(last, nowWallClock, bootWallClock))
    }

    @Test
    fun `开机后重新收到事件就恢复新鲜`() {
        val last = bootWallClock + 1L
        assertEquals(HeartbeatState.FRESH, ServiceHealth.heartbeatState(last, bootWallClock + 10_000L, bootWallClock))
    }

    // ---------- shouldPersist：60 秒写盘节流 ----------

    @Test
    fun `首次心跳立即写盘`() {
        assertTrue(ServiceHealth.shouldPersist(nowWallClock, -1L))
        assertTrue(ServiceHealth.shouldPersist(nowWallClock, 0L))
    }

    @Test
    fun `60 秒内的重复心跳被节流丢弃`() {
        assertFalse(ServiceHealth.shouldPersist(nowWallClock, nowWallClock))
        assertFalse(ServiceHealth.shouldPersist(nowWallClock, nowWallClock - 1L))
        assertFalse(ServiceHealth.shouldPersist(nowWallClock, nowWallClock - ServiceHealth.THROTTLE_MS + 1L))
    }

    @Test
    fun `满 60 秒的心跳允许写盘`() {
        assertTrue(ServiceHealth.shouldPersist(nowWallClock, nowWallClock - ServiceHealth.THROTTLE_MS))
        assertTrue(ServiceHealth.shouldPersist(nowWallClock, nowWallClock - ServiceHealth.THROTTLE_MS - 1L))
    }

    @Test
    fun `时钟回拨时强制写盘刷新基线`() {
        assertTrue(ServiceHealth.shouldPersist(nowWallClock, nowWallClock + 5_000L))
    }

    // ---------- 常量与内存态 ----------

    @Test
    fun `节流窗口为 60 秒告警阈值为 30 分钟`() {
        assertEquals(60_000L, ServiceHealth.THROTTLE_MS)
        assertEquals(30L * 60L * 1000L, ServiceHealth.STALE_THRESHOLD_MS)
    }

    @Test
    fun `从未收到事件时 lastEventAgoMs 返回 -1`() {
        ServiceHealth.reset()
        assertTrue(ServiceHealth.neverReceived())
        assertEquals(-1L, ServiceHealth.lastEventAgoMs(123_456L))
    }

    @Test
    fun `prefs 文件名与心跳 key 固定`() {
        assertEquals("cx330_health", ServiceHealth.PREFS_NAME)
        assertEquals("last_event_wall_clock", ServiceHealth.KEY_LAST_EVENT_WALL_CLOCK)
    }
}
