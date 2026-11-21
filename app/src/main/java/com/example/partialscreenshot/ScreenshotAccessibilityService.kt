package com.example.partialscreenshot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.Toast

class ScreenshotAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: ScreenshotAccessibilityService? = null
            private set

        @Volatile
        var pendingShot: Bitmap? = null

        val isRunning: Boolean
            get() = instance != null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pinnedView: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        removePinnedView()
    }

    override fun onDestroy() {
        super.onDestroy()
        removePinnedView()
        if (instance === this) {
            instance = null
        }
    }

    fun captureWithDelay() {
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        val delayMs = (ScreenshotPrefs.getDelaySeconds(this) * 1000).toLong().coerceAtLeast(150L)
        handler.postDelayed({ performCapture() }, delayMs)
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
                        pendingShot = softwareBitmap
                        val intent = Intent(this@ScreenshotAccessibilityService, CropActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                                    Intent.FLAG_ACTIVITY_NO_ANIMATION
                            )
                        }
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@ScreenshotAccessibilityService, "無法擷取螢幕畫面", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Toast.makeText(this@ScreenshotAccessibilityService, "截圖失敗 (錯誤碼: $errorCode)", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showPinnedBitmap(bitmap: Bitmap) {
        removePinnedView()
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 80
            y = 220
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            maxWidth = (resources.displayMetrics.widthPixels * 0.55f).toInt()
            maxHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()
            elevation = 16f
        }

        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false

        imageView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    wm.updateViewLayout(imageView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        removePinnedView()
                    }
                    true
                }
                else -> false
            }
        }

        pinnedView = imageView
        wm.addView(imageView, params)
        Toast.makeText(this, "已釘選於螢幕（輕觸可關閉）", Toast.LENGTH_SHORT).show()
    }

    private fun removePinnedView() {
        pinnedView?.let { v ->
            runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
            pinnedView = null
        }
    }
}
