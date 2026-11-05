package com.cx330.toolbox.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 坏规则语料（借鉴 SKIP 的 yaml-check fixture 思路）：
 * 每个文件都是一种「写错的规则」，必须被拒绝、且给出可读错误；
 * 一条坏规则绝不能拖垮整个规则包。
 */
class BadRuleCorpusTest {

    private fun raw(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("bad-rules/$name")
            ?: error("找不到语料: bad-rules/$name")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `坏规则逐条被拒绝并给出原因`() {
        val cases = mapOf(
            "malformed-json.json" to "JSON 解析失败",
            "empty-selector.json" to "selector[0] 不能为空",
            "invalid-text-regex.json" to "非法",
            "unknown-field.json" to "未知字段",
            "blank-activity.json" to "activityName",
            "negative-launch-age.json" to "不能为负",
            "bad-action-type.json" to "action.type 不支持",
            "bad-region.json" to "region 不支持",
            "zero-max-actions.json" to "maxActionsPerWindow",
            "tap-missing-point.json" to "tap",
        )
        cases.forEach { (file, expected) ->
            val result = RuleParser.parse(raw(file))
            assertTrue("$file 应该报错", result.errors.isNotEmpty())
            assertTrue(
                "$file 的错误应包含「$expected」，实际: ${result.errors}",
                result.errors.any { it.contains(expected) },
            )
            assertTrue("$file 不应产生有效规则", result.ruleSet.rules.isEmpty())
        }
    }

    @Test
    fun `合法规则不受坏语料影响`() {
        val result = RuleParser.parse(raw("valid-control.json"))
        assertEquals(emptyList<String>(), result.errors)
        assertEquals(1, result.ruleSet.rules.size)
    }

    @Test
    fun `重复 id 合并时只保留一条`() {
        val merged = RuleLoader.merge(listOf(raw("duplicate-id.json")))
        assertEquals(1, merged.ruleSet.rules.size)
        assertEquals("dup.rule", merged.ruleSet.rules.single().id)
    }
}
