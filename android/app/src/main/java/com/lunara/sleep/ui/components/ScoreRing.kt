package com.lunara.sleep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.sleep.ui.theme.StageAwake
import com.lunara.sleep.ui.theme.StageDeep
import com.lunara.sleep.ui.theme.StageLight
import com.lunara.sleep.ui.theme.StageRem
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.Track
import com.lunara.sleep.ui.theme.scoreColor
import com.lunara.sleep.ui.theme.scoreGrade

/**
 * Uyku skoru halkası.
 *
 * Halka saat 12'den başlayıp saat yönünde ilerler. Skor bandı rengi
 * **açıklık monotoniktir** (kötü → iyi), böylece gri tonlamada da anlamını
 * korur; ayrıca bandın sözcük etiketi her zaman yanında görünür.
 */
@Composable
fun ScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 148.dp,
    showGrade: Boolean = true,
) {
    val animated by animateFloatAsState(
        targetValue = score.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 900),
        label = "score",
    )
    val color = scoreColor(score)
    val grade = scoreGrade(score)

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Sleep score $score / 100, $grade" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.085f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)

            // Arka plan halkası
            drawArc(
                color = Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Skor yayı — saat 12'den başlar
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(color.copy(alpha = 0.55f), color, color.copy(alpha = 0.8f)),
                ),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                color = color,
                letterSpacing = (-1.5).sp,
            )
            if (showGrade) {
                Text(
                    text = grade,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Text2,
                )
            }
        }
    }
}

/**
 * Evre dağılımı halkası — uyku skoru halkasının yanında kullanılır.
 * Her dilim kendi rengini ve doluluğunu taşır.
 */
@Composable
fun StageDonut(
    awakeMin: Int,
    lightMin: Int,
    deepMin: Int,
    remMin: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 148.dp,
) {
    val total = (awakeMin + lightMin + deepMin + remMin).coerceAtLeast(1)

    val segments = listOf(
        Triple(deepMin, StageDeep, "Deep"),
        Triple(remMin, StageRem, "REM"),
        Triple(lightMin, StageLight, "Light"),
        Triple(awakeMin, StageAwake, "Awake"),
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = "Sleep stage breakdown: " +
                    segments.joinToString(", ") { "${it.third} ${it.first} minutes" }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.085f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            var startAngle = -90f

            segments.forEach { (mins, color, _) ->
                if (mins <= 0) return@forEach
                val sweep = 360f * mins / total
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatMinutes(lightMin + deepMin + remMin),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1E9D6),
            )
            Text(
                text = "asleep",
                fontSize = 11.sp,
                color = Text3,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Süreyi "7s 12dk" biçiminde yazar. */
fun formatMinutes(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

/** Statistik kartı — etiket + değer. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    hint: String? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF151D28))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Text3,
            letterSpacing = 0.9.sp,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = accent ?: Color(0xFFF1E9D6),
        )
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(text = hint, fontSize = 11.sp, color = Text3)
        }
    }
}

/** Başlık + isteğe bağlı aksiyon satırı. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFF1E9D6),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Text3,
                )
            }
        }
        action?.invoke()
    }
}
