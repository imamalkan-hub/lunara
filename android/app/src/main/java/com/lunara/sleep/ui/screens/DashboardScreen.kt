package com.lunara.sleep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunara.sleep.core.SleepSession
import com.lunara.sleep.core.SessionSummary
import com.lunara.sleep.ui.MainViewModel
import com.lunara.sleep.ui.components.Hypnogram
import com.lunara.sleep.ui.components.ScoreRing
import com.lunara.sleep.ui.components.SectionHeader
import com.lunara.sleep.ui.components.StageDonut
import com.lunara.sleep.ui.components.StatTile
import com.lunara.sleep.ui.components.formatMinutes
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Alert
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Hairline
import com.lunara.sleep.ui.theme.Ok
import com.lunara.sleep.ui.theme.Snore
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Text1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.Warn
import com.lunara.sleep.ui.theme.riskColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ana ekran — dün gece, bugünün durumu ve hızlı eylemler.
 */
@Composable
fun DashboardScreen(
    vm: MainViewModel,
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onOpenDoctors: () -> Unit,
    onOpenScreening: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val tracking by vm.tracking.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val screenings by vm.screenings.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            HeaderRow(
                tracking = tracking,
                onStart = onStartTracking,
                onStop = onStopTracking,
                onOpenAbout = onOpenAbout,
            )
        }

        if (tracking) {
            item { TrackingBanner() }
        }

        item {
            if (summary != null) {
                LastNightCard(summary!!, stats)
            } else {
                EmptyStateCard(
                    onStart = onStartTracking,
                    onScreening = onOpenScreening,
                )
            }
        }

        // Uyku apnesi tarama sonucu — risk pozitifse doktor akışına yönlendir.
        val latestScreening = screenings.firstOrNull { it.kind == "stop_bang" }
        if (latestScreening != null && latestScreening.riskLevel != "low") {
            item {
                ScreeningAlertCard(
                    screening = latestScreening,
                    onFindDoctor = onOpenDoctors,
                )
            }
        }

        stats?.let { s ->
            if (s.nights > 1) {
                item {
                    Column {
                        SectionHeader(
                            title = "Last 30 days",
                            subtitle = "${s.nights} nights recorded",
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Regularity",
                                value = "${s.regularity}",
                                hint = "100 = perfect",
                                modifier = Modifier.weight(1f),
                                accent = if (s.regularity >= 80) Ok else Warn,
                            )
                            StatTile(
                                label = "Sleep debt",
                                value = formatMinutes(kotlin.math.abs(s.debtMinutes)),
                                hint = if (s.debtMinutes < 0) "below target" else "above target",
                                modifier = Modifier.weight(1f),
                                accent = if (s.debtMinutes < 0) Warn else Ok,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(
                                label = "Average score",
                                value = "${s.avgScore}",
                                modifier = Modifier.weight(1f),
                            )
                            StatTile(
                                label = "Chronotype",
                                value = chronotypeLabel(s.chronotype),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        item {
            QuickActions(
                onOpenDoctors = onOpenDoctors,
                onOpenScreening = onOpenScreening,
                onOpenHistory = onOpenHistory,
                onOpenAbout = onOpenAbout,
            )
        }
    }
}

/** Üst bar: uygulama adı ve takip düğmesi. */
@Composable
private fun HeaderRow(
    tracking: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Lunara",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Text1,
                letterSpacing = (-0.5).sp,
            )
            Text(
                if (tracking) "Tracking active" else "Sleep tracking and doctor finder",
                fontSize = 12.sp,
                color = if (tracking) Accent else Text3,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Free \u00b7 by findsleepdoctors.com",
                    fontSize = 10.sp,
                    color = Ok,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        IconButton(
            onClick = onOpenAbout,
            modifier = Modifier.size(38.dp),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = "About Lunara",
                tint = Text2,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.width(4.dp))

        Button(
            onClick = { if (tracking) onStop() else onStart() },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (tracking) Alert else Accent,
                contentColor = Canvas,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 18.dp, vertical = 10.dp,
            ),
        ) {
            Icon(
                if (tracking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (tracking) "Stop" else "Start tracking",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Takip sürerken gösterilen bilgi bandı. */
@Composable
private fun TrackingBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Accent.copy(alpha = 0.16f), Surface1),
                ),
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Accent),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text("Sleep recording in progress", color = Text1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Leave your phone charging beside the pillow. Your morning report will be ready.",
                color = Text2,
                fontSize = 12.sp,
            )
        }
    }
}

/** Dün gecenin kartı. */
@Composable
private fun LastNightCard(summary: SessionSummary, stats: com.lunara.sleep.data.SleepStats?) {
    val session = summary.session
    val dateFmt = SimpleDateFormat("d MMMM", Locale("tr"))

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(18.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    "LAST NIGHT",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Text3,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    dateFmt.format(Date(session.startedAt)),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Text1,
                )
            }
            Text(
                "Score ${summary.score}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = com.lunara.sleep.ui.theme.scoreColor(summary.score),
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScoreRing(score = summary.score, size = 128.dp)
            StageDonut(
                awakeMin = summary.totals.awake,
                lightMin = summary.totals.light,
                deepMin = summary.totals.deep,
                remMin = summary.totals.rem,
                size = 128.dp,
            )
        }

        Spacer(Modifier.height(18.dp))
        Hypnogram(events = session.events)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Total",
                value = formatMinutes(summary.totals.totalSleep),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Deep",
                value = formatMinutes(summary.totals.deep),
                modifier = Modifier.weight(1f),
                accent = com.lunara.sleep.ui.theme.StageDeep.copy(alpha = 0.9f),
            )
            StatTile(
                label = "REM",
                value = formatMinutes(summary.totals.rem),
                modifier = Modifier.weight(1f),
                accent = com.lunara.sleep.ui.theme.StageRem,
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                label = "Sleep latency",
                value = "${session.latencyMin} min",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Awakenings",
                value = "${session.wakeCount}",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Efficiency",
                value = "%${session.efficiencyPct}",
                modifier = Modifier.weight(1f),
            )
        }

        // Horlama ve nefes tarama özeti
        if (session.snoreEvents > 0 || summary.apnea.gapCount > 0) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E2430))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Snoring & breathing", color = Text3, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${session.snoreEvents} snore events · ${summary.apnea.gapCount} breathing gaps",
                        color = Text1,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    if (session.peakDb > 0) {
                        Text(
                            "Peak: ${session.peakDb.toInt()} dB above the quiet floor",
                            color = Text3,
                            fontSize = 11.sp,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        summary.apnea.label,
                        color = riskColor(summary.apnea.level),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("screening", color = Text3, fontSize = 10.sp)
                }
            }
        }
    }
}

/** İlk kullanım — boş durum kartı. */
@Composable
private fun EmptyStateCard(onStart: () -> Unit, onScreening: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Surface1)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🌙", fontSize = 40.sp)
        Spacer(Modifier.height(12.dp))
        Text("No recordings yet", color = Text1, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Start tracking when you go to bed. Your phone detects sleep stages from movement and sound.",
            color = Text2,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onStart,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Record your first night", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onScreening,
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Text2),
        ) {
            Text("Screen your apnea risk", fontSize = 13.sp)
        }
    }
}

/** Tarama sonucu uyarı kartı — doktor akışına bağlar. */
@Composable
private fun ScreeningAlertCard(
    screening: com.lunara.sleep.data.ScreeningResult,
    onFindDoctor: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(riskColor(screening.riskLevel).copy(alpha = 0.10f))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(riskColor(screening.riskLevel)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "STOP-BANG: ${screening.score}/${screening.maxScore} — ${screening.result}",
                color = Text1,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(screening.advice, color = Text2, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onFindDoctor,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
        ) {
            Text("Find sleep doctors near me", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Hızlı eylem kartları. */
@Composable
private fun QuickActions(
    onOpenDoctors: () -> Unit,
    onOpenScreening: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column {
        SectionHeader(title = "What you can do")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionCard(
                title = "Find a sleep doctor",
                subtitle = "Specialists near you",
                emoji = "🩺",
                modifier = Modifier.weight(1f),
                onClick = onOpenDoctors,
            )
            ActionCard(
                title = "Risk screening",
                subtitle = "STOP-BANG & Epworth",
                emoji = "📋",
                modifier = Modifier.weight(1f),
                onClick = onOpenScreening,
            )
        }
        Spacer(Modifier.height(10.dp))
        ActionCard(
            title = "Past nights",
            subtitle = "All recordings and reports",
            emoji = "📊",
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenHistory,
        )
        Spacer(Modifier.height(10.dp))
        ActionCard(
            title = "About Lunara",
            subtitle = "Free forever · by findsleepdoctors.com",
            emoji = "🌙",
            modifier = Modifier.fillMaxWidth(),
            onClick = onOpenAbout,
        )
    }
}

/** Eylem kartı. */
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(10.dp))
        Text(title, color = Text1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Text3, fontSize = 11.sp)
    }
}

/** Kronotip etiketi. */
fun chronotypeLabel(key: String): String = when (key) {
    "extreme_early" -> "Definite Early"
    "early" -> "Early Bird"
    "intermediate" -> "Intermediate"
    "late" -> "Night Owl"
    else -> "—"
}
