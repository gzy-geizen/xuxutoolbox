package com.cx330.toolbox.core.module

import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleManagerTest {

    private val tree = NodeSnapshot(packageName = "com.demo.app", bounds = Bounds(0, 0, 100, 100))
    private val context = WindowContext(packageName = "com.demo.app")
    private val event = CxEvent(1, 2, "com.demo.app", 500L)

    private fun decision(moduleId: String, ruleId: String, priority: Int) = ModuleDecision(
        moduleId = moduleId,
        ruleId = ruleId,
        priority = priority,
        node = tree,
        action = RuleAction(),
    )

    private class FakeModule(
        override val id: String,
        override val name: String,
        override val priority: Int,
        private val decisions: List<ModuleDecision>,
        private val appRulePackages: Set<String> = emptySet(),
    ) : Module {
        override fun evaluate(event: CxEvent, context: WindowContext, tree: NodeSnapshot?): List<ModuleDecision> =
            decisions

        override fun hasAppRule(packageName: String): Boolean = packageName in appRulePackages
    }

    @Test
    fun `只有启用的模块参与 App 专属规则判断`() {
        val module = FakeModule("m1", "模块", 1, emptyList(), setOf("com.demo.app"))
        assertTrue(ModuleManager(listOf(module)) { true }.hasAppRule("com.demo.app"))
        assertTrue(!ModuleManager(listOf(module)) { false }.hasAppRule("com.demo.app"))
        assertTrue(!ModuleManager(listOf(module)) { true }.hasAppRule("com.other.app"))
    }

    @Test
    fun `按模块优先级汇总决策`() {
        val low = FakeModule("low", "低", 1, listOf(decision("low", "r1", 1)))
        val high = FakeModule("high", "高", 10, listOf(decision("high", "r2", 10)))
        val manager = ModuleManager(listOf(low, high)) { true }
        val decisions = manager.decide(event, context, tree)
        assertEquals(listOf("high", "low"), decisions.map { it.moduleId })
    }

    @Test
    fun `关闭的模块不参与决策`() {
        val module = FakeModule("m1", "模块", 1, listOf(decision("m1", "r1", 1)))
        val manager = ModuleManager(listOf(module)) { false }
        assertTrue(manager.decide(event, context, tree).isEmpty())
        assertTrue(manager.enabledModules().isEmpty())
    }

    @Test
    fun `树为空时返回空决策`() {
        val module = FakeModule("m1", "模块", 1, listOf(decision("m1", "r1", 1)))
        val manager = ModuleManager(listOf(module)) { true }
        assertTrue(manager.decide(event, context, null).isEmpty())
    }

    @Test
    fun `多个模块的决策合并后按优先级排序`() {
        val a = FakeModule("a", "A", 5, listOf(decision("a", "r1", 5), decision("a", "r0", 1)))
        val b = FakeModule("b", "B", 9, listOf(decision("b", "r2", 9)))
        val decisions = ModuleManager(listOf(a, b)) { true }.decide(event, context, tree)
        assertEquals(listOf("b", "a", "a"), decisions.map { it.moduleId })
        assertEquals(listOf(9, 5, 1), decisions.map { it.priority })
    }
}
