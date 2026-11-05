package com.cx330.toolbox.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListFilterTest {

    private val apps = listOf(
        AppEntry("com.taobao.taobao", "淘宝", false),
        AppEntry("com.tencent.mm", "微信", false),
        AppEntry("com.android.settings", "设置", true),
        AppEntry("com.taobao.idlefish", "闲鱼", false),
    )

    @Test
    fun `空查询返回全部`() {
        assertEquals(apps.size, AppListRepository.filter(apps, "").size)
    }

    @Test
    fun `空白查询返回全部`() {
        assertEquals(apps.size, AppListRepository.filter(apps, "   ").size)
    }

    @Test
    fun `按中文名称过滤`() {
        val result = AppListRepository.filter(apps, "闲")
        assertEquals(1, result.size)
        assertEquals("com.taobao.idlefish", result.single().packageName)
    }

    @Test
    fun `按包名片段过滤`() {
        val result = AppListRepository.filter(apps, "taobao")
        assertEquals(2, result.size)
    }

    @Test
    fun `过滤忽略大小写`() {
        val result = AppListRepository.filter(apps, "TAOBAO")
        assertEquals(2, result.size)
    }

    @Test
    fun `没有匹配返回空列表`() {
        assertTrue(AppListRepository.filter(apps, "不存在的应用").isEmpty())
    }
}
