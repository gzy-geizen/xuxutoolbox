package com.cx330.toolbox.ui

import com.cx330.toolbox.ui.theme.JournalColors

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cx330.toolbox.R


/**
 * 关于与隐私（奶油手账内页风）。
 */
@Composable
fun InfoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.2.4"

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
                text = "关于与隐私",
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
            // 品牌卡
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.home_mascot),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                    )
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = "须须工具箱 $version",
                            style = MaterialTheme.typography.titleMedium,
                            color = JournalColors.ink,
                        )
                        Text(
                            text = "个人自用 · 不联网 · 不上传",
                            style = MaterialTheme.typography.bodySmall,
                            color = JournalColors.inkSoft,
                        )
                    }
                }
            }

            InfoSection("隐私政策") {
                "① 本应用不申请网络权限，不联网、不上传任何数据。\n" +
                    "② 配置与日志只保存在本机；导出由你手动选择位置。\n" +
                    "③ 应用列表、规则、统计只在本机使用；卸载即删除。\n" +
                    "④ 不读取输入内容、不记录账号密码、不后台截屏。\n" +
                    "⑤ 布局检查的节点树/截图只存本机，可在诊断页一键清空。"
            }

            InfoSection("无障碍用途说明") {
                "本应用使用无障碍服务仅用于：读取当前窗口的控件文字/描述，判断是否存在开屏广告的「跳过 / 关闭」按钮，并在你允许时执行点击。\n" +
                    "不会用于收集信息或自动操作其他界面；你可以随时在系统设置中关闭该服务。"
            }

            InfoSection("免责声明") {
                "本应用为个人学习与自用工具，按「现状」提供，不保证适配所有应用与广告形态。\n" +
                    "默认开启试运行（只记录不点击）；请先确认最近候选无误后再关闭试运行。\n" +
                    "因使用本工具产生的误点、账号或财产损失，由使用者自行承担。"
            }

            InfoSection("开源许可") {
                "使用 Kotlin、Jetpack Compose、kotlinx.serialization（Apache-2.0 等开源许可）。\n" +
                    "规则文件为纯文本 JSON，可自行审阅；导入规则需签名（SHA256withRSA）。"
            }

            Text(
                text = "— 须须工具箱 · 手账里的小工具 —",
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun InfoSection(title: String, body: () -> String) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = JournalColors.paper.copy(alpha = 0.6f)),
        border = BorderStroke(1.dp, JournalColors.border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = JournalColors.ink)
            Text(
                text = body(),
                style = MaterialTheme.typography.bodySmall,
                color = JournalColors.inkSoft,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
