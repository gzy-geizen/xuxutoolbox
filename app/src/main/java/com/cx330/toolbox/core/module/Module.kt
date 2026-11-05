package com.cx330.toolbox.core.module

import com.cx330.toolbox.core.rule.Guard
import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.NodeSnapshot

/** 模块收到的事件（与 Android 解耦，方便未来单测/扩展）。 */
data class CxEvent(
    val eventType: Int,
    val windowId: Int,
    val packageName: String,
    val launchAgeMs: Long,
)

/** 模块给出的一个决策。 */
data class ModuleDecision(
    val moduleId: String,
    val ruleId: String,
    val priority: Int,
    val node: NodeSnapshot,
    val action: RuleAction,
    /** 规则声明的执行护栏；由流水线交给 SafetyGate 与全局上限合并执行。 */
    val guard: Guard = Guard(),
)

/**
 * 功能模块接口（S12）。
 *
 * 现在的实现是「跳过开屏广告」；未来加签到、红包提醒等只需新增模块，
 * 不用改事件分发与安全闸。模块只做「判断」，不直接点击（动作由流水线统一执行）。
 */
interface Module {
    val id: String
    val name: String
    val priority: Int get() = 0

    fun evaluate(event: CxEvent, context: WindowContext, tree: NodeSnapshot?): List<ModuleDecision>

    /** 该包是否有本模块的 App 专属规则；用于避免关键字快路径抢先于专属规则。 */
    fun hasAppRule(packageName: String): Boolean = false
}
