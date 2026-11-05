package com.cx330.toolbox.core.module

import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeFallbackModuleTest {

    private val tree = NodeSnapshot(
        packageName = "com.demo.app",
        bounds = Bounds(0, 0, 1280, 2772),
    )
    private val event = CxEvent(1, 2, "com.demo.app", 800L)
    private val module = ShakeFallbackModule()

    private fun context(fullscreen: Boolean = true, age: Long = 800L) = WindowContext(
        packageName = "com.demo.app",
        isFullscreen = fullscreen,
        launchAgeMs = age,
    )

    @Test
    fun `全屏且冷启动窗口内返回右上角决策`() {
        val decisions = module.evaluate(event, context(), tree)
        assertEquals(1, decisions.size)
        val decision = decisions.single()
        assertEquals("shake.fallback.top_right", decision.ruleId)
        assertEquals("gesture-center", decision.action.type)
        assertTrue(decision.node.bounds.right == 1280)
        assertTrue(decision.node.bounds.left < decision.node.bounds.right)
        assertTrue(decision.node.bounds.top > 0)
    }

    @Test
    fun `严格模式下不触发兜底`() {
        val strictContext = WindowContext(
            packageName = "com.demo.app",
            isFullscreen = true,
            launchAgeMs = 800L,
            strictMode = true,
        )
        assertTrue(module.evaluate(event, strictContext, tree).isEmpty())
    }

    @Test
    fun `非全屏不触发`() {
        assertTrue(module.evaluate(event, context(fullscreen = false), tree).isEmpty())
    }

    @Test
    fun `超过 2_5 秒不触发`() {
        assertTrue(module.evaluate(event, context(age = 3_000L), tree).isEmpty())
    }

    @Test
    fun `没有快照不触发`() {
        assertTrue(module.evaluate(event, context(), null).isEmpty())
    }

    @Test
    fun `优先级低于正式规则模块`() {
        assertTrue(module.priority < SkipSplashModule { null }.priority)
    }
}
