package com.example.partialscreenshot

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Environment
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScreenshotOverlayView(
    context: Context,
    private val screenshot: Bitmap,
    private val cropMode: CropMode,
    private val glowColor: Int,
    private val onDismiss: () -> Unit
) : View(context) {

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f
    private var isDragging = false

    private val lassoPath = Path()
    private val lassoPoints = mutableListOf<PointF>()

    private val dimPaint = Paint().apply {
        color = Color.argb(110, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val dashedRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }

    private val whiteRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val lassoGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = glowColor
        style = Paint.Style.STROKE
        strokeWidth = 18f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)
    }

    private val lassoCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        setShadowLayer(6f, 0f, 2f, Color.BLACK)
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(screenshot, 0f, 0f, null)

        val saveLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (isDragging) {
            when (cropMode) {
                CropMode.DASHED_RECT, CropMode.WHITE_RECT -> {
                    val rect = currentRect()
                    canvas.drawRect(rect, clearPaint)
                    canvas.restoreToCount(saveLayer)
                    val borderPaint = if (cropMode == CropMode.DASHED_RECT) dashedRectPaint else whiteRectPaint
                    canvas.drawRect(rect, borderPaint)
                }
                CropMode.TRANSPARENT_LASSO -> {
                    canvas.drawPath(lassoPath, clearPaint)
                    canvas.restoreToCount(saveLayer)
                    canvas.drawPath(lassoPath, lassoGlowPaint)
                    canvas.drawPath(lassoPath, lassoCorePaint)
                }
            }
        } else {
            canvas.restoreToCount(saveLayer)
            canvas.drawText("拖曳框選截圖區域 • 輕觸取消", width / 2f, height * 0.12f, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                currentX = event.x
                currentY = event.y
                isDragging = true
                lassoPath.reset()
                lassoPath.moveTo(startX, startY)
                lassoPoints.clear()
                lassoPoints.add(PointF(startX, startY))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                currentX = event.x
                currentY = event.y
                lassoPath.lineTo(currentX, currentY)
                lassoPoints.add(PointF(currentX, currentY))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                if (cropMode == CropMode.TRANSPARENT_LASSO) {
                    lassoPath.close()
                    handleLassoCrop()
                } else {
                    handleRectCrop()
                }
                onDismiss()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun currentRect(): RectF {
        return RectF(
            min(startX, currentX),
            min(startY, currentY),
            max(startX, currentX),
            max(startY, currentY)
        )
    }

    private fun handleRectCrop() {
        val rect = currentRect()
        if (rect.width() < 24f || rect.height() < 24f) {
            return
        }
        val left = rect.left.toInt().coerceIn(0, screenshot.width - 1)
        val top = rect.top.toInt().coerceIn(0, screenshot.height - 1)
        val right = rect.right.toInt().coerceIn(left + 1, screenshot.width)
        val bottom = rect.bottom.toInt().coerceIn(top + 1, screenshot.height)

        val cropped = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top)
        saveBitmap(cropped)
    }

    private fun handleLassoCrop() {
        if (lassoPoints.size < 5) return
        val bounds = RectF()
        lassoPath.computeBounds(bounds, true)
        if (bounds.width() < 24f || bounds.height() < 24f) return

        val left = bounds.left.toInt().coerceIn(0, screenshot.width - 1)
        val top = bounds.top.toInt().coerceIn(0, screenshot.height - 1)
        val right = bounds.right.toInt().coerceIn(left + 1, screenshot.width)
        val bottom = bounds.bottom.toInt().coerceIn(top + 1, screenshot.height)

        val outWidth = right - left
        val outHeight = bottom - top
        val output = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val shiftedPath = Path(lassoPath)
        shiftedPath.offset(-left.toFloat(), -top.toFloat())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(shiftedPath, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(screenshot, -left.toFloat(), -top.toFloat(), paint)

        saveBitmap(output)
    }

    private fun saveBitmap(bitmap: Bitmap) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "Partial_$timestamp.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PartialScreenshot")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ScreenshotPrefs.addRecentUri(context, uri.toString())
            Toast.makeText(context, "已儲存局部截圖", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "儲存截圖失敗", Toast.LENGTH_SHORT).show()
        }
    }
}
