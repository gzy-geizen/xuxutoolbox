package com.cx330.toolbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cx330.toolbox.ui.theme.JournalColors

private data class HelpSection(
    val title: String,
    val lines: List<String>,
)

private val helpSections = listOf(
    HelpSection(
        "先记住一句话",
        listOf(
            "你不是在“找按钮”，而是在告诉 App：广告页上哪个位置是跳过。",
            "有“跳过”文字 → 文本匹配 + 点击节点。",
            "跳过画在图片里 → 仅区域 + 按比例点击 + 相对屏幕。",
        ),
    ),
    HelpSection(
        "标准流程",
        listOf(
            "① 点「5 秒后抓取」；",
            "② 立刻切到有开屏广告的 App；",
            "③ 等 5 秒抓图，回到规则工坊点开这条记录；",
            "④ 在截图上点「跳过」的位置；",
            "⑤ 看下方提示，点「用这个位置建规则」；",
            "⑥ 检查匹配方式、动作、锚点，保存。",
        ),
    ),
    HelpSection(
        "抓取按钮怎么选",
        listOf(
            "5 秒后抓取：最常用。留时间切到目标 App。",
            "立即抓取：只抓当前屏幕，通常还是工坊自己；只有广告已在当前屏幕时才用。",
            "高级：从节点列表选：截图点不中时，手动从候选里选。",
        ),
    ),
    HelpSection(
        "点完截图，下方提示怎么理解",
        listOf(
            "「命中带跳过/关闭字样的按钮」→ 最理想，用文本包含「跳过」+ 点击节点。",
            "「命中一个可点击控件」→ 可能是广告图本身；不要直接点节点，改用仅区域 + 按比例点击。",
            "「命中整块广告图片」→ 纯图片广告，仅区域 + 按比例点击 + 相对屏幕。",
            "「命中大块容器但没有可写文字/描述/ID」→ 12306 常见；同样用仅区域 + 按比例点击 + 相对屏幕。",
            "「未命中节点，按整屏比例」→ 风险最大，保存后一定先试运行。",
        ),
    ),
    HelpSection(
        "确认规则页：匹配方式",
        listOf(
            "文本包含：最稳，填「跳过」，能匹配「跳过 5」「跳过广告」。",
            "文本精确：只适合文字完全固定的按钮。",
            "描述包含：图片广告描述含「广告/跳过」时用；「广告」太宽，谨慎。",
            "控件 ID：有稳定 ID 的真实按钮；不要用 decor_content_parent / content 这类系统 ID。",
            "仅区域（top_right）：纯图片广告、跳过画在右上角时用，匹配值留空。",
        ),
    ),
    HelpSection(
        "确认规则页：动作与比例",
        listOf(
            "点击节点：有文字或 ID、且可点击的真实按钮。",
            "按比例点击：图片广告、节点不可点、或没有可定位节点时。",
            "锚点：仅区域匹配时，一定选「相对屏幕」。",
            "X/Y 比例：0=最左/最上，1=最右/最下；右上角跳过通常约 0.85 / 0.07。",
            "节点没有文字也没有 ID 时，「点击节点」会被禁用，这是正常的。",
        ),
    ),
    HelpSection(
        "12306 这类纯图片广告",
        listOf(
            "抓图后，点截图上右上角“跳过”两个字的位置。",
            "匹配方式：仅区域（top_right）。",
            "动作：按比例点击；锚点：相对屏幕。",
            "保存后开试运行，冷启动 12306，看日志；出现 dry_run 再关试运行实测。",
            "如果这次 12306 没有开屏广告，先别判断失败，广告是频次下发的。",
        ),
    ),
    HelpSection(
        "保存后怎么验证",
        listOf(
            "先开「试运行（只记录，不点击）」。",
            "冷启动目标 App，让广告出现。",
            "到「诊断与日志」看最近事件。",
            "dry_run：命中成功但没真点；clicked/gesture：真实点击成功。",
            "node_not_found / click_failed：匹配到了但点不到，改用按比例点击。",
            "gated:*：被冷却或次数限制，不是规则失败。",
            "no_candidate：规则没匹配上，检查匹配值和区域。",
        ),
    ),
    HelpSection(
        "安全习惯",
        listOf(
            "先试运行，后真点。",
            "一次只测一条用户规则，其他关掉。",
            "规则保存后立即生效；保存前检查匹配值、动作、锚点。",
            "发现误点先关规则开关，再删除。",
            "仅区域规则只适合开屏广告那几秒，不要长期开着点首页广告。",
        ),
    ),
)

@Composable
fun RuleStudioHelpScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JournalColors.cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "照这个顺序做，不用看懂节点树。",
            style = MaterialTheme.typography.bodyMedium,
            color = JournalColors.inkSoft,
        )
        helpSections.forEach { section ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = JournalColors.card),
                border = BorderStroke(1.dp, JournalColors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        section.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = JournalColors.ink,
                    )
                    section.lines.forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = JournalColors.inkSoft,
                        )
                    }
                }
            }
        }
        Text(
            "口诀：有跳过文字 → 文本包含 + 点击节点；纯图片 → 仅区域 + 按比例点击 + 相对屏幕。",
            style = MaterialTheme.typography.bodySmall,
            color = JournalColors.caramel,
            modifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
