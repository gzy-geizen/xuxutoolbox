package com.cx330.toolbox.ui

import com.cx330.toolbox.ui.theme.JournalColors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cx330.toolbox.core.debug.Hit
import com.cx330.toolbox.core.debug.RecentHits
import com.cx330.toolbox.core.health.ServiceHealth
import com.cx330.toolbox.data.ConfigStore
import com.cx330.toolbox.keepalive.DisconnectNotifier
import com.cx330.toolbox.keepalive.KeepAliveGuide
import com.cx330.toolbox.keepalive.KeepAliveIntents
import com.cx330.toolbox.notify.NotificationHelper
import com.cx330.toolbox.util.AccessibilityUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay


/**
 * 「跳过开屏广告」工具详情页（奶油手账风）。
 *
 * 结构：状态 → 判定开关 → 功能模块 → 更多入口 → 保活 → 最近候选。
 */
@Composable
fun SkipSplashDetailScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val context = LocalContext.current
    val config = remember { ConfigStore(context) }
    var serviceEnabled by remember { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }
    var dryRun by remember { mutableStateOf(config.dryRun) }
    var strictMode by remember { mutableStateOf(config.strictMode) }
    var moduleEnabled by remember { mutableStateOf(config.isModuleEnabled("skip_splash")) }
    var shakeEnabled by remember { mutableStateOf(config.isModuleEnabled("shake_fallback")) }
    var hits by remember { mutableStateOf(RecentHits.snapshot()) }
    var lastEventAgo by remember { mutableStateOf(-1L) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val manufacturer = Build.MANUFACTURER ?: "Android"
    val guideSteps = remember(manufacturer) { KeepAliveGuide.stepsFor(manufacturer) }

    // Android 13+ 发通知需要 POST_NOTIFICATIONS 授权：先申请，授权后才弹常驻通知，
    // 被拒绝时给出可操作提示（否则系统会静默丢弃通知，看起来像按钮没反应）。
    var notificationHint by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationHint = null
            NotificationHelper.showOngoing(context)
        } else {
            notificationHint = "通知权限被拒绝：可在「系统设置 → 应用 → 须须工具箱 → 通知」里开启后再试。"
        }
    }

    fun requestOngoingNotification() {
        val needPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationHint = null
            NotificationHelper.showOngoing(context)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            serviceEnabled = AccessibilityUtils.isServiceEnabled(context)
            dryRun = config.dryRun
            strictMode = config.strictMode
            moduleEnabled = config.isModuleEnabled("skip_splash")
            shakeEnabled = config.isModuleEnabled("shake_fallback")
            hits = RecentHits.snapshot()
            lastEventAgo = ServiceHealth.lastEventAgoMs()
            delay(1_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JournalColors.cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回", color = JournalColors.inkSoft) }
            Text(
                text = "跳过开屏广告",
                style = MaterialTheme.typography.headlineSmall,
                color = JournalColors.ink,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 状态
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.card),
            border = BorderStroke(1.dp, JournalColors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (serviceEnabled) JournalColors.matcha else JournalColors.danger),
                    )
                    Text(
                        text = if (serviceEnabled) "无障碍服务 已开启" else "无障碍服务 未开启",
                        style = MaterialTheme.typography.titleMedium,
                        color = JournalColors.ink,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = healthSummary(lastEventAgo),
                    style = MaterialTheme.typography.bodySmall,
                    color = JournalColors.inkSoft,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (!serviceEnabled) {
                    Button(
                        onClick = { AccessibilityUtils.openAccessibilitySettings(context) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = JournalColors.caramel,
                            contentColor = JournalColors.onCaramel,
                        ),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                    ) {
                        Text("去开启无障碍服务")
                    }
                }
            }
        }

        // 判定与点击
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.card),
            border = BorderStroke(1.dp, JournalColors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                SectionLabel("判定与点击")
                SwitchRow(
                    title = "试运行",
                    subtitle = "只记录候选，不真正点击",
                    checked = dryRun,
                    onCheckedChange = { dryRun = it; config.dryRun = it },
                )
                if (!dryRun) {
                    WarnNote("试运行已关闭：命中后会直接点击，请先确认候选无误。")
                }
                SwitchRow(
                    title = "严格模式（默认）",
                    subtitle = "只用 App 专属规则；单个 App 可在应用列表覆盖",
                    checked = strictMode,
                    onCheckedChange = { strictMode = it; config.strictMode = it },
                )
                HorizontalDivider(color = JournalColors.border.copy(alpha = 0.7f))
                SectionLabel("功能模块")
                SwitchRow(
                    title = "跳过开屏广告",
                    subtitle = "关掉后不再判定，服务照常运行",
                    checked = moduleEnabled,
                    onCheckedChange = { moduleEnabled = it; config.setModuleEnabled("skip_splash", it) },
                )
                SwitchRow(
                    title = "摇一摇兜底",
                    subtitle = "规则没命中时点右上角，默认关",
                    checked = shakeEnabled,
                    onCheckedChange = { shakeEnabled = it; config.setModuleEnabled("shake_fallback", it) },
                )
            }
        }

        // 更多入口
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.card),
            border = BorderStroke(1.dp, JournalColors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                NavRow(
                    title = "应用列表",
                    subtitle = "每个 App 的开关与安全排除",
                    onClick = onOpenApps,
                )
                RowDivider()
                NavRow(
                    title = "规则导入",
                    subtitle = "预览并应用签名规则包",
                    onClick = onOpenRules,
                )
                RowDivider()
                NavRow(
                    title = "诊断与日志",
                    subtitle = "统计、最近事件与导出",
                    onClick = onOpenDiagnostics,
                )
                RowDivider()
                NavRow(
                    title = "常驻通知",
                    subtitle = "在通知栏显示守护状态",
                    onClick = { requestOngoingNotification() },
                )
            }
        }

        // 通知权限被拒绝时的可操作提示（Android 13+）
        notificationHint?.let { hint ->
            Column(modifier = Modifier.fillMaxWidth()) {
                WarnNote(hint)
                TextButton(onClick = { openNotificationSettings(context) }) {
                    Text("去通知设置", color = JournalColors.caramel)
                }
            }
        }

        OutlinedButton(
            onClick = {
                val stillEnabled = AccessibilityUtils.isServiceEnabled(context)
                serviceEnabled = stillEnabled
                if (!stillEnabled) DisconnectNotifier.show(context)
            },
            border = BorderStroke(1.dp, JournalColors.border),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("立即自检服务", color = JournalColors.inkSoft)
        }

        // 保活设置
        SectionLabel("保活设置（$manufacturer）")
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.paper.copy(alpha = 0.55f)),
            border = BorderStroke(1.dp, JournalColors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                guideSteps.forEachIndexed { index, step ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                step.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = JournalColors.ink,
                            )
                            Text(
                                step.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.inkSoft,
                            )
                        }
                        TextButton(onClick = { KeepAliveIntents.open(context, step.target) }) {
                            Text("去设置", color = JournalColors.caramel)
                        }
                    }
                }
            }
        }

        // 最近候选
        SectionLabel("最近候选（最新在前）")
        if (hits.isEmpty()) {
            Text(
                text = "暂无记录：打开一个带开屏广告的 App 试试。",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
            )
        } else {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    hits.take(8).forEach { hit -> HitRow(hit, timeFormat) }
                    TextButton(onClick = { RecentHits.clear(); hits = RecentHits.snapshot() }) {
                        Text("清空记录", color = JournalColors.inkSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = JournalColors.inkSoft,
        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = JournalColors.border.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun WarnNote(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(JournalColors.paper.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(JournalColors.yuzu),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hapticPress(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.bodyMedium,
            color = JournalColors.inkSoft,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = null) {
                onCheckedChange(!checked)
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JournalColors.card,
                checkedTrackColor = JournalColors.caramel,
                uncheckedThumbColor = JournalColors.card,
                uncheckedTrackColor = JournalColors.border,
            ),
        )
    }
}

@Composable
private fun HitRow(hit: Hit, timeFormat: SimpleDateFormat) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            text = "${timeFormat.format(Date(hit.timeMs))}  ${hit.packageName}",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
        )
        Text(
            text = "${hit.ruleId}  '${hit.nodeText ?: "-"}' → ${hit.result}" +
                if (hit.dryRun) "（试运行）" else "",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.ink,
        )
    }
}

private fun healthSummary(agoMs: Long): String = when {
    agoMs < 0 -> "尚未收到屏幕事件：开启无障碍后切换几个 App 试试。"
    agoMs < 60_000 -> "最近屏幕事件：${agoMs / 1000} 秒前"
    else -> "最近屏幕事件：${agoMs / 60_000} 分钟前（长时间无事件请检查省电/自启动设置）"
}

/**
 * 打开本 App 的通知设置页；部分 ROM 没有该页时退回系统设置首页。
 * 通知被拒绝后用户可以直接从这里改回来。
 */
private fun openNotificationSettings(context: Context) {
    val appNotificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(appNotificationSettings) }
        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } }
}
