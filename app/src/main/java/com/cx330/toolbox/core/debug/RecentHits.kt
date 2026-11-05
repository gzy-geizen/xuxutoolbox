package com.cx330.toolbox.core.debug

/** 一条候选记录：给首页/诊断页看「它刚才想点什么」。 */
data class Hit(
    val timeMs: Long,
    val packageName: String,
    val ruleId: String,
    val nodeText: String?,
    val bounds: String,
    val action: String,
    val result: String,
    val dryRun: Boolean,
)

/** 内存里的最近候选环形缓冲（最多 30 条，重启即清）。 */
object RecentHits {

    private const val CAPACITY = 30
    private val items = ArrayDeque<Hit>()

    @Synchronized
    fun add(hit: Hit) {
        items.addFirst(hit)
        while (items.size > CAPACITY) {
            items.removeLast()
        }
    }

    @Synchronized
    fun snapshot(): List<Hit> = items.toList()

    @Synchronized
    fun clear() = items.clear()
}
