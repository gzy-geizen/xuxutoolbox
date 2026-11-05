package com.cx330.toolbox.core.module

import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot

/**
 * 摇一摇兜底（S10，实验性、默认关闭）。
 *
 * 有些开屏广告没有「跳过/关闭」文字或控件，只能摇一摇，且右上角可能有一个不明显的关闭热区。
 * 这个模块在「全屏 + 冷启动 2.5 秒内 + 规则全部没命中」时，返回一个右上角坐标点击决策；
 * 它优先级最低（1），只有在正式规则都失败时才会被选中，且默认关闭，避免误点。
 */
class ShakeFallbackModule : Module {

    override val id: String = ID
    override val name: String = "摇一摇兜底（实验）"
    override val priority: Int = 1

    override fun evaluate(
        event: CxEvent,
        context: WindowContext,
        tree: NodeSnapshot?,
    ): List<ModuleDecision> {
        if (tree == null) return emptyList()
        // 严格模式的语义是“只用 App 专属规则”，兜底不能再绕过它。
        if (context.strictMode) return emptyList()
        if (!context.isFullscreen) return emptyList()
        if (context.launchAgeMs !in 0..TRIGGER_AGE_MS) return emptyList()

        val root = tree.bounds
        val width = root.right - root.left
        val height = root.bottom - root.top
        if (width <= 0 || height <= 0) return emptyList()

        // 右上角热区：右边 8%、顶部 2%~10%
        val bounds = Bounds(
            left = root.right - (width * RIGHT_INSET_RATIO).toInt(),
            top = root.top + (height * TOP_INSET_RATIO).toInt(),
            right = root.right,
            bottom = root.top + (height * TOP_HEIGHT_RATIO).toInt(),
        )
        return listOf(
            ModuleDecision(
                moduleId = id,
                ruleId = "shake.fallback.top_right",
                priority = priority,
                node = NodeSnapshot(
                    packageName = context.packageName,
                    bounds = bounds,
                    text = null,
                ),
                action = RuleAction(type = "gesture-center", fallback = "none"),
            ),
        )
    }

    companion object {
        const val ID = "shake_fallback"
        const val TRIGGER_AGE_MS = 2_500L
        private const val RIGHT_INSET_RATIO = 0.08
        private const val TOP_INSET_RATIO = 0.02
        private const val TOP_HEIGHT_RATIO = 0.10
    }
}
