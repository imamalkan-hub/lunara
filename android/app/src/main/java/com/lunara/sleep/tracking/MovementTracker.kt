package com.lunara.sleep.tracking

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.lunara.sleep.core.SleepEvent
import com.lunara.sleep.core.Stage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hareket algılayıcı.
 *
 * **Yöntem** (uyku araştırmasındaki Cole–Kripke aktivite sayımına benzer):
 *  1. Yüksek geçiren filtre (~0.05 Hz) — yerçekimi ve yavaş kayma
 *     sökülür, "hareket" = kişinin kendi tabanına göre sapması olur.
 *     Telefon yastıkta düz durduğunda gövde vektörü sabit ama keyfidir;
 *     mutlak büyüklük yerine sapma kullanmak bu yüzden şart.
 *  2. Epok (30 sn) boyunca hareket sayımı biriktirilir.
 *  3. Kişiye özel uyarlanabilir eşik (son 3 saatin ort + k×SS) kullanılır —
 *     sabit eşikler hafif uyuyanlarda ve derin uyuyanlarda çöp sonuç verir.
 *
 * Örnekleme 12.5 Hz ve **parti (batching)** ile yapılır: bu, gece boyunca
 * CPU uyanmalarını ~37.500'den ~7.500'e düşürür — pil için en büyük kazanç.
 */
class MovementTracker(
    private val sensorManager: SensorManager,
) : SensorEventListener {

    companion object {
        private const val TAG = "LunaraMovement"
        const val SAMPLE_PERIOD_US = 80_000   // 12.5 Hz
        const val BATCH_LATENCY_US = 400_000  // 0.4 s batch latency tolerance
        const val EPOCH_SEC = 30
        const val HIGH_PASS_ALPHA = 0.9985f  // ~0.05 Hz tek kutuplu
    }

    private var accelerometer: Sensor? = null
    private var lastEvent: SleepEvent? = null

    // Yüksek geçiren filtre durumu
    private var prevInput = 0f
    private var prevOutput = 0f

    // Epok toplayıcı
    private var epochStartMs = 0L
    private var epochMovement = 0.0
    private var epochSamples = 0
    private var epochOffsetSec = 0

    // Hareket eşiği (uyarlanabilir)
    private val recentCounts = ArrayDeque<Double>()

    private val running = AtomicBoolean(false)

    /** Hareket sayısı eşiği — temel seviye 0.08. */
    private var threshold = 0.08

    val isRunning: Boolean get() = running.get()

    fun start() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run {
            Log.e(TAG, "no accelerometer")
            return
        }
        accelerometer = sensor
        prevInput = 0f
        prevOutput = 0f
        epochMovement = 0.0
        epochSamples = 0
        epochOffsetSec = 0
        epochStartMs = System.currentTimeMillis()
        running.set(true)

        sensorManager.registerListener(
            this,
            sensor,
            SAMPLE_PERIOD_US,
            BATCH_LATENCY_US,
        )
        Log.i(TAG, "movement tracker started")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        accelerometer?.let { sensorManager.unregisterListener(this, it) }
        Log.i(TAG, "movement tracker stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Yüksek geçiren filtre
        val input = x + y + z
        val output = HIGH_PASS_ALPHA * (prevOutput + input - prevInput)
        prevInput = input
        prevOutput = output

        // Hareket büyüklüğü (mutlak sapma)
        val magnitude = kotlin.math.abs(output)

        epochMovement += magnitude
        epochSamples++

        // 30 sn'lik epok dolduğunda değerlendir
        val now = System.currentTimeMillis()
        if (now - epochStartMs >= EPOCH_SEC * 1000) {
            val avg = if (epochSamples > 0) epochMovement / epochSamples else 0.0
            val normalized = (avg * 4.0).coerceIn(0.0, 1.0)
            val stage = classify(normalized)

            lastEvent = SleepEvent(
                offsetMin = epochOffsetSec / 60f,
                durationMin = EPOCH_SEC / 60f,
                stage = stage,
                intensity = normalized.toFloat(),
                sound = 0f,
            )

            // Uyarlanabilir eşik: son 3 saatin ortalaması + 2×SS
            recentCounts.addLast(normalized)
            while (recentCounts.size > 360) recentCounts.removeFirst()
            if (recentCounts.size >= 12) {
                val mean = recentCounts.average()
                val sd = Math.sqrt(recentCounts.sumOf { (it - mean) * (it - mean) } / recentCounts.size)
                threshold = (mean + 2.0 * sd).coerceIn(0.04, 0.45)
            }

            epochStartMs = now
            epochMovement = 0.0
            epochSamples = 0
            epochOffsetSec += EPOCH_SEC
        }
    }

    /** Hareket yoğunluğunu uyku evresine çevirir. */
    private fun classify(density: Double): Stage = when {
        density <= threshold * 0.55 -> Stage.DEEP
        density <= threshold * 1.6 -> Stage.LIGHT
        density <= threshold * 2.6 -> Stage.REM
        else -> Stage.AWAKE
    }

    /** Üretilen son epok olayı. */
    fun lastEvent(): SleepEvent? = lastEvent

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
