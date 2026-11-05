package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.click.ClickTarget
import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRuleBuilderTest {

    private val context = WindowContext(
        packageName = "com.cx330.fakead",
        isFullscreen = true,
        launchAgeMs = 500,
    )

    private val tree = NodeSnapshot(
        packageName = "com.cx330.fakead",
        bounds = Bounds(0, 0, 1280, 2772),
        children = listOf(
            NodeSnapshot(
                packageName = "com.cx330.fakead",
                text = "跳过 5",
                clickable = true,
                bounds = Bounds(993, 160, 1240, 279),
                depth = 1,
            ),
        ),
    )

    @Test
    fun textRuleFromDraftMatchesCapturedNode() {
        val rule = UserRuleDraft(
            id = "user.fakead.1",
            packageName = "com.cx330.fakead",
            matchKind = UserMatchKind.TEXT,
            matchValue = "跳过 5",
            actionType = "click",
        ).build()

        val candidates = RuleEngine(RuleSet(rules = listOf(rule))).match(tree, context)
        assertEquals(1, candidates.size)
        assertEquals("user.fakead.1", candidates.first().ruleId)
    }

    @Test
    fun imageAdRuleUsesNodeRelativeTap() {
        val imageTree = NodeSnapshot(
            packageName = "com.demo.image",
            bounds = Bounds(0, 0, 1280, 2772),
            children = listOf(
                NodeSnapshot(
                    packageName = "com.demo.image",
                    className = "android.widget.ImageView",
                    contentDescription = "广告",
                    bounds = Bounds(0, 0, 1280, 2771),
                    depth = 1,
                ),
            ),
        )
        val rule = UserRuleDraft(
            id = "user.image.1",
            packageName = "com.demo.image",
            matchKind = UserMatchKind.DESC_CONTAINS,
            matchValue = "广告",
            actionType = "tap",
            tapXRatio = 0.854f,
            tapYRatio = 0.073f,
            anchor = "node",
        ).build()
        val candidates = RuleEngine(RuleSet(rules = listOf(rule))).match(
            imageTree,
            WindowContext(packageName = "com.demo.image", launchAgeMs = 300),
        )
        assertEquals(1, candidates.size)
        val decision = candidates.first()
        assertEquals("tap", decision.action.type)
        assertEquals("node", decision.action.anchor)
        val point = ClickTarget.resolve(decision.action, decision.node.bounds, imageTree.bounds)
        assertEquals((1280 * 0.854f).toInt(), point!!.first)
    }

    @Test
    fun emptyViewIdIsRejected() {
        val bad = UserRuleDraft(
            id = "user.bad",
            packageName = "com.cx330.fakead",
            matchKind = UserMatchKind.VIEW_ID,
            matchValue = "",
        )
        val error = runCatching { bad.build() }.exceptionOrNull()
        assertTrue(error != null)
    }

    @Test
    fun regionOnlyRuleBuilds() {
        val rule = UserRuleDraft(
            id = "user.region",
            packageName = "com.cx330.fakead",
            matchKind = UserMatchKind.REGION,
            matchValue = "",
            actionType = "tap",
            anchor = "screen",
        ).build()
        assertEquals("top_right", rule.match.any.first().region)
    }
}
