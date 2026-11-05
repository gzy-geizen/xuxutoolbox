package com.cx330.toolbox.data

import android.content.Context
import com.cx330.toolbox.core.rule.Rule
import com.cx330.toolbox.core.rule.RuleParser
import com.cx330.toolbox.core.rule.RuleSet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * 用户自己在“规则工坊”里创建的规则（本地、可编辑、不要求签名）。
 *
 * 与 [RuleImportStore] 的区别：
 * - imported.json 只能整体导入签名包，用户不能改；
 * - user.json 是用户逐条增删改的本地规则，优先级高于内置/导入规则（同 id 覆盖）。
 *
 * 安全性：本文件只在应用私有目录，导出/导入都要用户手动确认；服务加载时会做完整校验。
 *
 * 写入策略：user.json 可能被用户手工编辑，也可能出现暂时解析不了的规则。
 * 因此增删改都直接在原始 JSON 上按 id 操作，**不**用“校验通过的子集”整体回写，
 * 避免一条坏规则在下一次切开关时被静默删除。
 */
object UserRuleStore {

    private const val DIR = "rules"
    private const val FILE_NAME = "user.json"

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    private fun file(context: Context): File = File(File(context.filesDir, DIR), FILE_NAME)

    fun loadRaw(context: Context): String? = file(context).takeIf { it.exists() }?.readText(Charsets.UTF_8)

    fun saveRaw(context: Context, raw: String) {
        val target = file(context)
        target.parentFile?.mkdirs()
        // 先备份，再写临时文件后原子替换：进程被杀时不会留下半截 JSON。
        if (target.exists()) {
            runCatching { target.copyTo(File(target.parentFile, "${target.name}.bak"), overwrite = true) }
        }
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(raw, Charsets.UTF_8)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun clear(context: Context) {
        file(context).delete()
    }

    fun hasRules(context: Context): Boolean = (loadRaw(context)?.isNotBlank() == true)

    /** 解析当前用户规则；坏 JSON 返回空列表，不抛给 UI。 */
    fun rules(context: Context): List<Rule> {
        val raw = loadRaw(context) ?: return emptyList()
        return RuleParser.parse(raw).ruleSet.rules
    }

    fun parseErrors(context: Context): List<String> {
        val raw = loadRaw(context) ?: return emptyList()
        return RuleParser.parse(raw).errors
    }

    /** 新增或按 id 覆盖一条规则，返回保存后的完整规则列表。 */
    fun upsert(context: Context, rule: Rule): List<Rule> {
        val root = editableRoot(context)
        if (root == null) return rules(context)
        val existing = rulesArray(root).filterNot { elementId(it) == rule.id }
        val newArray = JsonArray(existing + prettyJson.encodeToJsonElement(Rule.serializer(), rule))
        persistRoot(context, root.replacing("rules", newArray))
        return rules(context)
    }

    fun delete(context: Context, id: String): List<Rule> {
        val root = editableRoot(context)
        if (root == null) return rules(context)
        val newArray = JsonArray(rulesArray(root).filterNot { elementId(it) == id })
        persistRoot(context, root.replacing("rules", newArray))
        return rules(context)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean): List<Rule> {
        val root = editableRoot(context)
        if (root == null) return rules(context)
        val newArray = JsonArray(
            rulesArray(root).map { element ->
                if (elementId(element) != id) return@map element
                val parsed = runCatching {
                    prettyJson.decodeFromJsonElement(Rule.serializer(), element)
                }.getOrNull() ?: return@map element
                prettyJson.encodeToJsonElement(Rule.serializer(), parsed.copy(enabled = enabled))
            },
        )
        persistRoot(context, root.replacing("rules", newArray))
        return rules(context)
    }

    fun exportJson(context: Context): String = prettyJson.encodeToString(RuleSet(rules = rules(context)))

    /**
     * 导入用户规则（未签名）。重复 id 会被文件里的版本覆盖。
     * 返回解析错误，便于 UI 提示。
     */
    data class ImportOutcome(val imported: Int, val errors: List<String>)

    fun importRaw(context: Context, raw: String): ImportOutcome {
        val parsed = RuleParser.parse(raw)
        if (parsed.ruleSet.rules.isNotEmpty()) {
            val root = editableRoot(context)
            if (root != null) {
                val importedIds = parsed.ruleSet.rules.map { it.id }.toSet()
                val remaining = rulesArray(root).filterNot { elementId(it) in importedIds }
                val merged = JsonArray(
                    remaining + parsed.ruleSet.rules.map {
                        prettyJson.encodeToJsonElement(Rule.serializer(), it)
                    },
                )
                persistRoot(context, root.replacing("rules", merged))
            }
        }
        return ImportOutcome(parsed.ruleSet.rules.size, parsed.errors)
    }

    /** 读取原始根对象；文件不存在返回空结构；整份 JSON 坏掉时先备份再新建，绝不静默覆盖。 */
    private fun editableRoot(context: Context): JsonObject? {
        val raw = loadRaw(context)
        if (raw.isNullOrBlank()) return emptyRoot()
        val parsed = runCatching { prettyJson.parseToJsonElement(raw).jsonObject }.getOrNull()
        if (parsed != null) return parsed
        // 整份文件解析失败：保存一份带时间戳的副本，避免用户手工内容被直接清空。
        runCatching {
            val backup = File(file(context).parentFile, "$FILE_NAME.corrupt-${System.currentTimeMillis()}")
            backup.writeText(raw, Charsets.UTF_8)
        }
        return emptyRoot()
    }

    private fun emptyRoot(): JsonObject = JsonObject(
        mapOf(
            "version" to JsonPrimitive(1),
            "rules" to JsonArray(emptyList()),
        ),
    )

    private fun rulesArray(root: JsonObject): JsonArray =
        (root["rules"] as? JsonArray) ?: JsonArray(emptyList())

    private fun elementId(element: kotlinx.serialization.json.JsonElement): String? = runCatching {
        (element as JsonObject)["id"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun JsonObject.replacing(key: String, value: kotlinx.serialization.json.JsonElement): JsonObject =
        JsonObject(toMutableMap().apply { put(key, value) })

    private fun persistRoot(context: Context, root: JsonObject) {
        saveRaw(context, prettyJson.encodeToString(JsonObject.serializer(), root))
    }
}
