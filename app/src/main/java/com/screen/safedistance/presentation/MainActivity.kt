package com.screen.safedistance.presentation

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ComponentName
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
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.screen.safedistance.SafeDistanceService
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

    private val phoneStatePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkNotificationPermission()
        } else {
            Toast.makeText(this, "Telefon durumu izni gerekli", Toast.LENGTH_SHORT).show()
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
                        onStartServiceClick = { checkAndRequestAllPermissions() },
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
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SafeDistanceService::class.java.name == service.service.className) {
                isServiceRunning.value = true
                return
            }
        }
        isServiceRunning.value = false
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }


    private val multiplePermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
            val notificationGranted =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
                } else {
                    true // Android 12 ve altı → gerek yok
                }

            if (cameraGranted && phoneStateGranted && notificationGranted) {
                startSafeDistanceService()
            } else {
                AlertDialog.Builder(this)
                    .setTitle("İzin Gerekli")
                    .setMessage("Servisin çalışabilmesi için gerekli izinleri ayarlardan vermelisiniz.")
                    .setPositiveButton("Ayarlar") { _, _ ->
                        openAppSettings()
                    }
                    .setNegativeButton("İptal", null)
                    .show()
            }
        }


    private fun checkAndRequestAllPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())

        } else {
            // Zaten hepsi verilmiş → direkt başlat
            startSafeDistanceService()
        }

    }




    private fun checkNotificationPermission() {
        // Android 13 ve üstü için POST_NOTIFICATIONS gerekiyor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkPhoneStatePermission() // Bildirim izni varsa sıradaki kontrol
            }
        } else {
            checkPhoneStatePermission() // Android 12 ve altı → direkt geç
        }
    }

    private fun checkPhoneStatePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
        } else {
            startSafeDistanceService() // Tüm izinler tamam → servisi başlat
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
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
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
        getSharedPreferences("app_prefs", MODE_PRIVATE)
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

                manufacturer.contains("samsung") ->
                    Intent().setComponent(
                        ComponentName(
                            "com.samsung.android.lool",
                            "com.samsung.android.sm.battery.ui.BatteryActivity"
                        )
                    )

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





