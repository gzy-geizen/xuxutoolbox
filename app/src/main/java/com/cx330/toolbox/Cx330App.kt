package com.cx330.toolbox

import android.app.Application
import android.util.Log
import com.cx330.toolbox.service.KeepAliveService
import com.cx330.toolbox.util.AccessibilityUtils

/**
 * 应用入口（S14）。
 *
 * 进程一起来（无论用户点图标还是系统因为无障碍服务/广播拉起进程），
 * 只要无障碍开关还开着，就尝试把保活前台服务拉起来，减少「App 在后台被冻住」的时间窗。
 *
 * Android 12+ 不允许后台随意启动前台服务，所以通过 [KeepAliveService.start] 的
 * Boolean 返回值判断：失败不算崩溃，只记日志，后续由无障碍服务连接/周期健康检查兜底。
 */
class Cx330App : Application() {

    override fun onCreate() {
        super.onCreate()
        if (!AccessibilityUtils.isServiceEnabled(this)) {
            Log.i(TAG, "onCreate: accessibility disabled, skip keep-alive")
            return
        }
        val started = KeepAliveService.start(this)
        Log.i(TAG, "onCreate: accessibility enabled, keep-alive started=$started")
    }

    private companion object {
        const val TAG = "CX330"
    }
}
