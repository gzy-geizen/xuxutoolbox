package com.cx330.toolbox.core.rule

import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import java.io.File

/** 单测用：快速构造节点快照。 */
internal fun node(
    text: String? = null,
    viewId: String? = null,
    desc: String? = null,
    className: String? = "android.widget.TextView",
    clickable: Boolean = true,
    visible: Boolean = true,
    depth: Int = 1,
    bounds: Bounds = Bounds(0, 0, 200, 100),
    pkg: String = "com.demo.app",
    children: List<NodeSnapshot> = emptyList(),
): NodeSnapshot = NodeSnapshot(
    viewId = viewId,
    className = className,
    text = text,
    contentDescription = desc,
    clickable = clickable,
    enabled = true,
    visibleToUser = visible,
    bounds = bounds,
    depth = depth,
    packageName = pkg,
    children = children,
)

/** 单测用：快速构造一条规则（默认只有一个 any 选择器）。 */
internal fun rule(
    id: String = "r1",
    priority: Int = 0,
    text: String? = null,
    textContains: String? = null,
    textRegex: String? = null,
    viewId: String? = null,
    viewIdRegex: String? = null,
    descContains: String? = null,
    descRegex: String? = null,
    className: String? = null,
    clickable: Boolean? = null,
    minAreaDp2: Int? = null,
    depthRange: List<Int>? = null,
    forbid: String? = null,
    appScope: AppScope = AppScope(),
    window: WindowCondition = WindowCondition(),
    enabled: Boolean = true,
    action: RuleAction = RuleAction(),
    matchAll: Boolean = false,
    layer: String = "app",
): Rule {
    val selector = Selector(
        viewId = viewId,
        viewIdRegex = viewIdRegex,
        text = text,
        textContains = textContains,
        textRegex = textRegex,
        descContains = descContains,
        descRegex = descRegex,
        className = className,
        clickable = clickable,
        minAreaDp2 = minAreaDp2,
        depthRange = depthRange,
    )
    val match = if (matchAll) MatchNode(all = listOf(selector)) else MatchNode(any = listOf(selector))
    return Rule(
        id = id,
        priority = priority,
        enabled = enabled,
        appScope = appScope,
        window = window,
        match = match,
        guard = Guard(forbidTextRegex = forbid),
        action = action,
        layer = layer,
    )
}

internal fun context(
    pkg: String = "com.demo.app",
    fullscreen: Boolean = true,
    ime: Boolean = false,
    launchAgeMs: Long = 1_000L,
    densityDpi: Int = 480,
): WindowContext = WindowContext(
    packageName = pkg,
    isFullscreen = fullscreen,
    imeVisible = ime,
    launchAgeMs = launchAgeMs,
    densityDpi = densityDpi,
)

/** 通用语义层的跳过正则（与 builtin.json 保持一致）。 */
internal const val SKIP_REGEX = "^(跳过|跳过广告|skip)(\\s*\\d+\\s*[sS]?)?$"

/** 读取仓库里的内置规则文件。 */
internal object BuiltinRules {
    val raw: String by lazy { readRuleAsset("builtin.json") }
}

/** 读取任意一份规则资源文件。 */
internal fun readRuleAsset(name: String): String {
    val candidates = listOf(
        "src/main/assets/rules/$name",
        "app/src/main/assets/rules/$name",
    )
    val file = candidates.map { File(it) }.firstOrNull { it.exists() }
        ?: error("找不到 assets/rules/$name（测试工作目录不对？）")
    return file.readText()
}
