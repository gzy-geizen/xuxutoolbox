package com.cx330.toolbox.core.inspect

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectModelTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `节点树可序列化并统计节点数`() {
        val node = InspectNode(
            depth = 0,
            index = 0,
            packageName = "com.demo.app",
            className = "android.widget.FrameLayout",
            bounds = "[0,0][1280,2772]",
            childCount = 1,
            children = listOf(
                InspectNode(
                    depth = 1,
                    index = 0,
                    text = "跳过 5",
                    clickable = true,
                    bounds = "[962,104][1228,182]",
                ),
            ),
        )
        val document = CaptureDocument(
            capturedAt = 1L,
            packageName = "com.demo.app",
            screenWidth = 1280,
            screenHeight = 2772,
            densityDpi = 480,
            nodeCount = InspectTreeBuilder.count(node),
            root = node,
        )
        val encoded = json.encodeToString(document)
        assertTrue(encoded.contains("跳过 5"))
        assertEquals(2, document.nodeCount)
        assertEquals(2, InspectTreeBuilder.count(node))
    }
}
