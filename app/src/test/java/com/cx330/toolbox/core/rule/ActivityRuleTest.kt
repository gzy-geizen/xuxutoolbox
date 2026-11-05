package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Activity 维度规则（借鉴 SKIP 的 activityName 精确匹配）。 */
class ActivityRuleTest {

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

    private fun context(activity: String? = "com.demo.app.SplashActivity") = WindowContext(
        packageName = "com.demo.app",
        isFullscreen = true,
        activityName = activity,
    )

    private fun engine(raw: String): RuleEngine = RuleEngine.fromJson(raw).first

    private val exactRule = """
        {"version":2,"rules":[
          {"id":"splash.exact","priority":10,
           "window":{"fullscreen":true,"activityName":"com.demo.app.SplashActivity"},
           "match":{"any":[{"text":"跳过"}]},
           "action":{"type":"click"}}
        ]}
    """.trimIndent()

    @Test
    fun `activityName 精确匹配时命中`() {
        assertEquals(1, engine(exactRule).match(tree, context()).size)
    }

    @Test
    fun `activityName 不同则不命中`() {
        val hits = engine(exactRule).match(tree, context("com.demo.app.MainActivity"))
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `没有 activityName 时不命中限定规则`() {
        assertTrue(engine(exactRule).match(tree, context(null)).isEmpty())
    }

    @Test
    fun `activityNameContains 包含匹配`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"splash.contains","priority":10,
               "window":{"fullscreen":true,"activityNameContains":"Splash"},
               "match":{"any":[{"text":"跳过"}]},
               "action":{"type":"click"}}
            ]}
        """.trimIndent()
        assertEquals(1, engine(raw).match(tree, context()).size)
        assertTrue(engine(raw).match(tree, context("com.demo.app.MainActivity")).isEmpty())
    }

    @Test
    fun `空 activityName 的规则被拒绝`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"bad.activity","window":{"activityName":"  "},
               "match":{"any":[{"text":"跳过"}]},"action":{"type":"click"}}
            ]}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("activityName") })
    }
}
