package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.NodeSnapshot

/**
 * 一次匹配命中的候选：哪条规则、优先级、命中哪个节点、要做什么动作。
 *
 * [guard] 必须一起带出去：安全闸要按规则声明的冷却/每窗口次数执行，
 * 否则规则里写 `maxActionsPerWindow: 1` 只是装饰（P0 评审问题）。
 */
data class Candidate(
    val ruleId: String,
    val priority: Int,
    val node: NodeSnapshot,
    val action: RuleAction,
    val guard: Guard = Guard(),
)

/**
 * 规则引擎：输入「节点快照树 + 窗口上下文」，输出候选动作列表（按优先级降序）。
 *
 * 它是纯函数式的：不碰 Android 对象、不点击、不读写配置，所以可以在 JVM 单测里直接跑。
 */
class RuleEngine(ruleSet: RuleSet) {

    private val compiledRules: List<CompiledRule> = ruleSet.rules
        .filter { it.enabled }
        .mapNotNull { rule -> runCatching { CompiledRule(rule) }.getOrNull() }

    /**
     * 该包是否存在 App 专属规则（layer=app 且作用域包含它）。
     *
     * 关键字快路径只看得到 text/desc 里带「跳过/关闭」的节点，会漏掉整屏图片广告的
     * desc/面积条件。此时若直接采信通用规则，就会抢在 App 专属规则之前误点。
     */
    fun hasAppRuleFor(packageName: String): Boolean =
        compiledRules.any { it.rule.layer == "app" && it.rule.appScope.allows(packageName) }

    fun match(root: NodeSnapshot?, context: WindowContext): List<Candidate> {
        if (root == null) return emptyList()
        val nodes = root.flatten().toList()
        if (nodes.isEmpty()) return emptyList()

        val result = ArrayList<Candidate>()
        val screenBounds = root.bounds
        for (compiled in compiledRules) {
            if (!compiled.matchesWindow(context)) continue
            val hit = nodes.firstOrNull { compiled.matches(it, context, screenBounds) } ?: continue
            result += Candidate(
                ruleId = compiled.rule.id,
                priority = compiled.rule.priority,
                node = hit,
                action = compiled.rule.action,
                guard = compiled.rule.guard,
            )
        }
        result.sortByDescending { it.priority }
        return result
    }

    companion object {
        /** 从 JSON 构建引擎；同时返回解析过程中发现的问题。 */
        fun fromJson(raw: String): Pair<RuleEngine, List<String>> {
            val parsed = RuleParser.parse(raw)
            return RuleEngine(parsed.ruleSet) to parsed.errors
        }
    }
}
