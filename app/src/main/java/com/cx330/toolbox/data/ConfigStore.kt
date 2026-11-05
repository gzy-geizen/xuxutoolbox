package com.cx330.toolbox.data

import android.content.Context
import android.content.SharedPreferences

/** 单个 App 的严格模式策略；FOLLOW 表示跟随全局开关。 */
enum class AppStrictMode { FOLLOW, STRICT, LOOSE }

data class ConfigSnapshot(
    val enabled: Boolean,
    val dryRun: Boolean,
    val excludedPackages: Set<String>,
    val disabledPackages: Set<String>,
    val maxLaunchAgeMs: Long,
    val strictMode: Boolean = false,
    val strictPackages: Set<String> = emptySet(),
    val loosePackages: Set<String> = emptySet(),
) {
    /** 当前 App 是否走严格模式：单 App 覆盖优先，否则用全局默认。 */
    fun isStrictFor(packageName: String): Boolean = when {
        packageName in strictPackages -> true
        packageName in loosePackages -> false
        else -> strictMode
    }
}

/**
 * 配置存储（SharedPreferences）。
 *
 * 说明：计划书里写的是 DataStore，但 SharedPreferences 同样能做到「重启不丢 + 实时生效」，
 * 且迁移成本低；为避免在 S8 同时引入新依赖与 UI 重构，先保持 SharedPreferences，
 * 待功能稳定后再统一替换（记录在 docs/step-8-evidence.md 的偏差里）。
 *
 * 默认值刻意保守：
 * - enabled = true（服务已开启就工作）
 * - dryRun = true（只记录不点击）
 * - 金融/支付/输入法/系统设置默认排除（DEFAULT_EXCLUDED，用户可编辑）
 * - disabledPackages = 用户在「应用列表」里手动关掉的 App
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var dryRun: Boolean
        get() = prefs.getBoolean(KEY_DRY_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_DRY_RUN, value).apply()

    /** 严格模式默认值：只按显式规则匹配，不用通用「跳过」兜底。 */
    var strictMode: Boolean
        get() = prefs.getBoolean(KEY_STRICT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_MODE, value).apply()

    /** 强制走严格模式的 App（覆盖全局默认）。 */
    fun strictPackages(): Set<String> =
        prefs.getStringSet(KEY_STRICT_PACKAGES, emptySet()) ?: emptySet()

    /** 强制走通用兜底的 App（覆盖全局默认）。 */
    fun loosePackages(): Set<String> =
        prefs.getStringSet(KEY_LOOSE_PACKAGES, emptySet()) ?: emptySet()

    fun appStrictMode(packageName: String): AppStrictMode = when (packageName) {
        in strictPackages() -> AppStrictMode.STRICT
        in loosePackages() -> AppStrictMode.LOOSE
        else -> AppStrictMode.FOLLOW
    }

    fun setAppStrictMode(packageName: String, mode: AppStrictMode) {
        val strict = strictPackages().toMutableSet()
        val loose = loosePackages().toMutableSet()
        strict.remove(packageName)
        loose.remove(packageName)
        when (mode) {
            AppStrictMode.STRICT -> strict.add(packageName)
            AppStrictMode.LOOSE -> loose.add(packageName)
            AppStrictMode.FOLLOW -> Unit
        }
        prefs.edit()
            .putStringSet(KEY_STRICT_PACKAGES, strict)
            .putStringSet(KEY_LOOSE_PACKAGES, loose)
            .apply()
    }

    /** 安全排除名单（默认含支付/输入法/系统设置；可在界面里增删）。 */
    fun excludedPackages(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED, null) ?: DEFAULT_EXCLUDED

    fun addExcluded(packageName: String) {
        val next = excludedPackages().toMutableSet()
        if (next.add(packageName)) {
            prefs.edit().putStringSet(KEY_EXCLUDED, next).apply()
        }
    }

    fun removeExcluded(packageName: String) {
        val next = excludedPackages().toMutableSet()
        if (next.remove(packageName)) {
            prefs.edit().putStringSet(KEY_EXCLUDED, next).apply()
        }
    }

    /** 用户在应用列表里关掉的 App（与安全排除名单分开，便于界面区分）。 */
    var disabledPackages: Set<String>
        get() = prefs.getStringSet(KEY_DISABLED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DISABLED, value).apply()

    fun isAppEnabled(packageName: String): Boolean =
        packageName !in excludedPackages() && packageName !in disabledPackages

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        val next = disabledPackages.toMutableSet()
        if (enabled) next.remove(packageName) else next.add(packageName)
        disabledPackages = next
    }

    /** 用户关闭的功能模块（S12 模块化）。摇一摇兜底默认关闭（实验性）。 */
    var disabledModules: Set<String>
        get() = prefs.getStringSet(KEY_DISABLED_MODULES, null) ?: DEFAULT_DISABLED_MODULES
        set(value) = prefs.edit().putStringSet(KEY_DISABLED_MODULES, value).apply()

    fun isModuleEnabled(moduleId: String): Boolean = moduleId !in disabledModules

    fun setModuleEnabled(moduleId: String, enabled: Boolean) {
        val next = disabledModules.toMutableSet()
        if (enabled) next.remove(moduleId) else next.add(moduleId)
        disabledModules = next
    }

    fun snapshot(): ConfigSnapshot = ConfigSnapshot(
        enabled = enabled,
        dryRun = dryRun,
        excludedPackages = excludedPackages(),
        disabledPackages = disabledPackages,
        maxLaunchAgeMs = MAX_LAUNCH_AGE_MS,
        strictMode = strictMode,
        strictPackages = strictPackages(),
        loosePackages = loosePackages(),
    )

    companion object {
        private const val PREFS_NAME = "cx330_config"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DRY_RUN = "dry_run"
        private const val KEY_EXCLUDED = "excluded_packages"
        private const val KEY_DISABLED = "disabled_packages"
        private const val KEY_DISABLED_MODULES = "disabled_modules"
        private const val KEY_STRICT_MODE = "strict_mode"
        private const val KEY_STRICT_PACKAGES = "strict_packages"
        private const val KEY_LOOSE_PACKAGES = "loose_packages"
        private const val MAX_LAUNCH_AGE_MS = 8_000L

        /** 默认关闭的模块：摇一摇兜底属于实验特性，避免误点。 */
        val DEFAULT_DISABLED_MODULES: Set<String> = setOf("shake_fallback")

        /** 默认排除：支付/银行/输入法/系统设置与商店/密码类。 */
        val DEFAULT_EXCLUDED: Set<String> = setOf(
            "com.eg.android.AlipayGphone",
            "com.unionpay",
            "com.android.settings",
            "com.miui.securitycenter",
            "com.miui.packageinstaller",
            "com.android.packageinstaller",
            "com.android.vending",
            "com.xiaomi.market",
            "com.iflytek.inputmethod.miui",
            "com.sohu.inputmethod.sogou",
            "com.baidu.input",
            "com.google.android.inputmethod.latin",
        )
    }
}
