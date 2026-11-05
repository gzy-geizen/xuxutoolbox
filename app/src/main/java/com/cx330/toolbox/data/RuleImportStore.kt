package com.cx330.toolbox.data

import android.content.Context
import java.io.File

/**
 * 用户导入的规则文件（保存在应用私有目录，不联网）。
 *
 * 服务启动时会把它和 assets 内置规则一起合并，因此导入的规则不需要重新发版。
 */
object RuleImportStore {

    private const val DIR = "rules"
    private const val FILE_NAME = "imported.json"

    private fun file(context: Context): File = File(File(context.filesDir, DIR), FILE_NAME)

    fun save(context: Context, raw: String) {
        val target = file(context)
        target.parentFile?.mkdirs()
        target.writeText(raw)
    }

    fun load(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    fun hasImported(context: Context): Boolean = file(context).exists()
}
