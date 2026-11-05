package com.cx330.toolbox.core.pipeline

import com.cx330.toolbox.core.module.ModuleDecision

/**
 * 关键字快路径是否可以直接采信。
 *
 * 快路径只搜索 text/desc 中包含「跳过/关闭」的节点，看不到整屏图片广告依赖的
 * desc/面积/区域条件。如果该 App 有专属规则，而快路径只命中通用规则，直接执行
 * 通用规则会抢在专属规则之前误点——这时必须回退到完整快照。
 */
internal object FastPathPolicy {

    fun canUse(decisions: List<ModuleDecision>, hasAppRule: Boolean): Boolean =
        decisions.isNotEmpty() &&
            (!hasAppRule || decisions.any { !it.ruleId.startsWith("generic.") })
}
