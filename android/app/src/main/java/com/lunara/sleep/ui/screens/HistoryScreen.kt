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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunara.sleep.core.SleepEngine
import com.lunara.sleep.core.SleepSession
import com.lunara.sleep.ui.MainViewModel
import com.lunara.sleep.ui.components.Hypnogram
import com.lunara.sleep.ui.components.formatMinutes
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Text1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.scoreColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Geçmiş geceler — tüm kayıtlar, gün gün.
 */
@Composable
fun HistoryScreen(vm: MainViewModel) {
    val history by vm.history.collectAsStateWithLifecycle()
    var expandedId by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    "History",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    color = Text1,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    "${history.size} nights recorded",
                    fontSize = 12.sp,
                    color = Text3,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        if (history.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No recordings yet", color = Text3, fontSize = 14.sp)
                }
            }
        }

        items(history, key = { it.clientId }) { session ->
            HistoryCard(
                session = session,
                expanded = expandedId == session.clientId,
                onToggle = {
                    expandedId = if (expandedId == session.clientId) null else session.clientId
                },
                onDelete = { vm.deleteLocalSession(session.clientId) },
            )
        }
    }
}

/** Geçmiş kartı — kapalı/açık durum. */
@Composable
private fun HistoryCard(
    session: SleepSession,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = SleepEngine.summarize(session)
    val dateFmt = SimpleDateFormat("d MMMM EEEE", Locale("tr"))
    val timeFmt = SimpleDateFormat("HH:mm", Locale("tr"))

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .clickable(onClick = onToggle)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    dateFmt.format(Date(session.startedAt)),
                    color = Text1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${timeFmt.format(Date(session.startedAt))} – ${if (session.endedAt > 0) timeFmt.format(Date(session.endedAt)) else "—"}" +
                        " · ${formatMinutes(summary.totals.totalSleep)}",
                    color = Text3,
                    fontSize = 12.sp,
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(scoreColor(summary.score).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${summary.score}",
                    color = scoreColor(summary.score),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            Hypnogram(events = session.events)
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniStat("Deep", formatMinutes(summary.totals.deep))
                MiniStat("REM", formatMinutes(summary.totals.rem))
                MiniStat("Light", formatMinutes(summary.totals.light))
                MiniStat("Awake", formatMinutes(summary.totals.awake))
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniStat("Efficiency", "%${session.efficiencyPct}")
                MiniStat("Latency", "${session.latencyMin} min")
                MiniStat("Awakenings", "${session.wakeCount}")
                if (session.snoreEvents > 0) MiniStat("Snoring", "${session.snoreEvents}")
            }

            if (session.note.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "\"${session.note}\"",
                    color = Text2,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDelete,
                    shape = CircleShape,
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = Text3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("Delete", fontSize = 12.sp)
                }
                androidx.compose.material3.Button(
                    onClick = { },
                    shape = CircleShape,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("Doctor report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Küçük istatistik. */
@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label.uppercase(), color = Text3, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
