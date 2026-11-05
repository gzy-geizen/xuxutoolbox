package com.cx330.toolbox.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.cx330.toolbox.core.debug.Diagnostics
import com.cx330.toolbox.core.inspect.WindowCapture
import com.cx330.toolbox.core.health.FrozenAlertNotifier
import com.cx330.toolbox.core.health.HealthCheckScheduler
import com.cx330.toolbox.core.health.ServiceHealth
import com.cx330.toolbox.core.module.ModuleManager
import com.cx330.toolbox.core.module.ShakeFallbackModule
import com.cx330.toolbox.core.module.SkipSplashModule
import com.cx330.toolbox.core.pipeline.SplashPipeline
import com.cx330.toolbox.core.rule.RuleEngine
import com.cx330.toolbox.core.rule.RuleRepository
import com.cx330.toolbox.data.ConfigStore
import com.cx330.toolbox.data.RuleImportStore
import com.cx330.toolbox.data.UserRuleStore
import com.cx330.toolbox.keepalive.DisconnectNotifier
import com.cx330.toolbox.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 无障碍服务（S2 骨架 + S4 流水线 + S12 模块化）。
 *
 * 事件回调只做「过滤 + 转发」；快照、模块决策、点击都在 [SplashPipeline] 里。
 */
class MyAccessibilityService : AccessibilityService() {

    private var pipeline: SplashPipeline? = null
    @Volatile
    private var engine: RuleEngine? = null
    private var moduleManager: ModuleManager? = null
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 最近一次「窗口状态变化」的 Activity 类名，布局检查记录用。 */
    @Volatile
    private var lastActivityName: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "service connected")
        instance = this
        WindowCapture.register(this) { lastActivityName }

        engine = loadRuleEngine()
        val configStore = ConfigStore(this)
        moduleManager = ModuleManager(
            modules = listOf(SkipSplashModule { engine }, ShakeFallbackModule()),
            isEnabled = { moduleId -> configStore.isModuleEnabled(moduleId) },
        )
        pipeline = SplashPipeline(this) { moduleManager }

        // HyperOS 会冻结普通后台进程，用前台服务把优先级抬起来（S4 实测需要）。
        // 前台服务起不来时（Android 12+ 后台限制）退回普通常驻通知。
        val keepAliveStarted = KeepAliveService.start(this)
        Log.i(TAG, "keep-alive started=$keepAliveStarted")
        if (!keepAliveStarted) NotificationHelper.showOngoing(this)
        // S9：排一次健康检查；服务恢复后取消「已断开 / 疑似被冻结」提醒
        HealthCheckScheduler.schedule(this)
        DisconnectNotifier.cancel(this)
        FrozenAlertNotifier.cancel(this)
        // 连上本身也是一次「活着」的证据：刷新墙钟心跳基线
        ServiceHealth.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        // 事件心跳：内存计数 + 按 60 秒节流持久化墙上时间（跨进程/重启可比较）
        ServiceHealth.onEvent(this)
        Diagnostics.onWindowEvent()
        if (e.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            lastActivityName = e.className?.toString()
        }
        // 事件日志用 v 级别：事件密集时避免刷屏与主线程日志开销
        Log.v(
            TAG,
            "event=${typeName(e.eventType)} pkg=${e.packageName} cls=${e.className} windowId=${e.windowId}"
        )
        pipeline?.onEvent(e)

        // 「准备捕获下一屏」：等目标 App 的窗口事件到了再抓，给用户留出打开广告的时间
        val eventPkg = e.packageName?.toString()
        val ignoredPkgs = setOf(packageName, "com.miui.home", "com.android.systemui", "com.miui.securitycenter")
        if (eventPkg != null && eventPkg !in ignoredPkgs && WindowCapture.consumeArmed()) {
            captureScope.launch {
                // 开屏广告可能晚几百毫秒才画出来：连拍 5 张，覆盖 0.4s~3.2s
                var last: WindowCapture.Result? = null
                repeat(5) { index ->
                    delay(if (index == 0) 400L else 700L)
                    last = WindowCapture.capture(this@MyAccessibilityService)
                }
                val result = last
                val text = result?.error
                    ?: "已捕获 ${result?.nodeCount ?: 0} 个节点（${result?.packageName ?: "未知包名"}）"
                NotificationHelper.notifyCaptureResult(this@MyAccessibilityService, text)
            }
        }
    }

    /** 规则工坊保存后调用：不重启服务也重新加载规则。 */
    fun reloadRules() {
        engine = loadRuleEngine()
        Log.i(TAG, "rules reloaded: engineReady=${engine != null}")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "onInterrupt")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.w(TAG, "onUnbind")
        if (instance === this) instance = null
        WindowCapture.unregister(this)
        captureScope.cancel()
        // 先取消流水线协程，避免解绑后还有探测/点击在跑
        pipeline?.close()
        pipeline = null
        moduleManager = null
        engine = null
        // 只撤「运行状态」通知（1001）；前台服务通知（1000）必须由前台服务自己处理
        NotificationHelper.cancelStatus(this)
        // S9：断开时立刻提醒（后台启动 Activity 受限，只能通知点按跳转）
        DisconnectNotifier.show(this)
        return super.onUnbind(intent)
    }

    private fun loadRuleEngine(): RuleEngine? {
        return try {
            val contents = ArrayList<String>()
            RULE_FILES.mapNotNullTo(contents) { path ->
                runCatching {
                    assets.open(path).bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            val loaded = RuleRepository.load(
                assetContents = contents,
                importedRaw = RuleImportStore.load(this),
                userRaw = UserRuleStore.loadRaw(this),
            )
            if (loaded.errors.isNotEmpty()) {
                Log.w(TAG, "rule warnings: ${loaded.errors}")
            }
            Log.i(
                TAG,
                "rules loaded: ${loaded.ruleSet.rules.size} 条, importedSigned=${loaded.importedSigned}, userRules=${loaded.userRuleCount}",
            )
            RuleEngine(loaded.ruleSet)
        } catch (e: Exception) {
            Log.e(TAG, "load rules failed", e)
            null
        }
    }

    private fun typeName(type: Int): String = when (type) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "WINDOW_STATE_CHANGED"
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "WINDOW_CONTENT_CHANGED"
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "WINDOWS_CHANGED"
        else -> "TYPE_$type"
    }

    companion object {
        @Volatile
        private var instance: MyAccessibilityService? = null

        /** 返回是否成功加载出新引擎；失败时 UI 应提示“需重启无障碍”。 */
        fun requestReload(): Boolean {
            val svc = instance ?: return false
            svc.reloadRules()
            return svc.engine != null
        }

        const val TAG = "CX330"
        private val RULE_FILES = listOf("rules/builtin.json", "rules/sdk.json")
    }
}
