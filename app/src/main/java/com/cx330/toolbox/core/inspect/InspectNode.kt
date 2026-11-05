package com.cx330.toolbox.core.inspect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.Serializable

/**
 * 布局检查用的节点树（只读快照）。
 *
 * 与运行时 [com.cx330.toolbox.core.snapshot.NodeSnapshot] 的区别：
 * 这里为了「造规则/复核」保留更完整的信息（viewId、完整 bounds、clickable/enabled/visible），
 * 不需要参与匹配，所以字段更「诊断向」。
 */
@Serializable
data class InspectNode(
    val depth: Int,
    val index: Int,
    val packageName: String? = null,
    val className: String? = null,
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val visibleToUser: Boolean = true,
    val bounds: String = "",
    val childCount: Int = 0,
    val children: List<InspectNode> = emptyList(),
)

@Serializable
data class CaptureDocument(
    val capturedAt: Long,
    val packageName: String? = null,
    val activityName: String? = null,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val densityDpi: Int = 0,
    val nodeCount: Int = 0,
    val root: InspectNode,
)

object InspectTreeBuilder {

    /** 节点树可能很大（Compose 页面动辄上千节点），限制深度与节点数，避免抓取时卡住。 */
    fun build(
        root: AccessibilityNodeInfo,
        maxDepth: Int = 20,
        maxNodes: Int = 800,
    ): InspectNode? {
        var remaining = maxNodes

        fun walk(node: AccessibilityNodeInfo, depth: Int, index: Int): InspectNode? {
            if (depth > maxDepth || remaining <= 0) return null
            remaining--

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val children = ArrayList<InspectNode>()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1, i)?.let(children::add)
            }
            return InspectNode(
                depth = depth,
                index = index,
                packageName = node.packageName?.toString(),
                className = node.className?.toString(),
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                visibleToUser = node.isVisibleToUser,
                bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                childCount = node.childCount,
                children = children,
            )
        }

        return walk(root, 0, 0)
    }

    fun count(node: InspectNode): Int =
        1 + node.children.sumOf { count(it) }
}
