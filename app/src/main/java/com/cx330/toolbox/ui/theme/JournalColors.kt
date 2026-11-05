package com.cx330.toolbox.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 奶油手账全局调色板（浅色 / 深色两套）。
 *
 * 说明：页面里直接引用这里，Theme 在组合时根据系统深浅色切换。
 * 用对象而不是 CompositionLocal，是为了让几十处硬编码颜色一次替换完成，后续可再重构。
 */
object JournalColors {
    var cream: Color = Color(0xFFFBF6EC); private set
    var paper: Color = Color(0xFFF3E8D6); private set
    var card: Color = Color(0xFFFFFDF8); private set
    var ink: Color = Color(0xFF2F2A26); private set
    var inkSoft: Color = Color(0xFF6B5D4F); private set
    var caramel: Color = Color(0xFF8A5A3B); private set
    var border: Color = Color(0xFFE4D5BE); private set
    var matcha: Color = Color(0xFF8FA97E); private set
    var yuzu: Color = Color(0xFFE9A25B); private set
    var danger: Color = Color(0xFFC96A5B); private set
    var tape: Color = Color(0xFFE8D9BF); private set
    var onCaramel: Color = Color(0xFFFFFDF8); private set

    fun apply(dark: Boolean) {
        if (dark) {
            cream = Color(0xFF211C18)
            paper = Color(0xFF2B2521)
            card = Color(0xFF2E2823)
            ink = Color(0xFFF5EAD9)
            inkSoft = Color(0xFFBFB2A2)
            caramel = Color(0xFFB57A52)
            border = Color(0xFF3E352D)
            matcha = Color(0xFF9DBE8B)
            yuzu = Color(0xFFD99A5C)
            danger = Color(0xFFD98A7A)
            tape = Color(0xFF4A3B2E)
            onCaramel = Color(0xFF241A12)
        } else {
            cream = Color(0xFFFBF6EC)
            paper = Color(0xFFF3E8D6)
            card = Color(0xFFFFFDF8)
            ink = Color(0xFF2F2A26)
            inkSoft = Color(0xFF6B5D4F)
            caramel = Color(0xFF8A5A3B)
            border = Color(0xFFE4D5BE)
            matcha = Color(0xFF8FA97E)
            yuzu = Color(0xFFE9A25B)
            danger = Color(0xFFC96A5B)
            tape = Color(0xFFE8D9BF)
            onCaramel = Color(0xFFFFFDF8)
        }
    }
}
