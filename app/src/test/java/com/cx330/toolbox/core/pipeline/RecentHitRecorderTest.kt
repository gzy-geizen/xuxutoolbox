package com.cx330.toolbox.core.pipeline

import com.cx330.toolbox.core.debug.Hit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁住「最近命中」的写入条件：只有真正放行的动作才记，
 * 被安全闸拦下的不写（它没点成，不该显示成「刚才想点什么」）。
 */
class RecentHitRecorderTest {

    private fun hit(dryRun: Boolean = false, result: String = "clicked") = Hit(
        timeMs = 1_000L,
        packageName = "com.demo.app",
        ruleId = "builtin.skip",
        nodeText = "跳过",
        bounds = "[0,100][200,200]",
        action = "click",
        result = result,
        dryRun = dryRun,
    )

    @Test
    fun `被拦截的动作不写入最近命中`() {
        var built = false
        val recorded = recentHitFor(gateAllowed = false) {
            built = true
            hit(result = "gated:window_action_limit")
        }
        assertNull(recorded)
        assertFalse("被拦截时不应构造 Hit，更不应写缓冲", built)
    }

    @Test
    fun `放行的动作写入最近命中`() {
        val recorded = recentHitFor(gateAllowed = true) { hit() }
        assertNotNull(recorded)
        assertEquals("builtin.skip", recorded!!.ruleId)
        assertEquals("clicked", recorded.result)
    }

    @Test
    fun `试运行放行动作也写入最近命中`() {
        val recorded = recentHitFor(gateAllowed = true) { hit(dryRun = true, result = "dry_run") }
        assertNotNull(recorded)
        assertTrue(recorded!!.dryRun)
        assertEquals("dry_run", recorded.result)
    }
}
