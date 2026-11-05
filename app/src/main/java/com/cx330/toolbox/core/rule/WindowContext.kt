package com.cx330.toolbox.core.rule

/**
 * 匹配时的窗口上下文：规则里的 window 条件靠它判断。
 *
 * @param packageName 当前前台应用包名
 * @param isFullscreen 窗口是否全屏（开屏广告通常全屏）
 * @param imeVisible 输入法是否可见（避免在输入场景误点）
 * @param launchAgeMs 距离该 App 进入前台的毫秒数（开屏广告通常在 8 秒内）
 * @param densityDpi 屏幕密度，用于 px² → dp² 换算
 */
data class WindowContext(
    val packageName: String,
    /** 当前 Activity 类名（来自事件 className），用于把规则限定在开屏页。 */
    val activityName: String? = null,
    val isFullscreen: Boolean = true,
    val imeVisible: Boolean = false,
    val launchAgeMs: Long = 0L,
    val densityDpi: Int = 480,
    /** 严格模式：只用显式规则，不做通用文字兜底。 */
    val strictMode: Boolean = false,
) {
    val density: Float get() = densityDpi / 160f
}
