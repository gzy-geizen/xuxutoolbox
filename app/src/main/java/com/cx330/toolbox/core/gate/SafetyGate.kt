package com.cx330.toolbox.core.gate

import com.cx330.toolbox.core.rule.Guard

/**
 * 动作安全闸：决定「这次命中到底允不允许点」。
 *
 * 纯逻辑（时间可注入），可以在 JVM 单测里跑。规则：
 * - 排除名单内的包直接拒绝；
 * - 同一窗口最多 [perWindowMax] 次动作（若规则 [Guard.maxActionsPerWindow] 更小则按规则值），
 *   两次动作间隔 ≥ [windowCooldownMs]（若规则 [Guard.cooldownMs] 更长则按规则值）；
 * - 同一节点 [nodeCooldownMs] 内不重复点；
 * - 全局 1 分钟内最多 [globalMaxActions] 次动作；
 * - 窗口状态 [windowStateTtlMs] 内没有新动作即视为过期（windowId 会被系统复用，
 *   不清理会导致上一次启动的计数永久卡住下一个窗口）。
 *
 * 规则 Guard 与全局值合并时一律取「更严格的一侧」：
 * 次数取 min（更少），冷却取 max（更慢）。规则只能收紧，不能放松全局安全下限。
 */
class SafetyGate(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val globalWindowMs: Long = 60_000L,
    private val globalMaxActions: Int = 6,
    private val perWindowMax: Int = 2,
    private val windowCooldownMs: Long = 800L,
    private val nodeCooldownMs: Long = 1_500L,
    private val windowStateTtlMs: Long = 60_000L,
) {

    data class Decision(val allowed: Boolean, val reason: String)

    private class WindowState(var actions: Int = 0, var lastActionAt: Long = 0L)

    private val windows = HashMap<Int, WindowState>()
    private val nodeLastActionAt = HashMap<String, Long>()
    private val globalActions = ArrayDeque<Long>()

    /**
     * 判定这次命中是否允许执行。
     *
     * @param guard 规则声明的执行护栏；传 null 时完全沿用全局默认值（兼容旧调用方）。
     */
    fun check(
        windowId: Int,
        nodeKey: String,
        packageName: String,
        excludedPackages: Set<String>,
        guard: Guard? = null,
    ): Decision {
        if (packageName in excludedPackages) {
            return Decision(false, "excluded_package")
        }
        val now = clock()
        pruneGlobal(now)
        pruneWindows(now)
        if (globalActions.size >= globalMaxActions) {
            return Decision(false, "global_rate_limit")
        }
        val maxActions = effectivePerWindowMax(guard)
        val cooldownMs = effectiveCooldownMs(guard)
        val state = windows[windowId]
        if (state != null) {
            if (state.actions >= maxActions) {
                return Decision(false, "window_action_limit")
            }
            if (state.lastActionAt > 0 && now - state.lastActionAt < cooldownMs) {
                return Decision(false, "window_cooldown")
            }
        }
        val lastNode = nodeLastActionAt[nodeKey]
        if (lastNode != null && now - lastNode < nodeCooldownMs) {
            return Decision(false, "node_cooldown")
        }
        return Decision(true, "ok")
    }

    /** 真正执行动作后调用，用于累计配额。dry-run 不调用。 */
    fun record(windowId: Int, nodeKey: String) {
        val now = clock()
        pruneWindows(now)
        val state = windows.getOrPut(windowId) { WindowState() }
        state.actions += 1
        state.lastActionAt = now
        nodeLastActionAt[nodeKey] = now
        globalActions.addLast(now)
        pruneGlobal(now)
    }

    fun reset() {
        windows.clear()
        nodeLastActionAt.clear()
        globalActions.clear()
    }

    /** 规则与全局的「每窗口最多次数」取更小值：规则只能收紧。 */
    internal fun effectivePerWindowMax(guard: Guard?): Int =
        if (guard == null) perWindowMax else minOf(guard.maxActionsPerWindow, perWindowMax)

    /** 规则与全局的「两次动作最短间隔」取更大值：规则只能让动作更慢，不能更快。 */
    internal fun effectiveCooldownMs(guard: Guard?): Long =
        if (guard == null) windowCooldownMs else maxOf(guard.cooldownMs, windowCooldownMs)

    private fun pruneGlobal(now: Long) {
        while (globalActions.isNotEmpty() && now - globalActions.first() > globalWindowMs) {
            globalActions.removeFirst()
        }
    }

    /** windowId 会被系统复用：超过 [windowStateTtlMs] 没动作的窗口状态直接丢弃。 */
    private fun pruneWindows(now: Long) {
        val iterator = windows.entries.iterator()
        while (iterator.hasNext()) {
            val state = iterator.next().value
            if (state.lastActionAt > 0 && now - state.lastActionAt > windowStateTtlMs) {
                iterator.remove()
            }
        }
    }
}
