package com.cx330.fakead

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * 假的「开屏广告」页：
 * - 全屏、深色背景；
 * - 右上角一个可点击的「跳过 5」文字（带 contentDescription「跳过广告」）；
 * - 点击后关闭自己。
 *
 * 它用来验证 cx330 toolbox 的判定流水线：是否能在 8 秒窗口内识别出「跳过」候选。
 */
class FakeAdActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC101010"))
        }

        val skip = TextView(this).apply {
            text = "跳过 5"
            textSize = 20f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#66000000"))
            setPadding(30, 16, 30, 16)
            contentDescription = "跳过广告"
            isClickable = true
            setOnClickListener { finish() }
        }

        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 160
            rightMargin = 40
        }
        root.addView(skip, params)

        val adLabel = TextView(this).apply {
            text = "这是测试桩广告页\n用于验证自动跳过逻辑"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        root.addView(
            adLabel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER },
        )

        setContentView(root)
    }
}
