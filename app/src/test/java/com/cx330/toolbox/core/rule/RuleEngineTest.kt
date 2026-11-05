package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private fun engine(vararg rules: Rule) = RuleEngine(RuleSet(rules = rules.toList()))

    @Test
    fun `匹配纯跳过`() {
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(node(text = "跳过"), context())
        assertEquals(1, candidates.size)
    }

    @Test
    fun `匹配跳过加倒计时`() {
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(node(text = "跳过 5"), context())
        assertEquals(1, candidates.size)
    }

    @Test
    fun `匹配跳过加秒数后缀`() {
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(node(text = "跳过5s"), context())
        assertEquals(1, candidates.size)
    }

    @Test
    fun `匹配跳过广告四字`() {
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(node(text = "跳过广告"), context())
        assertEquals(1, candidates.size)
    }

    @Test
    fun `不匹配跳到首页`() {
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(node(text = "跳到首页"), context())
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `正则区分大小写 SKIP 不匹配`() {
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(node(text = "SKIP"), context())
        assertTrue(candidates.isEmpty())
    }

    @Test
    fun `匹配关闭符号`() {
        val candidates = engine(rule(textRegex = "^(×|✕|关闭)$")).match(node(text = "×"), context())
        assertEquals(1, candidates.size)
    }

    @Test
    fun `点击区域太小的关闭按钮不匹配`() {
        val rules = rule(
            textRegex = "^(×|✕|关闭)$",
            clickable = true,
            minAreaDp2 = 24,
        )
        val small = node(text = "×", bounds = Bounds(0, 0, 10, 10))
        assertTrue(engine(rules).match(small, context(densityDpi = 480)).isEmpty())
    }

    @Test
    fun `点击区域足够的关闭按钮匹配`() {
        val rules = rule(textRegex = "^(×|✕|关闭)$", clickable = true, minAreaDp2 = 24)
        val big = node(text = "×", bounds = Bounds(0, 0, 200, 100))
        assertEquals(1, engine(rules).match(big, context(densityDpi = 480)).size)
    }

    @Test
    fun `禁止词优先于匹配`() {
        val rules = rule(textRegex = "跳过", forbid = "跳过")
        assertTrue(engine(rules).match(node(text = "跳过"), context()).isEmpty())
    }

    @Test
    fun `按 viewId 精确匹配`() {
        val rules = rule(viewId = "com.demo:id/skip")
        assertEquals(1, engine(rules).match(node(text = "点我", viewId = "com.demo:id/skip"), context()).size)
    }

    @Test
    fun `viewId 不一致不匹配`() {
        val rules = rule(viewId = "com.demo:id/skip")
        assertTrue(engine(rules).match(node(text = "点我", viewId = "com.demo:id/other"), context()).isEmpty())
    }

    @Test
    fun `按描述包含匹配`() {
        val rules = rule(descContains = "跳过广告")
        assertEquals(1, engine(rules).match(node(desc = "点击跳过广告"), context()).size)
    }

    @Test
    fun `按类名匹配`() {
        val rules = rule(className = "android.widget.Button")
        assertEquals(1, engine(rules).match(node(text = "x", className = "android.widget.Button"), context()).size)
    }

    @Test
    fun `要求可点击时不可点节点不匹配`() {
        val rules = rule(textRegex = "跳过", clickable = true)
        assertTrue(engine(rules).match(node(text = "跳过", clickable = false), context()).isEmpty())
    }

    @Test
    fun `深度范围之内匹配`() {
        val rules = rule(textRegex = "跳过", depthRange = listOf(2, 4))
        assertEquals(1, engine(rules).match(node(text = "跳过", depth = 3), context()).size)
    }

    @Test
    fun `深度范围之外不匹配`() {
        val rules = rule(textRegex = "跳过", depthRange = listOf(2, 4))
        assertTrue(engine(rules).match(node(text = "跳过", depth = 9), context()).isEmpty())
    }

    @Test
    fun `any 选择器任意一个命中即可`() {
        val selectorA = Selector(textRegex = "跳过")
        val selectorB = Selector(viewId = "com.demo:id/skip")
        val rules = Rule(id = "r", match = MatchNode(any = listOf(selectorA, selectorB)))
        val target = node(text = "别的文字", viewId = "com.demo:id/skip")
        assertEquals(1, engine(rules).match(target, context()).size)
    }

    @Test
    fun `all 选择器必须全部命中`() {
        val rules = rule(textRegex = "跳过", clickable = true, matchAll = true)
        assertEquals(1, engine(rules).match(node(text = "跳过", clickable = true), context()).size)
        assertTrue(engine(rules).match(node(text = "跳过", clickable = false), context()).isEmpty())
    }

    @Test
    fun `排除名单里的包不匹配`() {
        val scope = AppScope(mode = "excludeListed", excluded = listOf("com.bank.app"))
        val rules = rule(textRegex = "跳过", appScope = scope)
        assertTrue(engine(rules).match(node(text = "跳过", pkg = "com.bank.app"), context(pkg = "com.bank.app")).isEmpty())
    }

    @Test
    fun `白名单之外的包不匹配`() {
        val scope = AppScope(mode = "includeListed", included = listOf("com.demo.app"))
        val rules = rule(textRegex = "跳过", appScope = scope)
        assertTrue(engine(rules).match(node(text = "跳过", pkg = "com.other.app"), context(pkg = "com.other.app")).isEmpty())
    }

    @Test
    fun `非全屏窗口不匹配`() {
        val rules = rule(textRegex = "跳过", window = WindowCondition(fullscreen = true))
        assertTrue(engine(rules).match(node(text = "跳过"), context(fullscreen = false)).isEmpty())
    }

    @Test
    fun `超过启动时长上限不匹配`() {
        val rules = rule(textRegex = "跳过", window = WindowCondition(launchAgeMsMax = 8_000L))
        assertTrue(engine(rules).match(node(text = "跳过"), context(launchAgeMs = 9_000L)).isEmpty())
    }

    @Test
    fun `输入法可见时不匹配`() {
        val rules = rule(textRegex = "跳过", window = WindowCondition(excludeIfImeVisible = true))
        assertTrue(engine(rules).match(node(text = "跳过"), context(ime = true)).isEmpty())
    }

    @Test
    fun `多个规则按优先级降序返回`() {
        val low = rule(id = "low", priority = 1, textRegex = "跳过")
        val high = rule(id = "high", priority = 10, textRegex = "跳过")
        val candidates = engine(low, high).match(node(text = "跳过"), context())
        assertEquals(listOf("high", "low"), candidates.map { it.ruleId })
    }

    @Test
    fun `禁用的规则不参与匹配`() {
        val rules = rule(textRegex = "跳过", enabled = false)
        assertTrue(engine(rules).match(node(text = "跳过"), context()).isEmpty())
    }

    @Test
    fun `非法正则在引擎中不会崩`() {
        val rules = rule(textRegex = "(")
        assertTrue(engine(rules).match(node(text = "跳过"), context()).isEmpty())
    }

    @Test
    fun `嵌套子节点也能被找到`() {
        val child = node(text = "跳过", depth = 2)
        val root = node(text = "root", depth = 1, children = listOf(child))
        val candidates = engine(rule(textRegex = SKIP_REGEX)).match(root, context())
        assertEquals(1, candidates.size)
        assertEquals("跳过", candidates.single().node.text)
    }

    @Test
    fun `空节点树返回空结果`() {
        assertTrue(engine(rule(textRegex = "跳过")).match(null, context()).isEmpty())
    }

    @Test
    fun `没有规则返回空结果`() {
        assertTrue(RuleEngine(RuleSet()).match(node(text = "跳过"), context()).isEmpty())
    }

    @Test
    fun `app 专属规则按作用域存在`() {
        val appRule = rule(
            id = "app.demo",
            textRegex = "跳过",
            appScope = AppScope(mode = "includeListed", included = listOf("com.demo.app")),
        )
        val generic = rule(
            id = "generic.demo",
            textRegex = "跳过",
            appScope = AppScope(mode = "includeListed", included = listOf("com.demo.app")),
            layer = "generic",
        )
        assertTrue(engine(appRule, generic).hasAppRuleFor("com.demo.app"))
        assertFalse(engine(appRule, generic).hasAppRuleFor("com.other.app"))
        assertFalse(engine(generic).hasAppRuleFor("com.demo.app"))
    }

    @Test
    fun `候选保留动作定义`() {
        val action = RuleAction(type = "gesture-center", fallback = "none")
        val candidates = engine(rule(textRegex = "跳过", action = action)).match(node(text = "跳过"), context())
        assertEquals("gesture-center", candidates.single().action.type)
        assertEquals("none", candidates.single().action.fallback)
    }

    @Test
    fun `两条规则命中不同节点时返回两个候选`() {
        val skip = node(text = "跳过", depth = 1)
        val close = node(text = "×", depth = 1, bounds = Bounds(0, 0, 200, 100))
        val root = node(text = "root", depth = 0, children = listOf(skip, close))
        val candidates = engine(
            rule(id = "skip", textRegex = SKIP_REGEX, priority = 10),
            rule(id = "close", textRegex = "^(×|✕|关闭)$", priority = 5),
        ).match(root, context())
        assertEquals(2, candidates.size)
        assertEquals(listOf("skip", "close"), candidates.map { it.ruleId })
    }

    @Test
    fun `按 viewId 正则匹配（广告 SDK 常见 id）`() {
        val rules = rule(viewIdRegex = "(?i)(skip|splash_skip)")
        val target = node(text = "点我", viewId = "com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn")
        assertEquals(1, engine(rules).match(target, context()).size)
    }

    @Test
    fun `viewId 正则不匹配时返回空`() {
        val rules = rule(viewIdRegex = "(?i)(skip|splash_skip)")
        assertTrue(engine(rules).match(node(viewId = "com.demo:id/other"), context()).isEmpty())
    }

    @Test
    fun `按文字包含匹配`() {
        val rules = rule(textContains = "跳过")
        assertEquals(1, engine(rules).match(node(text = "点击跳过此广告"), context()).size)
    }

    @Test
    fun `按描述正则匹配`() {
        val rules = rule(descRegex = "跳过|skip")
        assertEquals(1, engine(rules).match(node(desc = "跳过广告"), context()).size)
    }

    @Test
    fun `内置规则能按描述识别跳过`() {
        val (engine, errors) = RuleEngine.fromJson(BuiltinRules.raw)
        assertTrue(errors.isEmpty())
        val candidates = engine.match(node(desc = "跳过广告", text = null), context())
        assertEquals("generic.splash.desc", candidates.first().ruleId)
    }

    @Test
    fun `内置规则能按 viewId 识别穿山甲跳过按钮`() {
        val (engine, errors) = RuleEngine.fromJson(BuiltinRules.raw)
        assertTrue(errors.isEmpty())
        val target = node(
            text = null,
            viewId = "com.bytedance.sdk.openadsdk:id/tt_splash_skip_btn",
        )
        val candidates = engine.match(target, context())
        assertTrue(candidates.any { it.ruleId == "generic.splash.id" })
    }
}
