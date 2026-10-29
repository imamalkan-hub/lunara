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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.sleep.data.ScreeningResult
import com.lunara.sleep.ui.MainViewModel
import com.lunara.sleep.ui.components.SectionHeader
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Alert
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Hairline
import com.lunara.sleep.ui.theme.Ok
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Surface2
import com.lunara.sleep.ui.theme.Text1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.Warn
import com.lunara.sleep.ui.theme.riskColor

/**
 * Uyku apnesi tarama ekranı.
 *
 * **Tıbbi sınır:** bu bir *tarama* (screening) aracıdır, tanı koymaz.
 * Sonuç kullanıcıyı bir uyku doktoruna yönlendirir; kesin tanı yalnızca
 * uyku testi (polysomnografi) ile konur. FDA "genel sağlık" politikası
 * gereği "tanıklarım" / "apne tespit ettim" gibi ifadeler kullanılmaz.
 */
@Composable
fun ScreeningScreen(
    vm: MainViewModel,
    onFindDoctor: () -> Unit,
) {
    var kind by remember { mutableStateOf("stop_bang") }
    var answers by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var result by remember { mutableStateOf<ScreeningResult?>(null) }
    var submitting by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Risk screening",
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            color = Text1,
            letterSpacing = (-0.5).sp,
        )
        Text(
            "Not a diagnosis. A guide for discussing concerns with your doctor.",
            fontSize = 12.sp,
            color = Text3,
        )

        Spacer(Modifier.height(18.dp))

        // Tarama seçimi
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ScreeningTab("STOP-BANG", "stop_bang", kind) { kind = it; answers = emptyMap(); result = null }
            ScreeningTab("Epworth", "epworth", kind) { kind = it; answers = emptyMap(); result = null }
        }

        Spacer(Modifier.height(20.dp))

        if (result == null) {
            when (kind) {
                "stop_bang" -> StopBangForm(answers) { q, v ->
                    answers = answers.toMutableMap().apply { put(q, v) }
                }
                "epworth" -> EpworthForm(answers) { q, v ->
                    answers = answers.toMutableMap().apply { put(q, v) }
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    submitting = true
                    vm.submitScreening(kind, answers) { r ->
                        result = r
                        submitting = false
                    }
                },
                enabled = !submitting && answers.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Canvas,
                    disabledContainerColor = Surface2,
                    disabledContentColor = Text3,
                ),
            ) {
                Text(
                    if (submitting) "Scoring…" else "Score my risk",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }
        } else {
            ScreeningResultView(result!!, onFindDoctor = onFindDoctor, onReset = { result = null; answers = emptyMap() })
        }

        Spacer(Modifier.height(24.dp))

        // Yasal uyarı
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Surface1.copy(alpha = 0.7f))
                .padding(14.dp),
        ) {
            Text(
                "Lunara is not a medical device and never makes a diagnosis. " +
                    "If you stop breathing during sleep, feel excessively sleepy during the day, " +
                    "or wake with morning headaches, speak to a sleep doctor. " +
                    "Only a sleep study can confirm a diagnosis.",
                color = Text3,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

/** Tarama sekmesi. */
@Composable
private fun ScreeningTab(label: String, id: String, selected: String, onSelect: (String) -> Unit) {
    val active = id == selected
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (active) Accent else Surface1)
            .clickable { onSelect(id) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (active) Canvas else Text2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** STOP-BANG formu — 8 evet/hayır sorusu. */
@Composable
private fun StopBangForm(answers: Map<String, Int>, onAnswer: (String, Int) -> Unit) {
    val questions = listOf(
        Triple("snoring", "Do you snore loudly while you sleep?", "Has someone else told you?"),
        Triple("tired", "Do you feel tired or sleepy during the day?", "Have you dozed off while driving?"),
        Triple("observed", "Have you noticed yourself stopping breathing or choking in your sleep?", "Has a partner or family member mentioned it?"),
        Triple("pressure", "Do you have high blood pressure?", "Are you being monitored for blood pressure?"),
        Triple("bmi", "Are you overweight? (BMI over 35)", "Is your BMI above 35?"),
        Triple("age", "Are you over 50 years old?", ""),
        Triple("neck", "Do you have a large or thick neck?", "Is your shirt collar over 40 cm (16 in)?"),
        Triple("gender", "Are you male?", ""),
    )

    Column {
        SectionHeader(
            title = "STOP-BANG",
            subtitle = "Answer yes or no to each question",
        )
        Spacer(Modifier.height(14.dp))

        questions.forEach { (key, label, hint) ->
            QuestionCard(
                label = label,
                hint = hint,
                selected = answers[key],
                onSelect = { onAnswer(key, it) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Epworth formu — 8 durum, 0-4 puan. */
@Composable
private fun EpworthForm(answers: Map<String, Int>, onAnswer: (String, Int) -> Unit) {
    val questions = listOf(
        "reading" to "Sitting in bed reading",
        "tv" to "Watching television",
        "sitting" to "Sitting inactive in a public place",
        "car" to "As a passenger in a car for a short while",
        "lying" to "Lying down to rest in the afternoon",
        "talk" to "Sitting and talking to someone",
        "restless" to "Sitting quietly after lunch without alcohol",
        "driving" to "Driving a car",
    )

    Column {
        SectionHeader(
            title = "Epworth Sleepiness Scale",
            subtitle = "Choose your likelihood of dozing in each situation",
        )
        Spacer(Modifier.height(14.dp))

        questions.forEach { (key, label) ->
            ScaleQuestion(
                label = label,
                selected = answers[key],
                onSelect = { onAnswer(key, it) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Evet/hayır soru kartı. */
@Composable
private fun QuestionCard(
    label: String,
    hint: String,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(15.dp),
    ) {
        Text(label, color = Text1, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(hint, color = Text3, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            YesNoButton("Yes", selected == 1, Modifier.weight(1f)) { onSelect(1) }
            YesNoButton("No", selected == 0, Modifier.weight(1f)) { onSelect(0) }
        }
    }
}

/** Evet/hayır düğmesi. */
@Composable
private fun YesNoButton(label: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) Accent else Surface2)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) Canvas else Text2,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** 0-4 ölçekli soru. */
@Composable
private fun ScaleQuestion(
    label: String,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .padding(15.dp),
    ) {
        Text(label, color = Text1, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..4).forEach { value ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected == value) Accent else Surface2)
                        .clickable { onSelect(value) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "$value",
                        color = if (selected == value) Canvas else Text2,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Never doze", color = Text3, fontSize = 10.sp)
            Text("Very likely", color = Text3, fontSize = 10.sp)
        }
    }
}

/** Tarama sonucu görünümü. */
@Composable
private fun ScreeningResultView(
    result: ScreeningResult,
    onFindDoctor: () -> Unit,
    onReset: () -> Unit,
) {
    val color = riskColor(result.riskLevel)

    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${result.score}/${result.maxScore}",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = color,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    result.result,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Text1,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${result.name} · screening result",
                    fontSize = 11.sp,
                    color = Text3,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface1)
                .padding(16.dp),
        ) {
            Text(result.advice, color = Text2, fontSize = 13.sp, lineHeight = 19.sp)
        }

        if (result.actions.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader(
                title = "Recommendations",
                subtitle = "Steps you can take tonight",
            )
            Spacer(Modifier.height(12.dp))
            result.actions.forEach { action ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Surface1)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Accent),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(action.title, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(action.detail, color = Text3, fontSize = 12.sp, lineHeight = 16.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onFindDoctor,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
        ) {
            Text("Find a sleep doctor to book", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Text2),
        ) {
            Text("Try again", fontSize = 13.sp)
        }
    }
}
