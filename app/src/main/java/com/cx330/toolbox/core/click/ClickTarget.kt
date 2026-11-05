package com.cx330.toolbox.core.click

import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.snapshot.Bounds

/**
 * 把规则里的相对点击坐标解析成屏幕像素点（纯函数，便于单测）。
 *
 * - anchor=screen：相对整块屏幕（旧行为，兼容已有规则）。
 * - anchor=node：相对规则命中的广告节点（推荐用于整屏图片广告，换机型/换分辨率也不跑偏）。
 */
object ClickTarget {

    /** anchor=node 时，节点至少要覆盖屏幕的 20%，避免把点击挂到小按钮上导致点位跑偏。 */
    const val MIN_NODE_AREA_RATIO = 0.2f

    fun resolve(
        action: RuleAction,
        nodeBounds: Bounds,
        screenBounds: Bounds,
    ): Pair<Int, Int>? {
        val xRatio = action.tapXRatio ?: return null
        val yRatio = action.tapYRatio ?: return null
        if (xRatio !in 0f..1f || yRatio !in 0f..1f) return null

        val (left, top, width, height) = when (action.anchor) {
            "node" -> {
                if (nodeBounds.width <= 0 || nodeBounds.height <= 0) return null
                // 节点必须是“整块广告”级别，避免用户把一条 tap 规则挂在小按钮上导致点击位置跑偏。
                val screenArea = screenBounds.width.toLong() * screenBounds.height
                val nodeArea = nodeBounds.width.toLong() * nodeBounds.height
                if (screenArea <= 0) return null
                // 与 CapturePointRule 生成端共用 20% 门槛：弹窗/半屏广告也能正常执行。
                if (nodeArea < (screenArea * MIN_NODE_AREA_RATIO).toLong()) return null
                listOf(nodeBounds.left, nodeBounds.top, nodeBounds.width, nodeBounds.height)
            }
            else -> {
                val w = screenBounds.width
                val h = screenBounds.height
                if (w <= 0 || h <= 0) return null
                listOf(screenBounds.left, screenBounds.top, w, h)
            }
        }

        val x = (left + width * xRatio).toInt().coerceIn(
            screenBounds.left,
            (screenBounds.right - 1).coerceAtLeast(screenBounds.left),
        )
        val y = (top + height * yRatio).toInt().coerceIn(
            screenBounds.top,
            (screenBounds.bottom - 1).coerceAtLeast(screenBounds.top),
        )
        if (action.anchor == "node" &&
            (x < nodeBounds.left || x > nodeBounds.right || y < nodeBounds.top || y > nodeBounds.bottom)
        ) {
            return null
        }
        return x to y
    }
}
