package com.cx330.toolbox.core.click

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.cx330.toolbox.core.rule.Candidate
import com.cx330.toolbox.core.rule.RuleAction

/**
 * 点击执行器。
 *
 * 顺序与计划书一致：
 * 1) 用快照里的 viewId/text 在当前窗口重新定位节点，并校验文字仍一致；
 * 2) `performAction(ACTION_CLICK)`；
 * 3) 失败时按规则声明的 `action.fallback` 决定要不要用 `dispatchGesture` 点节点中心：
 *    - `gesture-center`（默认）：允许坐标兜底；
 *    - `none`：不兜底，直接返回 `click_failed`（盲点中心可能点到广告本身，反而触发了下载）。
 * 4) 都失败返回失败原因，由调用方记录。
 *
 * 安全约束（P0 修复）：执行前必须确认「当前活动窗口」仍属于决策时的包名，
 * 否则在转场/输入法弹出时可能点到别的窗口。
 */
class ClickExecutor {

    fun execute(
        service: AccessibilityService,
        candidate: Candidate,
        expectedPackage: String,
    ): String {
        // 决策时的节点如果已经被别的 App 覆盖，宁可放弃也不盲点。
        if (candidate.node.packageName.isNotBlank() &&
            candidate.node.packageName != expectedPackage
        ) {
            return "package_changed"
        }

        val root = service.rootInActiveWindow ?: return "root_unavailable"
        val rootPackage = root.packageName?.toString()
        if (rootPackage != null && rootPackage != expectedPackage) {
            return "package_changed"
        }

        // 全局返回动作（部分广告用返回键关闭）
        if (candidate.action.type == "back") {
            return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                "back"
            } else {
                "back_failed"
            }
        }

        // 纯坐标决策（如摇一摇兜底）：不需要节点，直接点中心坐标
        if (candidate.action.type == "gesture-center") {
            return tapByGesture(service, candidate)
        }

        // 广告按钮不在无障碍树里（例如整张广告是图片）：按规则给的比例点击。
        // anchor=screen 用屏幕比例（旧规则）；anchor=node 用命中节点的相对位置（多机型更稳）。
        if (candidate.action.type == "tap") {
            val metrics = service.resources.displayMetrics
            val screen = com.cx330.toolbox.core.snapshot.Bounds(
                left = 0,
                top = 0,
                right = metrics.widthPixels,
                bottom = metrics.heightPixels,
            )
            val point = ClickTarget.resolve(candidate.action, candidate.node.bounds, screen)
                ?: return if (candidate.action.tapXRatio == null || candidate.action.tapYRatio == null) {
                    "tap_point_missing"
                } else {
                    "invalid_bounds"
                }
            return tapPoint(service, point.first, point.second)
        }

        val node = findNode(root, candidate) ?: return "node_not_found"
        val nodePackage = node.packageName?.toString()
        if (nodePackage != null && nodePackage != expectedPackage) {
            return "package_changed"
        }

        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return "clicked"
        }

        // 节点点不动：规则声明 fallback=none 时宁可失败，也不盲目点节点中心。
        return nodeClickFailureResult(candidate.action) { tapByGesture(service, candidate) }
    }

    private fun tapPoint(service: AccessibilityService, x: Int, y: Int): String {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 40L))
            .build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        Log.i(TAG, "tap-point at ($x, $y) dispatched=$dispatched")
        return if (dispatched) "tap_point" else "gesture_unavailable"
    }

    private fun tapByGesture(service: AccessibilityService, candidate: Candidate): String {
        val bounds = candidate.node.bounds
        if (bounds.width <= 0 || bounds.height <= 0) return "invalid_bounds"
        val path = Path().apply {
            moveTo(bounds.centerX.toFloat(), bounds.centerY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 40L))
            .build()
        val dispatched = service.dispatchGesture(gesture, null, null)
        Log.i(TAG, "gesture at (${bounds.centerX}, ${bounds.centerY}) dispatched=$dispatched")
        return if (dispatched) "gesture" else "gesture_unavailable"
    }

    private fun findNode(root: AccessibilityNodeInfo, candidate: Candidate): AccessibilityNodeInfo? {
        val expectedText = candidate.node.text
        candidate.node.viewId?.let { viewId ->
            val byId = root.findAccessibilityNodeInfosByViewId(viewId).orEmpty()
            val match = byId.firstOrNull { node ->
                expectedText == null || node.text?.toString() == expectedText
            }
            if (match != null) return match
        }
        expectedText?.let { text ->
            val byText = root.findAccessibilityNodeInfosByText(text).orEmpty()
            return byText.firstOrNull { it.text?.toString() == text }
        }
        return null
    }

    companion object {
        const val TAG = "CX330_ACTION"
    }
}

/**
 * 节点点击失败后的兜底策略（纯逻辑，不碰 Android，可在 JVM 单测里锁住）。
 *
 * 规则可以声明 `action.fallback`：
 * - `gesture-center`（默认）：节点点不动就按节点中心做坐标手势；
 * - `none`：不兜底，返回 `click_failed`。
 *
 * 这条策略只用于「节点点击失败」这一步。`action.type=gesture-center` 的规则本身就是
 * 按坐标点，不受 fallback 影响。
 */
internal fun nodeClickFailureResult(action: RuleAction, dispatchFallback: () -> String): String =
    if (action.fallback == FALLBACK_NONE) "click_failed" else dispatchFallback()

/** 规则声明「不允许手势兜底」的 fallback 取值。 */
internal const val FALLBACK_NONE = "none"
