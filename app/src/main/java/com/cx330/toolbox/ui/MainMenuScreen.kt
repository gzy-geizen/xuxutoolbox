package com.cx330.toolbox.ui

import com.cx330.toolbox.ui.theme.JournalColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cx330.toolbox.R
import com.cx330.toolbox.core.debug.Hit
import com.cx330.toolbox.core.debug.RecentHits
import com.cx330.toolbox.data.ConfigStore
import com.cx330.toolbox.util.AccessibilityUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext


/**
 * 主页 = 手账目录式工具总览。
 *
 * 工具卡片完全由 [ToolRegistry] 驱动：新增工具只要在 ToolCatalog 里登记一条，
 * 这里就会自动多一张卡（不写死任何工具名/工具 id）。
 * 装饰：胶带、邮票/小票质感卡片、爪印、敬请期待占位。
 */
@Composable
fun MainMenuScreen(
    onOpenTool: (String) -> Unit,
    onOpenStudio: () -> Unit,
    onOpenInfo: () -> Unit,
    registry: ToolRegistry = ToolRegistry(),
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.2.4"
    }
    val config = remember { ConfigStore(context) }
    var serviceEnabled by remember { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }
    var dryRun by remember { mutableStateOf(config.dryRun) }
    var moduleEnabled by remember { mutableStateOf(config.isModuleEnabled(BuiltinTools.SKIP_SPLASH_ID)) }
    var hits by remember { mutableStateOf(RecentHits.snapshot()) }
    var hitLabels by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val hitTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateText = remember { SimpleDateFormat("M月d日 · EEE", Locale.CHINA).format(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            serviceEnabled = AccessibilityUtils.isServiceEnabled(context)
            dryRun = config.dryRun
            moduleEnabled = config.isModuleEnabled(BuiltinTools.SKIP_SPLASH_ID)
            hits = RecentHits.snapshot()
            delay(1_000L)
        }
    }

    LaunchedEffect(hits) {
        val packages = hits.take(3).map { it.packageName }.distinct()
        if (packages.isNotEmpty()) {
            hitLabels = withContext(Dispatchers.IO) {
                packages.mapNotNull { pkg ->
                    runCatching {
                        val info = context.packageManager.getApplicationInfo(pkg, 0)
                        pkg to context.packageManager.getApplicationLabel(info).toString()
                    }.getOrNull()
                }.toMap()
            }
        }
    }

    // 卡片状态用轮询出来的状态值，保证模块开关/试运行变化能即时刷新。
    val isModuleOn: (String) -> Boolean = { moduleId ->
        if (moduleId == BuiltinTools.SKIP_SPLASH_ID) moduleEnabled else config.isModuleEnabled(moduleId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JournalColors.cream)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.home_mascot),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
            )
            Column {
                Text("须须工具箱", style = MaterialTheme.typography.headlineSmall, color = JournalColors.ink)
                Text(dateText, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
            }
        }

        // 工具卡：按注册表分组渲染，新增工具会自动出现在这里。
        registry.tools.groupBy { it.category }.forEach { (category, tools) ->
            SectionTitle(category)
            tools.forEachIndexed { index, tool ->
                ToolEntryCard(
                    tool = tool,
                    statusText = toolStatusText(
                        tool = tool,
                        serviceEnabled = serviceEnabled,
                        dryRun = dryRun,
                        isModuleEnabled = isModuleOn,
                    ),
                    showTape = index == 0,
                    onClick = { onOpenTool(tool.id) },
                )
            }
        }

        // 规则工坊：用户自助抓节点 + 写规则
        val studioInteraction = remember { MutableInteractionSource() }
        Card(
            onClick = onOpenStudio,
            interactionSource = studioInteraction,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.card),
            border = BorderStroke(1.dp, JournalColors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier
                .fillMaxWidth()
                .hapticPress(studioInteraction),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StampBadge("规则")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text("规则工坊", style = MaterialTheme.typography.titleMedium, color = JournalColors.ink)
                    Text(
                        "抓当前广告页，点节点自己写跳过规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                    )
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = JournalColors.caramel)
            }
        }

        // 敬请期待占位：虚线小票
        DashedCard {
            Column {
                Text(
                    text = "敬请期待",
                    style = MaterialTheme.typography.titleSmall,
                    color = JournalColors.inkSoft,
                )
                Text(
                    text = "下一个顺手的小工具，正在路上。",
                    style = MaterialTheme.typography.bodySmall,
                    color = JournalColors.inkSoft.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        if (hits.isNotEmpty()) {
            DashedCard {
                Column {
                    Text(
                        text = "最近跳过",
                        style = MaterialTheme.typography.titleSmall,
                        color = JournalColors.inkSoft,
                    )
                    hits.take(3).forEach { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = hitTimeFormat.format(Date(hit.timeMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.inkSoft,
                            )
                            Text(
                                text = hitLabels[hit.packageName] ?: hit.packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = JournalColors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                            )
                            Text(
                                text = hitResultLabel(hit),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hit.dryRun) JournalColors.inkSoft else JournalColors.caramel,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .instantHaptic()
                .clickable { onOpenInfo() }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "关于与隐私",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "›",
                style = MaterialTheme.typography.bodyMedium,
                color = JournalColors.inkSoft,
            )
        }
        HorizontalDivider(color = JournalColors.border.copy(alpha = 0.7f))
        Text(
            text = "须须工具箱 v$versionName · 本地运行 · 不联网",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** 分区标题：一小段胶带 + 分区名（如「工具」）。 */
@Composable
private fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .offset(y = 1.dp)
                .rotate(-2f)
                .size(width = 34.dp, height = 12.dp)
                .background(JournalColors.tape.copy(alpha = 0.75f), RoundedCornerShape(2.dp))
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = JournalColors.inkSoft,
        )
    }
}

/** 一张工具卡：小票质感 + 印章 + 实时状态，点击只回传工具 id。 */
@Composable
private fun ToolEntryCard(
    tool: ToolDescriptor,
    statusText: String,
    showTape: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember(tool.id) { MutableInteractionSource() }
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            interactionSource = interaction,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.card),
            border = BorderStroke(1.dp, JournalColors.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .hapticPress(interaction),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StampBadge(tool.badge)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                ) {
                    Text(tool.title, style = MaterialTheme.typography.titleMedium, color = JournalColors.ink)
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = JournalColors.inkSoft)
                }
                Text("›", style = MaterialTheme.typography.headlineSmall, color = JournalColors.caramel)
            }
        }
        if (showTape) {
            Box(
                modifier = Modifier
                    .padding(start = 18.dp)
                    .offset(y = (-8).dp)
                    .rotate(-4f)
                    .size(width = 58.dp, height = 18.dp)
                    .background(JournalColors.tape.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun StampBadge(text: String) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(JournalColors.paper)
            .border(1.dp, JournalColors.caramel.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.caramel,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun DashedCard(content: @Composable () -> Unit) {
    val dash = remember {
        PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(JournalColors.paper.copy(alpha = 0.55f))
            .drawBehind {
                drawRoundRect(
                    color = JournalColors.border,
                    style = Stroke(width = 1.2.dp.toPx(), pathEffect = dash),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .padding(16.dp),
    ) {
        content()
    }
}

private fun hitResultLabel(hit: Hit): String = when {
    hit.dryRun -> "试运行"
    hit.result.startsWith("gesture") || hit.result.startsWith("click") -> "已跳过"
    else -> hit.result
}
