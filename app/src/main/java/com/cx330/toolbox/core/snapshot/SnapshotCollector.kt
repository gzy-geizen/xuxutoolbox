package com.cx330.toolbox.core.snapshot

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 把 Android 的节点树复制成 NodeSnapshot 树。
 *
 * 三条预算（防止在主线程卡住事件流）：
 * - 最多 [maxNodes] 个节点；
 * - 最大深度 [maxDepth]；
 * - 最多 [budgetMs] 毫秒。
 * 任一超限即截断，并通过 [Result.truncated] 告知调用方。
 */
class SnapshotCollector(
    private val maxNodes: Int = 800,
    private val maxDepth: Int = 25,
    private val budgetMs: Long = 120L,
    private val clock: () -> Long = { SystemClock.uptimeMillis() },
) {

    data class Result(
        val root: NodeSnapshot?,
        val nodeCount: Int,
        val elapsedMs: Long,
        val truncated: Boolean,
    )

    fun collect(rootInfo: AccessibilityNodeInfo?): Result {
        if (rootInfo == null) {
            return Result(null, 0, 0L, false)
        }
        val start = clock()
        var count = 0
        var truncated = false

        fun snapshot(node: AccessibilityNodeInfo, depth: Int, index: Int): NodeSnapshot? {
            if (count >= maxNodes || depth > maxDepth) {
                truncated = true
                return null
            }
            if (clock() - start > budgetMs) {
                truncated = true
                return null
            }
            count++
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val children = ArrayList<NodeSnapshot>()
            for (i in 0 until node.childCount) {
                if (count >= maxNodes || clock() - start > budgetMs) {
                    truncated = true
                    break
                }
                val child = node.getChild(i) ?: continue
                snapshot(child, depth + 1, i)?.let { children += it }
            }

            return NodeSnapshot(
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                visibleToUser = node.isVisibleToUser,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                depth = depth,
                indexInParent = index,
                packageName = node.packageName?.toString() ?: "",
                children = children,
            )
        }

        val root = snapshot(rootInfo, 0, 0)
        return Result(
            root = root,
            nodeCount = count,
            elapsedMs = clock() - start,
            truncated = truncated,
        )
    }

    companion object {
        /** 只复制单个节点（不遍历子节点），用于「按关键字定向搜索」的快路径。 */
        fun snapshotOf(node: AccessibilityNodeInfo, depth: Int = 1, index: Int = 0): NodeSnapshot {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            return NodeSnapshot(
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                clickable = node.isClickable,
                enabled = node.isEnabled,
                visibleToUser = node.isVisibleToUser,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                depth = depth,
                indexInParent = index,
                packageName = node.packageName?.toString() ?: "",
            )
        }
    }
}
