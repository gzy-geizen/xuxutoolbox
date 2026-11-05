package com.cx330.toolbox.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleCompatTest {

    @Test
    fun oldTapRuleWithoutAnchorDefaultsToScreen() {
        val raw = """
            {"version":2,"rules":[
              {"id":"old.rule","match":{"any":[{"text":"跳过"}]},
               "action":{"type":"tap","fallback":"none","tapXRatio":0.5,"tapYRatio":0.5}}
            ]}
        """.trimIndent()
        val parsed = RuleParser.parse(raw)
        assertTrue(parsed.errors.isEmpty())
        assertEquals("screen", parsed.ruleSet.rules.first().action.anchor)
    }

    @Test
    fun blankSelectorValueIsRejectedByParser() {
        val raw = """
            {"version":2,"rules":[
              {"id":"blank","match":{"any":[{"textContains":"  "}]},
               "action":{"type":"click"}}
            ]}
        """.trimIndent()
        val parsed = RuleParser.parse(raw)
        assertTrue(parsed.errors.any { it.contains("不能为空字符串") })
    }

    @Test
    fun importedRuleWithNonUserIdOrGlobalScopeIsRejected() {
        val user = """
            {"version":2,"rules":[
              {"id":"builtin-like","appScope":{"mode":"all"},
               "match":{"any":[{"text":"跳过"}]},"action":{"type":"click"}},
              {"id":"user.global","appScope":{"mode":"all"},
               "match":{"any":[{"text":"跳过"}]},"action":{"type":"click"}}
            ]}
        """.trimIndent()
        val loaded = RuleRepository.load(emptyList(), null, userRaw = user)
        assertEquals(0, loaded.userRuleCount)
        assertTrue(loaded.errors.any { it.contains("user. 开头") })
        assertTrue(loaded.errors.any { it.contains("includeListed") })
    }

    @Test
    fun builderRejectsBlankMatchValue() {
        val draft = UserRuleDraft(
            id = "user.blank",
            packageName = "a.b",
            matchKind = UserMatchKind.TEXT_CONTAINS,
            matchValue = "  ",
        )
        assertThrows(IllegalArgumentException::class.java) { draft.build() }
    }

    @Test
    fun builderRejectsNonUserPrefixedId() {
        val draft = UserRuleDraft(
            id = "app.12306.splash",
            packageName = "a.b",
            matchKind = UserMatchKind.TEXT,
            matchValue = "跳过",
        )
        assertThrows(IllegalArgumentException::class.java) { draft.build() }
    }
}
