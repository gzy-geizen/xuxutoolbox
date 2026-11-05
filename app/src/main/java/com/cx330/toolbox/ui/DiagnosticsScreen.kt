package com.cx330.toolbox.ui

import com.cx330.toolbox.ui.theme.JournalColors

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cx330.toolbox.core.debug.Diagnostics
import com.cx330.toolbox.core.inspect.WindowCapture
import com.cx330.toolbox.util.AccessibilityUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch


/**
 * 诊断页（奶油手账风）：统计便签 + 按规则命中 + 最近事件 + 一键导出。
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var captures by remember(refreshToken) { mutableStateOf(WindowCapture.captures(context)) }

    val summary = remember(refreshToken) { Diagnostics.summary() }
    val entries = remember(refreshToken) { Diagnostics.entriesSnapshot() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = Diagnostics.exportText()
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            }
        }.isSuccess
        message = if (ok) "已导出" else "导出失败"
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
            TextButton(onClick = onBack) { Text("← 返回", color = JournalColors.inkSoft) }
            Text(
                text = "诊断与日志",
                style = MaterialTheme.typography.headlineSmall,
                color = JournalColors.ink,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 统计便签
            val stats = listOf(
                "窗口事件" to summary.totalWindowEvents,
                "候选" to summary.candidateCount,
                "试运行" to summary.dryRunCount,
                "点击" to summary.clickCount,
                "拦截" to summary.gatedCount,
                "无候选" to summary.noCandidateCount,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, value) ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = JournalColors.paper.copy(alpha = 0.7f)),
                                border = BorderStroke(1.dp, JournalColors.border),
                                modifier = Modifier.weight(1f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = value.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = JournalColors.caramel,
                                    )
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JournalColors.inkSoft,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { exportLauncher.launch("须须工具箱-诊断-${System.currentTimeMillis()}.txt") },
                    colors = ButtonDefaults.buttonColors(containerColor = JournalColors.caramel, contentColor = JournalColors.onCaramel),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导出日志")
                }
                OutlinedButton(
                    onClick = {
                        Diagnostics.reset()
                        refreshToken++
                        message = "已清空统计"
                    },
                    border = BorderStroke(1.dp, JournalColors.border),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("清空", color = JournalColors.inkSoft)
                }
                OutlinedButton(
                    onClick = { refreshToken++ },
                    border = BorderStroke(1.dp, JournalColors.border),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("刷新", color = JournalColors.inkSoft)
                }
            }

            if (message.isNotEmpty()) {
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
            }

            // 窗口捕获（P1-3 布局检查）
            Text("窗口捕获（造规则用）", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "手动捕获当前窗口的节点树 + 截图，只存本机，可随时清空。",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                    )
                    Button(
                        onClick = {
                            if (!AccessibilityUtils.isServiceEnabled(context)) {
                                message = "先开启无障碍服务，才能读取窗口"
                                return@Button
                            }
                            scope.launch {
                                val result = WindowCapture.capture(context)
                                message = result.error
                                    ?: "已捕获 ${result.nodeCount} 个节点（${result.packageName ?: "未知包名"} / ${result.activityName ?: "未知页面"}）"
                                captures = WindowCapture.captures(context)
                                refreshToken++
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JournalColors.caramel,
                            contentColor = JournalColors.onCaramel,
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                    ) {
                        Text("捕获当前窗口")
                    }
                    OutlinedButton(
                        onClick = {
                            if (!AccessibilityUtils.isServiceEnabled(context)) {
                                message = "先开启无障碍服务，才能读取窗口"
                                return@OutlinedButton
                            }
                            WindowCapture.armNext()
                            message = "已准备：现在打开目标 App，出现广告那一屏会自动捕获"
                        },
                        border = BorderStroke(1.dp, JournalColors.border),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("准备捕获下一屏（打开目标 App）", color = JournalColors.caramel)
                    }
                    OutlinedButton(
                        onClick = {
                            if (!AccessibilityUtils.isServiceEnabled(context)) {
                                message = "先开启无障碍服务，才能读取窗口"
                                return@OutlinedButton
                            }
                            message = "5 秒后捕获：现在切到目标 App 的广告页"
                            scope.launch {
                                val result = WindowCapture.captureAfter(context, 5_000L)
                                message = result.error
                                    ?: "已捕获 ${result.nodeCount} 个节点（${result.packageName ?: "未知包名"} / ${result.activityName ?: "未知页面"}）"
                                captures = WindowCapture.captures(context)
                                refreshToken++
                            }
                        },
                        border = BorderStroke(1.dp, JournalColors.border),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text("5 秒后捕获（切到广告页）", color = JournalColors.inkSoft)
                    }

                    if (captures.isEmpty()) {
                        Text(
                            text = "还没有捕获记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = JournalColors.inkSoft,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        captures.take(6).forEach { json ->
                            val png = File(json.parentFile, json.nameWithoutExtension + ".png")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(json.name, style = MaterialTheme.typography.bodySmall, color = JournalColors.ink)
                                    Text(
                                        text = timeFormat.format(Date(json.lastModified())) +
                                            " · ${json.length() / 1024} KB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = JournalColors.inkSoft,
                                    )
                                }
                                TextButton(onClick = { shareFile(context, json, "application/json") }) {
                                    Text("节点树", color = JournalColors.caramel)
                                }
                                if (png.exists()) {
                                    TextButton(onClick = { shareFile(context, png, "image/png") }) {
                                        Text("截图", color = JournalColors.caramel)
                                    }
                                }
                            }
                        }
                        TextButton(
                            onClick = {
                                WindowCapture.clearAll(context)
                                captures = emptyList()
                                message = "已清空捕获文件"
                            },
                        ) {
                            Text("清空捕获", color = JournalColors.inkSoft)
                        }
                    }
                }
            }

            // 按规则命中
            Text("按规则命中", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (summary.perRule.isEmpty()) {
                        Text("暂无命中", style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
                    } else {
                        summary.perRule.entries.sortedByDescending { it.value }.take(8).forEach { (rule, count) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(rule, style = MaterialTheme.typography.bodySmall, color = JournalColors.ink)
                                Text(count.toString(), style = MaterialTheme.typography.bodySmall, color = JournalColors.caramel)
                            }
                        }
                    }
                }
            }

            // 最近事件
            Text("最近事件（最新在前）", style = MaterialTheme.typography.titleSmall, color = JournalColors.inkSoft)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (entries.isEmpty()) {
                        Text("暂无事件", style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
                    } else {
                        entries.take(60).forEach { entry ->
                            Text(
                                text = "${timeFormat.format(Date(entry.timeMs))} [${entry.category}] ${entry.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.inkSoft,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun shareFile(context: Context, file: File, mime: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.inspect", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享捕获"))
    }
}