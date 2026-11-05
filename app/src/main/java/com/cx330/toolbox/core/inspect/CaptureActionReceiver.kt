package com.cx330.toolbox.core.inspect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cx330.toolbox.notify.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 常驻通知里的「捕获窗口」按钮接收器。
 *
 * 用法：目标 App 出现开屏广告时，下拉通知栏点「捕获窗口」，
 * 不用切回工具箱，抓到的是广告所在窗口。
 */
class CaptureActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_CAPTURE) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 等通知栏收起，避免抓到 SystemUI
                delay(400)
                val result = WindowCapture.capture(context)
                val text = result.error
                    ?: "已捕获 ${result.nodeCount} 个节点（${result.packageName ?: "未知包名"}）"
                Log.i(TAG, "notification capture: $text")
                NotificationHelper.notifyCaptureResult(context, text)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "CX330_CAPTURE_ACTION"
    }
}
