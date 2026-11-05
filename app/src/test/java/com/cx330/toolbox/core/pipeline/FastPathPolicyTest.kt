package com.cx330.toolbox.core.pipeline

import com.cx330.toolbox.core.module.ModuleDecision
import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastPathPolicyTest {

    private val node = NodeSnapshot(packageName = "com.demo.app", bounds = Bounds(0, 0, 100, 100))

    private fun decision(ruleId: String) = ModuleDecision(
        moduleId = "skip_splash",
        ruleId = ruleId,
        priority = 1,
        node = node,
        action = RuleAction(),
    )

    @Test
    fun `没有自定义规则时可用快路径`() {
        assertTrue(FastPathPolicy.canUse(listOf(decision("generic.splash.skip")), hasAppRule = false))
    }

    @Test
    fun `有自定义规则但快路径命中自定义规则时可用`() {
        assertTrue(FastPathPolicy.canUse(listOf(decision("app.demo.splash")), hasAppRule = true))
        assertTrue(
            FastPathPolicy.canUse(
                listOf(decision("generic.splash.skip"), decision("app.demo.splash")),
                hasAppRule = true,
            ),
        )
    }

    @Test
    fun `有自定义规则但快路径只命中通用规则时必须回退完整快照`() {
        assertFalse(FastPathPolicy.canUse(listOf(decision("generic.splash.skip")), hasAppRule = true))
    }

    @Test
    fun `快路径没有结果时不可用`() {
        assertFalse(FastPathPolicy.canUse(emptyList(), hasAppRule = false))
        assertFalse(FastPathPolicy.canUse(emptyList(), hasAppRule = true))
    }
}
