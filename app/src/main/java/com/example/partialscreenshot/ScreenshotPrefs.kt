package com.example.partialscreenshot

import android.content.Context

enum class CropMode(val label: String) {
    DASHED_RECT("虛線矩形"),
    WHITE_RECT("白邊矩形"),
    TRANSPARENT_LASSO("透明套索")
}

object ScreenshotPrefs {
    private const val PREFS_NAME = "partial_screenshot_prefs"
    private const val KEY_DELAY_SECONDS = "delay_seconds"
    private const val KEY_CROP_MODE = "crop_mode"
    private const val KEY_GLOW_COLOR = "glow_color"
    private const val KEY_RECENT_URIS = "recent_uris"

    val GLOW_COLORS = listOf(
        0xFF2F5D8C.toInt(), // Deep Blue
        0xFFC4842D.toInt(), // Amber Gold
        0xFF557C67.toInt(), // Sage Green
        0xFFA6616B.toInt()  // Rose Mauve
    )

    fun getDelaySeconds(context: Context): Float {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_DELAY_SECONDS, 0.15f)
    }

    fun setDelaySeconds(context: Context, value: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_DELAY_SECONDS, value).apply()
    }

    fun getCropMode(context: Context): CropMode {
        val name = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CROP_MODE, CropMode.DASHED_RECT.name)
        return runCatching { CropMode.valueOf(name ?: CropMode.DASHED_RECT.name) }
            .getOrDefault(CropMode.DASHED_RECT)
    }

    fun setCropMode(context: Context, mode: CropMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CROP_MODE, mode.name).apply()
    }

    fun getGlowColor(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_GLOW_COLOR, GLOW_COLORS.first())
    }

    fun setGlowColor(context: Context, color: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_GLOW_COLOR, color).apply()
    }

    fun getRecentUris(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_URIS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("|").filter { it.isNotBlank() }
    }

    fun addRecentUri(context: Context, uriString: String) {
        val current = getRecentUris(context).toMutableList()
        current.remove(uriString)
        current.add(0, uriString)
        val trimmed = current.take(12)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_RECENT_URIS, trimmed.joinToString("|")).apply()
    }
}
