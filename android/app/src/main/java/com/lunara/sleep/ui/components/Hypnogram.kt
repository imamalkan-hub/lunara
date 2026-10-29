package com.lunara.sleep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.sleep.core.SleepEvent
import com.lunara.sleep.core.Stage
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Hairline
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.StageInBed
import com.lunara.sleep.ui.theme.stageColor

/**
 * Hipnogram — gecenin uyku evreleri şeridi.
 *
 * Erişilebilirlik: her evre yalnızca renkle değil, **ayrı bir yükseklik**
 * (doluluk seviyesi) ile de kodlanır; böylece renk körlüğünde ve gri
 * tonlamada ayrım korunur. Ayrıca ekran okuyucu için tam bir cümle üretilir.
 */
@Composable
fun Hypnogram(
    events: List<SleepEvent>,
    modifier: Modifier = Modifier,
    highContrast: Boolean = false,
) {
    if (events.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("No record for this night", color = Text3, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val totalMin = events.maxOf { it.offsetMin + it.durationMin }.coerceAtLeast(1f)
    val description = buildDescription(events)

    Column(modifier = modifier.semantics { contentDescription = description }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Surface1),
        ) {
            // Yatakta geçen süre arka planı
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(StageInBed.copy(alpha = 0.12f)),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                horizontalArrangement = Arrangement.spacedBy(1.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                events.forEach { event ->
                    StageColumn(
                        event = event,
                        totalMin = totalMin,
                        highContrast = highContrast,
                        modifier = Modifier.weight(event.durationMin.coerceAtLeast(0.5f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        StageLegend()
    }
}

/** Tek bir evre sütunu — yüksekliği evre derinliğini kodlar. */
@Composable
private fun StageColumn(
    event: SleepEvent,
    totalMin: Float,
    highContrast: Boolean,
    modifier: Modifier = Modifier,
) {
    // Evre derinliği: REM ve derin uyku daha "yüksek" gösterilir.
    val fillFactor = when (event.stage) {
        Stage.AWAKE -> 0.35f
        Stage.LIGHT -> 0.55f
        Stage.REM -> 0.80f
        Stage.DEEP -> 1.0f
    }
    val color = if (highContrast) highContrastColor(event.stage) else event.stage.stageColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((96 * fillFactor).dp)
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color),
    )
}

/**
 * Yüksek kontrast paleti — açıklık monotonik (amber → beyaz → mavi → siyah).
 * Özel renk körlüğü modunda varsayılan olarak açılabilir.
 */
@Composable
fun highContrastColor(stage: Stage): Color = when (stage) {
    Stage.AWAKE -> Color(0xFFFFC107)
    Stage.LIGHT -> Color(0xFFFFFFFF)
    Stage.REM -> Color(0xFF64B5F6)
    Stage.DEEP -> Color(0xFF1A237E)
}

/** Evre gösterge kutusu — renk + etiket (renk tek başına anlam taşımaz). */
@Composable
fun StageLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Stage.entries.forEach { stage ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(stage.stageColor),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    stage.label,
                    color = Text3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** Ekran okuyucu için gecenin cümle özeti. */
private fun buildDescription(events: List<SleepEvent>): String {
    val total = events.filter { it.stage != Stage.AWAKE }.sumOf { it.durationMin.toDouble() }.toInt()
    val h = total / 60
    val m = total % 60
    val wakes = events.count { it.stage == Stage.AWAKE }
    return "Hypnogram. Total sleep $h hours $m minutes. " +
        "$wakes awakenings during the night. " +
        Stage.entries.joinToString(", ") { stage ->
            val mins = events.filter { it.stage == stage }.sumOf { it.durationMin.toDouble() }.toInt()
            "$stage.label $mins minutes"
        }
}

/** Yatay ayraç. */
@Composable
fun SectionDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Hairline, Hairline.copy(alpha = 0f), Hairline),
                ),
            ),
    )
}
