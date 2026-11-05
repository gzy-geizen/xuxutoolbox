package com.cx330.toolbox.core.click

import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.snapshot.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClickTargetTest {

    private val screen = Bounds(0, 0, 1280, 2772)

    @Test
    fun screenAnchorUsesWholeScreen() {
        val point = ClickTarget.resolve(
            RuleAction(type = "tap", tapXRatio = 0.5f, tapYRatio = 0.2f, anchor = "screen"),
            Bounds(100, 100, 200, 200),
            screen,
        )
        assertEquals(640, point!!.first)
        assertEquals(554, point.second)
    }

    @Test
    fun nodeAnchorUsesMatchedNodeBounds() {
        val node = Bounds(left = 100, top = 200, right = 1200, bottom = 2600)
        val point = ClickTarget.resolve(
            RuleAction(type = "tap", tapXRatio = 0.9f, tapYRatio = 0.1f, anchor = "node"),
            node,
            screen,
        )
        assertEquals(1090, point!!.first)
        assertEquals(440, point.second)
    }

    @Test
    fun smallNodeIsRejectedForNodeAnchor() {
        assertNull(
            ClickTarget.resolve(
                RuleAction(type = "tap", tapXRatio = 0.9f, tapYRatio = 0.1f, anchor = "node"),
                Bounds(0, 0, 200, 100),
                screen,
            ),
        )
    }

    @Test
    fun mediumNodeAboveMinimumAreaIsAccepted() {
        // 1280x2772 屏幕上 30% 面积的弹窗节点，应与生成端 20% 门槛一致地可执行。
        val node = Bounds(0, 0, 1280, 832)
        val point = ClickTarget.resolve(
            RuleAction(type = "tap", tapXRatio = 0.5f, tapYRatio = 0.2f, anchor = "node"),
            node,
            screen,
        )
        assertEquals(640, point!!.first)
    }

    @Test
    fun nodeAnchorScalesWithDifferentResolution() {
        val smallScreen = Bounds(0, 0, 1080, 2400)
        val smallNode = Bounds(0, 0, 1080, 2400)
        val point = ClickTarget.resolve(
            RuleAction(type = "tap", tapXRatio = 0.854f, tapYRatio = 0.073f, anchor = "node"),
            smallNode,
            smallScreen,
        )
        assertEquals((1080 * 0.854f).toInt(), point!!.first)
        assertEquals((2400 * 0.073f).toInt(), point.second)
    }

    @Test
    fun missingOrInvalidNodeBoundsReturnsNull() {
        assertNull(
            ClickTarget.resolve(
                RuleAction(type = "tap", tapXRatio = 0.5f, tapYRatio = 0.5f, anchor = "node"),
                Bounds(0, 0, 0, 0),
                screen,
            ),
        )
        assertNull(
            ClickTarget.resolve(
                RuleAction(type = "tap", tapXRatio = 1.5f, tapYRatio = 0.5f),
                Bounds(0, 0, 10, 10),
                screen,
            ),
        )
    }
}
