package com.cx330.toolbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cx330.toolbox.ui.theme.JournalColors

/**
 * 工具目录（硬要求①：新增工具不用改主界面 / 主流程）。
 *
 * 新增一个工具只需要在这里登记一条 [ToolDescriptor]：
 * - 主页卡片由 [ToolRegistry] 自动生成，MainMenuScreen 不再写死任何工具；
 * - 导航统一走「按 id 打开」（MainActivity 的 Screen.Tool(id)），不用再加分支；
 * - 需要自定义详情页时，只在 [ToolDetailHost] 里加一条渲染分支；没有专属页面的
 *   工具会落到通用详情页 [GenericToolDetail]，所以「只注册」就已经能点进去。
 *
 * 这个文件里只放 UI 的「目录/路由」数据，不碰服务、规则执行与通知实现。
 */

/** 工具所属的默认分区名（主页按 category 分组显示）。 */
const val DEFAULT_TOOL_CATEGORY: String = "工具"

/**
 * 一个工具的描述。
 *
 * @param id 稳定标识，导航（Screen.Tool(id)）与模块开关都用它，改了会断历史状态。
 * @param title 卡片标题。
 * @param subtitle 一句话说明。
 * @param order 排序，小的在前。
 * @param category 主页分区名。
 * @param moduleId 关联的功能模块 id（ConfigStore.isModuleEnabled）；null = 没有模块开关。
 * @param badge 卡片左侧印章短标签，默认取标题前两个字。
 */
data class ToolDescriptor(
    val id: String,
    val title: String,
    val subtitle: String,
    val order: Int = 0,
    val category: String = DEFAULT_TOOL_CATEGORY,
    val moduleId: String? = null,
    val badge: String = title.take(2),
) {
    init {
        require(id.isNotBlank()) { "工具 id 不能为空" }
        require(title.isNotBlank()) { "工具标题不能为空" }
    }
}

/**
 * 工具注册表：主页卡片和按 id 路由都从这里取。
 *
 * 只依赖纯数据，方便 JVM 单测直接构造「真实工具 + FakeTool」的组合来验证契约。
 */
class ToolRegistry(tools: List<ToolDescriptor> = BuiltinTools.tools) {

    /** 排好序的工具列表：order 小的在前，同 order 按 id 稳定排序。 */
    val tools: List<ToolDescriptor> = tools.sortedWith(compareBy({ it.order }, { it.id }))

    /** 按 id 找工具；找不到返回 null（调用方应退回主页）。 */
    fun find(id: String): ToolDescriptor? = this.tools.firstOrNull { it.id == id }
}

/** 主页一张工具卡片需要的数据（纯数据，单测可直接断言卡片数量与内容）。 */
data class ToolCardModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val category: String,
    val enabled: Boolean,
)

/**
 * 把注册表翻译成主页卡片列表。
 *
 * 主页只渲染这个函数的返回值，所以「往 registry 里加一条」就等于「主页多一张卡」，
 * 不需要改 MainMenuScreen 的布局代码。
 */
fun buildToolCards(
    registry: ToolRegistry,
    isModuleEnabled: (String) -> Boolean,
): List<ToolCardModel> = registry.tools.map { tool ->
    ToolCardModel(
        id = tool.id,
        title = tool.title,
        subtitle = tool.subtitle,
        badge = tool.badge,
        category = tool.category,
        enabled = tool.moduleId?.let(isModuleEnabled) ?: true,
    )
}

/**
 * 卡片副标题：有模块开关的工具显示实时状态，其余显示描述。
 *
 * 只在「跳过开屏广告」上有专属文案；新增工具不传 moduleId 时会直接显示 subtitle。
 */
fun toolStatusText(
    tool: ToolDescriptor,
    serviceEnabled: Boolean,
    dryRun: Boolean,
    isModuleEnabled: (String) -> Boolean,
): String {
    val moduleId = tool.moduleId ?: return tool.subtitle
    if (!isModuleEnabled(moduleId)) return "暂时停用"
    if (moduleId == BuiltinTools.SKIP_SPLASH_ID) {
        return when {
            !serviceEnabled -> "还没开启无障碍，点这里去设置"
            dryRun -> "试运行中 · 只看不点"
            else -> "守护中 · 自动跳过"
        }
    }
    return tool.subtitle
}

/** 内置工具登记处：新增工具就加一条到这里（有专属详情页再改 [ToolDetailHost]）。 */
object BuiltinTools {

    /** 「跳过开屏广告」的稳定 id（与 ConfigStore 的模块 id 一致）。 */
    const val SKIP_SPLASH_ID: String = "skip_splash"

    /** 第一个工具：跳过开屏广告，详情页复用 [SkipSplashDetailScreen]。 */
    val skipSplash = ToolDescriptor(
        id = SKIP_SPLASH_ID,
        title = "跳过开屏广告",
        subtitle = "自动识别并点掉开屏页的「跳过」，默认只看不点",
        order = 0,
        category = DEFAULT_TOOL_CATEGORY,
        moduleId = SKIP_SPLASH_ID,
        badge = "跳过",
    )

    /** 已注册的内置工具（顺序由各自的 order 决定）。 */
    val tools: List<ToolDescriptor> = listOf(skipSplash)
}

/** 工具详情页可用的导航动作，由 MainActivity 注入；具体工具不直接接触 Screen。 */
data class ToolNavigation(
    val onBack: () -> Unit,
    val onOpenApps: () -> Unit = {},
    val onOpenRules: () -> Unit = {},
    val onOpenDiagnostics: () -> Unit = {},
)

/**
 * 详情页总入口：按工具 id 分发。
 *
 * 没有专属页面的工具会走 [GenericToolDetail]（占位页），所以新增工具只要注册就能点开；
 * 以后给某个工具做专属页面时，只在这里加一条分支，主流程（MainActivity / 主页）不动。
 */
@Composable
fun ToolDetailHost(tool: ToolDescriptor, navigation: ToolNavigation) {
    if (tool.id == BuiltinTools.SKIP_SPLASH_ID) {
        SkipSplashDetailScreen(
            onBack = navigation.onBack,
            onOpenApps = navigation.onOpenApps,
            onOpenRules = navigation.onOpenRules,
            onOpenDiagnostics = navigation.onOpenDiagnostics,
        )
    } else {
        GenericToolDetail(tool = tool, navigation = navigation)
    }
}

/** 还没有专属页面的工具使用的通用详情页（奶油手账风占位）。 */
@Composable
private fun GenericToolDetail(tool: ToolDescriptor, navigation: ToolNavigation) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JournalColors.cream)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = navigation.onBack) {
                Text("← 返回", color = JournalColors.inkSoft)
            }
            Text(
                text = tool.title,
                style = MaterialTheme.typography.headlineSmall,
                color = JournalColors.ink,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = JournalColors.card),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JournalColors.ink,
                )
                Text(
                    text = "这个工具已经注册好了，专属界面还在准备中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = JournalColors.inkSoft,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
