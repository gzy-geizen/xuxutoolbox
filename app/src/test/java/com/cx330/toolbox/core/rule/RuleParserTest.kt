package com.cx330.toolbox.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleParserTest {

    @Test
    fun `合法 JSON 可以解析出规则`() {
        val raw = """
            {"version":2,"rules":[{"id":"a","match":{"any":[{"textRegex":"^跳过$"}]}}]}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertEquals(1, result.ruleSet.rules.size)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `缺省字段使用默认值`() {
        val result = RuleParser.parse("""{"rules":[{"id":"a","match":{"any":[{"text":"x"}]}}]}""")
        val rule = result.ruleSet.rules.single()
        assertEquals(2, result.ruleSet.version)
        assertEquals(800L, rule.guard.cooldownMs)
        assertEquals(1, rule.guard.maxActionsPerWindow)
        assertEquals("click", rule.action.type)
        assertEquals("gesture-center", rule.action.fallback)
    }

    @Test
    fun `未知字段导致该规则被拒绝`() {
        val raw = """
            {"version":2,"rules":[
              {"id":"bad","foo":"bar","match":{"any":[{"text":"x"}]}},
              {"id":"good","match":{"any":[{"text":"y"}]}}
            ],"bar":1}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        // 根节点的未知字段忽略（向前兼容），但规则里的未知字段会让该条规则被拒绝
        assertEquals(listOf("good"), result.ruleSet.rules.map { it.id })
        assertTrue(result.errors.any { it.contains("未知字段") })
    }

    @Test
    fun `坏 JSON 返回错误且规则为空`() {
        val result = RuleParser.parse("""{"rules": [ """)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.single().startsWith("JSON 解析失败"))
    }

    @Test
    fun `空 id 的规则被丢弃`() {
        val result = RuleParser.parse("""{"rules":[{"id":"","match":{"any":[{"text":"x"}]}}]}""")
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("<no-id>") })
    }

    @Test
    fun `没有 match 的规则被丢弃`() {
        val result = RuleParser.parse("""{"rules":[{"id":"a"}]}""")
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("match.any") })
    }

    @Test
    fun `非法 action 的规则被丢弃`() {
        val raw = """{"rules":[{"id":"a","match":{"any":[{"text":"x"}]},"action":{"type":"explode"}}]}"""
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("action.type") })
    }

    @Test
    fun `非法正则的规则被丢弃`() {
        val raw = """{"rules":[{"id":"a","match":{"any":[{"textRegex":"("}]}}]}"""
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("textRegex") })
    }

    @Test
    fun `非法 forbid 正则的规则被丢弃`() {
        val raw = """{"rules":[{"id":"a","match":{"any":[{"text":"x"}]},"guard":{"forbidTextRegex":"[z-a]"}}]}"""
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("forbidTextRegex") })
    }

    @Test
    fun `非法 appScope 模式被丢弃`() {
        val raw = """{"rules":[{"id":"a","match":{"any":[{"text":"x"}]},"appScope":{"mode":"whatever"}}]}"""
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("appScope.mode") })
    }

    @Test
    fun `一条坏规则不影响其它好规则`() {
        val raw = """
            {"rules":[
              {"id":"bad","match":{"any":[{"textRegex":"("}]}},
              {"id":"good","match":{"any":[{"text":"跳过"}]}}
            ]}
        """.trimIndent()
        val result = RuleParser.parse(raw)
        assertEquals(1, result.ruleSet.rules.size)
        assertEquals("good", result.ruleSet.rules.single().id)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `内置规则文件可以解析且没有错误`() {
        val result = RuleParser.parse(BuiltinRules.raw)
        assertTrue("内置规则不应有错误: ${result.errors}", result.errors.isEmpty())
        assertTrue(result.ruleSet.rules.size >= 2)
    }

    @Test
    fun `内置规则的 id 唯一`() {
        val result = RuleParser.parse(BuiltinRules.raw)
        val ids = result.ruleSet.rules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `内置通用层能识别纯跳过并带禁止词`() {
        val (engine, errors) = RuleEngine.fromJson(BuiltinRules.raw)
        assertTrue(errors.isEmpty())
        val candidates = engine.match(node(text = "跳过"), context())
        assertEquals(1, candidates.size)
        assertEquals("generic.splash.skip", candidates.single().ruleId)
        assertTrue(candidates.single().action.type == "click")
    }

    @Test
    fun `内置通用层能识别倒计时跳过`() {
        val (engine, _) = RuleEngine.fromJson(BuiltinRules.raw)
        val candidates = engine.match(node(text = "跳过 5"), context())
        assertEquals(1, candidates.size)
    }

    @Test
    fun `非法 viewIdRegex 的规则被丢弃`() {
        val raw = """{"rules":[{"id":"a","match":{"any":[{"viewIdRegex":"("}]}}]}"""
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("viewIdRegex") })
    }

    @Test
    fun `非法 descRegex 的规则被丢弃`() {
        val raw = """{"rules":[{"id":"a","match":{"any":[{"descRegex":"[z-a]"}]}}]}"""
        val result = RuleParser.parse(raw)
        assertTrue(result.ruleSet.rules.isEmpty())
        assertTrue(result.errors.any { it.contains("descRegex") })
    }

    @Test
    fun `内置规则包含描述与 viewId 两类兜底`() {
        val result = RuleParser.parse(BuiltinRules.raw)
        val ids = result.ruleSet.rules.map { it.id }
        assertTrue(ids.contains("generic.splash.desc"))
        assertTrue(ids.contains("generic.splash.id"))
    }
}
