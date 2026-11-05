package com.cx330.toolbox.core.health

import com.cx330.toolbox.keepalive.DisconnectNotifier
import com.cx330.toolbox.notify.NotificationHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 通知 id / channel 的纯逻辑契约测试：
 * 四类通知共用一条通知栏，id 一旦撞车就会互相覆盖（S14 的问题来源）。
 * 这里只校验常量（const 会被编译期内联，不需要 Android 运行时）。
 */
class HealthNotificationsTest {

    @Test
    fun `前台服务通知 id 固定为 1000`() {
        assertEquals(1000, NotificationHelper.FGS_NOTIFICATION_ID)
    }

    @Test
    fun `运行状态通知 id 固定为 1001`() {
        assertEquals(1001, NotificationHelper.STATUS_NOTIFICATION_ID)
    }

    @Test
    fun `抓取结果通知 id 固定为 1002`() {
        assertEquals(1002, NotificationHelper.CAPTURE_RESULT_ID)
    }

    @Test
    fun `断线提醒使用告警槽位 1003`() {
        assertEquals(1003, DisconnectNotifier.NOTIFICATION_ID)
    }

    @Test
    fun `冻结提醒使用独立槽位 1004`() {
        assertEquals(1004, FrozenAlertNotifier.NOTIFICATION_ID)
        assertNotEquals(DisconnectNotifier.NOTIFICATION_ID, FrozenAlertNotifier.NOTIFICATION_ID)
    }

    @Test
    fun `五类通知 id 互不重复`() {
        val ids = listOf(
            NotificationHelper.FGS_NOTIFICATION_ID,
            NotificationHelper.STATUS_NOTIFICATION_ID,
            NotificationHelper.CAPTURE_RESULT_ID,
            DisconnectNotifier.NOTIFICATION_ID,
            FrozenAlertNotifier.NOTIFICATION_ID,
        )
        assertEquals(5, ids.size)
        assertEquals(5, ids.toSet().size)
        assertNotEquals(NotificationHelper.FGS_NOTIFICATION_ID, NotificationHelper.STATUS_NOTIFICATION_ID)
    }

    @Test
    fun `前台服务通知与状态通知必须是不同 id 以便分别取消`() {
        assertNotEquals(NotificationHelper.FGS_NOTIFICATION_ID, NotificationHelper.STATUS_NOTIFICATION_ID)
    }

    @Test
    fun `三个通知渠道 id 互不重复`() {
        val channels = listOf(
            NotificationHelper.CHANNEL_ID,
            DisconnectNotifier.CHANNEL_ID,
            FrozenAlertNotifier.CHANNEL_ID,
        )
        assertEquals(listOf("cx330_status", "cx330_alert", "cx330_frozen"), channels)
        assertEquals(3, channels.toSet().size)
    }
}
