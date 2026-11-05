package com.cx330.toolbox.core.gate

import com.cx330.toolbox.core.rule.Guard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyGateTest {

    private var now = 1_000L

    private fun createGate() = SafetyGate(
        clock = { now },
        globalWindowMs = 60_000L,
        globalMaxActions = 3,
        perWindowMax = 2,
        windowCooldownMs = 800L,
        nodeCooldownMs = 1_500L,
    )

    @Test
    fun `首次命中允许`() {
        assertTrue(createGate().check(1, "n1", "com.demo.app", emptySet()).allowed)
    }

    @Test
    fun `排除名单里的包直接拒绝`() {
        val decision = createGate().check(1, "n1", "com.bank.app", setOf("com.bank.app"))
        assertFalse(decision.allowed)
        assertEquals("excluded_package", decision.reason)
    }

    @Test
    fun `同窗口动作次数达到上限后拒绝`() {
        val gate = createGate()
        gate.record(1, "n1")
        now += 900
        gate.record(1, "n2")
        now += 900
        val decision = gate.check(1, "n3", "com.demo.app", emptySet())
        assertFalse(decision.allowed)
        assertEquals("window_action_limit", decision.reason)
    }

    @Test
    fun `两次动作间隔太短被拒绝`() {
        val gate = createGate()
        gate.record(1, "n1")
        now += 100
        val decision = gate.check(1, "n2", "com.demo.app", emptySet())
        assertFalse(decision.allowed)
        assertEquals("window_cooldown", decision.reason)
    }

    @Test
    fun `同一节点冷却期内不重复点`() {
        val gate = createGate()
        gate.record(1, "n1")
        now += 900
        val decision = gate.check(2, "n1", "com.demo.app", emptySet())
        assertFalse(decision.allowed)
        assertEquals("node_cooldown", decision.reason)
    }

    @Test
    fun `全局频率限制生效`() {
        val gate = createGate()
        gate.record(1, "n1")
        now += 900
        gate.record(2, "n2")
        now += 900
        gate.record(3, "n3")
        now += 900
        val decision = gate.check(4, "n4", "com.demo.app", emptySet())
        assertFalse(decision.allowed)
        assertEquals("global_rate_limit", decision.reason)
    }

    @Test
    fun `全局窗口过期后恢复允许`() {
        val gate = createGate()
        gate.record(1, "n1")
        now += 900
        gate.record(2, "n2")
        now += 900
        gate.record(3, "n3")
        now += 61_000
        assertTrue(gate.check(4, "n4", "com.demo.app", emptySet()).allowed)
    }

    @Test
    fun `不同窗口各自的次数配额独立`() {
        val gate = createGate()
        gate.record(1, "n1")
        gate.record(1, "n2")
        now += 61_000
        assertTrue(gate.check(2, "n3", "com.demo.app", emptySet()).allowed)
    }

    @Test
    fun `reset 之后状态清空`() {
        val gate = createGate()
        gate.record(1, "n1")
        gate.record(1, "n2")
        gate.reset()
        assertTrue(gate.check(1, "n3", "com.demo.app", emptySet()).allowed)
    }

    @Test
    fun `规则声明的每窗口一次真正生效`() {
        val gate = createGate()
        // Guard 默认 maxActionsPerWindow = 1，即「这个窗口跳过一次就别再点了」。
        val guard = Guard(cooldownMs = 800L, maxActionsPerWindow = 1)
        gate.record(1, "n1")
        now += 900
        val decision = gate.check(1, "n2", "com.demo.app", emptySet(), guard)
        assertFalse(decision.allowed)
        assertEquals("window_action_limit", decision.reason)
    }

    @Test
    fun `规则比全局更长的冷却时间生效`() {
        val gate = createGate()
        val guard = Guard(cooldownMs = 5_000L, maxActionsPerWindow = 5)
        gate.record(1, "n1")
        now += 1_500
        val decision = gate.check(1, "n2", "com.demo.app", emptySet(), guard)
        assertFalse(decision.allowed)
        assertEquals("window_cooldown", decision.reason)
        now += 3_600
        assertTrue(gate.check(1, "n2", "com.demo.app", emptySet(), guard).allowed)
    }

    @Test
    fun `规则不能放宽全局冷却下限`() {
        val gate = createGate()
        val guard = Guard(cooldownMs = 0L, maxActionsPerWindow = 5)
        gate.record(1, "n1")
        now += 100
        val decision = gate.check(1, "n2", "com.demo.app", emptySet(), guard)
        assertFalse(decision.allowed)
        assertEquals("window_cooldown", decision.reason)
    }

    @Test
    fun `规则不能放宽全局每窗口次数`() {
        val gate = createGate()
        val guard = Guard(cooldownMs = 0L, maxActionsPerWindow = 10)
        gate.record(1, "n1")
        now += 900
        gate.record(1, "n2")
        now += 900
        val decision = gate.check(1, "n3", "com.demo.app", emptySet(), guard)
        assertFalse(decision.allowed)
        assertEquals("window_action_limit", decision.reason)
    }

    @Test
    fun `窗口空闲超过 TTL 后配额清零`() {
        val gate = SafetyGate(
            clock = { now },
            globalWindowMs = 60_000L,
            globalMaxActions = 3,
            perWindowMax = 2,
            windowCooldownMs = 800L,
            nodeCooldownMs = 1_500L,
            windowStateTtlMs = 10_000L,
        )
        gate.record(1, "n1")
        gate.record(1, "n2")
        now += 11_000
        // 全局窗口还没到期（2/3），但该窗口的状态已过期，windowId 复用时不该被旧计数卡死。
        assertTrue(gate.check(1, "n3", "com.demo.app", emptySet()).allowed)
    }

    @Test
    fun `不传 guard 时保持全局默认行为`() {
        val gate = createGate()
        gate.record(1, "n1")
        now += 900
        gate.record(1, "n2")
        now += 900
        val decision = gate.check(1, "n3", "com.demo.app", emptySet(), guard = null)
        assertFalse(decision.allowed)
        assertEquals("window_action_limit", decision.reason)
    }
}
