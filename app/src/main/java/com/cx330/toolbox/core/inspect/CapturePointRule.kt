package com.cx330.toolbox.core.inspect

/**
 * 从「用户在截图上点的位置」推断要建什么规则。
 *
 * 为什么需要它：抓取树里节点成百上千，普通用户根本分不清哪个是「跳过」。
 * 让用户直接在截图上点他看到的「跳过」按钮，程序再把点换算成节点/比例，门槛最低。
 *
 * 选择顺序：
 * 1. 点中带「跳过/关闭/广告」等关键词的可点击节点 → 直接点击该节点；
 * 2. 点中其它可点击节点 → 点击该节点；
 * 3. 点中整屏图片容器（面积足够大）→ 用「相对节点」比例点击；
 * 4. 都没点中 → 用「相对屏幕」比例点击。
 */
object CapturePointRule {

    val SKIP_WORDS = listOf("跳过", "关闭", "skip", "×", "✕", "广告", "会员去广告")

    data class Pick(
        val node: InspectNode?,
        val tapXRatioInNode: Float,
        val tapYRatioInNode: Float,
        val tapXRatioOnScreen: Float,
        val tapYRatioOnScreen: Float,
        val reason: String,
        val hasKeyword: Boolean,
    )

    fun flatten(root: InspectNode): List<InspectNode> {
        val out = ArrayList<InspectNode>()
        fun walk(node: InspectNode) {
            out += node
            node.children.forEach(::walk)
        }
        walk(root)
        return out
    }

    fun boundsOf(node: InspectNode): IntArray? {
        val match = Regex("""\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)]""").find(node.bounds) ?: return null
        return match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }.toIntArray()
    }

    fun areaOf(node: InspectNode): Long {
        val b = boundsOf(node) ?: return 0
        return (b[2] - b[0]).coerceAtLeast(0).toLong() * (b[3] - b[1]).coerceAtLeast(0)
    }

    fun contains(node: InspectNode, x: Int, y: Int): Boolean {
        val b = boundsOf(node) ?: return false
        return x in b[0]..b[2] && y in b[1]..b[3]
    }

    fun hasMatchInfo(node: InspectNode): Boolean =
        !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank() || !node.viewId.isNullOrBlank()

    fun hasSkipKeyword(node: InspectNode): Boolean {
        val haystack = (node.text.orEmpty() + " " + node.contentDescription.orEmpty()).lowercase()
        return SKIP_WORDS.any { haystack.contains(it.lowercase()) }
    }

    /** 只保留“像跳过按钮/广告容器”的节点，减少列表噪音。 */
    fun candidateNodes(root: InspectNode): List<InspectNode> {
        val screenArea = areaOf(root).coerceAtLeast(1)
        return flatten(root).filter { node ->
            if (node === root) return@filter false
            val area = areaOf(node)
            hasSkipKeyword(node) ||
                (node.clickable && area > 0) ||
                (area * 100 >= screenArea * 50) // 整屏/大半屏容器
        }
    }

    fun pick(root: InspectNode, tapX: Int, tapY: Int, screenWidth: Int, screenHeight: Int): Pick? {
        if (screenWidth <= 0 || screenHeight <= 0) return null
        val screenX = tapX.toFloat() / screenWidth
        val screenY = tapY.toFloat() / screenHeight
        // 根节点代表整屏，不参与“点中了哪个控件”的判断，否则永远只会选中根。
        val inside = flatten(root).filter { it !== root && contains(it, tapX, tapY) && areaOf(it) > 0 }
        if (inside.isEmpty()) {
            return Pick(
                node = null,
                tapXRatioInNode = screenX,
                tapYRatioInNode = screenY,
                tapXRatioOnScreen = screenX,
                tapYRatioOnScreen = screenY,
                reason = "没有命中节点，将按整屏比例点击",
                hasKeyword = false,
            )
        }

        val keywordClickable = inside
            .filter { it.clickable && hasSkipKeyword(it) }
            .minByOrNull { areaOf(it) }
        val clickable = inside.filter { it.clickable }.minByOrNull { areaOf(it) }
        val screenArea = screenWidth.toLong() * screenHeight
        val bigContainer = inside
            .filter { areaOf(it) * 100 >= screenArea * 20 && hasMatchInfo(it) }
            .maxByOrNull { areaOf(it) }
        val chosen = keywordClickable ?: clickable ?: bigContainer ?: inside.maxByOrNull { areaOf(it) }!!
        if (chosen !in listOfNotNull(keywordClickable, clickable, bigContainer) && !hasMatchInfo(chosen)) {
            return Pick(
                node = null,
                tapXRatioInNode = screenX,
                tapYRatioInNode = screenY,
                tapXRatioOnScreen = screenX,
                tapYRatioOnScreen = screenY,
                reason = "命中了大块容器但没有可写规则的文字/描述/ID，将按整屏比例点击",
                hasKeyword = false,
            )
        }
        val b = boundsOf(chosen) ?: return null
        val w = (b[2] - b[0]).coerceAtLeast(1)
        val h = (b[3] - b[1]).coerceAtLeast(1)
        val inNodeX = ((tapX - b[0]).toFloat() / w).coerceIn(0f, 1f)
        val inNodeY = ((tapY - b[1]).toFloat() / h).coerceIn(0f, 1f)
        val reason = when {
            keywordClickable != null -> "命中带“跳过/关闭”字样的按钮"
            clickable != null -> "命中可点击按钮"
            bigContainer != null -> "命中整块广告图片，将按图片内相对位置点击"
            else -> "命中节点，将按节点内相对位置点击"
        }
        return Pick(
            node = chosen,
            tapXRatioInNode = inNodeX,
            tapYRatioInNode = inNodeY,
            tapXRatioOnScreen = screenX,
            tapYRatioOnScreen = screenY,
            reason = reason,
            hasKeyword = hasSkipKeyword(chosen),
        )
    }
}
