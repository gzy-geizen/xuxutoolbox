package com.cx330.toolbox.core.module

import com.cx330.toolbox.core.rule.RuleEngine
import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.NodeSnapshot

/**
 * 开屏广告模块：把「规则引擎匹配」包装成模块。
 * 规则加载由外层负责，模块只负责把命中结果翻译成统一的决策结构。
 */
class SkipSplashModule(
    private val engineProvider: () -> RuleEngine?,
) : Module {

    override val id: String = ID
    override val name: String = "跳过开屏广告"
    override val priority: Int = 10

    override fun hasAppRule(packageName: String): Boolean =
        engineProvider()?.hasAppRuleFor(packageName) == true

    override fun evaluate(
        event: CxEvent,
        context: WindowContext,
        tree: NodeSnapshot?,
    ): List<ModuleDecision> {
        val engine = engineProvider() ?: return emptyList()
        val candidates = engine.match(tree, context)
        if (candidates.isEmpty()) return emptyList()
        return candidates.map { candidate ->
            ModuleDecision(
                moduleId = id,
                ruleId = candidate.ruleId,
                priority = candidate.priority,
                node = candidate.node,
                action = candidate.action,
                guard = candidate.guard,
            )
        }
    }

    companion object {
        const val ID = "skip_splash"
    }
}
