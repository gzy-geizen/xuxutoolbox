package com.cx330.toolbox.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 硬要求①「新增工具不改主界面/主流程」的契约测试。
 *
 * 只注册一条 [ToolDescriptor]：主页卡片自动增加、按 id 可以路由到详情页；
 * 全程不改 MainActivity 的 when，也不改 MainMenuScreen 的卡片代码。
 */
class ToolRegistryContractTest {

    private val fakeTool = ToolDescriptor(
        id = "fake_contract_tool",
        title = "契约测试工具",
        subtitle = "只注册就能出现在主页",
        order = 90,
        category = DEFAULT_TOOL_CATEGORY,
    )

    private fun extendRegistry(): ToolRegistry = ToolRegistry(BuiltinTools.tools + fakeTool)

    @Test
    fun `跳过开屏广告是注册表里的第一个工具`() {
        val registry = ToolRegistry()
        assertEquals(BuiltinTools.SKIP_SPLASH_ID, registry.tools.first().id)
        assertEquals("跳过开屏广告", registry.tools.first().title)
        assertNotNull(registry.find(BuiltinTools.SKIP_SPLASH_ID))
    }

    @Test
    fun `只注册 FakeTool 主页卡片就增加一张`() {
        val before = buildToolCards(ToolRegistry()) { true }
        val after = buildToolCards(extendRegistry()) { true }
        assertEquals(before.size + 1, after.size)
        val added = after.single { it.id == fakeTool.id }
        assertEquals(fakeTool.title, added.title)
        assertEquals(fakeTool.category, added.category)
        // 主页按返回的卡片列表渲染：列表多一张，主页就多一张，不需要改 MainMenuScreen。
        assertEquals(after.size - 1, after.indexOf(added))
    }

    @Test
    fun `只注册 FakeTool 就能按 id 路由`() {
        val registry = extendRegistry()
        val resolved = registry.find("fake_contract_tool")
        assertNotNull(resolved)
        // MainActivity 的路由是 Screen.Tool(id) + registry.find(id)；
        // 这里能按 id 查到，就代表路由可用，不需要再加 when 分支。
        assertEquals(fakeTool, resolved)
        assertNull(registry.find("没有注册的工具"))
    }

    @Test
    fun `卡片顺序按 order 排序且同 order 按 id 稳定`() {
        val early = fakeTool.copy(id = "a_early", order = -1)
        val late = fakeTool.copy(id = "z_late", order = 100)
        val registry = ToolRegistry(listOf(late, early, BuiltinTools.skipSplash))
        assertEquals(
            listOf("a_early", BuiltinTools.SKIP_SPLASH_ID, "z_late"),
            registry.tools.map { it.id },
        )
    }

    @Test
    fun `模块开关状态映射到卡片 enabled`() {
        assertTrue(buildToolCards(ToolRegistry()) { true }.single().enabled)
        assertFalse(buildToolCards(ToolRegistry()) { false }.single().enabled)
    }

    @Test
    fun `没有模块开关的工具不受模块开关影响`() {
        val cards = buildToolCards(ToolRegistry(listOf(fakeTool))) { false }
        assertTrue(cards.single().enabled)
        assertEquals(
            fakeTool.subtitle,
            toolStatusText(fakeTool, serviceEnabled = false, dryRun = true) { false },
        )
    }

    @Test
    fun `跳过开屏广告卡片状态随模块试运行与无障碍状态变化`() {
        val tool = BuiltinTools.skipSplash
        assertEquals("暂时停用", toolStatusText(tool, serviceEnabled = true, dryRun = false) { false })
        assertEquals(
            "还没开启无障碍，点这里去设置",
            toolStatusText(tool, serviceEnabled = false, dryRun = true) { true },
        )
        assertEquals("试运行中 · 只看不点", toolStatusText(tool, serviceEnabled = true, dryRun = true) { true })
        assertEquals("守护中 · 自动跳过", toolStatusText(tool, serviceEnabled = true, dryRun = false) { true })
    }

    @Test
    fun `主界面与主流程源码不再写死具体工具`() {
        // 防止以后有人为了加工具，又把「跳过开屏广告」写回主页或导航。
        val mainMenu = File("src/main/java/com/cx330/toolbox/ui/MainMenuScreen.kt")
        val mainActivity = File("src/main/java/com/cx330/toolbox/MainActivity.kt")
        assertTrue("找不到 MainMenuScreen.kt，测试工作目录可能不对", mainMenu.exists())
        assertTrue("找不到 MainActivity.kt，测试工作目录可能不对", mainActivity.exists())
        assertFalse(mainMenu.readText().contains("跳过开屏广告"))
        assertFalse(mainActivity.readText().contains("跳过开屏广告"))
        assertTrue(mainMenu.readText().contains("registry.tools"))
    }
}
