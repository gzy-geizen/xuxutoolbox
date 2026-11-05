package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 安全修复的回归测试：
 * 空选择器（`{}`）会匹配任意节点，必须在解析与引擎两层都被拒绝。
 */
class RuleSafetyTest {

    private val context = WindowContext(packageName = "com.demo.app", isFullscreen = true)
    private val tree = NodeSnapshot(
        packageName = "com.demo.app",
        bounds = Bounds(0, 0, 1280, 2772),
        children = listOf(
            NodeSnapshot(
                packageName = "com.demo.app",
                bounds = Bounds(962, 104, 1228, 182),
                text = "跳过",
            ),
        ),
    )

    @Test
    fun `解析器拒绝空选择器`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"empty.selector","match":{"any":[{}]},"action":{"type":"click"}}
            ]}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("不能为空") })
    }

    @Test
    fun `解析器拒绝空白 text 选择器`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"blank.text","match":{"any":[{"text":"   "}]},"action":{"type":"click"}}
            ]}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("不能为空") })
    }

    @Test
    fun `引擎直接构造时也会丢弃空选择器规则`() {
        val badRule = Rule(
            id = "empty.selector",
            match = MatchNode(any = listOf(Selector())),
            action = RuleAction(type = "gesture-center"),
        )
        val engine = RuleEngine(RuleSet(rules = listOf(badRule)))
        assertTrue(engine.match(tree, context).isEmpty())
    }

    @Test
    fun `合法选择器仍然正常命中`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"valid.text","match":{"any":[{"text":"跳过"}]},"action":{"type":"click"}}
            ]}
        """.trimIndent()
        val engine = RuleEngine.fromJson(raw).first
        assertEquals("valid.text", engine.match(tree, context).single().ruleId)
    }

    @Test
    fun `只有 region 的选择器算有效条件`() {
        val selector = Selector(region = "top_right")
        assertTrue(selector.hasCondition())
    }

    @Test
    fun `clickable 为 false 也算有效条件`() {
        assertTrue(Selector(clickable = false).hasCondition())
    }

    @Test
    fun `全空选择器不算有效条件`() {
        assertFalse(Selector().hasCondition())
    }
}
