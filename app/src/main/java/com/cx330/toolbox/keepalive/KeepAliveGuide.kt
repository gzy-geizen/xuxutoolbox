package com.cx330.toolbox.keepalive

/** 保活指引里「去设置」按钮要跳转的目标。 */
enum class DeepLinkTarget {
    BATTERY_OPTIMIZATION,
    APP_DETAILS,
    ACCESSIBILITY,
    AUTOSTART,
}

/** 一条保活指引：标题 + 说明 + 跳转目标。 */
data class GuideStep(
    val title: String,
    val detail: String,
    val target: DeepLinkTarget,
)

/**
 * 按厂商给出保活步骤（纯逻辑，可单测）。
 *
 * 依据：HyperOS/ColorOS/OriginOS 等会在第三方 App 前台时冻结普通后台进程，
 * 导致无障碍服务收不到事件；这里把「省电无限制 / 自启动 / 任务锁定」做成引导。
 */
object KeepAliveGuide {

    fun stepsFor(manufacturer: String): List<GuideStep> {
        val vendor = manufacturer.lowercase()
        return when {
            vendor.contains("xiaomi") || vendor.contains("redmi") -> miuiSteps()
            vendor.contains("oppo") || vendor.contains("oneplus") || vendor.contains("realme") -> colorOsSteps()
            vendor.contains("vivo") || vendor.contains("iqoo") -> originSteps()
            vendor.contains("huawei") || vendor.contains("honor") -> harmonySteps()
            else -> genericSteps()
        }
    }

    private fun miuiSteps(): List<GuideStep> = listOf(
        GuideStep(
            title = "省电策略设为「无限制」",
            detail = "应用信息 → 省电策略 → 无限制。否则第三方 App 在前台时，系统会冻结本进程。",
            target = DeepLinkTarget.APP_DETAILS,
        ),
        GuideStep(
            title = "允许自启动",
            detail = "应用信息 → 自启动 → 允许。部分版本在「安全中心 → 应用管理」里。",
            target = DeepLinkTarget.AUTOSTART,
        ),
        GuideStep(
            title = "锁定最近任务",
            detail = "最近任务界面 → 下拉 cx330 toolbox 卡片 → 点锁图标。",
            target = DeepLinkTarget.APP_DETAILS,
        ),
        GuideStep(
            title = "开启无障碍服务",
            detail = "系统设置 → 无障碍 → 已下载的应用 → cx330 toolbox 自动跳过。",
            target = DeepLinkTarget.ACCESSIBILITY,
        ),
        GuideStep(
            title = "忽略电池优化（可选）",
            detail = "把 App 加入电池优化白名单，减少深度休眠时被回收。",
            target = DeepLinkTarget.BATTERY_OPTIMIZATION,
        ),
    )

    private fun colorOsSteps(): List<GuideStep> = listOf(
        GuideStep(
            title = "允许自启动",
            detail = "手机管家 → 权限隐私 → 自启动管理 → 允许 cx330 toolbox。",
            target = DeepLinkTarget.AUTOSTART,
        ),
        GuideStep(
            title = "省电策略设为「允许后台运行」",
            detail = "设置 → 电池 → 应用耗电管理 → cx330 toolbox → 允许后台运行。",
            target = DeepLinkTarget.BATTERY_OPTIMIZATION,
        ),
        GuideStep(
            title = "开启无障碍服务",
            detail = "设置 → 无障碍 → 已下载的服务 → cx330 toolbox。",
            target = DeepLinkTarget.ACCESSIBILITY,
        ),
    )

    private fun originSteps(): List<GuideStep> = listOf(
        GuideStep(
            title = "允许后台高耗电",
            detail = "设置 → 电池 → 后台耗电管理 → cx330 toolbox → 允许后台高耗电。",
            target = DeepLinkTarget.BATTERY_OPTIMIZATION,
        ),
        GuideStep(
            title = "允许自启动",
            detail = "i 管家 → 应用管理 → 权限管理 → 自启动 → 允许。",
            target = DeepLinkTarget.AUTOSTART,
        ),
        GuideStep(
            title = "开启无障碍服务",
            detail = "设置 → 快捷与辅助 → 无障碍 → 已下载的服务。",
            target = DeepLinkTarget.ACCESSIBILITY,
        ),
    )

    private fun harmonySteps(): List<GuideStep> = listOf(
        GuideStep(
            title = "允许自启动与后台活动",
            detail = "设置 → 应用和服务 → 应用启动管理 → cx330 toolbox → 手动管理，全部允许。",
            target = DeepLinkTarget.AUTOSTART,
        ),
        GuideStep(
            title = "开启无障碍服务",
            detail = "设置 → 辅助功能 → 无障碍 → 已安装的应用。",
            target = DeepLinkTarget.ACCESSIBILITY,
        ),
    )

    private fun genericSteps(): List<GuideStep> = listOf(
        GuideStep(
            title = "关闭电池优化",
            detail = "设置 → 电池 → 电池优化 → 找到 cx330 toolbox → 不优化。",
            target = DeepLinkTarget.BATTERY_OPTIMIZATION,
        ),
        GuideStep(
            title = "开启无障碍服务",
            detail = "设置 → 无障碍 → 已下载的服务 → cx330 toolbox。",
            target = DeepLinkTarget.ACCESSIBILITY,
        ),
        GuideStep(
            title = "允许后台运行",
            detail = "应用信息 → 电池/后台 → 允许后台活动。",
            target = DeepLinkTarget.APP_DETAILS,
        ),
    )
}
