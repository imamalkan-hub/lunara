package com.lunara.sleep.tracking

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Horlama ve ortam gürültüsü algılayıcı.
 *
 * **Neden AudioRecord (MediaRecorder değil):** ham PCM erişimi, anlık genlik
 * (dBFS) ölçümü ve kendi VAD'imizi uygulayabilmemiz gerekiyor. MediaRecorder
 * sıkıştırılmış konteyner üretir — bunun üzerinden DSP yapılamaz.
 *
 * **Kaynak seçimi:** `VOICE_RECOGNITION` (API 29+ `UNPROCESSED` tercih edilir).
 * AGC (otomatik kazanç) ve gürültü bastırma kapalı olduğu için sessiz taban
 * seviyesi kaymaz — aksi halde dB ölçümleri anlamsızlaşır.
 *
 * **Önemli sınır:** Android'de kalibre edilmiş mutlak SPL (dB) ölçülemez.
 * Bu yüzden raporlanan değer **gece taban seviyesinin üstündeki dBFS**
 * olarak ifade edilir — cihazlar arası karşılaştırılabilir ve dürüst.
 */
class SnoreTracker(private val appContext: Context) {

    companion object {
        private const val TAG = "LunaraSnore"
        const val SAMPLE_RATE = 16_000

        /** Gece taban seviyesinin bu kadar üstü = horlama eşiği. */
        const val SNORE_ABOVE_FLOOR_DB = 9f

        /** Sesi olay olarak işaretlemek için gereken RMS eşiği. */
        const val LOUD_RMS = 0.045f

        /** Saklanacak azami horlama klibi sayısı (disk ve RAM sınırı). */
        const val MAX_CLIPS = 10

        /** RMS -> dBFS. 0 = -90 dB'e kırpılır (log(0) koruması). */
        fun rmsToDb(rms: Float): Float {
            if (rms <= 1e-7f) return -90f
            return (20.0 * log10(rms.toDouble())).toFloat().coerceAtLeast(-90f)
        }

        /** RMS hesabı. */
        fun rms(buf: ShortArray, len: Int): Float {
            if (len <= 0) return 0f
            var sum = 0.0
            for (i in 0 until len) {
                val v = buf[i] / 32768.0
                sum += v * v
            }
            return sqrt(sum / len).toFloat()
        }
    }

    private var recorder: AudioRecord? = null

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Gece boyunca toplanan 100 ms'lik RMS kovaları. */
    private val buckets = mutableListOf<Float>()

    @Volatile private var snoreEvents = 0
    @Volatile private var snoreMillis = 0L
    @Volatile private var peakAboveFloor = 0f
    @Volatile private var noiseEvents = 0
    @Volatile private var clipsSaved = 0

    /** Gece taban seviyesi (RMS'in 10. yüzdeliği). */
    @Volatile private var floorRms = 0f

    /** Horlama klipleri — en fazla MAX_CLIPS adet, 100 ms'lik pencereler. */
    private val clips = ArrayDeque<ByteArray>()

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            Log.w(TAG, "AudioRecord not supported")
            return
        }
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 2)

        val rec = try {
            AudioRecord(
                audioSource(),
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "no microphone permission", e)
            return
        }

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "AudioRecord could not start")
            rec.release()
            return
        }

        recorder = rec
        running = true
        rec.startRecording()

        thread = thread(name = "lunara-audio", isDaemon = true) {
            val buf = ShortArray(bufferSize / 2)
            val epoch = ShortArray(SAMPLE_RATE / 10) // 100 ms
            var epochLen = 0

            while (running) {
                val read = try { rec.read(buf, 0, buf.size) } catch (e: IllegalStateException) { break }
                if (read <= 0) continue

                for (i in 0 until read) {
                    epoch[epochLen++] = buf[i]
                    if (epochLen == epoch.size) {
                        val rms = rms(epoch, epochLen)
                        handleEpoch(rms, epoch)
                        epochLen = 0
                    }
                }
            }
        }
        Log.i(TAG, "audio tracker started ($SAMPLE_RATE Hz)")
    }

    fun stop() {
        if (!running) return
        running = false
        thread?.join(1500)
        thread = null
        recorder?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        recorder = null
        computeFloor()
        Log.i(TAG, "audio tracker stopped")
    }

    /**
     * 100 ms'lik kovayı değerlendirir.
     *
     * Zemin seviyesi henüz oluşmadıysa (ilk saniyeler) kova biriktirilir;
     * zemin hesaplandıktan sonra horlama eşiği "zemin + 9 dB" olur.
     */
    private fun handleEpoch(rms: Float, samples: ShortArray) {
        buckets.add(rms)
        if (buckets.size < 10) return

        if (floorRms <= 0f) computeFloor()

        val db = rmsToDb(rms)
        val floorDb = rmsToDb(floorRms)
        val above = db - floorDb

        if (above >= SNORE_ABOVE_FLOOR_DB && rms >= LOUD_RMS) {
            snoreEvents++
            snoreMillis += 100
            if (above > peakAboveFloor) peakAboveFloor = above
            if (clips.size < MAX_CLIPS) {
                clips.addLast(samples.toPcmBytes())
                clipsSaved++
            }
        } else if (rms >= LOUD_RMS * 1.4f) {
            noiseEvents++
        }
    }

    /** Gece taban seviyesi = RMS'in 10. yüzdeliği. */
    private fun computeFloor() {
        if (buckets.isEmpty()) return
        val sorted = buckets.sorted()
        floorRms = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.size - 1)]
    }

    /**
     * Ham (işlenmemiş) ses desteği varsa onu kullan; yoksa VOICE_RECOGNITION.
     * Her ikisinde de AGC ve gürültü bastırma kapalıdır.
     */
    private fun audioSource(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val supported = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            if (supported) return MediaRecorder.AudioSource.UNPROCESSED
        }
        return MediaRecorder.AudioSource.VOICE_RECOGNITION
    }

    // --- Özet ---
    fun snoreEvents(): Int = snoreEvents
    fun snoreMinutes(): Int = (snoreMillis / 60_000).toInt()
    fun peakAboveFloorDb(): Float = peakAboveFloor
    fun avgDb(): Float = if (buckets.isEmpty()) 0f else rmsToDb(buckets.average().toFloat())
    fun noiseEvents(): Int = noiseEvents
    fun clipsSaved(): Int = clipsSaved
    fun snoreClips(): List<ByteArray> = clips.toList()
    fun reset() {
        buckets.clear()
        clips.clear()
        floorRms = 0f
        snoreEvents = 0
        snoreMillis = 0
        peakAboveFloor = 0f
        noiseEvents = 0
        clipsSaved = 0
    }
}

/** ShortArray -> 16-bit little-endian PCM baytları. */
private fun ShortArray.toPcmBytes(): ByteArray {
    val out = ByteArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt()
        out[i * 2] = (v and 0xFF).toByte()
        out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
    }
    return out
}
