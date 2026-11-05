package com.cx330.toolbox.core.module

import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.NodeSnapshot

/**
 * 模块管理器：注册模块、按配置过滤、按优先级汇总决策。
 */
class ModuleManager(
    private val modules: List<Module>,
    private val isEnabled: (moduleId: String) -> Boolean,
) {

    fun enabledModules(): List<Module> =
        modules.filter { isEnabled(it.id) }.sortedByDescending { it.priority }

    fun decide(
        event: CxEvent,
        context: WindowContext,
        tree: NodeSnapshot?,
    ): List<ModuleDecision> {
        if (tree == null) return emptyList()
        val result = ArrayList<ModuleDecision>()
        for (module in enabledModules()) {
            result += module.evaluate(event, context, tree)
        }
        result.sortByDescending { it.priority }
        return result
    }

    fun modules(): List<Module> = modules

    fun hasAppRule(packageName: String): Boolean =
        enabledModules().any { it.hasAppRule(packageName) }
}
