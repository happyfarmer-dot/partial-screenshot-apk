package com.example.partialscreenshot

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ScreenshotAccessibilityService? = null
            private set

        val isRunning: Boolean
            get() = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var overlayView: ScreenshotOverlayView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed; triggered on demand
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
        if (instance === this) {
            instance = null
        }
    }

    fun captureWithDelay() {
        val delayMs = (ScreenshotPrefs.getDelaySeconds(this) * 1000).toLong().coerceAtLeast(50L)
        handler.postDelayed({
            performCapture()
        }, delayMs)
    }

    private fun performCapture() {
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val hardwareBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace
                    val hwBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                    val softwareBitmap = hwBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()
                    hwBitmap?.recycle()

                    if (softwareBitmap != null) {
                        showOverlay(softwareBitmap)
                    } else {
                        Toast.makeText(
                            this@ScreenshotAccessibilityService,
                            "無法擷取螢幕畫面",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Toast.makeText(
                        this@ScreenshotAccessibilityService,
                        "截圖失敗 (錯誤碼: $errorCode)",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun showOverlay(bitmap: Bitmap) {
        removeOverlay()
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val view = ScreenshotOverlayView(
            context = this,
            screenshot = bitmap,
            cropMode = ScreenshotPrefs.getCropMode(this),
            glowColor = ScreenshotPrefs.getGlowColor(this),
            onDismiss = { removeOverlay() }
        )
        overlayView = view
        windowManager.addView(view, params)
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            runCatching {
                val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
                windowManager.removeView(view)
            }
            overlayView = null
        }
    }
}
