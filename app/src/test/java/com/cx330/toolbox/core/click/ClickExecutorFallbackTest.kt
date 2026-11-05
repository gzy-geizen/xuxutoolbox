package com.cx330.toolbox.core.click

import com.cx330.toolbox.core.rule.RuleAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住「节点点击失败后要不要手势兜底」这条策略：
 * 规则声明 fallback="none" 时必须失败退出，不能盲点节点中心。
 */
class ClickExecutorFallbackTest {

    @Test
    fun `fallback none 时返回 click_failed 且不做手势兜底`() {
        var fallbackDispatched = false
        val result = nodeClickFailureResult(RuleAction(fallback = "none")) {
            fallbackDispatched = true
            "gesture"
        }
        assertEquals("click_failed", result)
        assertFalse("fallback=none 不允许再派发坐标手势", fallbackDispatched)
    }

    @Test
    fun `默认 fallback 允许手势兜底`() {
        var fallbackDispatched = false
        val result = nodeClickFailureResult(RuleAction()) {
            fallbackDispatched = true
            "gesture"
        }
        assertEquals("gesture", result)
        assertTrue("默认 fallback 应保留旧的坐标兜底行为", fallbackDispatched)
    }

    @Test
    fun `显式 gesture-center 允许手势兜底`() {
        val result = nodeClickFailureResult(RuleAction(fallback = "gesture-center")) { "gesture" }
        assertEquals("gesture", result)
    }

    @Test
    fun `未知 fallback 值保持旧行为`() {
        // 旧版本导出的规则可能没有这个字段或写了别的东西，不能因为不认识就变成永久失败。
        val result = nodeClickFailureResult(RuleAction(fallback = "gesture")) { "gesture" }
        assertEquals("gesture", result)
    }
}
