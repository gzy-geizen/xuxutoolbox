package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot

/**
 * 把一条规则编译成可执行的匹配器。
 * 编译期会把正则预编译；非法正则会让编译失败，由引擎丢弃该规则（不拖垮整个引擎）。
 */
class CompiledRule(val rule: Rule) {

    private val anySelectors: List<CompiledSelector> = rule.match.any.map(::CompiledSelector)
    private val allSelectors: List<CompiledSelector> = rule.match.all.map(::CompiledSelector)
    private val forbidRegex: Regex? = rule.guard.forbidTextRegex?.let { Regex(it) }

    init {
        // 防御：空选择器会匹配任意节点，直接拒绝整条规则（引擎会丢弃并记录）。
        require(anySelectors.all { it.hasCondition } && allSelectors.all { it.hasCondition }) {
            "规则 ${rule.id} 存在空选择器"
        }
    }

    fun matchesWindow(context: WindowContext): Boolean {
        if (!rule.appScope.allows(context.packageName)) return false
        if (context.strictMode && rule.layer == "generic") return false
        val window = rule.window
        if (window.fullscreen && !context.isFullscreen) return false
        if (context.launchAgeMs > window.launchAgeMsMax) return false
        if (window.excludeIfImeVisible && context.imeVisible) return false
        val activity = context.activityName
        if (window.activityName != null && activity != window.activityName) return false
        if (window.activityNameContains != null &&
            (activity == null || !activity.contains(window.activityNameContains))
        ) {
            return false
        }
        return true
    }

    fun matches(node: NodeSnapshot, context: WindowContext, screenBounds: Bounds): Boolean {
        val hasAny = anySelectors.isNotEmpty()
        val hasAll = allSelectors.isNotEmpty()
        if (!hasAny && !hasAll) return false

        if (hasAny && anySelectors.none { it.matches(node, context, screenBounds) }) return false
        if (hasAll && allSelectors.any { !it.matches(node, context, screenBounds) }) return false

        val forbidden = forbidRegex?.let { regex ->
            node.text?.let { regex.containsMatchIn(it) } ?: false
        } ?: false
        return !forbidden
    }
}

/** 单个选择器的匹配逻辑。 */
class CompiledSelector(private val selector: Selector) {

    val hasCondition: Boolean = selector.hasCondition()

    private val viewIdRegex: Regex? = selector.viewIdRegex?.let { Regex(it) }
    private val textRegex: Regex? = selector.textRegex?.let { Regex(it) }
    private val descRegex: Regex? = selector.descRegex?.let { Regex(it) }
    private val depthRange: IntRange? = selector.depthRange
        ?.takeIf { it.size == 2 }
        ?.let { it[0]..it[1] }

    fun matches(node: NodeSnapshot, context: WindowContext, screenBounds: Bounds): Boolean {
        if (!hasCondition) return false

        if (selector.viewId != null && node.viewId != selector.viewId) return false

        if (viewIdRegex != null) {
            val viewId = node.viewId ?: return false
            if (!viewIdRegex.containsMatchIn(viewId)) return false
        }

        if (selector.text != null && node.text?.trim() != selector.text) return false

        if (selector.textContains != null) {
            val text = node.text ?: return false
            if (!text.contains(selector.textContains)) return false
        }

        if (textRegex != null) {
            val text = node.text ?: return false
            if (!textRegex.containsMatchIn(text)) return false
        }

        if (selector.descContains != null &&
            node.contentDescription?.contains(selector.descContains) != true
        ) {
            return false
        }

        if (descRegex != null) {
            val desc = node.contentDescription ?: return false
            if (!descRegex.containsMatchIn(desc)) return false
        }

        if (selector.className != null && node.className != selector.className) return false
        if (selector.clickable != null && node.clickable != selector.clickable) return false

        if (selector.minAreaDp2 != null) {
            val density = context.density
            val dp2 = node.bounds.area / (density * density)
            if (dp2 < selector.minAreaDp2) return false
        }

        if (depthRange != null && node.depth !in depthRange) return false

        if (selector.region != null && !matchesRegion(node, screenBounds)) return false
        return true
    }

    /** 按「节点中心相对屏幕的位置」判断区域（用于排除非广告的关闭按钮）。 */
    private fun matchesRegion(node: NodeSnapshot, screen: Bounds): Boolean {
        val width = screen.right - screen.left
        val height = screen.bottom - screen.top
        if (width <= 0 || height <= 0) return false
        val centerX = (node.bounds.left + node.bounds.right) / 2f - screen.left
        val centerY = (node.bounds.top + node.bounds.bottom) / 2f - screen.top
        val nx = centerX / width
        val ny = centerY / height
        return when (selector.region) {
            "top_right" -> nx > 0.65f && ny < 0.25f
            "top_center" -> nx in 0.25f..0.75f && ny < 0.25f
            "top_left" -> nx < 0.35f && ny < 0.25f
            "bottom_right" -> nx > 0.65f && ny > 0.75f
            "bottom_center" -> nx in 0.25f..0.75f && ny > 0.75f
            else -> true
        }
    }
}
