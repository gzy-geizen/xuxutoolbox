package com.cx330.toolbox.core.pipeline

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.cx330.toolbox.core.click.ClickExecutor
import com.cx330.toolbox.core.debug.Diagnostics
import com.cx330.toolbox.core.debug.Hit
import com.cx330.toolbox.core.debug.RecentHits
import com.cx330.toolbox.core.gate.SafetyGate
import com.cx330.toolbox.core.module.CxEvent
import com.cx330.toolbox.core.module.ModuleDecision
import com.cx330.toolbox.core.module.ModuleManager
import com.cx330.toolbox.core.rule.Candidate
import com.cx330.toolbox.core.rule.WindowContext
import com.cx330.toolbox.core.snapshot.Bounds
import com.cx330.toolbox.core.snapshot.NodeSnapshot
import com.cx330.toolbox.core.snapshot.SnapshotCollector
import com.cx330.toolbox.data.ConfigSnapshot
import com.cx330.toolbox.data.ConfigStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 事件 → 快照 → 模块决策 → 安全闸 →（dry-run 记录 / 真点击）。
 *
 * 事件回调本身只做轻量过滤并立刻返回；快照与匹配在协程里做，
 * 其中「模块决策」被切到 Dispatchers.Default，避免占用主线程。
 */
class SplashPipeline(
    private val service: AccessibilityService,
    private val moduleManagerProvider: () -> ModuleManager?,
) {

    // 注意：这里必须是 Dispatchers.Main（非 immediate）。
    // 用 immediate 会让协程体在 onAccessibilityEvent 内同步执行（含快照遍历），
    // 事件密集时会把主线程压住，系统会出现 "wait for adding window timeout" 并停止派发事件。
    private val collector = SnapshotCollector(maxNodes = 400, maxDepth = 20, budgetMs = 60L)
    private val gate = SafetyGate()
    private val executor = ClickExecutor()
    private val configStore = ConfigStore(service)
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, throwable ->
            // 任何未捕获异常（节点失效、手势失败等）只记日志，不能把进程打崩。
            Log.e(DEBUG_TAG, "pipeline coroutine failed", throwable)
        },
    )
    private val lastHandledAt = HashMap<Int, Long>()

    private val processing = AtomicBoolean(false)
    private val lastSnapshotAt = AtomicLong(0L)

    private var lastPackage: String? = null
    private var lastPackageAt = 0L

    /**
     * 桌面/启动器不处理（它们不是「App 开屏广告」）。
     * 注意：PackageManager 查询很慢（实测首次可达 500ms），绝不能放在事件处理路径上；
     * 这里先用常见桌面兜底，再在 IO 线程补全。
     */
    @Volatile
    private var launcherPackages: Set<String> = CommonLaunchers.PACKAGES

    init {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val queried = service.packageManager.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toSet()
                launcherPackages = queried + CommonLaunchers.PACKAGES
            }
        }
    }

    fun onEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == service.packageName || pkg.startsWith("com.android.systemui")) return
        if (pkg in launcherPackages) return

        val now = SystemClock.uptimeMillis()
        if (event.eventType !in PROCESSABLE_EVENTS) return

        var newApp = false
        if (pkg != lastPackage) {
            lastPackage = pkg
            lastPackageAt = now
            newApp = true
        }

        val config = configStore.snapshot()
        if (!config.enabled) return
        // 安全排除名单（支付/输入法/系统设置）与用户在应用列表里手动关掉的 App
        if (pkg in config.excludedPackages || pkg in config.disabledPackages) return

        val launchAge = if (pkg == lastPackage) now - lastPackageAt else Long.MAX_VALUE
        if (launchAge > config.maxLaunchAgeMs) return

        val windowId = event.windowId
        val last = lastHandledAt[windowId] ?: 0L
        if (now - last < WINDOW_THROTTLE_MS) return
        lastHandledAt[windowId] = now

        // 再加一道全局节流：同一时间只允许一个快照/匹配在跑，且两次快照间隔 ≥ 500ms。
        if (now - lastSnapshotAt.get() < GLOBAL_SNAPSHOT_INTERVAL_MS) return
        if (!processing.compareAndSet(false, true)) return
        lastSnapshotAt.set(now)
        val eventType = event.eventType
        val activityName = event.className?.toString()
        scope.launch {
            try {
                handle(pkg, windowId, launchAge, eventType, activityName, config)
            } finally {
                processing.set(false)
            }
        }

        // 新 App 进来后，广告可能在窗口切换后几百毫秒才绘制出来。
        // 只靠事件回调容易漏（首次窗口常指向转场窗口），所以在 8 秒窗口内做有限次探测。
        if (newApp) {
            val probeConfig = config
            val probeWindowId = windowId
            val probePkg = pkg
            scope.launch {
                repeat(PROBE_TIMES) { index ->
                    delay(PROBE_INTERVAL_MS)
                    val age = SystemClock.uptimeMillis() - lastPackageAt
                    if (age > probeConfig.maxLaunchAgeMs) return@launch
                    if (lastPackage != probePkg) return@launch
                    if (!processing.compareAndSet(false, true)) return@repeat
                    try {
                        // 关键字快搜只能看到 text/desc 里带「跳过/关闭」的节点；
                        // 12306、迅雷这类整屏图片广告的命中条件在 contentDescription/区域上，
                        // 因此每隔 3 次探测做一次完整快照（约 150ms 一次），保证广告一出现就能命中。
                        val deepProbe = index % 3 == 0
                        if (handle(probePkg, probeWindowId, age, eventType, activityName, probeConfig, deep = deepProbe)) {
                            // 已经命中并处理过，不再继续探测，避免同一次启动反复扫描耗电。
                            return@launch
                        }
                    } finally {
                        processing.set(false)
                    }
                }
            }
        }
    }

    private suspend fun handle(
        pkg: String,
        windowId: Int,
        launchAge: Long,
        eventType: Int,
        activityName: String?,
        config: ConfigSnapshot,
        deep: Boolean = true,
    ): Boolean {
        val manager = moduleManagerProvider() ?: return false
        val root = activeWindowRoot() ?: return false
        // 关键校验：当前活动窗口必须属于事件里的包名。
        // 转场/探测期间可能拿到别的 App 的窗口，若不校验会把 A 的按钮记到 B 头上。
        val rootPackage = root.packageName?.toString()
        if (rootPackage != pkg) return false
        val context = buildContext(pkg, launchAge, root, activityName, config.isStrictFor(pkg))
        val event = CxEvent(eventType, windowId, pkg, launchAge)

        // 快路径：按关键字定向搜节点。冷启动时目标 App 主线程很忙，
        // 全树遍历会被预算截断（nodes=1 truncated=true），定向搜索更可靠。
        var decisions = findDecisionsByKeyword(root, pkg, context, event, manager)
        // 快路径只看到带关键字文本的节点，看不到整屏图片广告的 desc/面积条件。
        // 若该包有 App 专属规则，而快路径只命中通用规则，就必须走完整快照，
        // 否则通用「跳过」会抢在专属规则之前误点（H6）。
        val appRuleExists = manager.hasAppRule(pkg)
        val fastPathSafe = FastPathPolicy.canUse(decisions, appRuleExists)
        if (!fastPathSafe) {
            if (!deep) return false
            decisions = emptyList()
            val snapshot = collector.collect(root)
            val tree = snapshot.root ?: return false
            decisions = withContext(Dispatchers.Default) { manager.decide(event, context, tree) }
            if (decisions.isEmpty()) {
                Diagnostics.onNoCandidate()
                val hints = tree.flatten().mapNotNull { it.text }
                    .filter { text -> text.contains("跳过") || text.contains("关闭") || text == "×" }
                    .take(3)
                    .toList()
                Log.d(
                    DEBUG_TAG,
                    "no_candidate pkg=$pkg nodes=${snapshot.nodeCount} truncated=${snapshot.truncated} " +
                        "full=${context.isFullscreen} ime=${context.imeVisible} age=$launchAge hints=$hints",
                )
            }
        }
        if (decisions.isEmpty()) return false

        val decision = decisions.first()
        val candidate = Candidate(
            ruleId = decision.ruleId,
            priority = decision.priority,
            node = decision.node,
            action = decision.action,
            guard = decision.guard,
        )
        val nodeKey = "${decision.node.viewId}|${decision.node.text}|${decision.node.bounds}"
        // 规则自己声明的 guard（冷却/每窗口次数）也要参与判定，和全局上限取更严格的一侧。
        val gateDecision = gate.check(
            windowId = windowId,
            nodeKey = nodeKey,
            packageName = pkg,
            excludedPackages = config.excludedPackages,
            guard = decision.guard,
        )
        if (!gateDecision.allowed) {
            Diagnostics.onGated(pkg, decision.ruleId, gateDecision.reason)
        }
        Log.d(
            DEBUG_TAG,
            "candidate module=${decision.moduleId} rule=${decision.ruleId} pkg=$pkg " +
                "node='${decision.node.text}' gated=${gateDecision.reason} dryRun=${config.dryRun} " +
                "decisions=${decisions.size}",
        )

        val result = when {
            !gateDecision.allowed -> "gated:${gateDecision.reason}"
            config.dryRun -> "dry_run"
            else -> {
                gate.record(windowId, nodeKey)
                executor.execute(service, candidate, pkg)
            }
        }

        Log.i(
            ACTION_TAG,
            "module=${decision.moduleId} rule=${decision.ruleId} pkg=$pkg node='${decision.node.text}' " +
                "viewId=${decision.node.viewId} cls=${decision.node.className} " +
                "desc=${decision.node.contentDescription} bounds=${decision.node.bounds} " +
                "action=${decision.action.type} result=$result dryRun=${config.dryRun}",
        )
        // 被安全闸拦截的动作只计入「拦截」统计（onGated），不重复计入命中/试运行统计，
        // 也不写入「最近命中」——否则首页会把「被拦下」显示成「它刚才想点什么」。
        if (gateDecision.allowed) {
            Diagnostics.onAction(
                packageName = pkg,
                ruleId = decision.ruleId,
                nodeText = decision.node.text,
                result = result,
                dryRun = config.dryRun,
            )
        }
        recentHitFor(gateDecision.allowed) {
            Hit(
                timeMs = System.currentTimeMillis(),
                packageName = pkg,
                ruleId = decision.ruleId,
                nodeText = decision.node.text,
                bounds = decision.node.bounds.toString(),
                action = decision.action.type,
                result = result,
                dryRun = config.dryRun,
            )
        }?.let(RecentHits::add)
        return true
    }

    /** 按关键字定向搜索，构造合成树交给模块决策。 */
    private fun findDecisionsByKeyword(
        root: AccessibilityNodeInfo,
        pkg: String,
        context: WindowContext,
        event: CxEvent,
        manager: ModuleManager,
    ): List<ModuleDecision> {
        val nodes = ArrayList<NodeSnapshot>()
        for (keyword in KEYWORDS) {
            val found = root.findAccessibilityNodeInfosByText(keyword).orEmpty()
            for (node in found) {
                val snapshot = SnapshotCollector.snapshotOf(node)
                val duplicated = nodes.any {
                    it.viewId == snapshot.viewId &&
                        it.text == snapshot.text &&
                        it.bounds == snapshot.bounds
                }
                if (!duplicated) nodes += snapshot
            }
            if (nodes.size >= 20) break
        }
        if (nodes.isEmpty()) return emptyList()

        val metrics = service.resources.displayMetrics
        val syntheticRoot = NodeSnapshot(
            packageName = pkg,
            bounds = Bounds(0, 0, metrics.widthPixels, metrics.heightPixels),
            depth = 0,
            children = nodes,
        )
        return manager.decide(event, context, syntheticRoot)
    }

    /** 取当前活动的应用窗口根节点；rootInActiveWindow 在转场时可能指向系统窗口。 */
    private fun activeWindowRoot(): AccessibilityNodeInfo? {
        val windows = service.windows
        if (windows != null) {
            windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
                .maxByOrNull { it.layer }
                ?.root
                ?.let { return it }
        }
        return service.rootInActiveWindow
    }

    private fun buildContext(
        pkg: String,
        launchAge: Long,
        root: AccessibilityNodeInfo,
        activityName: String?,
        strictMode: Boolean,
    ): WindowContext {
        val metrics = service.resources.displayMetrics
        val rect = Rect().also { root.getBoundsInScreen(it) }
        val fullscreen = rect.width() >= metrics.widthPixels * 0.9 &&
            rect.height() >= metrics.heightPixels * 0.9
        val imeVisible = service.windows?.any {
            it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD
        } ?: false
        return WindowContext(
            packageName = pkg,
            activityName = activityName,
            isFullscreen = fullscreen,
            imeVisible = imeVisible,
            launchAgeMs = launchAge,
            densityDpi = metrics.densityDpi,
            strictMode = strictMode,
        )
    }

    /** 服务解绑/销毁时取消协程，避免断开后还有探测/点击在跑。 */
    fun close() {
        scope.cancel()
    }

    companion object {
        const val ACTION_TAG = "CX330_ACTION"
        const val DEBUG_TAG = "CX330_DEBUG"
        private const val WINDOW_THROTTLE_MS = 20L
        private const val GLOBAL_SNAPSHOT_INTERVAL_MS = 30L
        private const val PROBE_TIMES = 130
        private const val PROBE_INTERVAL_MS = 60L
        private val PROCESSABLE_EVENTS = setOf(
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )
        private val KEYWORDS = listOf("跳过", "关闭", "skip", "×", "✕")
    }
}

/**
 * 「最近命中」写入策略（纯逻辑，不碰 Android，可在 JVM 单测里锁住）。
 *
 * 只有真正放行的动作（含试运行 dry-run）才写入 [RecentHits]；
 * 被安全闸拦截的命中只进 `Diagnostics.onGated`，不写入这里。
 */
internal fun recentHitFor(gateAllowed: Boolean, build: () -> Hit): Hit? =
    if (gateAllowed) build() else null

private object CommonLaunchers {
    val PACKAGES: Set<String> = setOf(
        "com.miui.home",
        "com.android.launcher",
        "com.android.launcher2",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.vivo.launcher",
        "com.bbk.launcher2",
    )
}