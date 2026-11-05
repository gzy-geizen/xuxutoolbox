package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRegionTest {

    private val context = WindowContext(packageName = "com.demo.app", isFullscreen = true)

    private fun engine(raw: String): RuleEngine = RuleEngine.fromJson(raw).first

    private fun root(bounds: Bounds): NodeSnapshot =
        NodeSnapshot(packageName = "com.demo.app", bounds = bounds)

    private val rawRule = """
        {"version":2,"rules":[
          {"id":"close.topright","priority":10,
           "window":{"fullscreen":true,"launchAgeMsMax":8000},
           "match":{"any":[{"viewIdRegex":"close_btn","clickable":true,"region":"top_right"}]},
           "action":{"type":"click"}}
        ]}
    """.trimIndent()

    @Test
    fun `右上角关闭按钮命中`() {
        val tree = NodeSnapshot(
            packageName = "com.demo.app",
            bounds = Bounds(0, 0, 1280, 2772),
            children = listOf(
                NodeSnapshot(
                    packageName = "com.demo.app",
                    bounds = Bounds(962, 104, 1228, 182),
                    viewId = "com.demo:id/close_btn",
                    clickable = true,
                ),
            ),
        )
        val hit = engine(rawRule).match(tree, context)
        assertEquals(1, hit.size)
        assertEquals("close.topright", hit.single().ruleId)
    }

    @Test
    fun `底部按钮即使 viewId 相似也不命中`() {
        // 美团定位引导那种底部关闭按钮
        val tree = NodeSnapshot(
            packageName = "com.demo.app",
            bounds = Bounds(0, 0, 1280, 2772),
            children = listOf(
                NodeSnapshot(
                    packageName = "com.demo.app",
                    bounds = Bounds(1149, 2363, 1247, 2461),
                    viewId = "com.demo:id/locate_guide_bottom_layer_close_btn",
                    clickable = true,
                ),
            ),
        )
        assertTrue(engine(rawRule).match(tree, context).isEmpty())
    }

    @Test
    fun `不带 region 的规则位置不受限`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"close.any","priority":10,
               "match":{"any":[{"viewIdRegex":"close_btn"}]},
               "action":{"type":"click"}}
            ]}
        """.trimIndent()
        val tree = NodeSnapshot(
            packageName = "com.demo.app",
            bounds = Bounds(0, 0, 1280, 2772),
            children = listOf(
                NodeSnapshot(
                    packageName = "com.demo.app",
                    bounds = Bounds(1149, 2363, 1247, 2461),
                    viewId = "com.demo:id/close_btn",
                ),
            ),
        )
        assertEquals(1, engine(raw).match(tree, context).size)
    }

    @Test
    fun `非法 region 在解析阶段被拒绝`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"bad.region","match":{"any":[{"text":"跳过","region":"middle"}]},"action":{"type":"click"}}
            ]}
        """.trimIndent()
        val (_, errors) = RuleEngine.fromJson(raw)
        assertTrue(errors.any { it.contains("region") })
    }

    @Test
    fun `sdk 内置规则要求右上角关闭`() {
        val merged = RuleLoader.merge(
            listOf(readRuleAsset("builtin.json"), readRuleAsset("sdk.json")),
        )
        val rule = merged.ruleSet.rules.first { it.id == "sdk.generic.close.id" }
        assertEquals("top_right", rule.match.any.single().region)
    }
}
