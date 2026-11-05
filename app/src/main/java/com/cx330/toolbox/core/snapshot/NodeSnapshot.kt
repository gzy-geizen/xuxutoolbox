package com.cx330.toolbox.core.snapshot

import kotlinx.serialization.Serializable

/** 屏幕上的一个矩形区域（单位：像素）。 */
@Serializable
data class Bounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Int get() = width * height
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

/**
 * 节点快照：把 AccessibilityNodeInfo 的关键信息复制成不可变数据。
 *
 * 为什么需要它：
 * - AccessibilityNodeInfo 的方法都是跨进程调用，节点随时失效，不能跨线程乱传；
 * - 复制成纯数据后，规则匹配就是纯函数，可以脱离 Android 在 JVM 单测里跑；
 * - 快照还能序列化成 JSON 当测试 fixture。
 */
@Serializable
data class NodeSnapshot(
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val visibleToUser: Boolean = true,
    val bounds: Bounds = Bounds(),
    val depth: Int = 0,
    val indexInParent: Int = 0,
    val packageName: String = "",
    val children: List<NodeSnapshot> = emptyList(),
) {
    /** 深度优先展开成节点序列，便于规则引擎统一遍历。 */
    fun flatten(): Sequence<NodeSnapshot> = sequence {
        yield(this@NodeSnapshot)
        children.forEach { child -> yieldAll(child.flatten()) }
    }
}
