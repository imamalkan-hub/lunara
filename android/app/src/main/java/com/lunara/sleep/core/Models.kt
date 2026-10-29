package com.lunara.sleep.core

/**
 * Uyku evresi. Grafiklerde ve hesaplarda bu sıra kullanılır.
 *
 * Renkler `Color.kt` içinde; her evre ayrıca ikinci bir kanal (kenar/desen)
 * taşır ki renk körlüğünde ayrım bozulmasın.
 */
enum class Stage(val key: String, val label: String) {
    AWAKE("awake", "Awake"),
    LIGHT("light", "Light"),
    DEEP("deep", "Deep"),
    REM("rem", "REM");

    companion object {
        fun fromKey(key: String): Stage? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Bir gece boyunca üretilen uyku olayı (30 sn ya da 1 dk kovalar).
 *
 * @param offsetMin gece başlangıcına göre dakika cinsinden konum
 * @param durationMin olayın süresi (dk)
 * @param intensity hareket yoğunluğu (0..1) — jeneratör kovası için ortalama
 * @param sound ses yoğunluğu (0..1) — ortam gürültüsü / horlama seviyesi
 */
data class SleepEvent(
    val offsetMin: Float,
    val durationMin: Float,
    val stage: Stage,
    val intensity: Float = 0f,
    val sound: Float = 0f,
)

/** Bir uyku oturumu (bir gece). */
data class SleepSession(
    val id: String = "",
    val clientId: String = "",
    val startedAt: Long = 0L,
    val endedAt: Long = 0L,
    val timezone: String = "UTC",
    val deviceModel: String = "",
    val events: List<SleepEvent> = emptyList(),
    val note: String = "",
    val factors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val snoreMin: Int = 0,
    val snoreEvents: Int = 0,
    val peakDb: Float = 0f,
    val avgDb: Float = 0f,
    val noiseEvents: Int = 0,
) {
    val inBedMin: Int
        get() {
            if (events.isEmpty()) return 0
            val last = events.maxOf { it.offsetMin + it.durationMin }
            return last.toInt()
        }

    val totalSleepMin: Int
        get() = events.filter { it.stage != Stage.AWAKE }.sumOf { it.durationMin.toDouble() }.toInt()

    val stageMinutes: Map<Stage, Int>
        get() = Stage.entries.associateWith { stage ->
            events.filter { it.stage == stage }.sumOf { it.durationMin.toDouble() }.toInt()
        }

    val efficiencyPct: Int
        get() {
            val bed = inBedMin
            return if (bed > 0) (totalSleepMin * 100 / bed) else 0
        }

    val wakeCount: Int
        get() = events.count { it.stage == Stage.AWAKE }

    /** Uyanık kovaların toplamı. */
    val awakeMin: Int
        get() = stageMinutes[Stage.AWAKE] ?: 0

    /** Uykuya dalma gecikmesi: baştan itibaren ilk uyku kovasına kadar. */
    val latencyMin: Int
        get() {
            var leading = 0f
            for (e in events.sortedBy { it.offsetMin }) {
                if (e.stage != Stage.AWAKE) break
                leading += e.durationMin
            }
            return leading.toInt().coerceAtMost(45)
        }
}

/** Yatakta geçen sürenin bileşenleri. */
data class SleepTotals(
    val awake: Int = 0,
    val light: Int = 0,
    val deep: Int = 0,
    val rem: Int = 0,
) {
    val totalSleep: Int get() = light + deep + rem
}

/** Apne-benzeri nefes boşluğu olayı (ses tabanlı tarama). */
data class BreathingGap(
    val offsetMin: Float,
    val durationSec: Int,
)

/** Nefes tarama sonucu — "tarama", tanı değildir. */
data class ApneaScreening(
    val level: String,   // low | moderate | high | unknown
    val score: Int,
    val label: String,
    val gapCount: Int = 0,
    val longestGapSec: Int = 0,
)

/** Bir gecenin özet metrikleri. */
data class SessionSummary(
    val session: SleepSession,
    val score: Int,
    val totals: SleepTotals,
    val apnea: ApneaScreening,
)
