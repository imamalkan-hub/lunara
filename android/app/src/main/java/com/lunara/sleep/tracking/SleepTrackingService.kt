package com.lunara.sleep.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lunara.sleep.MainActivity
import com.lunara.sleep.R
import com.lunara.sleep.core.SleepEvent
import com.lunara.sleep.core.SleepSession
import com.lunara.sleep.core.Stage
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gece boyunca çalışan ön plan servisi.
 *
 * Android 9+ arka plan mikrofon/ivmeölçer erişimini öldürür; Android 12+
 * ön plan servisini arka plandan başlatmayı yasaklar; Android 14+ her servis
 * türü için açık izin ister. Bu servis:
 *  - yalnızca kullanıcı eylemiyle başlatılır (düğmeye basma),
 *  - `startForeground` çağrısını 5 saniye içinde yapar,
 *  - kalıcı ve faydalı bir bildirim gösterir (süre, "Durdur" aksiyonu).
 *
 * **Pil:** wakelock kullanılmaz. Sensör parti (batching) ile gelir ve CPU
 * uyanmaları gece boyunca ~7.500'e düşer. 8 saatlik toplam bekleme süresi
 * tipik olarak pilin %1-2'sidir (ses açıkken %4-7).
 */
class SleepTrackingService : Service() {

    companion object {
        private const val TAG = "LunaraService"
        const val ACTION_START = "com.lunara.sleep.START_TRACKING"
        const val ACTION_STOP = "com.lunara.sleep.STOP_TRACKING"
        const val CHANNEL_ID = "lunara_tracking"
        private const val NOTIFICATION_ID = 4101

        /** Gece boyunca toplanan olaylar — servis durunca okunur. */
        @Volatile var lastSession: SleepSession? = null
            private set

        @Volatile var isTracking = false
            private set
    }

    private lateinit var movementTracker: MovementTracker
    private lateinit var snoreTracker: SnoreTracker
    private val events = mutableListOf<SleepEvent>()
    private var startedAt = 0L
    private var clientId = ""
    private val stopRequested = AtomicBoolean(false)

    /** Sensör olaylarını 30 sn'de bir toplayan zamanlayıcı. */
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            if (isTracking) {
                tick()
                handler.postDelayed(this, 30_000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        movementTracker = MovementTracker(sensorManager)
        snoreTracker = SnoreTracker(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> beginTracking()
            ACTION_STOP -> stopTracking()
        }
        return START_STICKY
    }

    private fun beginTracking() {
        if (isTracking) return

        startedAt = System.currentTimeMillis()
        clientId = UUID.randomUUID().toString()
        events.clear()
        snoreTracker.reset()

        // Android 14+ servis türü zorunlu; 13 ve altında tip belirtilmez.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }

        isTracking = true
        stopRequested.set(false)

        movementTracker.start()
        snoreTracker.start()

        handler.postDelayed(tickRunnable, 30_000)

        Log.i(TAG, "tracking started: $clientId")
        updateNotification()
    }

    private fun stopTracking() {
        if (!isTracking) return
        stopRequested.set(true)
        isTracking = false
        handler.removeCallbacks(tickRunnable)

        movementTracker.stop()
        snoreTracker.stop()

        // Son epoku ekle.
        movementTracker.lastEvent()?.let { events.add(it) }

        val session = SleepSession(
            clientId = clientId,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            timezone = java.util.TimeZone.getDefault().id,
            deviceModel = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            events = events.toList(),
            snoreMin = snoreTracker.snoreMinutes(),
            snoreEvents = snoreTracker.snoreEvents(),
            peakDb = snoreTracker.peakAboveFloorDb(),
            avgDb = snoreTracker.avgDb(),
            noiseEvents = snoreTracker.noiseEvents(),
        )

        lastSession = session
        Log.i(TAG, "tracking ended: ${events.size} events, ${session.totalSleepMin} min")

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (isTracking) stopTracking()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // --- Bildirim ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_tracking),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_tracking_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val elapsed = ((System.currentTimeMillis() - startedAt) / 60000).toInt()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_tracking_title))
            .setContentText("Tracking for ${elapsed / 60}h ${elapsed % 60} min")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_tracking_stop), stopIntent)
            .build()
    }

    private fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(NOTIFICATION_ID, buildNotification()) }
    }

    /**
     * Servis durdurulmadan önce olayların toplanması için periyodik bildirim
     * güncellemesi. Aynı zamanda sensör olaylarını toplar.
     */
    fun tick() {
        if (!isTracking) return
        movementTracker.lastEvent()?.let { event ->
            if (events.isEmpty() || event.offsetMin > events.last().offsetMin) {
                events.add(event)
            }
        }
        updateNotification()
    }
}
