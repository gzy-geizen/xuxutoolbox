package com.cx330.toolbox.core.inspect

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/**
 * 「捕获当前窗口」：节点树 JSON + 截图 PNG。
 *
 * 设计约束（用户拍板）：
 * - 只在用户手动点击时触发，不后台自动截屏；
 * - 支持「延迟 5 秒捕获」，方便切到目标 App 的广告页再抓；
 * - 服务需要声明 `canTakeScreenshot`（无障碍截图能力）；
 * - 文件只存应用私有目录，可一键清空/分享，不上传。
 */
object WindowCapture {

    private val prettyJson = Json { prettyPrint = true }

    @Volatile
    private var service: AccessibilityService? = null

    @Volatile
    private var activityNameProvider: (() -> String?)? = null

    @Volatile
    private var armed = false

    fun register(service: AccessibilityService, activityNameProvider: () -> String?) {
        this.service = service
        this.activityNameProvider = activityNameProvider
    }

    fun unregister(service: AccessibilityService) {
        if (this.service === service) {
            this.service = null
            this.activityNameProvider = null
        }
    }

    /** 准备捕获「下一个非本 App 的窗口」，用于来不及点通知按钮的广告。 */
    fun armNext() {
        armed = true
    }

    fun consumeArmed(): Boolean {
        if (armed) {
            armed = false
            return true
        }
        return false
    }

    fun dir(context: Context): File =
        File(context.filesDir, "inspect").apply { mkdirs() }

    fun captures(context: Context): List<File> =
        dir(context)
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** 删除一条抓取记录：连同同名 PNG 一起删；已生成的规则不受影响。 */
    fun deleteCapture(jsonFile: File) {
        CaptureFiles.delete(jsonFile)
    }

    fun clearAll(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    data class Result(
        val jsonFile: File? = null,
        val pngFile: File? = null,
        val nodeCount: Int = 0,
        val packageName: String? = null,
        val activityName: String? = null,
        val error: String? = null,
    )

    suspend fun capture(context: Context): Result = withContext(Dispatchers.Main) {
        val svc = service ?: return@withContext Result(error = "无障碍服务未连接，无法捕获")
        val root = svc.rootInActiveWindow
            ?: return@withContext Result(error = "当前没有可读取的窗口，请切到目标 App 再试")

        val tree = InspectTreeBuilder.build(root)
            ?: return@withContext Result(error = "节点树为空")

        val metrics = context.resources.displayMetrics
        val activityName = activityNameProvider?.invoke()
        val document = CaptureDocument(
            capturedAt = System.currentTimeMillis(),
            packageName = root.packageName?.toString(),
            activityName = activityName,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            densityDpi = metrics.densityDpi,
            nodeCount = InspectTreeBuilder.count(tree),
            root = tree,
        )

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val jsonFile = File(dir(context), "capture-$stamp.json")
        runCatching {
            jsonFile.writeText(prettyJson.encodeToString(document), Charsets.UTF_8)
        }.onFailure { return@withContext Result(error = "写节点树失败: ${it.message}") }

        val pngFile = File(dir(context), "capture-$stamp.png")
        val shot = takeScreenshot(svc, pngFile)
        Result(
            jsonFile = jsonFile,
            pngFile = if (shot) pngFile else null,
            nodeCount = document.nodeCount,
            packageName = document.packageName,
            activityName = activityName,
        )
    }

    /** 延迟捕获：给用户留出切到目标 App 广告页的时间（默认 5 秒）。 */
    suspend fun captureAfter(context: Context, delayMs: Long = 5_000L): Result {
        delay(delayMs)
        return capture(context)
    }

    private suspend fun takeScreenshot(svc: AccessibilityService, target: File): Boolean =
        suspendCancellableCoroutine { cont ->
            try {
                svc.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    Handler(Looper.getMainLooper())::post,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            val colorSpace = result.colorSpace
                            val bitmap = Bitmap.wrapHardwareBuffer(buffer, colorSpace)
                            val ok = if (bitmap == null) {
                                false
                            } else {
                                runCatching {
                                    FileOutputStream(target).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                }.isSuccess
                            }
                            buffer.close()
                            if (cont.isActive) cont.resume(ok)
                        }

                        override fun onFailure(errorCode: Int) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(false)
            }
        }
}
