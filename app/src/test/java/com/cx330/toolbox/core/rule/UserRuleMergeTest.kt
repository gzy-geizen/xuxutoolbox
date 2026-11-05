package com.cx330.toolbox.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleMergeTest {

    private val builtin = """
        {"version":2,"rules":[
          {"id":"r1","priority":10,"appScope":{"mode":"includeListed","included":["a.b"]},"window":{"launchAgeMsMax":8000},
           "match":{"any":[{"text":"跳过"}]},"action":{"type":"click"}}
        ]}
    """.trimIndent()

    @Test
    fun userRuleWithSameIdOverridesBuiltin() {
        val user = """
            {"version":2,"rules":[
              {"id":"user.r1","priority":900,"appScope":{"mode":"includeListed","included":["a.b"]},"window":{"launchAgeMsMax":8000},
               "match":{"any":[{"text":"跳过广告"}]},"action":{"type":"tap","fallback":"none",
               "tapXRatio":0.9,"tapYRatio":0.1,"anchor":"node"}}
            ]}
        """.trimIndent()

        val loaded = RuleRepository.load(
            assetContents = listOf(builtin),
            importedRaw = null,
            userRaw = user,
        )
        assertEquals(2, loaded.ruleSet.rules.size)
        val userRule = loaded.ruleSet.rules.first { it.id == "user.r1" }
        assertEquals(900, userRule.priority)
        assertEquals("node", userRule.action.anchor)
        assertEquals(1, loaded.userRuleCount)
        // 用户规则不能顶掉内置 id，内置仍保留
        assertTrue(loaded.ruleSet.rules.any { it.id == "r1" })
    }

    @Test
    fun userRuleAddsNewRuleAndKeepsBuiltin() {
        val user = """
            {"version":2,"rules":[
              {"id":"user.r2","priority":300,"appScope":{"mode":"includeListed","included":["c.d"]},"window":{"launchAgeMsMax":8000},
               "match":{"any":[{"descContains":"广告"}]},"action":{"type":"tap","fallback":"none",
               "tapXRatio":0.85,"tapYRatio":0.07,"anchor":"node"}}
            ]}
        """.trimIndent()

        val loaded = RuleRepository.load(listOf(builtin), null, userRaw = user)
        assertEquals(2, loaded.ruleSet.rules.size)
        assertTrue(loaded.ruleSet.rules.any { it.id == "r1" })
        assertTrue(loaded.ruleSet.rules.any { it.id == "user.r2" })
        val imported = loaded.ruleSet.rules.first { it.id == "user.r2" }
        assertTrue(!imported.guard.forbidTextRegex.isNullOrBlank())
    }

    @Test
    fun brokenUserRuleIsReportedButDoesNotBreakBuiltin() {
        val user = """{"version":2,"rules":[{"id":"bad","match":{"any":[]}}]}"""
        val loaded = RuleRepository.load(listOf(builtin), null, userRaw = user)
        assertTrue(loaded.errors.isNotEmpty())
        assertEquals(1, loaded.ruleSet.rules.size)
    }

    @Test
    fun parserRejectsUnknownTapAnchor() {
        val raw = """
            {"version":2,"rules":[
              {"id":"x","match":{"any":[{"text":"跳过"}]},
               "action":{"type":"tap","fallback":"none","tapXRatio":0.5,"tapYRatio":0.5,"anchor":"weird"}}
            ]}
        """.trimIndent()
        val parsed = RuleParser.parse(raw)
        assertTrue(parsed.errors.any { it.contains("anchor") })
    }

    @Test
    fun parserAcceptsNodeAnchor() {
        val raw = """
            {"version":2,"rules":[
              {"id":"x","match":{"any":[{"text":"跳过"}]},
               "action":{"type":"tap","fallback":"none","tapXRatio":0.5,"tapYRatio":0.5,"anchor":"node"}}
            ]}
        """.trimIndent()
        val parsed = RuleParser.parse(raw)
        assertTrue(parsed.errors.isEmpty())
        assertEquals("node", parsed.ruleSet.rules.first().action.anchor)
    }
}
