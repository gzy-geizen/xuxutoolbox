package com.cx330.toolbox.ui

import com.cx330.toolbox.ui.theme.JournalColors

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.cx330.toolbox.core.rule.BuiltinKeys
import com.cx330.toolbox.core.rule.RuleParser
import com.cx330.toolbox.core.rule.RuleSignatureVerifier
import com.cx330.toolbox.data.RuleImportStore


private data class ImportPreview(
    val raw: String,
    val validCount: Int,
    val errors: List<String>,
    val ids: List<String>,
    val keyId: String,
)

/**
 * 规则导入（奶油手账风）：选签名规则包 → 验签 → 预览 → 应用。
 */
@Composable
fun RuleImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var hasImported by remember { mutableStateOf(RuleImportStore.hasImported(context)) }
    var message by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (raw == null) {
            message = "读取文件失败"
            return@rememberLauncherForActivityResult
        }
        if (!RuleSignatureVerifier.looksSigned(raw)) {
            preview = null
            message = "这不是签名规则包：请用 tools/RuleSigner.java（或 scripts/sign-rules.ps1）生成后再导入。"
            return@rememberLauncherForActivityResult
        }
        val verified = RuleSignatureVerifier.verify(raw, BuiltinKeys.PUBLIC_KEY_B64)
        val payload = verified.getOrNull()
        if (payload == null) {
            preview = null
            message = "签名校验失败：${verified.exceptionOrNull()?.message}"
            return@rememberLauncherForActivityResult
        }
        val parsed = RuleParser.parse(payload.payloadJson)
        preview = ImportPreview(
            raw = raw,
            validCount = parsed.ruleSet.rules.size,
            errors = parsed.errors,
            ids = parsed.ruleSet.rules.map { "${it.id}（优先级 ${it.priority}）" },
            keyId = payload.keyId,
        )
        message = ""
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
                text = "规则导入",
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
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.paper.copy(alpha = 0.7f)),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("为什么要签名", style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
                    Text(
                        text = "规则能驱动点击，所以导入包必须验签；验签失败会被拒绝，不会退回明文规则。",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Text(
                text = if (hasImported) "当前：已有一份导入规则（与内置规则合并）" else "当前：仅使用内置规则",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
            )

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    picker.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                colors = ButtonDefaults.buttonColors(containerColor = JournalColors.caramel, contentColor = JournalColors.onCaramel),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("选择签名规则包（.json）")
            }

            val current = preview
            if (current != null) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                    border = BorderStroke(1.dp, JournalColors.matcha.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "✓ 签名有效（keyId=${current.keyId}）",
                            style = MaterialTheme.typography.titleSmall,
                            color = JournalColors.matcha,
                        )
                        Text(
                            text = "规则 ${current.validCount} 条 · 问题 ${current.errors.size} 条",
                            style = MaterialTheme.typography.bodySmall,
                            color = JournalColors.inkSoft,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        current.errors.take(5).forEach { error ->
                            Text(
                                text = "⚠ $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.danger,
                            )
                        }
                        current.ids.take(10).forEach { id ->
                            Text(
                                text = "· $id",
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.ink,
                            )
                        }
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                RuleImportStore.save(context, current.raw)
                                hasImported = true
                                message = "已保存。重启无障碍服务（关闭再开启）后生效。"
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = JournalColors.caramel,
                                contentColor = JournalColors.onCaramel,
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text("应用这份规则")
                        }
                    }
                }
            }

            if (message.isNotEmpty()) {
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
            }

            if (hasImported) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        RuleImportStore.clear(context)
                        hasImported = false
                        preview = null
                        message = "已删除导入规则，重启服务后恢复为内置规则。"
                    },
                ) {
                    Text("删除已导入的规则", color = JournalColors.danger)
                }
            }
        }
    }
}
