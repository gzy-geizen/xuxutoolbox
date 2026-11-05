package com.cx330.toolbox.core.rule

/**
 * 多份规则文件的合并加载。
 *
 * 目的：把「通用语义规则」和「SDK 特征规则」分文件维护，互不干扰；
 * 某一份写坏/重复 id 只影响该条，不会拖垮整体。
 */
object RuleLoader {

    data class MergedRules(
        val ruleSet: RuleSet,
        val errors: List<String>,
    )

    fun merge(jsonContents: List<String>): MergedRules {
        val errors = mutableListOf<String>()
        val rules = mutableListOf<Rule>()
        val seenIds = mutableSetOf<String>()

        jsonContents.forEach { raw ->
            val parsed = RuleParser.parse(raw)
            errors += parsed.errors
            parsed.ruleSet.rules.forEach { rule ->
                if (seenIds.add(rule.id)) {
                    rules += rule
                } else {
                    errors += "重复规则 id 已丢弃: ${rule.id}"
                }
            }
        }

        return MergedRules(
            ruleSet = RuleSet(version = 2, rules = rules),
            errors = errors,
        )
    }
}
