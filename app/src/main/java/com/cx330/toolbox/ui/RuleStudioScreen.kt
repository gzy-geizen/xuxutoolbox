package com.cx330.toolbox.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cx330.toolbox.core.inspect.CaptureDocument
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import com.cx330.toolbox.core.inspect.CapturePointRule
import com.cx330.toolbox.core.inspect.InspectNode
import com.cx330.toolbox.core.inspect.WindowCapture
import com.cx330.toolbox.core.rule.AppScope
import com.cx330.toolbox.core.rule.Guard
import com.cx330.toolbox.core.rule.MatchNode
import com.cx330.toolbox.core.rule.Rule
import com.cx330.toolbox.core.rule.RuleAction
import com.cx330.toolbox.core.rule.RuleParser
import com.cx330.toolbox.core.rule.Selector
import com.cx330.toolbox.core.rule.WindowCondition
import com.cx330.toolbox.core.rule.UserMatchKind
import com.cx330.toolbox.core.rule.UserRuleDraft
import com.cx330.toolbox.data.UserRuleStore
import com.cx330.toolbox.ui.theme.JournalColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class StudioMode { LIST, CAPTURE, NODES, EDIT, HELP }

@Composable
fun RuleStudioScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var mode by remember { mutableStateOf(StudioMode.LIST) }
    var rules by remember { mutableStateOf(UserRuleStore.rules(context)) }
    var captures by remember { mutableStateOf(WindowCapture.captures(context)) }
    var message by remember { mutableStateOf("") }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showClearCaptures by remember { mutableStateOf(false) }
    // 单条删除也要二次确认：规则删了要重新抓；抓取记录删了 JSON/PNG 一起没。
    var pendingDeleteRule by remember { mutableStateOf<Rule?>(null) }
    var pendingDeleteCapture by remember { mutableStateOf<File?>(null) }

    var capture by remember { mutableStateOf<CaptureDocument?>(null) }
    var capturePng by remember { mutableStateOf<File?>(null) }
    var selectedNode by remember { mutableStateOf<InspectNode?>(null) }
    var draft by remember { mutableStateOf<UserRuleDraft?>(null) }

    BackHandler(enabled = mode != StudioMode.LIST) {
        mode = StudioMode.LIST
        selectedNode = null
        draft = null
        capturePng = null
    }

    fun refreshRules() {
        rules = UserRuleStore.rules(context)
    }

    fun refreshCaptures() {
        captures = WindowCapture.captures(context)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = UserRuleStore.exportJson(context).toByteArray(Charsets.UTF_8)
                    val out = context.contentResolver.openOutputStream(uri) ?: return@runCatching false
                    out.use { it.write(bytes); it.flush() }
                    bytes.isNotEmpty()
                }.getOrDefault(false)
            }
            message = if (ok) "已导出用户规则" else "导出失败"
        }
    }

    var pendingImportRaw by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            pendingImportRaw = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            showImportConfirm = pendingImportRaw != null
        }
    }

    fun reloadIfOnline(onDone: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                com.cx330.toolbox.service.MyAccessibilityService.requestReload()
            }
            onDone(ok)
            if (!ok) message = "规则已保存；无障碍未连接时需重启服务后生效"
        }
    }

    fun startCaptureView(document: CaptureDocument, jsonFile: File?) {
        capture = document
        capturePng = jsonFile?.let { File(it.parentFile, it.nameWithoutExtension + ".png") }
            ?.takeIf { it.exists() }
        selectedNode = null
        draft = null
        mode = StudioMode.CAPTURE
    }

    fun captureNow(delayMs: Long) {
        scope.launch {
            message = if (delayMs > 0) "已准备：${delayMs / 1000} 秒后抓取，请切到广告页" else "正在抓取…"
            val result = withContext(Dispatchers.Main) {
                if (delayMs > 0) WindowCapture.captureAfter(context, delayMs) else WindowCapture.capture(context)
            }
            WindowCapture.consumeArmed()
            refreshCaptures()
            if (result.error != null) {
                message = result.error
                return@launch
            }
            if (result.packageName == context.packageName) {
                message = "注意：抓的是工具箱自己，规则不会作用于自身；仅用于预览/试功能。"
            } else {
                message = "已抓取 ${result.packageName}（${result.nodeCount} 节点）"
            }
            val json = result.jsonFile ?: return@launch
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    Json.decodeFromString(CaptureDocument.serializer(), json.readText(Charsets.UTF_8))
                }.getOrNull()
            }
            if (parsed != null) startCaptureView(parsed, json) else message = "读取抓取文件失败"
        }
    }

    fun deleteCapture(file: File) {
        scope.launch {
            withContext(Dispatchers.IO) { WindowCapture.deleteCapture(file) }
            refreshCaptures()
            message = "已删除这条抓取记录"
        }
    }

    fun deleteRule(rule: Rule) {
        scope.launch {
            withContext(Dispatchers.IO) { UserRuleStore.delete(context, rule.id) }
            refreshRules()
            reloadIfOnline()
        }
    }

    fun clearCaptures() {
        scope.launch {
            withContext(Dispatchers.IO) { WindowCapture.clearAll(context) }
            refreshCaptures()
            message = "已清空抓取记录"
        }
    }

    fun openCapture(file: File) {
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching {
                    Json.decodeFromString(CaptureDocument.serializer(), file.readText(Charsets.UTF_8))
                }.getOrNull()
            }
            if (parsed == null) message = "读取抓取文件失败" else startCaptureView(parsed, file)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JournalColors.cream)
            .statusBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            TextButton(onClick = {
                if (mode == StudioMode.LIST) onBack() else {
                    mode = StudioMode.LIST
                    selectedNode = null
                    draft = null
                }
            }) {
                Text("← 返回", color = JournalColors.inkSoft)
            }
            Text(
                text = when (mode) {
                    StudioMode.LIST -> "规则工坊"
                    StudioMode.CAPTURE -> "点截图上的「跳过」"
                    StudioMode.NODES -> "节点列表（高级）"
                    StudioMode.EDIT -> "确认规则"
                    StudioMode.HELP -> "使用说明"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = JournalColors.ink,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            if (mode == StudioMode.LIST) {
                TextButton(onClick = { mode = StudioMode.HELP }) {
                    Text("使用说明", color = JournalColors.caramel)
                }
            }
        }

        when (mode) {
            StudioMode.LIST -> StudioList(
                rules = rules,
                captures = captures,
                timeFormat = timeFormat,
                message = message,
                onCaptureNow = { captureNow(0) },
                onCaptureDelayed = { captureNow(5_000) },
                onOpenCapture = ::openCapture,
                onDeleteCapture = { file -> pendingDeleteCapture = file },
                onClearCaptures = { showClearCaptures = true },
                onToggleRule = { rule, enabled ->
                    scope.launch {
                        withContext(Dispatchers.IO) { UserRuleStore.setEnabled(context, rule.id, enabled) }
                        refreshRules()
                        reloadIfOnline()
                    }
                },
                onDeleteRule = { rule -> pendingDeleteRule = rule },
                onExport = { exportLauncher.launch("cx330-user-rules.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
            )

            StudioMode.CAPTURE -> CaptureView(
                document = capture,
                png = capturePng,
                message = message,
                modifier = Modifier.weight(1f),
                onPick = { pick ->
                    val doc = capture
                    val pkg = doc?.packageName.orEmpty()
                    if (doc != null && pkg.isNotBlank()) {
                        selectedNode = pick.node
                        draft = draftFromPick(doc, pick)
                        mode = StudioMode.EDIT
                    }
                },
                onAdvanced = { mode = StudioMode.NODES },
            )

            StudioMode.NODES -> NodePicker(
                document = capture,
                onPick = { node ->
                    selectedNode = node
                    val pkg = capture?.packageName ?: ""
                    val screenW = (capture?.screenWidth ?: 1).coerceAtLeast(1).toFloat()
                    val screenH = (capture?.screenHeight ?: 1).coerceAtLeast(1).toFloat()
                    val relocatable = !node.text.isNullOrBlank() || !node.viewId.isNullOrBlank()
                    val nodeClick = node.clickable && relocatable
                    val b = CapturePointRule.boundsOf(node)
                    val centerX = if (b != null) (b[0] + b[2]) / 2f else screenW / 2f
                    val centerY = if (b != null) (b[1] + b[3]) / 2f else screenH / 2f
                    draft = UserRuleDraft(
                        id = "user.${pkg.substringAfterLast('.')}.${System.currentTimeMillis()}",
                        packageName = pkg,
                        matchKind = when {
                            !node.text.isNullOrBlank() -> UserMatchKind.TEXT
                            !node.contentDescription.isNullOrBlank() -> UserMatchKind.DESC_CONTAINS
                            !node.viewId.isNullOrBlank() -> UserMatchKind.VIEW_ID
                            else -> UserMatchKind.REGION
                        },
                        matchValue = node.text ?: node.contentDescription ?: node.viewId.orEmpty(),
                        region = null,
                        actionType = if (nodeClick) "click" else "tap",
                        tapXRatio = centerX / screenW,
                        tapYRatio = centerY / screenH,
                        anchor = "screen",
                        priority = 200,
                    )
                    mode = StudioMode.EDIT
                },
            )

            StudioMode.EDIT -> RuleEditor(
                draft = draft,
                node = selectedNode,
                message = message,
                onChange = { draft = it },
                onSave = {
                    val d = draft ?: return@RuleEditor
                    val result = runCatching { d.build() }
                    result.onSuccess {
                        scope.launch {
                            withContext(Dispatchers.IO) { UserRuleStore.upsert(context, it) }
                            refreshRules()
                            val reloaded = withContext(Dispatchers.IO) {
                                com.cx330.toolbox.service.MyAccessibilityService.requestReload()
                            }
                            message = "规则已保存：${it.id}" + if (reloaded) "（已热加载）" else "（重启无障碍后生效）" +
                                "；如未生效，请到诊断查看最近事件里的 rule id 和结果。"
                            mode = StudioMode.LIST
                            draft = null
                        }
                    }.onFailure {
                        message = "保存失败：${it.message}"
                    }
                },
            )

            StudioMode.HELP -> RuleStudioHelpScreen(modifier = Modifier.weight(1f))
        }
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            containerColor = JournalColors.card,
            titleContentColor = JournalColors.ink,
            textContentColor = JournalColors.inkSoft,
            title = { Text("导入未签名规则？") },
            text = {
                Text("这些规则来自文件，没有签名校验。只导入你信任的来源；导入后同 id 规则会覆盖本机用户规则。")
            },
            confirmButton = {
                TextButton(onClick = {
                    val raw = pendingImportRaw
                    showImportConfirm = false
                    if (raw != null) {
                        scope.launch {
                            val outcome = withContext(Dispatchers.IO) { UserRuleStore.importRaw(context, raw) }
                            refreshRules()
                            val reloaded = withContext(Dispatchers.IO) {
                                com.cx330.toolbox.service.MyAccessibilityService.requestReload()
                            }
                            message = when {
                                outcome.imported == 0 -> "导入 0 条" +
                                    if (outcome.errors.isEmpty()) "" else "（${outcome.errors.size} 条问题）"
                                outcome.errors.isEmpty() -> "导入 ${outcome.imported} 条" +
                                    if (reloaded) "（已热加载）" else "（重启无障碍后生效）"
                                else -> "导入 ${outcome.imported} 条，另有 ${outcome.errors.size} 条问题" +
                                    if (reloaded) "（已热加载）" else "（重启无障碍后生效）"
                            }
                        }
                    }
                    pendingImportRaw = null
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportRaw = null
                }) { Text("取消") }
            },
        )
    }

    if (showClearCaptures) {
        AlertDialog(
            onDismissRequest = { showClearCaptures = false },
            containerColor = JournalColors.card,
            titleContentColor = JournalColors.ink,
            textContentColor = JournalColors.inkSoft,
            title = { Text("清空抓取记录？") },
            text = { Text("只删除抓取产生的 JSON/PNG 文件；已经用它生成的规则会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCaptures = false
                    clearCaptures()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearCaptures = false }) { Text("取消") }
            },
        )
    }

    val ruleToDelete = pendingDeleteRule
    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteRule = null },
            containerColor = JournalColors.card,
            titleContentColor = JournalColors.ink,
            textContentColor = JournalColors.inkSoft,
            title = { Text("删除这条规则？") },
            text = {
                Text("规则「${ruleToDelete.id}」删除后不再生效，其他规则不受影响。需要时可重新抓取广告页生成。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteRule = null
                    deleteRule(ruleToDelete)
                }) { Text("删除", color = Color(0xFFC96A5B)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteRule = null }) { Text("取消") }
            },
        )
    }

    val captureToDelete = pendingDeleteCapture
    if (captureToDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteCapture = null },
            containerColor = JournalColors.card,
            titleContentColor = JournalColors.ink,
            textContentColor = JournalColors.inkSoft,
            title = { Text("删除这条抓取记录？") },
            text = {
                Text("只删除「${captureToDelete.name}」对应的 JSON/PNG 文件；已经用它生成的规则会保留。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDeleteCapture = null
                    deleteCapture(captureToDelete)
                }) { Text("删除", color = Color(0xFFC96A5B)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCapture = null }) { Text("取消") }
            },
        )
    }
}

private fun draftFromPick(doc: CaptureDocument, pick: CapturePointRule.Pick): UserRuleDraft {
    val pkg = doc.packageName.orEmpty()
    val id = "user.${pkg.substringAfterLast('.')}.${System.currentTimeMillis()}"
    val node = pick.node
    if (node == null) {
        return UserRuleDraft(
            id = id,
            packageName = pkg,
            matchKind = UserMatchKind.REGION,
            matchValue = "",
            actionType = "tap",
            tapXRatio = pick.tapXRatioOnScreen,
            tapYRatio = pick.tapYRatioOnScreen,
            anchor = "screen",
        )
    }
    val text = node.text?.trim().orEmpty()
    val desc = node.contentDescription?.trim().orEmpty()
    val viewId = node.viewId?.trim().orEmpty()
    val kind = when {
        text.isNotEmpty() -> UserMatchKind.TEXT
        desc.isNotEmpty() -> UserMatchKind.DESC_CONTAINS
        viewId.isNotEmpty() -> UserMatchKind.VIEW_ID
        else -> UserMatchKind.REGION
    }
    val value = when (kind) {
        UserMatchKind.TEXT -> text
        UserMatchKind.DESC_CONTAINS -> desc
        UserMatchKind.VIEW_ID -> viewId
        else -> ""
    }
    // 只有 text 或 viewId 才能在执行时重新定位到节点；只有 desc 的图片广告定位不到。
    val relocatable = text.isNotEmpty() || viewId.isNotEmpty()
    val clickable = node.clickable && relocatable && kind != UserMatchKind.REGION
    // REGION 是按“区域/整块广告”匹配，点击点必须也用屏幕比例；
    // 不能“按区域匹配、按节点内比例点击”，否则执行时会套到另一个节点上。
    val useNodeAnchor = clickable
    return UserRuleDraft(
        id = id,
        packageName = pkg,
        matchKind = kind,
        matchValue = value,
        actionType = if (clickable) "click" else "tap",
        tapXRatio = if (useNodeAnchor) pick.tapXRatioInNode else pick.tapXRatioOnScreen,
        tapYRatio = if (useNodeAnchor) pick.tapYRatioInNode else pick.tapYRatioOnScreen,
        anchor = if (useNodeAnchor) "node" else "screen",
    )
}

@Composable
private fun CaptureView(
    document: CaptureDocument?,
    png: File?,
    message: String,
    modifier: Modifier = Modifier,
    onPick: (CapturePointRule.Pick) -> Unit,
    onAdvanced: () -> Unit,
) {
    if (document == null) {
        Text("没有抓取数据", modifier = Modifier.padding(24.dp))
        return
    }
    var picked by remember(document) { mutableStateOf<CapturePointRule.Pick?>(null) }
    val bitmap = remember(png) { png?.let { BitmapFactory.decodeFile(it.path) } }
    val candidates = remember(document) { CapturePointRule.candidateNodes(document.root) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "直接在截图上点你平时点的「跳过 / 关闭」位置。",
            style = MaterialTheme.typography.titleSmall,
            color = JournalColors.ink,
        )
        Text(
            "程序会自动把这一下换算成规则，不用在几百个节点里找。绿色框是推荐的候选。",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
        )

        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "抓取截图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(Modifier.matchParentSize()) {
                    // 图片是 ContentScale.Fit：按较短边取缩放，并扣除居中留白。
                    val sw = document.screenWidth.coerceAtLeast(1).toFloat()
                    val sh = document.screenHeight.coerceAtLeast(1).toFloat()
                    val s = minOf(size.width / sw, size.height / sh)
                    val offsetX = (size.width - sw * s) / 2f
                    val offsetY = (size.height - sh * s) / 2f
                    candidates.forEach { node ->
                        val b = CapturePointRule.boundsOf(node) ?: return@forEach
                        drawRect(
                            color = if (CapturePointRule.hasSkipKeyword(node)) {
                                Color(0xFF8FA97E)
                            } else {
                                Color(0x66E9A25B)
                            },
                            topLeft = Offset(offsetX + b[0] * s, offsetY + b[1] * s),
                            size = Size((b[2] - b[0]) * s, (b[3] - b[1]) * s),
                            style = Stroke(width = 2f),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(document, png) {
                            detectTapGestures { offset ->
                                val sw = document.screenWidth.coerceAtLeast(1).toFloat()
                                val sh = document.screenHeight.coerceAtLeast(1).toFloat()
                                val s = minOf(size.width / sw, size.height / sh)
                                if (s <= 0f) return@detectTapGestures
                                val offsetX = (size.width - sw * s) / 2f
                                val offsetY = (size.height - sh * s) / 2f
                                val x = ((offset.x - offsetX) / s).toInt()
                                    .coerceIn(0, document.screenWidth - 1)
                                val y = ((offset.y - offsetY) / s).toInt()
                                    .coerceIn(0, document.screenHeight - 1)
                                picked = CapturePointRule.pick(
                                    root = document.root,
                                    tapX = x,
                                    tapY = y,
                                    screenWidth = document.screenWidth,
                                    screenHeight = document.screenHeight,
                                )
                            }
                        },
                )
            }
        } else {
            Text("这次抓取没有截图，请用下面的节点列表。", color = JournalColors.inkSoft)
        }

        picked?.let { p ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(p.reason, style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
                    Text(
                        p.node?.let { "${it.text ?: it.contentDescription ?: it.viewId}  ${it.bounds}" }
                            ?: "未命中节点，将按整屏比例",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                    )
                    Text(
                        "相对节点 ${(p.tapXRatioInNode * 100).toInt()}% / ${(p.tapYRatioInNode * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                    )
                }
            }
            Button(
                onClick = { onPick(p) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = JournalColors.caramel,
                    contentColor = JournalColors.cream,
                ),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("用这个位置建规则") }
        }

        TextButton(onClick = onAdvanced) {
            Text("高级：从节点列表选（${candidates.size} 个候选）", color = JournalColors.caramel)
        }
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
        }
    }
}

@Composable
private fun StudioList(
    rules: List<Rule>,
    captures: List<File>,
    timeFormat: SimpleDateFormat,
    message: String,
    onCaptureNow: () -> Unit,
    onCaptureDelayed: () -> Unit,
    onOpenCapture: (File) -> Unit,
    onDeleteCapture: (File) -> Unit,
    onClearCaptures: () -> Unit,
    onToggleRule: (Rule, Boolean) -> Unit,
    onDeleteRule: (Rule) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.paper.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, JournalColors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("抓取当前广告页", style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
                Text(
                    "先让广告出现，再点下面按钮；5 秒抓取会留出切换 App 的时间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = JournalColors.inkSoft,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = onCaptureDelayed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JournalColors.caramel,
                            contentColor = JournalColors.cream,
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("5 秒后抓取") }
                    OutlinedButton(
                        onClick = onCaptureNow,
                        border = BorderStroke(1.dp, JournalColors.border),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("立即抓取", color = JournalColors.inkSoft) }
                }
            }
        }

        Text("我的规则（${rules.size} 条）", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
        Text(
            "你建的规则属于这个 App 的专属规则，严格模式下也会参与；想只测一条，请先关掉其它用户规则。",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
        )
        if (rules.isEmpty()) {
            Text(
                "还没有自定义规则。抓一张广告页，在截图上点一下「跳过 / 关闭」的位置，再保存即可。",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
            )
        } else {
            rules.forEach { rule ->
                val clickWithoutLocator = rule.action.type == "click" &&
                    rule.match.any.none { !it.viewId.isNullOrBlank() || !it.text.isNullOrBlank() }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                    border = BorderStroke(1.dp, JournalColors.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.id, style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
                            Text(
                                text = "${rule.appScope.included.joinToString()} · ${rule.action.type}" +
                                    if (rule.action.type == "tap") " ${rule.action.anchor}" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.inkSoft,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (clickWithoutLocator) {
                                Text(
                                    "⚠ 只按描述匹配却用「点击节点」，运行时可能定位不到；建议删除后重新抓图，在截图上点一下跳过位置。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = JournalColors.danger,
                                )
                            }
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { onToggleRule(rule, it) })
                        TextButton(onClick = { onDeleteRule(rule) }) { Text("删除", color = Color(0xFFC96A5B)) }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, border = BorderStroke(1.dp, JournalColors.border), modifier = Modifier.weight(1f)) {
                Text("导出规则", color = JournalColors.inkSoft)
            }
            OutlinedButton(onClick = onImport, border = BorderStroke(1.dp, JournalColors.border), modifier = Modifier.weight(1f)) {
                Text("导入规则", color = JournalColors.inkSoft)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "抓取记录（${captures.size}）",
                style = MaterialTheme.typography.titleSmall,
                color = JournalColors.inkSoft,
                modifier = Modifier.weight(1f),
            )
            if (captures.isNotEmpty()) {
                TextButton(onClick = onClearCaptures) {
                    Text("清空记录", color = Color(0xFFC96A5B))
                }
            }
        }
        captures.take(10).forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onOpenCapture(file) }, modifier = Modifier.weight(1f)) {
                    Text(
                        "${timeFormat.format(Date(file.lastModified()))}  ${file.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.ink,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { onDeleteCapture(file) }) {
                    Text("删除", color = Color(0xFFC96A5B))
                }
            }
        }
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
        }
    }
}

@Composable
private fun NodePicker(document: CaptureDocument?, onPick: (InspectNode) -> Unit) {
    val allNodes = remember(document) {
        document?.root?.let { root ->
            val out = ArrayList<InspectNode>()
            fun walk(node: InspectNode) {
                val usable = !node.text.isNullOrBlank() ||
                    !node.contentDescription.isNullOrBlank() ||
                    !node.viewId.isNullOrBlank()
                if (usable) out += node
                node.children.forEach(::walk)
            }
            walk(root)
            out
        } ?: emptyList()
    }
    val candidates = remember(document) {
        document?.root?.let { CapturePointRule.candidateNodes(it) } ?: emptyList()
    }
    var showAll by remember(document) { mutableStateOf(false) }
    val nodes = if (showAll) allNodes else candidates
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (document == null) {
            Text("没有抓取数据", style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
            return
        }
        Text(
            "${document.packageName ?: "?"} · ${document.nodeCount} 节点 · ${document.screenWidth}x${document.screenHeight}",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                if (showAll) "全部节点 ${allNodes.size}" else "推荐候选 ${candidates.size}",
                style = MaterialTheme.typography.titleSmall,
                color = JournalColors.inkSoft,
            )
            TextButton(onClick = { showAll = !showAll }) {
                Text(if (showAll) "只看推荐" else "显示全部", color = JournalColors.caramel)
            }
        }
        if (!showAll && candidates.isEmpty()) {
            Text(
                "没有识别出明显候选；可以点「显示全部」手动找，或返回截图直接点位置。",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
            )
        }
        nodes.forEach { node ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(node) },
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        node.text ?: node.contentDescription ?: node.viewId.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = JournalColors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            node.viewId?.takeIf { it.isNotBlank() },
                            node.contentDescription?.takeIf { it.isNotBlank() }?.let { "desc=$it" },
                            node.bounds,
                            if (node.clickable) "可点击" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleEditor(
    draft: UserRuleDraft?,
    node: InspectNode?,
    message: String,
    onChange: (UserRuleDraft) -> Unit,
    onSave: () -> Unit,
) {
    val d = draft
    if (d == null) {
        Text("没有可编辑的规则", modifier = Modifier.padding(24.dp))
        return
    }
    val nodeRelocatable = node == null || !node.text.isNullOrBlank() || !node.viewId.isNullOrBlank()
    val nodeClickable = node?.clickable ?: true
    val clickBlocked = d.actionType == "click" && node != null && !(nodeRelocatable && nodeClickable)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("来源节点", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
        Text(
            node?.let { "${it.text ?: it.contentDescription ?: it.viewId}  ${it.bounds}" } ?: "-",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.ink,
        )

        Text("匹配方式", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
        UserMatchKind.entries.forEach { kind ->
            OutlinedButton(
                onClick = { onChange(d.copy(matchKind = kind)) },
                border = BorderStroke(
                    1.dp,
                    if (d.matchKind == kind) JournalColors.caramel else JournalColors.border,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (d.matchKind == kind) "✓ ${kind.label}" else kind.label,
                    color = JournalColors.ink,
                )
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = d.matchValue,
            onValueChange = { onChange(d.copy(matchValue = it)) },
            label = { Text("匹配值") },
            modifier = Modifier.fillMaxWidth(),
        )
        androidx.compose.material3.OutlinedTextField(
            value = d.id,
            onValueChange = { onChange(d.copy(id = it)) },
            label = { Text("规则 id（唯一）") },
            modifier = Modifier.fillMaxWidth(),
        )

        Text("动作", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onChange(d.copy(actionType = "click")) },
                enabled = nodeRelocatable && nodeClickable,
                border = BorderStroke(1.dp, if (d.actionType == "click") JournalColors.caramel else JournalColors.border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) { Text("点击节点", color = JournalColors.ink) }
            OutlinedButton(
                onClick = { onChange(d.copy(actionType = "tap")) },
                border = BorderStroke(1.dp, if (d.actionType == "tap") JournalColors.caramel else JournalColors.border),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) { Text("按比例点击", color = JournalColors.ink) }
        }
        if (d.actionType == "tap") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onChange(d.copy(anchor = "node")) },
                    border = BorderStroke(1.dp, if (d.anchor == "node") JournalColors.caramel else JournalColors.border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("相对节点", color = JournalColors.ink) }
                OutlinedButton(
                    onClick = { onChange(d.copy(anchor = "screen")) },
                    border = BorderStroke(1.dp, if (d.anchor == "screen") JournalColors.caramel else JournalColors.border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("相对屏幕", color = JournalColors.ink) }
            }
            androidx.compose.material3.OutlinedTextField(
                value = d.tapXRatio.toString(),
                onValueChange = { onChange(d.copy(tapXRatio = it.toFloatOrNull() ?: d.tapXRatio)) },
                label = { Text("X 比例 0..1") },
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.OutlinedTextField(
                value = d.tapYRatio.toString(),
                onValueChange = { onChange(d.copy(tapYRatio = it.toFloatOrNull() ?: d.tapYRatio)) },
                label = { Text("Y 比例 0..1") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "整屏图片广告：用「相对节点 + 右上角比例」，换分辨率也能点准。",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
            )
        }

        if (clickBlocked) {
            Text(
                "这个节点没有文字或 ID、或者本身不可点击，运行时无法重新定位，不能用「点击节点」。请改用「按比例点击」。",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.danger,
            )
        }
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
        }

        Button(
            onClick = onSave,
            enabled = !clickBlocked,
            colors = ButtonDefaults.buttonColors(
                containerColor = JournalColors.caramel,
                contentColor = JournalColors.cream,
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存规则") }
        Text(
            "保存后立即生效：规则会在服务下次判定时参与匹配；可先开试运行观察。",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
        )
    }
}
