package com.screen.safedistance

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.screen.safedistance.ui.theme.SafeDistanceTheme

class MainActivity : ComponentActivity() {

    private val distanceViewModel by viewModels<DistanceViewModel>()

    private var showAutoStartDialog = mutableStateOf(false)
    private var isServiceRunning = mutableStateOf(false)

    private var notificationPermissionRequestCount = 0
    private val maxNotificationPermissionRequests = 3

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkNotificationPermission()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startSafeDistanceService()
            if (shouldShowAutoStartPermission()) {
                showAutoStartDialog.value = true
            }
            notificationPermissionRequestCount = 0
        } else {
            notificationPermissionRequestCount++
            if (notificationPermissionRequestCount < maxNotificationPermissionRequests) {
                Toast.makeText(
                    this,
                    "Bildirim izni gerekli. Lütfen izin verin.",
                    Toast.LENGTH_SHORT
                ).show()
                //notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Toast.makeText(
                    this,
                    "Bildirim izni verilmedi. Servis başlatılamıyor.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SafeDistanceTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ScreenDistanceView(
                        viewModel = distanceViewModel,
                        isServiceRunning = isServiceRunning.value,
                        onStartServiceClick = { checkAndStartService() },
                        onStopServiceClick = { stopSafeDistanceService() }
                    )

                    if (showAutoStartDialog.value) {
                        AutoStartPermissionDialog(
                            onDismiss = {
                                showAutoStartDialog.value = false
                                markAutoStartAsked()
                            },
                            onAllow = {
                                showAutoStartDialog.value = false
                                markAutoStartAsked()
                                requestAutoStartPermission()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkServiceRunning()
    }

    private fun checkServiceRunning() {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SafeDistanceService::class.java.name == service.service.className) {
                isServiceRunning.value = true
                return
            }
        }
        isServiceRunning.value = false
    }

    private fun checkAndStartService() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionRequestCount = 0
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startSafeDistanceService()
                if (shouldShowAutoStartPermission()) {
                    showAutoStartDialog.value = true
                }
            }
        } else {
            startSafeDistanceService()
            if (shouldShowAutoStartPermission()) {
                showAutoStartDialog.value = true
            }
        }
    }

    private fun startSafeDistanceService() {
        val intent = Intent(this, SafeDistanceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning.value = true
        Toast.makeText(this, "Servis başlatıldı", Toast.LENGTH_SHORT).show()
    }

    private fun stopSafeDistanceService() {
        val intent = Intent(this, SafeDistanceService::class.java)
        stopService(intent)
        isServiceRunning.value = false
        Toast.makeText(this, "Servis durduruldu", Toast.LENGTH_SHORT).show()
    }

    private fun shouldShowAutoStartPermission(): Boolean {
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasAsked = prefs.getBoolean("auto_start_asked", false)
        val manufacturer = Build.MANUFACTURER.lowercase()

        val supportedManufacturers = listOf(
            "xiaomi", "oppo", "vivo", "letv", "asus", "samsung",
            "huawei", "oneplus", "realme", "honor"
        )

        val isSupported = supportedManufacturers.any { manufacturer.contains(it) }
        return !hasAsked && isSupported
    }

    private fun markAutoStartAsked() {
        getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("auto_start_asked", true)
            .apply()
    }
    private fun requestAutoStartPermission() {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = when {
                manufacturer.contains("xiaomi") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    )
                manufacturer.contains("oppo") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    )
                manufacturer.contains("vivo") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.iqoo.secure",
                            "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
                        )
                    )
                manufacturer.contains("letv") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.letv.android.letvsafe",
                            "com.letv.android.letvsafe.AutobootManageActivity"
                        )
                    )
                manufacturer.contains("asus") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.asus.mobilemanager",
                            "com.asus.mobilemanager.entry.FunctionActivity"
                        )
                    ).putExtra("function", "auto_start")
                manufacturer.contains("samsung") -> {
                    Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
                manufacturer.contains("huawei") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    )
                manufacturer.contains("oneplus") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.oneplus.security",
                            "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"
                        )
                    )
                manufacturer.contains("realme") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.FakeActivity"
                        )
                    )
                else -> {
                    Toast.makeText(
                        this,
                        "Otomatik başlatma ayarı bu cihazda desteklenmiyor",
                        Toast.LENGTH_SHORT
                    ).show()
                    return
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Otomatik başlatma izni ekranı açılamadı",
                Toast.LENGTH_SHORT
            ).show()
            e.printStackTrace()
        }
    }

}

@Composable
fun AutoStartPermissionDialog(
    onDismiss: () -> Unit,
    onAllow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Batarya Optimizasyonu",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Text(
                "Uygulamanın arka planda düzgün çalışabilmesi ve size güvenli mesafe uyarıları gönderebilmesi için batarya optimizasyonunu kapatmanız önerilir.",
                style = TextStyle(fontSize = 14.sp),
                color = Color.White
            )
        },
        icon = {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFF4CAF50)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAllow,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFF4CAF50)
                )
            ) {
                Text("İzin Ver", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.Gray
                )
            ) {
                Text("Daha Sonra")
            }
        },
        containerColor = Color(0xFF222222),
        iconContentColor = Color.White,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun ScreenDistanceView(
    viewModel: DistanceViewModel,
    isServiceRunning: Boolean,
    onStartServiceClick: () -> Unit,
    onStopServiceClick: () -> Unit
) {
    val backgroundColor = Color.Black
    val boxColor = Color(0xFF222222)
    val textColor = Color.White

    val distanceThreshold by viewModel.distanceThreshold.collectAsState()
    val intervalSeconds by viewModel.intervalSeconds.collectAsState()

    Column(
        modifier = Modifier
            .background(backgroundColor)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Screen Distance",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = textColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Servis Kontrol Butonu
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isServiceRunning) Color(0xFF4CAF50) else Color(0xFFFF5252)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isServiceRunning) "Servis Çalışıyor" else "Servis Durduruldu",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = if (isServiceRunning) "Ekran mesafesi takip ediliyor" else "Takip başlatmak için butona tıklayın",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }

                Button(
                    onClick = {
                        if (isServiceRunning) {
                            onStopServiceClick()
                        } else {
                            onStartServiceClick()
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Text(if (isServiceRunning) "Durdur" else "Başlat")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp)
                .background(boxColor, shape = RoundedCornerShape(25.dp))
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ölçüm Yapılabilmesi için Yüzünüzün Algılanması Gerekmektedir.",
                    color = textColor,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Uyarı Mesafesi: ${distanceThreshold.toInt()} cm", color = Color.White)
        Slider(
            value = distanceThreshold,
            onValueChange = { viewModel.setDistanceThreshold(it) },
            valueRange = 10f..40f,
            steps = 5,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Ölçüm Sıklığı: ${intervalSeconds.toInt()} saniye", color = Color.White)
        Slider(
            value = intervalSeconds,
            onValueChange = { viewModel.setIntervalSeconds(it) },
            valueRange = 3f..30f,
            steps = 8,
        )
    }
}
