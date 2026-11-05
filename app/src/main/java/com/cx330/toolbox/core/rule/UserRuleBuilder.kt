package com.cx330.toolbox.core.rule

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 用户自助规则的“表单草稿”到 [Rule] 的转换。
 *
 * 放在 core 里而不是 UI 里，是为了：
 * - 可以脱离 Compose 在 JVM 单测里验证（表单 → 合法规则 → 能命中节点树）；
 * - UI 只负责收集字段，规则合法性由同一份逻辑保证。
 */
enum class UserMatchKind(val label: String) {
    TEXT("文本精确"),
    TEXT_CONTAINS("文本包含"),
    DESC_CONTAINS("描述包含"),
    VIEW_ID("控件 ID"),
    REGION("仅区域（top_right）"),
}

@kotlinx.serialization.Serializable
private data class RuleWrapper(val rules: List<Rule>)

object RuleDefaults {
    /** 用户规则默认防误点词：避免比例点击落到“下载/安装/同意/支付”这类按钮上。 */
    const val FORBID_TEXT_REGEX = "下载|安装|同意|授权|支付|领取|登录|打开|前往|立即"
}

data class UserRuleDraft(
    val id: String,
    val packageName: String,
    val matchKind: UserMatchKind,
    val matchValue: String,
    val region: String? = null,
    val actionType: String = "click",
    val tapXRatio: Float = 0.854f,
    val tapYRatio: Float = 0.073f,
    val anchor: String = "node",
    val priority: Int = 200,
) {
    /** 返回合法规则；不合法时抛出带可读原因的异常，交给 UI 显示。 */
    fun build(): Rule {
        require(packageName.isNotBlank()) { "包名不能为空" }
        require(id.isBlank() || id.startsWith("user.")) { "规则 id 必须以 user. 开头" }
        val value = matchValue.trim()
        if (matchKind != UserMatchKind.REGION) {
            require(value.isNotBlank()) { "匹配值不能为空" }
        }
        val selector = when (matchKind) {
            UserMatchKind.TEXT -> Selector(text = value)
            UserMatchKind.TEXT_CONTAINS -> Selector(textContains = value)
            UserMatchKind.DESC_CONTAINS -> Selector(descContains = value)
            UserMatchKind.VIEW_ID -> Selector(viewId = value)
            UserMatchKind.REGION -> Selector(region = "top_right")
        }
        val withRegion = if (region != null && matchKind != UserMatchKind.REGION) {
            selector.copy(region = region)
        } else {
            selector
        }
        val rule = Rule(
            id = id.ifBlank {
                "user.${packageName.substringAfterLast('.')}.${System.currentTimeMillis()}"
            },
            priority = priority,
            enabled = true,
            appScope = AppScope(mode = "includeListed", included = listOf(packageName)),
            window = WindowCondition(launchAgeMsMax = 8_000),
            match = MatchNode(any = listOf(withRegion)),
            guard = Guard(forbidTextRegex = RuleDefaults.FORBID_TEXT_REGEX),
            action = RuleAction(
                type = actionType,
                fallback = "none",
                tapXRatio = if (actionType == "tap") tapXRatio else null,
                tapYRatio = if (actionType == "tap") tapYRatio else null,
                anchor = anchor,
            ),
            evidence = "用户在规则工坊创建",
        )
        val parsed = RuleParser.parse(
            Json.encodeToString(RuleWrapper.serializer(), RuleWrapper(listOf(rule))),
        )
        require(parsed.errors.isEmpty()) { parsed.errors.joinToString("；") }
        return parsed.ruleSet.rules.firstOrNull() ?: error("规则未通过校验")
    }
}
