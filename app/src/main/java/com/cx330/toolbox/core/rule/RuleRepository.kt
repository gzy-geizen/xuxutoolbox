package com.cx330.toolbox.core.rule

/**
 * 规则来源汇总：内置 assets + 用户导入（必须签名）+ 用户本地规则（可编辑，同 id 覆盖）。
 *
 * 合并顺序体现优先级：内置 < 签名导入 < 用户本地规则。
 * 用户本地规则来自“规则工坊”，用于适配自己的机型/App 版本，不需要重新发版。
 */
object RuleRepository {

    data class LoadedRules(
        val ruleSet: RuleSet,
        val errors: List<String>,
        val importedSigned: Boolean,
        val userRuleCount: Int = 0,
    )

    fun load(
        assetContents: List<String>,
        importedRaw: String?,
        publicKeyBase64: String = BuiltinKeys.PUBLIC_KEY_B64,
        userRaw: String? = null,
    ): LoadedRules {
        val errors = mutableListOf<String>()
        val contents = assetContents.toMutableList()
        var importedSigned = false

        if (importedRaw != null) {
            val verified = RuleSignatureVerifier.verify(importedRaw, publicKeyBase64)
            if (verified.isSuccess) {
                contents += verified.getOrThrow().payloadJson
                importedSigned = true
            } else {
                errors += "导入规则未通过签名校验，已忽略：${verified.exceptionOrNull()?.message}"
            }
        }

        val merged = RuleLoader.merge(contents)
        errors += merged.errors

        // 本地用户规则最后合并：同 id 覆盖内置/导入规则；非 App 层规则仍参与严格模式判断。
        val byId = LinkedHashMap<String, Rule>()
        merged.ruleSet.rules.forEach { byId[it.id] = it }
        var userRuleCount = 0
        if (userRaw != null) {
            val parsed = RuleParser.parse(userRaw)
            errors += parsed.errors
            parsed.ruleSet.rules.forEach { rule ->
                val problem = when {
                    !rule.id.startsWith("user.") -> "用户规则 id 必须以 user. 开头"
                    rule.appScope.mode != "includeListed" || rule.appScope.included.isEmpty() ->
                        "用户规则必须限定一个 App（includeListed）"
                    rule.window.launchAgeMsMax > 15_000L -> "用户规则启动时间窗不能超过 15 秒"
                    else -> null
                }
                if (problem != null) {
                    errors += "用户规则 ${rule.id} 已忽略：$problem"
                    return@forEach
                }
                val guarded = if (rule.guard.forbidTextRegex.isNullOrBlank()) {
                    rule.copy(guard = rule.guard.copy(forbidTextRegex = RuleDefaults.FORBID_TEXT_REGEX))
                } else {
                    rule
                }
                byId[guarded.id] = guarded
                userRuleCount++
            }
        }

        return LoadedRules(
            ruleSet = RuleSet(version = 2, rules = byId.values.toList()),
            errors = errors,
            importedSigned = importedSigned,
            userRuleCount = userRuleCount,
        )
    }
}
