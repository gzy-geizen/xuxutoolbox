package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.NodeSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 规则证据包（借鉴 SKIP 的 capture 工件思路）：
 * 每条内置规则都有一份「节点树 fixture」，用真实规则引擎回归，防止改规则时悄悄失效。
 *
 * fixture 位于 app/src/test/resources/rules/<ruleId>.json，可视化证据见 docs/rules/README.md。
 */
class RuleFixtureEvidenceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val engine: RuleEngine by lazy {
        val merged = RuleLoader.merge(
            listOf(readRuleAsset("builtin.json"), readRuleAsset("sdk.json")),
        )
        assertTrue("规则不应有错误: ${merged.errors}", merged.errors.isEmpty())
        RuleEngine(merged.ruleSet)
    }

    private fun loadFixture(name: String): NodeSnapshot {
        val stream = javaClass.classLoader?.getResourceAsStream("rules/$name")
            ?: error("找不到 fixture: rules/$name")
        return json.decodeFromString(
            NodeSnapshot.serializer(),
            stream.bufferedReader().use { it.readText() },
        )
    }

    @Test
    fun `每条内置规则都有可命中的节点树证据`() {
        data class Case(val file: String, val ruleId: String, val pkg: String)

        val cases = listOf(
            Case("generic.splash.skip.json", "generic.splash.skip", "com.demo.app"),
            // 无数字、纯「跳过」（圆形进度条广告常见）
            Case("generic.splash.skip.bare.json", "generic.splash.skip", "com.demo.app"),
            Case("generic.splash.close.json", "generic.splash.close", "com.demo.app"),
            Case("generic.splash.desc.json", "generic.splash.desc", "com.demo.app"),
            Case("generic.splash.id.json", "generic.splash.id", "com.demo.app"),
            Case("sdk.csj.splash.skip.json", "sdk.csj.splash.skip", "com.demo.app"),
            Case("sdk.generic.skip.id.json", "sdk.generic.skip.id", "com.demo.app"),
            Case("sdk.generic.close.id.json", "sdk.generic.close.id", "com.demo.app"),
            Case("sdk.generic.close.text.json", "sdk.generic.close.text", "com.demo.app"),
            // 真实广告捕获（2026-11-01 用户手动测试所得）
            Case("app.tieba.splash.json", "app.tieba.splash", "com.baidu.tieba"),
            Case("app.mtt.splash.json", "app.mtt.splash", "com.tencent.mtt"),
            Case("app.cmcc.splash.json", "app.cmcc.splash", "com.greenpoint.android.mc10086.activity"),
            Case("app.cbn.splash.json", "app.cbn.splash", "com.ai.obc.cbn.app"),
            Case("app.12306.splash.json", "app.12306.splash", "com.MobileTicket"),
            Case("app.xunlei.splash.json", "app.xunlei.splash", "com.xunlei.downloadprovider"),
        )
        cases.forEach { case ->
            val context = WindowContext(
                packageName = case.pkg,
                isFullscreen = true,
                launchAgeMs = 1_000L,
                densityDpi = 480,
            )
            val tree = loadFixture(case.file)
            val hits = engine.match(tree, context)
            assertTrue(
                "${case.file} 应命中 ${case.ruleId}，实际命中: ${hits.map { it.ruleId }}",
                hits.any { it.ruleId == case.ruleId },
            )
        }
    }
}
