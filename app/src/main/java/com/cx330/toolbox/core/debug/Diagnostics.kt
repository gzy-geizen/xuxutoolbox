package com.cx330.toolbox.core.debug

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地诊断数据（内存环形缓冲 + 计数），用于「诊断页展示」与「一键导出」。
 * 不联网、不写敏感内容：只记录包名、规则 id、节点文字与结果。
 */
object Diagnostics {

    private const val MAX_ENTRIES = 300

    data class Entry(
        val timeMs: Long,
        val category: String,
        val message: String,
    )

    data class Summary(
        val totalWindowEvents: Long,
        val candidateCount: Long,
        val dryRunCount: Long,
        val clickCount: Long,
        val gatedCount: Long,
        val noCandidateCount: Long,
        val perRule: Map<String, Int>,
        val perPackage: Map<String, Int>,
    )

    private val entries = ArrayDeque<Entry>()
    private var totalWindowEvents = 0L
    private var candidateCount = 0L
    private var dryRunCount = 0L
    private var clickCount = 0L
    private var gatedCount = 0L
    private var noCandidateCount = 0L
    private val perRule = HashMap<String, Int>()
    private val perPackage = HashMap<String, Int>()
    private val startedAt = System.currentTimeMillis()

    @Synchronized
    fun onWindowEvent() {
        totalWindowEvents++
    }

    @Synchronized
    fun onNoCandidate() {
        noCandidateCount++
    }

    @Synchronized
    fun onGated(packageName: String, ruleId: String, reason: String) {
        gatedCount++
        perRule.merge(ruleId, 1, Int::plus)
        add("gated", "pkg=$packageName rule=$ruleId reason=$reason")
    }

    @Synchronized
    fun onAction(
        packageName: String,
        ruleId: String,
        nodeText: String?,
        result: String,
        dryRun: Boolean,
    ) {
        candidateCount++
        perRule.merge(ruleId, 1, Int::plus)
        perPackage.merge(packageName, 1, Int::plus)
        if (dryRun) dryRunCount++
        if (result == "clicked" || result == "gesture") clickCount++
        add("action", "pkg=$packageName rule=$ruleId node=$nodeText result=$result dryRun=$dryRun")
    }

    @Synchronized
    fun entriesSnapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun summary(): Summary = Summary(
        totalWindowEvents = totalWindowEvents,
        candidateCount = candidateCount,
        dryRunCount = dryRunCount,
        clickCount = clickCount,
        gatedCount = gatedCount,
        noCandidateCount = noCandidateCount,
        perRule = perRule.toMap(),
        perPackage = perPackage.toMap(),
    )

    @Synchronized
    fun reset() {
        entries.clear()
        totalWindowEvents = 0L
        candidateCount = 0L
        dryRunCount = 0L
        clickCount = 0L
        gatedCount = 0L
        noCandidateCount = 0L
        perRule.clear()
        perPackage.clear()
    }

    /** 导出为纯文本（可直接发微信/邮件给开发者排查）。 */
    @Synchronized
    fun exportText(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val builder = StringBuilder()
        builder.appendLine("cx330 toolbox 诊断日志")
        builder.appendLine("导出时间：${format.format(Date())}")
        builder.appendLine("统计起点：${format.format(Date(startedAt))}")
        builder.appendLine(
            "窗口事件=${totalWindowEvents} 候选=${candidateCount} 试运行=${dryRunCount} " +
                "点击=${clickCount} 被拦截=${gatedCount} 无候选=${noCandidateCount}",
        )
        builder.appendLine("--- 按规则 ---")
        perRule.entries.sortedByDescending { it.value }.forEach { (rule, count) ->
            builder.appendLine("$rule: $count")
        }
        builder.appendLine("--- 按应用 ---")
        perPackage.entries.sortedByDescending { it.value }.forEach { (pkg, count) ->
            builder.appendLine("$pkg: $count")
        }
        builder.appendLine("--- 最近事件（最多 $MAX_ENTRIES 条） ---")
        entries.forEach { entry ->
            builder.appendLine("${format.format(Date(entry.timeMs))} [${entry.category}] ${entry.message}")
        }
        return builder.toString()
    }

    private fun add(category: String, message: String) {
        entries.addFirst(Entry(System.currentTimeMillis(), category, message))
        while (entries.size > MAX_ENTRIES) {
            entries.removeLast()
        }
    }
}
