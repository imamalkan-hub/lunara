package com.lunara.sleep.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Uyku skoru ve evre çıkarımı.
 *
 * Bu, backend'deki `sleep-engine.js` ile **aynı kuralları** uygular; iki
 * tarafın farklı sonuç vermemesi için kural setleri eşitlenmiştir. Cihaz
 * tarafı çevrimdışı çalışır, sunucu tarafı doğrulama ve senkronizasyon
 * içindir.
 */
object SleepEngine {

    /** Kova uzunluğu (dk). Telefon-only sensörler için 1 dk yeterli hassasiyet. */
    const val BUCKET_MIN = 1

    /** Bu süreden kısa uyanıklıklar gerçek uyanma sayılmaz (gürültü filtresi). */
    const val MIN_WAKE_MIN = 4

    /** Uyku penceresi: bu süreden az uyku geçerli sayılmaz. */
    const val MIN_SLEEP_MIN = 60

    /** Uyanık öncülü: yatış ama uyuyamama en fazla bu kadar sayılır. */
    const val MAX_LATENCY_MIN = 45

    /**
     * Kovaları oluşturur ve evreleri çıkarır.
     *
     * Yaklaşım: olayları 1 dakikalık kovalara indirger, her koval için
     * hareket yoğunluğu ortalaması hesaplar, profili çeyreklik eşikleriyle
     * dört banta ayırır ve kısa uyanıklıkları yumuşatır.
     */
    fun classifyStages(events: List<SleepEvent>): List<SleepEvent> {
        if (events.isEmpty()) return emptyList()

        data class Bucket(val idx: Int, var movement: Double = 0.0, var weight: Double = 0.0,
                          var sound: Double = 0.0, var wakeWeight: Double = 0.0)

        val buckets = HashMap<Int, Bucket>()
        for (e in events) {
            val idx = (e.offsetMin / BUCKET_MIN).toInt()
            val b = buckets.getOrPut(idx) { Bucket(idx) }
            val w = max(e.durationMin.toDouble(), 0.001)
            b.movement += e.intensity * w
            b.weight += w
            b.sound = max(b.sound, e.sound.toDouble())
            if (e.stage == Stage.AWAKE) b.wakeWeight += w
        }

        val list = buckets.values.sortedBy { it.idx }
        val density = list.map { if (it.weight > 0) it.movement / it.weight else 0.0 }

        val sorted = density.sorted()
        fun q(p: Double): Double = sorted[min(sorted.size - 1, (p * sorted.size).toInt())]

        val tLow = q(0.20)
        val tMid = q(0.50)
        val tHigh = q(0.85)

        val classified = list.mapIndexed { i, b ->
            val d = density[i]
            val stage = when {
                d <= tLow -> Stage.DEEP
                d <= tMid -> Stage.LIGHT
                d <= tHigh -> Stage.REM
                else -> Stage.AWAKE
            }
            // Hareket düşük ama gürültü yüksekse bu uyanık değil, bozulmuş
            // bir uyku dilimi — uyanık saymak yanlış olurdu.
            val finalStage = if (stage == Stage.DEEP && b.wakeWeight >= MIN_WAKE_MIN && b.sound > 0.6) Stage.AWAKE else stage
            SleepEvent(
                offsetMin = (b.idx * BUCKET_MIN).toFloat(),
                durationMin = BUCKET_MIN.toFloat(),
                stage = finalStage,
                intensity = d.toFloat(),
                sound = b.sound.toFloat(),
            )
        }

        return smoothAwake(classified)
    }

    /** Kısa uyanıklıkları (kıpırdanma, alarm) birleştirir. */
    private fun smoothAwake(stages: List<SleepEvent>): List<SleepEvent> {
        if (stages.isEmpty()) return stages
        val out = stages.toMutableList()
        var i = 0
        while (i < out.size) {
            if (out[i].stage != Stage.AWAKE) { i++; continue }
            var j = i
            var total = out[i].durationMin
            while (j + 1 < out.size && out[j + 1].stage == Stage.AWAKE) {
                j++
                total += out[j].durationMin
            }
            if (total < MIN_WAKE_MIN) {
                for (k in i..j) out[k] = out[k].copy(stage = Stage.LIGHT)
            }
            i = j + 1
        }
        return out
    }

    /** Evre sürelerini toplar. */
    fun summarize(stages: List<SleepEvent>): SleepTotals {
        var awake = 0.0; var light = 0.0; var deep = 0.0; var rem = 0.0
        for (s in stages) {
            when (s.stage) {
                Stage.AWAKE -> awake += s.durationMin
                Stage.LIGHT -> light += s.durationMin
                Stage.DEEP -> deep += s.durationMin
                Stage.REM -> rem += s.durationMin
            }
        }
        return SleepTotals(awake.toInt(), light.toInt(), deep.toInt(), rem.toInt())
    }

    /**
     * Uyku skoru (0-100).
     *
     * Bileşenler: süre %25, verimlilik %20, derin %20, REM %15,
     * gecikme %10, bütünlük %10.
     */
    fun score(totals: SleepTotals, latencyMin: Int, wakeCount: Int, totalSleepMin: Int, inBedMin: Int): Int {
        if (totalSleepMin <= 0) return 0

        val durScore = (100 - abs(totalSleepMin - 420) / 150.0 * 100).coerceIn(0.0, 100.0)
        val eff = if (inBedMin > 0) totalSleepMin * 100.0 / inBedMin else 100.0
        val effScore = ((eff - 60) / 30.0 * 100).coerceIn(0.0, 100.0)

        val deepPct = totals.deep * 100.0 / totalSleepMin
        val deepScore = if (deepPct in 13.0..23.0) 100.0 else (100 - abs(deepPct - 18) * 7).coerceAtLeast(0.0)

        val remPct = totals.rem * 100.0 / totalSleepMin
        val remScore = if (remPct in 20.0..25.0) 100.0 else (100 - abs(remPct - 22.5) * 8).coerceAtLeast(0.0)

        val latScore = (100 - max(0, latencyMin - 10) * 5.0).coerceAtLeast(0.0)
        val wakeScore = (100 - wakeCount * 8.0 - totals.awake * 1.5).coerceAtLeast(0.0)

        val score = durScore * 0.25 + effScore * 0.20 + deepScore * 0.20 +
                    remScore * 0.15 + latScore * 0.10 + wakeScore * 0.10
        return score.roundToInt().coerceIn(0, 100)
    }

    /**
     * Nefes boşluklarını bulur: yüksek ses -> sessizlik -> yüksek ses paterni.
     * Bu bir **tarama** göstergesidir, tanı değildir.
     */
    fun detectBreathingGaps(events: List<SleepEvent>): List<BreathingGap> {
        val sorted = events.sortedBy { it.offsetMin }
        val gaps = mutableListOf<BreathingGap>()
        var lastSoundEnd: Float? = null

        for (e in sorted) {
            val loud = e.sound >= 0.35f || e.stage == Stage.AWAKE
            if (loud) {
                val prev = lastSoundEnd
                if (prev != null) {
                    val gapMin = e.offsetMin - prev
                    val gapSec = (gapMin * 60).roundToInt()
                    if (gapSec in 10..120) {
                        gaps.add(BreathingGap(prev, gapSec))
                    }
                }
                lastSoundEnd = e.offsetMin + e.durationMin
            }
        }
        return gaps
    }

    /** Nefes tarama riski — ses sinyali + uyukluk girdisine dayalı. */
    fun apneaRisk(
        snoreMin: Int,
        totalSleepMin: Int,
        gaps: List<BreathingGap>,
        sleepinessScore: Int = 0,
    ): ApneaScreening {
        val sleepHours = totalSleepMin / 60.0
        if (sleepHours <= 0) return ApneaScreening("unknown", 0, "Unknown")

        val snoreRatio = min(1.0, snoreMin / max(totalSleepMin, 1).toDouble() / 0.4)
        val gapPerHour = gaps.size / sleepHours
        val gapScore = min(1.0, gapPerHour / 8.0)
        val longest = gaps.maxOfOrNull { it.durationSec } ?: 0
        val longGapScore = min(1.0, longest / 60.0)
        val sleepScore = min(1.0, sleepinessScore / 16.0)

        val score = ((snoreRatio * 0.30 + gapScore * 0.35 + longGapScore * 0.20 + sleepScore * 0.15) * 100).roundToInt()

        val level: String
        val label: String
        when {
            score >= 65 -> { level = "high"; label = "High risk" }
            score >= 35 -> { level = "moderate"; label = "Moderate risk" }
            else -> { level = "low"; label = "Low risk" }
        }
        return ApneaScreening(level, score, label, gaps.size, longest)
    }

    /**
     * Uyku düzenliliği (0-100). Aynı saat dilimindeki uyku başlangıç
     * saatlerinin standart sapması tersine çevrilerek hesaplanır.
     */
    fun regularity(startMinutes: List<Int>): Int {
        if (startMinutes.size < 2) return 0
        val mean = startMinutes.average()
        val variance = startMinutes.sumOf { (it - mean) * (it - mean) } / startMinutes.size
        val sd = sqrt(variance)
        return (100 - (sd / 120.0 * 100)).roundToInt().coerceIn(0, 100)
    }

    /** Uyku borcu: ortalama süre - 8 saat hedef. Negatif = borç. */
    fun sleepDebt(sessions: List<SleepSession>, targetMinutes: Int = 480): Int {
        if (sessions.isEmpty()) return 0
        val avg = sessions.sumOf { it.totalSleepMin } / sessions.size
        return avg - targetMinutes
    }

    /**
     * Kronotip tahmini. Uyku başlangıç saati + ortalama süreye göre 4 sınıf.
     *
     * "Uyku ortası" (mid-sleep) hesaplanır: gece yarısını geçen başlangıçlarda
     * 24 saatlik göreli hesap yapılır — 23:00 yatıp 07:00 kalkmak 02:00 orta
     * nokta demektir, 26:00 değil.
     */
    fun chronotype(startMinutes: Int, totalSleepMin: Int): String {
        val startHours = ((startMinutes % 1440) + 1440) % 1440 / 60.0
        val midSleep = (startHours + (totalSleepMin / 60.0) / 2) % 24
        return when {
            midSleep < 3.0 -> "extreme_early"
            midSleep < 4.3 -> "early"
            midSleep <= 5.7 -> "intermediate"
            else -> "late"
        }
    }

    /** Bir oturumun tam özetini üretir. */
    fun summarize(session: SleepSession): SessionSummary {
        val stages = classifyStages(session.events)
        val totals = summarize(stages)
        val gaps = detectBreathingGaps(session.events)
        val apnea = apneaRisk(session.snoreMin, totals.totalSleep, gaps)
        val score = score(totals, session.latencyMin, session.wakeCount, totals.totalSleep, session.inBedMin)
        return SessionSummary(session.copy(events = stages), score, totals, apnea)
    }
}
