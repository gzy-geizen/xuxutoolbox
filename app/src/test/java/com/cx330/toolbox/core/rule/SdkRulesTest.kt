package com.cx330.toolbox.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SdkRulesTest {

    private fun mergedEngine(): RuleEngine {
        val merged = RuleLoader.merge(
            listOf(readRuleAsset("builtin.json"), readRuleAsset("sdk.json")),
        )
        assertTrue("规则不应有错误: ${merged.errors}", merged.errors.isEmpty())
        return RuleEngine(merged.ruleSet)
    }

    @Test
    fun `通用与 SDK 规则合并后 id 唯一`() {
        val merged = RuleLoader.merge(
            listOf(readRuleAsset("builtin.json"), readRuleAsset("sdk.json")),
        )
        val ids = merged.ruleSet.rules.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.size >= 8)
    }

    @Test
    fun `穿山甲跳过按钮 viewId 命中`() {
        val target = node(
            text = null,
            viewId = "com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn",
            clickable = true,
        )
        val candidates = mergedEngine().match(target, context())
        assertEquals("sdk.csj.splash.skip", candidates.first().ruleId)
    }

    @Test
    fun `SDK 通用 skip viewId 命中`() {
        val target = node(viewId = "com.demo.sdk:id/splash_skip_view", clickable = true)
        val candidates = mergedEngine().match(target, context())
        assertTrue(candidates.any { it.ruleId == "sdk.generic.skip.id" })
    }

    @Test
    fun `SDK 通用 close viewId 命中`() {
        // 右上角关闭按钮：close.id 规则现在要求 region=top_right
        val target = com.cx330.toolbox.core.snapshot.NodeSnapshot(
            packageName = "com.demo.sdk",
            bounds = com.cx330.toolbox.core.snapshot.Bounds(0, 0, 1280, 2772),
            children = listOf(
                com.cx330.toolbox.core.snapshot.NodeSnapshot(
                    packageName = "com.demo.sdk",
                    bounds = com.cx330.toolbox.core.snapshot.Bounds(962, 104, 1228, 182),
                    viewId = "com.demo.sdk:id/ad_close_btn",
                    clickable = true,
                ),
            ),
        )
        val candidates = mergedEngine().match(target, context())
        assertTrue(candidates.any { it.ruleId == "sdk.generic.close.id" })
    }

    @Test
    fun `SDK close viewId 不可点击时不命中`() {
        val target = node(viewId = "com.demo.sdk:id/ad_close_btn", clickable = false)
        val candidates = mergedEngine().match(target, context())
        assertTrue(candidates.none { it.ruleId == "sdk.generic.close.id" })
    }

    @Test
    fun `关闭广告带倒计时文字命中`() {
        val target = node(text = "关闭广告 3", clickable = true, bounds = com.cx330.toolbox.core.snapshot.Bounds(0, 0, 200, 100))
        val candidates = mergedEngine().match(target, context())
        assertTrue(candidates.any { it.ruleId == "sdk.generic.close.text" })
    }

    @Test
    fun `SDK 规则优先级高于通用规则`() {
        val target = node(
            text = "跳过",
            viewId = "com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn",
            clickable = true,
        )
        val candidates = mergedEngine().match(target, context())
        assertEquals("sdk.csj.splash.skip", candidates.first().ruleId)
    }

    @Test
    fun `重复 id 会被丢弃并记录`() {
        val raw = """{"rules":[{"id":"dup","match":{"any":[{"text":"跳过"}]}}]}"""
        val merged = RuleLoader.merge(listOf(raw, raw))
        assertEquals(1, merged.ruleSet.rules.size)
        assertTrue(merged.errors.any { it.contains("重复规则 id") })
    }

    @Test
    fun `一份坏 JSON 不影响另一份`() {
        val good = """{"rules":[{"id":"ok","match":{"any":[{"text":"跳过"}]}}]}"""
        val merged = RuleLoader.merge(listOf("{\"rules\": [", good))
        assertEquals(1, merged.ruleSet.rules.size)
        assertEquals("ok", merged.ruleSet.rules.single().id)
        assertTrue(merged.errors.any { it.startsWith("JSON 解析失败") })
    }
}
