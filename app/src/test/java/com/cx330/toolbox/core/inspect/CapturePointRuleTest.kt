package com.cx330.toolbox.core.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturePointRuleTest {

    private fun node(
        bounds: String,
        text: String? = null,
        desc: String? = null,
        clickable: Boolean = false,
        viewId: String? = null,
        children: List<InspectNode> = emptyList(),
    ) = InspectNode(
        depth = 1,
        index = 0,
        packageName = "com.demo.app",
        className = "android.view.View",
        text = text,
        contentDescription = desc,
        clickable = clickable,
        viewId = viewId,
        bounds = bounds,
        children = children,
    )

    @Test
    fun picksKeywordClickableButton() {
        val button = node("[900,100][1200,220]", text = "跳过广告", clickable = true)
        val root = node("[0,0][1280,2772]", children = listOf(button))
        val pick = CapturePointRule.pick(root, 1050, 160, 1280, 2772)
        assertNotNull(pick)
        assertEquals(button, pick!!.node)
        assertEquals(true, pick.hasKeyword)
        assertEquals(0.5f, pick.tapXRatioInNode, 0.01f)
        assertEquals(0.5f, pick.tapYRatioInNode, 0.01f)
    }

    @Test
    fun fallsBackToFullscreenImageRatio() {
        val image = node("[0,0][1280,2771]", desc = "广告")
        val root = node("[0,0][1280,2772]", children = listOf(image))
        val pick = CapturePointRule.pick(root, 1093, 202, 1280, 2772)
        assertNotNull(pick)
        assertEquals(image, pick!!.node)
        assertEquals("广告", pick.node?.contentDescription)
        assertEquals(0.854f, pick.tapXRatioInNode, 0.01f)
        assertEquals(0.073f, pick.tapYRatioInNode, 0.01f)
    }

    @Test
    fun noContainingNodeUsesScreenRatio() {
        val root = node("[0,0][1280,2772]")
        val pick = CapturePointRule.pick(root, 640, 1386, 1280, 2772)
        assertEquals(null, pick!!.node)
        assertEquals(0.5f, pick.tapXRatioOnScreen, 0.01f)
        assertEquals(0.5f, pick.tapYRatioOnScreen, 0.01f)
    }

    @Test
    fun frameworkRootDoesNotBecomeRuleMatch() {
        val decor = node("[0,0][1280,2772]", viewId = "android:id/decor_content_parent")
        val root = node("[0,0][1280,2772]", children = listOf(decor))
        val pick = CapturePointRule.pick(root, 640, 1386, 1280, 2772)
        assertNotNull(pick)
        assertNull(pick!!.node)
        assertEquals(0.5f, pick.tapXRatioOnScreen, 0.01f)
    }

    @Test
    fun candidateNodesFiltersNoise() {
        val skip = node("[900,100][1200,220]", text = "跳过", clickable = true)
        val noise = node("[10,10][20,20]", text = "普通文字")
        val image = node("[0,0][1280,2771]", desc = "广告")
        val root = node("[0,0][1280,2772]", children = listOf(skip, noise, image))
        val candidates = CapturePointRule.candidateNodes(root)
        assertTrue(candidates.contains(skip))
        assertTrue(candidates.contains(image))
        assertTrue(!candidates.contains(noise))
    }
}
