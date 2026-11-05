package com.cx330.toolbox.core.rule

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

data class ParseResult(
    val ruleSet: RuleSet,
    val errors: List<String>,
)

/**
 * 规则 JSON 的解析与校验。
 *
 * 原则：
 * - 坏规则「逐条丢弃 + 记录错误」，绝不让一条写错的规则拖垮整个引擎；
 * - 每条规则用严格模式解析：字段拼错（未知字段）会让该条规则被拒绝，而不是被静默忽略
 *   （静默忽略可能让规则“变宽”，属于安全隐患）。
 */
object RuleParser {

    private val allowedActions = setOf("click", "gesture-center", "back", "tap")
    private val allowedFallbacks = setOf("gesture-center", "none")
    private val allowedScopeModes = setOf("all", "excludeListed", "includeListed")
    private val allowedLayers = setOf("app", "generic")
    private val allowedRegions = setOf(
        "top_right", "top_center", "top_left", "bottom_right", "bottom_center",
    )
    private val allowedTapAnchors = setOf("screen", "node")

    private val strictJson = Json {
        ignoreUnknownKeys = false
        isLenient = false
        encodeDefaults = false
    }

    fun parse(raw: String): ParseResult {
        val root = try {
            Json.parseToJsonElement(raw).jsonObject
        } catch (e: Exception) {
            return ParseResult(RuleSet(), listOf("JSON 解析失败: ${e.message}"))
        }
        val rulesElement = root["rules"]
            ?: return ParseResult(RuleSet(), listOf("JSON 缺少 rules 数组"))
        val rulesArray = rulesElement as? JsonArray
            ?: return ParseResult(RuleSet(), listOf("rules 必须是数组"))

        val errors = mutableListOf<String>()
        val validRules = ArrayList<Rule>()

        rulesArray.forEachIndexed { index, element ->
            val rule = runCatching {
                strictJson.decodeFromJsonElement(Rule.serializer(), element)
            }.getOrElse { e ->
                errors += "rules[$index] 解析失败（未知字段或类型错误）: ${e.message}"
                null
            } ?: return@forEachIndexed

            val problems = validate(rule)
            if (problems.isEmpty()) {
                validRules += rule
            } else {
                val id = rule.id.ifBlank { "<no-id>" }
                errors += problems.map { "$id: $it" }
            }
        }
        return ParseResult(RuleSet(rules = validRules), errors)
    }

    private fun validate(rule: Rule): List<String> {
        val problems = mutableListOf<String>()
        if (rule.id.isBlank()) problems += "id 不能为空"
        if (rule.match.any.isEmpty() && rule.match.all.isEmpty()) {
            problems += "match.any 与 match.all 不能同时为空"
        }
        if (rule.action.type !in allowedActions) {
            problems += "action.type 不支持: ${rule.action.type}"
        }
        if (rule.action.fallback !in allowedFallbacks) {
            problems += "action.fallback 不支持: ${rule.action.fallback}"
        }
        if (rule.action.type == "tap") {
            val x = rule.action.tapXRatio
            val y = rule.action.tapYRatio
            if (x == null || y == null) {
                problems += "action.tap 需要 tapXRatio 与 tapYRatio"
            } else if (x !in 0f..1f || y !in 0f..1f) {
                problems += "action.tap 的坐标比例需在 0..1"
            }
            if (rule.action.anchor !in allowedTapAnchors) {
                problems += "action.anchor 不支持: ${rule.action.anchor}（可选 screen/node）"
            }
        }
        if (rule.appScope.mode !in allowedScopeModes) {
            problems += "appScope.mode 不支持: ${rule.appScope.mode}"
        }
        if (rule.window.launchAgeMsMax < 0) {
            problems += "window.launchAgeMsMax 不能为负"
        }
        if (rule.window.activityName != null && rule.window.activityName.isBlank()) {
            problems += "window.activityName 不能为空"
        }
        if (rule.window.activityNameContains != null && rule.window.activityNameContains.isBlank()) {
            problems += "window.activityNameContains 不能为空"
        }
        if (rule.layer !in allowedLayers) {
            problems += "layer 不支持: ${rule.layer}"
        }
        if (rule.guard.maxActionsPerWindow < 1) {
            problems += "guard.maxActionsPerWindow 至少为 1"
        }

        (rule.match.any + rule.match.all).forEachIndexed { index, selector ->
            if (!selector.hasCondition()) {
                problems += "selector[$index] 不能为空（至少需要一个条件，如 text/viewIdRegex/clickable/region）"
            }
            listOf(
                "text" to selector.text,
                "textContains" to selector.textContains,
                "descContains" to selector.descContains,
                "viewId" to selector.viewId,
                "viewIdRegex" to selector.viewIdRegex,
                "textRegex" to selector.textRegex,
                "descRegex" to selector.descRegex,
            ).forEach { (name, value) ->
                if (value != null && value.isBlank()) {
                    problems += "selector[$index].$name 不能为空字符串"
                }
            }
            selector.viewIdRegex?.let { regex ->
                runCatching { Regex(regex) }.onFailure { e ->
                    problems += "selector[$index].viewIdRegex 非法: ${e.message}"
                }
            }
            selector.textRegex?.let { regex ->
                runCatching { Regex(regex) }.onFailure { e ->
                    problems += "selector[$index].textRegex 非法: ${e.message}"
                }
            }
            selector.descRegex?.let { regex ->
                runCatching { Regex(regex) }.onFailure { e ->
                    problems += "selector[$index].descRegex 非法: ${e.message}"
                }
            }
            selector.depthRange?.let { range ->
                if (range.size != 2 || range[0] > range[1]) {
                    problems += "selector[$index].depthRange 需为 [min,max]"
                }
            }
            selector.minAreaDp2?.let { area ->
                if (area < 0) problems += "selector[$index].minAreaDp2 不能为负"
            }
            selector.region?.let { region ->
                if (region !in allowedRegions) {
                    problems += "selector[$index].region 不支持: $region"
                }
            }
        }

        rule.guard.forbidTextRegex?.let { regex ->
            runCatching { Regex(regex) }.onFailure { e ->
                problems += "guard.forbidTextRegex 非法: ${e.message}"
            }
        }
        return problems
    }
}
