package com.example.partialscreenshot

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PartialScreenshotScreen()
            }
        }
    }
}

@Composable
fun PartialScreenshotScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isServiceRunning by remember { mutableStateOf(ScreenshotAccessibilityService.isRunning) }
    var delaySeconds by remember { mutableFloatStateOf(ScreenshotPrefs.getDelaySeconds(context)) }
    var selectedCropMode by remember { mutableStateOf(ScreenshotPrefs.getCropMode(context)) }
    var selectedGlowColor by remember { mutableIntStateOf(ScreenshotPrefs.getGlowColor(context)) }
    var recentUris by remember { mutableStateOf(ScreenshotPrefs.getRecentUris(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isServiceRunning = ScreenshotAccessibilityService.isRunning
                recentUris = ScreenshotPrefs.getRecentUris(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestAddTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val statusBarManager = context.getSystemService(StatusBarManager::class.java)
            statusBarManager?.requestAddTileService(
                ComponentName(context, ScreenshotTileService::class.java),
                "局截鍵",
                Icon.createWithResource(context, R.drawable.ic_partial_crop),
                context.mainExecutor
            ) { resultCode ->
                if (resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    resultCode == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                ) {
                    Toast.makeText(context, "已將「局截鍵」加入快速設定", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "請從通知列下拉編輯快速設定，加入「局截鍵」", Toast.LENGTH_LONG).show()
        }
    }

    val bgColor = Color(0xFFEEF3F8)
    val cardColor = Color.White
    val primaryBlue = Color(0xFF2F5D8C)
    val pillBg = Color(0xFFE7EFF7)
    val textDark = Color(0xFF1F2937)
    val textMuted = Color(0xFF6B7280)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFDCE7F3)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_partial_crop),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "局部截圖",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.85f)
            ) {
                Text(
                    text = "v2.3",
                    fontSize = 12.sp,
                    color = primaryBlue,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // Card 1: Service Status & Setup Steps
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isServiceRunning) Color(0xFF2E7D32) else Color(0xFFC4842D)
                                )
                        )
                        Text(
                            text = if (isServiceRunning) "截圖服務已開啟" else "截圖服務尚未開啟",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textDark
                        )
                    }

                    OutlinedButton(
                        onClick = { requestAddTile() },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = pillBg,
                            contentColor = primaryBlue
                        ),
                        border = BorderStroke(1.dp, Color(0xFFC5D6E8)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("加入局截鍵", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                SetupStepRow(
                    stepNumber = "1",
                    title = "允許受限制的設定",
                    buttonText = "前往",
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    }
                )

                SetupStepRow(
                    stepNumber = "2",
                    title = "開啟「局部截圖」無障礙服務",
                    buttonText = "開啟",
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                )

                SetupStepRow(
                    stepNumber = "3",
                    title = "把「局截鍵」加入快速設定",
                    buttonText = "加入",
                    onClick = { requestAddTile() }
                )
            }
        }

        // Card 2: Settings
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Delay slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "截圖等待時間",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textDark
                        )
                        Text(
                            text = String.format("%.2f s", delaySeconds),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryBlue
                        )
                    }
                    Slider(
                        value = delaySeconds,
                        onValueChange = { raw ->
                            val stepped = (raw * 20f).roundToInt() / 20f
                            delaySeconds = stepped
                            ScreenshotPrefs.setDelaySeconds(context, stepped)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1E8875),
                            activeTrackColor = Color(0xFF1E8875),
                            inactiveTrackColor = Color(0xFFE2E8F0)
                        )
                    )
                }

                // Crop mode
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "預設裁切形態",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textDark
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CropMode.entries.forEach { mode ->
                            val selected = mode == selectedCropMode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) pillBg else Color.White,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) Color(0xFF8CAAD0) else Color(0xFFE5E7EB)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedCropMode = mode
                                        ScreenshotPrefs.setCropMode(context, mode)
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp)
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (selected) primaryBlue else textDark
                                    )
                                }
                            }
                        }
                    }
                }

                // Glow color
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "圈選流光筆跡色",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textDark
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ScreenshotPrefs.GLOW_COLORS.forEach { colorInt ->
                            val isSelected = colorInt == selectedGlowColor
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorInt))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.5.dp, Color(0xFF1F2937), CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        selectedGlowColor = colorInt
                                        ScreenshotPrefs.setGlowColor(context, colorInt)
                                    }
                            )
                        }
                    }
                }
            }
        }

        // Card 3: Recent Screenshots
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "近期截圖",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textDark
                )

                if (recentUris.isEmpty()) {
                    Text(
                        text = "尚無近期截圖",
                        fontSize = 14.sp,
                        color = textMuted
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(recentUris) { uriString ->
                            RecentScreenshotThumbnail(uriString = uriString)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStepRow(
    stepNumber: String,
    title: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE7EFF7)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2F5D8C)
                )
            }
            Text(
                text = title,
                fontSize = 14.sp,
                color = Color(0xFF374151)
            )
        }

        OutlinedButton(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Text(
                text = buttonText,
                fontSize = 13.sp,
                color = Color(0xFF2F5D8C)
            )
        }
    }
}

@Composable
private fun RecentScreenshotThumbnail(uriString: String) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "近期截圖",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                .clickable {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(uriString), "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                }
        )
    }
}
