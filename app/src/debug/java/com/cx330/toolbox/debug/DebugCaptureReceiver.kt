package com.cx330.toolbox.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cx330.toolbox.core.inspect.WindowCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 仅 debug 构建：用 adb 触发「捕获当前窗口」，便于验证「目标 App 在前台」时的抓取。
 *
 * 用法：
 * adb shell am broadcast -a com.cx330.toolbox.DEBUG_CAPTURE -p com.cx330.toolbox
 */
class DebugCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = WindowCapture.capture(context)
                Log.i(
                    "CX330_DEBUG_CAPTURE",
                    "capture: pkg=${result.packageName} act=${result.activityName} " +
                        "nodes=${result.nodeCount} error=${result.error}",
                )
            } finally {
                pending.finish()
            }
        }
    }
}
