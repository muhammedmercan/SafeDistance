package com.screen.safedistance

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.*
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.google.mlkit.vision.camera.CameraSourceConfig
import com.google.mlkit.vision.camera.CameraXSource
import com.google.mlkit.vision.face.*
import com.screen.safedistance.widget.SafeDistanceGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.abs

class SafeDistanceService : Service() {

    companion object {
        const val IMAGE_WIDTH = 1024
        const val IMAGE_HEIGHT = 1024
        const val AVERAGE_EYE_DISTANCE = 63 // mm
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "SafeDistanceChannel"
        const val TAG = "SafeDistanceService"
    }

    private lateinit var prefs: SharedPreferences
    private var distanceThresholdCm: Float = 30f
    private var intervalMillis: Long = 3000L

    private var focalLength: Float = 0f
    private var sensorX: Float = 0f
    private var sensorY: Float = 0f

    private var cameraXSource: CameraXSource? = null
    private var soClose: Boolean = false
    private var isMeasuring = false
    private var isServiceRunning = true
    private var hasProcessedFace = false

    private val handler = Handler(Looper.getMainLooper())
    private val powerHandler = Handler(Looper.getMainLooper())

    private var screenOnDuration = 0 // saniye

    private var lastWarningTime = 0 // saniye
    private val screenOnThreshold = 20 * 60  // 20 dakika

    private val measurementRunnable = Runnable {
        performMeasurement()
    }

    private val screenCheckRunnable = object : Runnable {

        override fun run() {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                @Suppress("DEPRECATION")
                powerManager.isScreenOn
            }

            if (isScreenOn) {
                screenOnDuration++

                if (screenOnDuration >= screenOnThreshold) {
                    // Ekran açık süresi, son uyarı zamanından 20dk (screenOnThreshold) fazla mı?
                    if (screenOnDuration - lastWarningTime >= screenOnThreshold) {
                        sendScreenOnWarning(screenOnDuration)
                        lastWarningTime = screenOnDuration
                    }
                }
            } else {
                screenOnDuration = 0
                lastWarningTime = 0
            }

            powerHandler.postDelayed(this, 1000L)
        }
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            when (key) {
                "distanceThreshold" -> {
                    distanceThresholdCm = sharedPrefs.getFloat(key, 30f)
                    Log.d(TAG, "Mesafe eşiği güncellendi: $distanceThresholdCm cm")
                }
                "intervalSeconds" -> {
                    val intervalSaniye = sharedPrefs.getFloat(key, 3f)
                    intervalMillis = (intervalSaniye * 1000).toLong()
                    Log.d(TAG, "Ölçüm sıklığı güncellendi: $intervalMillis ms")
                }
            }
        }

    override fun onCreate() {
        super.onCreate()

        updateWidgetServiceRunningState(true)
        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)

        distanceThresholdCm = prefs.getFloat("distanceThreshold", 30f)
        intervalMillis = (prefs.getFloat("intervalSeconds", 3f) * 1000).toLong()

        createNotificationChannels()
        startForeground(NOTIFICATION_ID, initialNotification())

        try {
            initializeParams()
        } catch (e: Exception) {
            Log.e(TAG, "Kamera parametreleri alınamadı: ${e.message}")
            stopSelf()
            return
        }

        handler.post(measurementRunnable)
        powerHandler.post(screenCheckRunnable) // ekran süresi kontrolü başlat
    }


    private fun initialNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ekran Mesafesi Takibi")
            .setContentText("Servis başlatıldı.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVibrate(longArrayOf(0L))
            .build()
    }

    private fun initializeParams() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = getFrontCameraId(this)
        val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)

        val focalLengths: FloatArray? =
            cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        focalLength = focalLengths?.getOrNull(0) ?: throw Exception("Focal length bulunamadı")

        val sensorSize =
            cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                ?: throw Exception("Sensor size bulunamadı")

        sensorX = sensorSize.width
        sensorY = sensorSize.height
    }

    private fun getFrontCameraId(context: Context): String {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                return cameraId
            }
        }
        throw Exception("Ön kamera bulunamadı")
    }

    private fun scheduleNextMeasurement() {
        if (isServiceRunning) {
            handler.postDelayed(measurementRunnable, intervalMillis)
        }
    }

    private fun performMeasurement() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }


        if (!isScreenOn) {
            // Ekran kapalı, ölçüm yapma, sonraki ölçüm için zamanlayıcıyı ayarla
            scheduleNextMeasurement()
            return
        }

        if (isMeasuring || !isServiceRunning) {
            return
        }

        isMeasuring = true
        hasProcessedFace = false

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Kamera izni yok!")
            isMeasuring = false
            scheduleNextMeasurement()
            return
        }

        val options = FaceDetectorOptions.Builder()
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()

        val detector = FaceDetection.getClient(options)

        val cameraSourceConfig = CameraSourceConfig.Builder(this, detector) {
            it.addOnSuccessListener { faces ->
                if (!hasProcessedFace && faces.isNotEmpty()) {
                    hasProcessedFace = true
                    processFaces(faces)
                    handler.post { stopCamera() }
                }
            }
            it.addOnFailureListener {
                if (!hasProcessedFace) {
                    hasProcessedFace = true
                    handler.post { stopCamera() }
                }
            }
        }.setFacing(CameraSourceConfig.CAMERA_FACING_FRONT).build()

        try {
            cameraXSource = CameraXSource(cameraSourceConfig)
            cameraXSource?.start()

            handler.postDelayed({
                if (isMeasuring) {
                    stopCamera()
                }
            }, 2000)

        } catch (e: IOException) {
            isMeasuring = false
            scheduleNextMeasurement()
        }
    }


    private fun stopCamera() {
        if (!isMeasuring) return

        try {
            cameraXSource?.stop()
        } catch (_: Exception) {
        }

        cameraXSource = null
        isMeasuring = false
        scheduleNextMeasurement()
    }

    private fun processFaces(faces: List<Face>) {
        val face = faces.firstOrNull() ?: return
        val leftEyePos = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
        val rightEyePos = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position

        if (leftEyePos != null && rightEyePos != null) {
            val deltaX = abs(leftEyePos.x - rightEyePos.x)
            val deltaY = abs(leftEyePos.y - rightEyePos.y)

            val distanceMm = if (deltaX >= deltaY) {
                focalLength * (AVERAGE_EYE_DISTANCE / sensorX) * (IMAGE_WIDTH / deltaX)
            } else {
                focalLength * (AVERAGE_EYE_DISTANCE / sensorY) * (IMAGE_HEIGHT / deltaY)
            }

            if (distanceMm < distanceThresholdCm * 10) {
                showWarning(distanceMm)
                soClose = true
            } else {
                if (soClose) {
                    showInfo(distanceMm)
                    soClose = false
                }
            }
        }
    }

    private fun showInfo(distance: Float) {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID + 1)

        val notification = NotificationCompat.Builder(this, "info_channel")
            .setContentTitle("Ekran Mesafesi Takibi Aktif")
            .setContentText("Ekrana ideal uzaklıktasınız.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun showWarning(distance: Float) {
        saveEvent("closeWarning") // ← çok yaklaştı kaydı
        val notification = NotificationCompat.Builder(this, "warning_channel")
            .setContentTitle("Uyarı")
            .setContentText("Ekrana çok yaklaştınız! (${distance.toInt()} mm)")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun sendScreenOnWarning(durationSeconds: Int) {
        saveEvent("longLookWarning") // ← uzun süre bakıldı kaydı

        val minutes = durationSeconds / 60
        val notification = NotificationCompat.Builder(this, "warning_channel")
            .setContentTitle("Ekran çok uzun süredir açık")
            .setContentText("$minutes dakikadır ekran açık. Göz sağlığınız için mola verin.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID + 2, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateWidgetServiceRunningState(false)
        isServiceRunning = false
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacksAndMessages(null)
        powerHandler.removeCallbacksAndMessages(null)
        try {
            cameraXSource?.stop()
        } catch (_: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val infoChannel = NotificationChannel(
                "info_channel",
                "Bilgilendirme Bildirimleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(false)
                setSound(null, null)
            }

            val warningChannel = NotificationChannel(
                "warning_channel",
                "Uyarı Bildirimleri",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Servis Bildirimleri",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                enableVibration(false)
                setSound(null, null)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(infoChannel)
            manager.createNotificationChannel(warningChannel)
            manager.createNotificationChannel(serviceChannel) // ← bu satır eklendi
        }
    }


    private fun saveEvent(eventType: String) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        val key = "${eventType}_$today"

        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        Log.d(TAG, "Event kaydedildi: $eventType = $count ($today)")
    }


    private fun updateWidgetServiceRunningState(running: Boolean) {
        CoroutineScope(Dispatchers.Default).launch {
            val manager = GlanceAppWidgetManager(applicationContext)
            val glanceIds = manager.getGlanceIds(SafeDistanceGlanceWidget::class.java)
            glanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[booleanPreferencesKey("service_running")] = running
                    }
                }
                SafeDistanceGlanceWidget().update(applicationContext, id)
            }
        }
    }

}
