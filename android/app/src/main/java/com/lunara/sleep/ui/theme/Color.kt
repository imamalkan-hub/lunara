package com.lunara.sleep.ui.theme

import androidx.compose.ui.graphics.Color
import com.lunara.sleep.core.Stage

/**
 * Lunara tasarım token'ları.
 *
 * Palet rakipler araştırmasından türetildi: koyu, AMOLED uyumlu canvas ve
 * uyku evreleri için ayrılmış renkler. Her skor bandı **açıklık monotonik**
 * olacak şekilde seçildi — renk körlüğünde ve gri tonlamada anlamını korur.
 */

// Temel yüzeyler
val Canvas = Color(0xFF0B0B0F)
val Surface1 = Color(0xFF151D28)
val Surface2 = Color(0xFF1E2430)
val Hairline = Color(0xFF242B38)
val Track = Color(0xFF27272F)

// Metin
val Text1 = Color(0xFFF1E9D6)
val Text2 = Color(0xFF8A93A6)
val Text3 = Color(0xFF5A6272)

// Aksiyon
val Accent = Color(0xFF4FD1E6)
val Accent2 = Color(0xFF6C7BFF)
val Warn = Color(0xFFFFB74D)
val Alert = Color(0xFFF44336)
val Snore = Color(0xFFFF7043)
val Ok = Color(0xFF8BE28B)

// Uyku evreleri
val StageAwake = Color(0xFFFFB74D)
val StageRem = Color(0xFFE85A9E)
val StageLight = Color(0xFF4FC3F7)
val StageDeep = Color(0xFF3F51B5)
val StageInBed = Color(0xFF3A4152)

/** Evrenin rengi. */
val Stage.stageColor: Color
    get() = when (this) {
        Stage.AWAKE -> StageAwake
        Stage.LIGHT -> StageLight
        Stage.DEEP -> StageDeep
        Stage.REM -> StageRem
    }

/**
 * Skor bandı — açıklık monotonik ramp (poor → excellent) böylece gri
 * tonlamada ve renk körlüğünde ayrım korunur. Band her zaman sözcük
 * etiketiyle birlikte gösterilir.
 */
fun scoreColor(score: Int): Color = when {
    score < 40 -> Alert
    score < 60 -> Warn
    score < 80 -> Accent
    else -> Ok
}

/** Skor bandının sözcük etiketi — erişilebilirliğin renk olmayan kanalı. */
fun scoreGrade(score: Int): String = when {
    score < 40 -> "Poor"
    score < 60 -> "Fair"
    score < 80 -> "Good"
    else -> "Great"
}

/** Risk seviyesi rengi. Kırmızı/yeşil ayrımı kullanılmaz (renk körlüğü). */
fun riskColor(level: String): Color = when (level) {
    "high" -> Alert
    "moderate" -> Warn
    "low" -> Ok
    else -> Text3
}
