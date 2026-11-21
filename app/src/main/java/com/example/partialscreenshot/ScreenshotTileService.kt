package com.example.partialscreenshot

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class ScreenshotTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (ScreenshotAccessibilityService.isRunning) {
                Tile.STATE_ACTIVE
            } else {
                Tile.STATE_INACTIVE
            }
            label = "局截鍵"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val service = ScreenshotAccessibilityService.instance
        if (service != null) {
            service.captureWithDelay()
        } else {
            Toast.makeText(this, "請先開啟「局部截圖」無障礙服務", Toast.LENGTH_SHORT).show()
        }
    }
}
