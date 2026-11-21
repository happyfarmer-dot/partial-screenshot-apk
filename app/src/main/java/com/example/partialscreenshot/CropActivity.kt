package com.example.partialscreenshot

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class MarkupTool(val label: String) {
    HIGHLIGHTER("螢光"),
    BOX("紅框"),
    MOSAIC("馬賽克")
}

class CropActivity : Activity() {

    private lateinit var shot: Bitmap
    private lateinit var root: FrameLayout
    private lateinit var overlayView: InteractiveCropView
    private lateinit var siriEdgeView: SiriEdgeView
    private lateinit var topPillText: TextView
    private lateinit var dockContainer: LinearLayout
    private lateinit var markupBar: LinearLayout

    private var currentCropMode: CropMode = CropMode.DASHED_RECT
    private var activeMarkupTool: MarkupTool? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        shot = ScreenshotAccessibilityService.pendingShot ?: run {
            finish()
            return
        }
        ScreenshotAccessibilityService.pendingShot = null
        window.insetsController?.hide(WindowInsets.Type.systemBars())

        currentCropMode = ScreenshotPrefs.getCropMode(this)
        val glowColor = ScreenshotPrefs.getGlowColor(this)

        root = FrameLayout(this)

        overlayView = InteractiveCropView(
            context = this,
            screenshot = shot,
            cropMode = currentCropMode,
            glowColor = glowColor,
            onSelectionChanged = { w, h, valid ->
                if (valid) {
                    topPillText.text = "已選取 ${w}×${h}"
                    dockContainer.visibility = View.VISIBLE
                } else {
                    topPillText.text = "請框選截圖範圍"
                    dockContainer.visibility = View.GONE
                }
            }
        )
        root.addView(overlayView, FrameLayout.LayoutParams(-1, -1))

        siriEdgeView = SiriEdgeView(this)
        root.addView(siriEdgeView, FrameLayout.LayoutParams(-1, -1))

        // Top Liquid Glass Pill
        val topPill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(0xCC1E293B.toInt())
                cornerRadius = dp(22f).toFloat()
                setStroke(dp(1f), 0x44FFFFFF)
            }
            setPadding(dp(16f), dp(8f), dp(16f), dp(8f))
        }
        topPillText = TextView(this).apply {
            text = "請框選截圖範圍"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        topPill.addView(topPillText)
        root.addView(
            topPill,
            FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dp(52f)
            }
        )

        // Bottom Liquid Glass Action Dock
        dockContainer = buildBottomDock()
        dockContainer.visibility = View.GONE
        root.addView(
            dockContainer,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                leftMargin = dp(12f)
                rightMargin = dp(12f)
                bottomMargin = dp(28f)
            }
        )

        setContentView(root)
    }

    private fun buildBottomDock(): LinearLayout {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Markup sub-bar
        markupBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(0xCC1E293B.toInt())
                cornerRadius = dp(18f).toFloat()
                setStroke(dp(1f), 0x44FFFFFF)
            }
            setPadding(dp(10f), dp(6f), dp(10f), dp(6f))
        }
        MarkupTool.entries.forEach { tool ->
            val btn = createPillButton(tool.label) {
                activeMarkupTool = if (activeMarkupTool == tool) null else tool
                overlayView.activeMarkupTool = activeMarkupTool
                Toast.makeText(
                    this,
                    if (activeMarkupTool != null) "已啟用：${tool.label}" else "已關閉標註",
                    Toast.LENGTH_SHORT
                ).show()
            }
            markupBar.addView(btn)
        }
        markupBar.addView(createPillButton("復原") { overlayView.undoMarkup() })
        wrapper.addView(
            markupBar,
            LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8f) }
        )

        // Main Action Dock
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            background = GradientDrawable().apply {
                setColor(0xD91E293B.toInt())
                cornerRadius = dp(24f).toFloat()
                setStroke(dp(1f), 0x44FFFFFF)
            }
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        row.addView(createPillButton("標註") {
            markupBar.visibility = if (markupBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        })
        row.addView(createPillButton("文字辨識") { performOcr() })
        row.addView(createPillButton("釘選") { pinCroppedBitmap() })
        row.addView(createPillButton("複製") { copyCroppedBitmap() })
        row.addView(createPillButton("分享") { shareCroppedBitmap() })
        row.addView(createPillButton("儲存") {
            saveCroppedBitmap()
            finish()
        })
        row.addView(createPillButton("取消") { finish() })

        scroll.addView(row)
        wrapper.addView(scroll, LinearLayout.LayoutParams(-2, -2))
        return wrapper
    }

    private fun createPillButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
            setOnClickListener { onClick() }
        }
    }

    private fun saveCroppedBitmap() {
        val bmp = overlayView.exportCroppedBitmap() ?: return
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Partial_$timestamp.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PartialScreenshot")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ScreenshotPrefs.addRecentUri(this, uri.toString())
            Toast.makeText(this, "已儲存局部截圖", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyCroppedBitmap() {
        val bmp = overlayView.exportCroppedBitmap() ?: return
        val uri = saveTempPng(bmp) ?: return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newUri(contentResolver, "局部截圖", uri))
        Toast.makeText(this, "已複製到剪貼簿", Toast.LENGTH_SHORT).show()
    }

    private fun shareCroppedBitmap() {
        val bmp = overlayView.exportCroppedBitmap() ?: return
        val uri = saveTempPng(bmp) ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "分享局部截圖"))
    }

    private fun pinCroppedBitmap() {
        val bmp = overlayView.exportCroppedBitmap() ?: return
        val service = ScreenshotAccessibilityService.instance
        if (service != null) {
            service.showPinnedBitmap(bmp)
            finish()
        } else {
            Toast.makeText(this, "無障礙服務未開啟", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performOcr() {
        val bmp = overlayView.exportCroppedBitmap() ?: return
        val image = InputImage.fromBitmap(bmp, 0)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        Toast.makeText(this, "正在辨識文字…", Toast.LENGTH_SHORT).show()
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val text = result.text.trim()
                if (text.isEmpty()) {
                    Toast.makeText(this, "未偵測到文字", Toast.LENGTH_SHORT).show()
                } else {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
                    Toast.makeText(this, "已辨識並複製文字：\n${text.take(40)}", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "文字辨識失敗", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveTempPng(bmp: Bitmap): Uri? {
        return runCatching {
            val dir = File(cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "partial_share.png")
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        }.getOrNull()
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}

class SiriEdgeView(context: Context) : View(context) {
    private var phase = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 14f
        maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)
    }
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3200L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val colors = intArrayOf(
            0xFF38BDF8.toInt(),
            0xFFA855F7.toInt(),
            0xFFEC4899.toInt(),
            0xFFF59E0B.toInt(),
            0xFF38BDF8.toInt()
        )
        val shader = SweepGradient(cx, cy, colors, null)
        val matrix = Matrix()
        matrix.postRotate(phase, cx, cy)
        shader.setLocalMatrix(matrix)
        paint.shader = shader
        canvas.drawRect(4f, 4f, width - 4f, height - 4f, paint)
    }
}

class InteractiveCropView(
    context: Context,
    private val screenshot: Bitmap,
    private val cropMode: CropMode,
    glowColor: Int,
    private val onSelectionChanged: (Int, Int, Boolean) -> Unit
) : View(context) {

    var activeMarkupTool: MarkupTool? = null

    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var hasSelection = false
    private var isSelecting = false

    private val lassoPath = Path()
    private val markupStrokes = mutableListOf<Pair<MarkupTool, Path>>()
    private var currentMarkupPath: Path? = null

    private val dimPaint = Paint().apply { color = Color.argb(115, 0, 0, 0) }
    private val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val dashedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(18f, 12f), 0f)
    }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun undoMarkup() {
        if (markupStrokes.isNotEmpty()) {
            markupStrokes.removeAt(markupStrokes.lastIndex)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(screenshot, 0f, 0f, null)

        val saveLayer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)

        if (isSelecting || hasSelection) {
            if (cropMode == CropMode.TRANSPARENT_LASSO) {
                canvas.drawPath(lassoPath, clearPaint)
                canvas.restoreToCount(saveLayer)
                canvas.drawPath(lassoPath, lassoGlowPaint)
                canvas.drawPath(lassoPath, lassoCorePaint)
            } else {
                val rect = currentRect()
                canvas.drawRect(rect, clearPaint)
                canvas.restoreToCount(saveLayer)
                canvas.drawRect(rect, if (cropMode == CropMode.DASHED_RECT) dashedPaint else whitePaint)
            }
        } else {
            canvas.restoreToCount(saveLayer)
        }

        markupStrokes.forEach { (tool, path) ->
            canvas.drawPath(path, paintForTool(tool))
        }
        currentMarkupPath?.let { path ->
            activeMarkupTool?.let { tool -> canvas.drawPath(path, paintForTool(tool)) }
        }
    }

    private fun paintForTool(tool: MarkupTool): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            when (tool) {
                MarkupTool.HIGHLIGHTER -> {
                    color = 0x88FACC15.toInt()
                    strokeWidth = 28f
                }
                MarkupTool.BOX -> {
                    color = 0xFFEF4444.toInt()
                    strokeWidth = 8f
                }
                MarkupTool.MOSAIC -> {
                    color = 0xCC94A3B8.toInt()
                    strokeWidth = 32f
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tool = activeMarkupTool
        if (tool != null && hasSelection) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    currentMarkupPath = Path().apply { moveTo(event.x, event.y) }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    currentMarkupPath?.lineTo(event.x, event.y)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    currentMarkupPath?.let { markupStrokes.add(tool to it) }
                    currentMarkupPath = null
                    invalidate()
                    return true
                }
            }
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                endX = event.x
                endY = event.y
                isSelecting = true
                hasSelection = false
                lassoPath.reset()
                lassoPath.moveTo(startX, startY)
                onSelectionChanged(0, 0, false)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                endX = event.x
                endY = event.y
                lassoPath.lineTo(endX, endY)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isSelecting = false
                if (cropMode == CropMode.TRANSPARENT_LASSO) {
                    lassoPath.close()
                    val bounds = RectF()
                    lassoPath.computeBounds(bounds, true)
                    hasSelection = bounds.width() >= 24f && bounds.height() >= 24f
                    onSelectionChanged(bounds.width().toInt(), bounds.height().toInt(), hasSelection)
                } else {
                    val rect = currentRect()
                    hasSelection = rect.width() >= 24f && rect.height() >= 24f
                    onSelectionChanged(rect.width().toInt(), rect.height().toInt(), hasSelection)
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun currentRect(): RectF {
        return RectF(min(startX, endX), min(startY, endY), max(startX, endX), max(startY, endY))
    }

    fun exportCroppedBitmap(): Bitmap? {
        if (!hasSelection) return null
        val fullAnnotated = Bitmap.createBitmap(screenshot.width, screenshot.height, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(fullAnnotated)
        fullCanvas.drawBitmap(screenshot, 0f, 0f, null)
        markupStrokes.forEach { (tool, path) ->
            fullCanvas.drawPath(path, paintForTool(tool))
        }

        return if (cropMode == CropMode.TRANSPARENT_LASSO) {
            val bounds = RectF()
            lassoPath.computeBounds(bounds, true)
            val left = bounds.left.toInt().coerceIn(0, fullAnnotated.width - 1)
            val top = bounds.top.toInt().coerceIn(0, fullAnnotated.height - 1)
            val right = bounds.right.toInt().coerceIn(left + 1, fullAnnotated.width)
            val bottom = bounds.bottom.toInt().coerceIn(top + 1, fullAnnotated.height)

            val output = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val shifted = Path(lassoPath).apply { offset(-left.toFloat(), -top.toFloat()) }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawPath(shifted, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(fullAnnotated, -left.toFloat(), -top.toFloat(), paint)
            output
        } else {
            val rect = currentRect()
            val left = rect.left.toInt().coerceIn(0, fullAnnotated.width - 1)
            val top = rect.top.toInt().coerceIn(0, fullAnnotated.height - 1)
            val right = rect.right.toInt().coerceIn(left + 1, fullAnnotated.width)
            val bottom = rect.bottom.toInt().coerceIn(top + 1, fullAnnotated.height)
            Bitmap.createBitmap(fullAnnotated, left, top, right - left, bottom - top)
        }
    }
}
