package com.cx330.toolbox.core.rule

import kotlinx.serialization.Serializable

/**
 * 规则数据模型（v2）。
 *
 * 设计原则：规则是「数据」，引擎是「解释器」，这样广告 SDK 改版时只改 JSON、不发版。
 * 字段含义见计划书 §4.3。
 */
@Serializable
data class RuleSet(
    val version: Int = 2,
    val rules: List<Rule> = emptyList(),
)

@Serializable
data class Rule(
    val id: String = "",
    val priority: Int = 0,
    val enabled: Boolean = true,
    val appScope: AppScope = AppScope(),
    val window: WindowCondition = WindowCondition(),
    val match: MatchNode = MatchNode(),
    val guard: Guard = Guard(),
    val action: RuleAction = RuleAction(),
    val evidence: String? = null,
    /** 规则层级：app=显式规则；generic=通用兜底（严格模式下不参与）。 */
    val layer: String = "app",
)

@Serializable
data class AppScope(
    val mode: String = "all",
    val excluded: List<String> = emptyList(),
    val included: List<String> = emptyList(),
) {
    fun allows(packageName: String): Boolean = when (mode) {
        "excludeListed" -> packageName !in excluded
        "includeListed" -> packageName in included
        else -> true
    }
}

@Serializable
data class WindowCondition(
    val fullscreen: Boolean = false,
    val launchAgeMsMax: Long = Long.MAX_VALUE,
    val excludeIfImeVisible: Boolean = false,
    /** 精确匹配 Activity 类名；为空表示不限制。 */
    val activityName: String? = null,
    /** 包含匹配 Activity 类名（用于 Activity 名带版本/混淆的情况）。 */
    val activityNameContains: String? = null,
)

@Serializable
data class MatchNode(
    val any: List<Selector> = emptyList(),
    val all: List<Selector> = emptyList(),
)

@Serializable
data class Selector(
    val viewId: String? = null,
    val viewIdRegex: String? = null,
    val text: String? = null,
    val textContains: String? = null,
    val textRegex: String? = null,
    val descContains: String? = null,
    val descRegex: String? = null,
    val className: String? = null,
    val clickable: Boolean? = null,
    val minAreaDp2: Int? = null,
    val depthRange: List<Int>? = null,
    /** 相对屏幕的位置约束：top_right / top_center / top_left / bottom_right / bottom_center。 */
    val region: String? = null,
) {
    /**
     * 是否至少有一个有效条件。
     *
     * 空选择器 `{}` 会匹配任意节点，属于危险写法（尤其配 gesture-center 会点屏幕中心），
     * 因此解析器与引擎都会拒绝。
     */
    fun hasCondition(): Boolean =
        !viewId.isNullOrBlank() ||
            !viewIdRegex.isNullOrBlank() ||
            !text.isNullOrBlank() ||
            !textContains.isNullOrBlank() ||
            !textRegex.isNullOrBlank() ||
            !descContains.isNullOrBlank() ||
            !descRegex.isNullOrBlank() ||
            !className.isNullOrBlank() ||
            clickable != null ||
            minAreaDp2 != null ||
            depthRange != null ||
            region != null
}

@Serializable
data class Guard(
    val cooldownMs: Long = 800L,
    val maxActionsPerWindow: Int = 1,
    val forbidTextRegex: String? = null,
)

@Serializable
data class RuleAction(
    val type: String = "click",
    val fallback: String = "gesture-center",
    /** type=tap 时的归一化点击点（0..1，相对屏幕宽高），用于无障碍树里没有跳过按钮的广告。 */
    val tapXRatio: Float? = null,
    val tapYRatio: Float? = null,
    /** type=tap 的坐标锚点：screen=相对整块屏幕（旧行为）；node=相对命中的广告节点。 */
    val anchor: String = "screen",
)
