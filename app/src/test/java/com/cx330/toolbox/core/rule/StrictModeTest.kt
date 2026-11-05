package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 严格模式：只按显式规则匹配，不启用通用文字兜底（借鉴 SKIP 的严格模式）。 */
class StrictModeTest {

    private val tree = NodeSnapshot(
        packageName = "com.demo.app",
        bounds = Bounds(0, 0, 1280, 2772),
        children = listOf(
            NodeSnapshot(
                packageName = "com.demo.app",
                bounds = Bounds(962, 104, 1228, 182),
                text = "跳过",
                clickable = true,
            ),
        ),
    )

    private fun engine(layer: String): RuleEngine {
        val raw = """
            {"version":2,"rules":[
              {"id":"demo.rule","layer":"$layer",
               "match":{"any":[{"text":"跳过"}]},
               "action":{"type":"click"}}
            ]}
        """.trimIndent()
        return RuleEngine.fromJson(raw).first
    }

    private fun context(strict: Boolean) = WindowContext(
        packageName = "com.demo.app",
        isFullscreen = true,
        launchAgeMs = 1_000L,
        strictMode = strict,
    )

    @Test
    fun `通用规则在严格模式下不参与`() {
        val generic = engine("generic")
        assertEquals(1, generic.match(tree, context(strict = false)).size)
        assertTrue(generic.match(tree, context(strict = true)).isEmpty())
    }

    @Test
    fun `显式规则在严格模式下仍然命中`() {
        assertEquals(1, engine("app").match(tree, context(strict = true)).size)
    }

    @Test
    fun `未知 layer 被解析器拒绝`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"bad.layer","layer":"weird",
               "match":{"any":[{"text":"跳过"}]},
               "action":{"type":"click"}}
            ]}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("layer") })
    }

    @Test
    fun `内置规则里 generic 层级已标记`() {
        val merged = RuleLoader.merge(listOf(readRuleAsset("builtin.json")))
        val genericIds = merged.ruleSet.rules.filter { it.layer == "generic" }.map { it.id }.toSet()
        assertTrue(genericIds.contains("generic.splash.skip"))
        assertTrue(genericIds.contains("generic.splash.close"))
        assertTrue(genericIds.size == 4)
    }
}
