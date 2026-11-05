package com.cx330.toolbox.core.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun `动作计数与规则统计`() {
        Diagnostics.reset()
        Diagnostics.onAction("com.demo.a", "rule.x", "跳过 5", "dry_run", dryRun = true)
        Diagnostics.onAction("com.demo.a", "rule.x", "跳过 3", "clicked", dryRun = false)
        val summary = Diagnostics.summary()
        assertEquals(2L, summary.candidateCount)
        assertEquals(1L, summary.dryRunCount)
        assertEquals(1L, summary.clickCount)
        assertEquals(2, summary.perRule["rule.x"])
        assertEquals(2, summary.perPackage["com.demo.a"])
    }

    @Test
    fun `被拦截也会计入规则统计`() {
        Diagnostics.reset()
        Diagnostics.onGated("com.demo.a", "rule.y", "window_cooldown")
        val summary = Diagnostics.summary()
        assertEquals(1L, summary.gatedCount)
        assertEquals(1, summary.perRule["rule.y"])
    }

    @Test
    fun `导出文本包含统计与事件`() {
        Diagnostics.reset()
        Diagnostics.onAction("com.demo.a", "rule.z", "关闭", "dry_run", dryRun = true)
        val text = Diagnostics.exportText()
        assertTrue(text.contains("cx330 toolbox 诊断日志"))
        assertTrue(text.contains("rule.z"))
        assertTrue(text.contains("com.demo.a"))
    }

    @Test
    fun `重置后统计清零`() {
        Diagnostics.reset()
        Diagnostics.onAction("com.demo.a", "rule.x", "跳过", "dry_run", dryRun = true)
        Diagnostics.reset()
        val summary = Diagnostics.summary()
        assertEquals(0L, summary.candidateCount)
        assertTrue(summary.perRule.isEmpty())
        assertTrue(Diagnostics.entriesSnapshot().isEmpty())
    }

    @Test
    fun `环形缓冲不会无限增长`() {
        Diagnostics.reset()
        repeat(400) { index ->
            Diagnostics.onAction("com.demo.$index", "rule.x", "跳过", "dry_run", dryRun = true)
        }
        assertTrue(Diagnostics.entriesSnapshot().size <= 300)
    }
}
