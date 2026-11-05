package com.cx330.toolbox.core.inspect

import java.io.File

/**
 * 抓取记录的纯文件操作，放在这里便于 JVM 单测覆盖。
 *
 * 一条记录 = 一个 JSON 节点树 + 同名 PNG 截图；删除记录只删这两个文件，
 * 已经用该记录生成的规则不受影响。
 */
object CaptureFiles {

    fun delete(jsonFile: File) {
        val png = File(jsonFile.parentFile, jsonFile.nameWithoutExtension + ".png")
        jsonFile.delete()
        png.delete()
    }
}
