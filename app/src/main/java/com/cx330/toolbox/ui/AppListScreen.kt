package com.cx330.toolbox.ui

import com.cx330.toolbox.ui.theme.JournalColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.cx330.toolbox.R
import com.cx330.toolbox.data.AppEntry
import com.cx330.toolbox.data.AppListRepository
import com.cx330.toolbox.data.AppStrictMode
import com.cx330.toolbox.data.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


/**
 * 应用列表（奶油手账风）：搜索 + 每个 App 开关 + 安全默认排除标记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val config = remember { ConfigStore(context) }
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var refreshToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppListRepository.loadInstalledApps(context) }
        loading = false
    }

    val filtered = remember(apps, query, refreshToken) { AppListRepository.filter(apps, query) }

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
                text = "应用列表",
                style = MaterialTheme.typography.headlineSmall,
                color = JournalColors.ink,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("搜索应用名或包名") },
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = JournalColors.card,
                unfocusedContainerColor = JournalColors.card,
                focusedBorderColor = JournalColors.caramel,
                unfocusedBorderColor = JournalColors.border,
                focusedLabelColor = JournalColors.caramel,
                unfocusedLabelColor = JournalColors.inkSoft,
                cursorColor = JournalColors.caramel,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )

        Text(
            text = "${filtered.size} 个应用 · 关闭开关后不再跳过 · 点「严格模式」可单独设置",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.inkSoft,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )

        when {
            loading -> LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                color = JournalColors.caramel,
                trackColor = JournalColors.paper,
            )

            filtered.isEmpty() -> EmptyAppsState(onClear = { query = "" })

            else -> {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                    border = BorderStroke(1.dp, JournalColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = JournalColors.border.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 66.dp, end = 14.dp),
                                )
                            }
                            AppRow(app = app, config = config, onChanged = { refreshToken++ })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAppsState(onClear: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.home_mascot),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape),
            )
            Text(
                text = "没找到这个应用",
                style = MaterialTheme.typography.bodyMedium,
                color = JournalColors.inkSoft,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = onClear) { Text("清除搜索", color = JournalColors.caramel) }
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, config: ConfigStore, onChanged: () -> Unit) {
    val safetyExcluded = app.packageName in config.excludedPackages()
    var enabled by remember(app.packageName, safetyExcluded, config.disabledPackages) {
        mutableStateOf(!safetyExcluded && config.isAppEnabled(app.packageName))
    }
    var showModeDialog by remember(app.packageName) { mutableStateOf(false) }
    var appMode by remember(app.packageName, config.strictPackages(), config.loosePackages()) {
        mutableStateOf(config.appStrictMode(app.packageName))
    }

    val interaction = remember { MutableInteractionSource() }
    val toggle = {
        val next = !enabled
        enabled = next
        config.setAppEnabled(app.packageName, next)
        onChanged()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (safetyExcluded) JournalColors.paper.copy(alpha = 0.45f) else JournalColors.card,
            )
            .instantHaptic()
            .clickable(
                enabled = !safetyExcluded,
                interactionSource = interaction,
                indication = null,
            ) { toggle() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName = app.packageName)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (safetyExcluded) JournalColors.inkSoft else JournalColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (appMode) {
                    AppStrictMode.FOLLOW -> "严格模式：跟随默认"
                    AppStrictMode.STRICT -> "严格模式：严格"
                    AppStrictMode.LOOSE -> "严格模式：通用兜底"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (safetyExcluded) JournalColors.inkSoft else JournalColors.caramel,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(enabled = !safetyExcluded) { showModeDialog = true },
            )
            if (safetyExcluded) {
                Text(
                    text = "默认排除 · 不会自动跳过",
                    style = MaterialTheme.typography.bodySmall,
                    color = JournalColors.danger,
                )
            }
        }
        Switch(
            checked = enabled,
            enabled = !safetyExcluded,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = JournalColors.card,
                checkedTrackColor = JournalColors.caramel,
                uncheckedThumbColor = JournalColors.card,
                uncheckedTrackColor = JournalColors.border,
            ),
        )
    }

    if (showModeDialog) {
        AlertDialog(
            onDismissRequest = { showModeDialog = false },
            containerColor = JournalColors.card,
            titleContentColor = JournalColors.ink,
            textContentColor = JournalColors.inkSoft,
            title = { Text("${app.label} 的跳过策略") },
            text = {
                Column {
                    Text(
                        "严格模式只用 App 专属规则；通用模式额外允许通用「跳过」兜底。",
                        style = MaterialTheme.typography.bodySmall,
                        color = JournalColors.inkSoft,
                    )
                    ModeChoice("跟随默认（当前全局：${if (config.strictMode) "严格" else "通用"}）") {
                        config.setAppStrictMode(app.packageName, AppStrictMode.FOLLOW)
                        appMode = AppStrictMode.FOLLOW
                        showModeDialog = false
                        onChanged()
                    }
                    ModeChoice("强制严格（只用专属规则）") {
                        config.setAppStrictMode(app.packageName, AppStrictMode.STRICT)
                        appMode = AppStrictMode.STRICT
                        showModeDialog = false
                        onChanged()
                    }
                    ModeChoice("强制通用（允许「跳过」兜底）") {
                        config.setAppStrictMode(app.packageName, AppStrictMode.LOOSE)
                        appMode = AppStrictMode.LOOSE
                        showModeDialog = false
                        onChanged()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModeDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ModeChoice(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, color = JournalColors.ink, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager
                    .getApplicationIcon(packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JournalColors.paper),
        )
    }
}
