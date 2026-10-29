package com.lunara.sleep.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunara.sleep.BuildConfig
import com.lunara.sleep.ui.components.SectionHeader
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Hairline
import com.lunara.sleep.ui.theme.Ok
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Text1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3

/** Uygulamanın kaynak sitesi. */
const val SOURCE_SITE = "https://findsleepdoctors.com/"

/**
 * About ekranı.
 *
 * Üç şeyi açıkça belgeler:
 *  1. Uygulama **ücretsizdir** — hiçbir ödeme duvarı, reklam veya premium katman yok.
 *  2. Uygulama **findsleepdoctors.com** tarafından geliştirilmiştir.
 *  3. Gizlilik politikası — ham sesin cihazdan hiç çıkmadığı.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Canvas),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Text2)
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    "About Lunara",
                    style = MaterialTheme.typography.titleLarge,
                    color = Text1,
                )
            }
        }

        // --- Ücretsiz vurgusu ---
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface1)
                    .padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Ok,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "100% free — forever",
                            color = Ok,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Lunara is completely free. There are no ads, no subscriptions, no " +
                            "premium tier and no paywall. Every feature — overnight sleep " +
                            "tracking, hypnograms, sleep scores, risk screening and the " +
                            "full doctor directory — is available at no cost.",
                        color = Text2,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                }
            }
        }

        // --- Kaynak site ---
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface1)
                    .padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Developed by findsleepdoctors.com",
                            color = Accent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Lunara was developed by the team behind findsleepdoctors.com, " +
                            "the free sleep clinic directory. The sleep doctor and clinic " +
                            "listings you see in this app come directly from that directory, " +
                            "and each listing shows its own public source so you can verify it.",
                        color = Text2,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    LinkButton(
                        label = "findsleepdoctors.com",
                        url = SOURCE_SITE,
                    )
                }
            }
        }

        // --- Gizlilik politikası ---
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface1)
                    .padding(18.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Text1,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Privacy Policy",
                            color = Text1,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(12.dp))

                    PrivacyParagraph(
                        heading = "What we collect",
                        body = "Your sleep sessions (start and end time, derived sleep stages, " +
                            "snore and breathing-event counts, device model) and your " +
                            "screening questionnaire answers. If you create an account we " +
                            "store your email address and display name.",
                    )
                    PrivacyParagraph(
                        heading = "What we never collect",
                        body = "Raw microphone audio never leaves your device. Lunara analyses " +
                            "sound in memory to detect snore and breathing events and sends " +
                            "only numeric counts and relative levels — never the recording. We " +
                            "do not collect your location; it is used on your device to sort " +
                            "nearby clinics and is never transmitted.",
                    )
                    PrivacyParagraph(
                        heading = "How your data is used",
                        body = "Your data is used only to show your own history, statistics and " +
                            "reports. It is never sold, never shared with advertisers and never " +
                            "used to build advertising profiles.",
                    )
                    PrivacyParagraph(
                        heading = "Doctor and clinic listings",
                        body = "Clinic information is compiled from findsleepdoctors.com and " +
                            "verified against public sources such as the NPI registry, the " +
                            "sleepclinicdirectory.com directory and the NHS. Each listing shows " +
                            "its source and source link. Contact the clinic directly for " +
                            "appointments and fees.",
                    )
                    PrivacyParagraph(
                        heading = "Not a medical device",
                        body = "Lunara is a wellness and screening tool. It does not diagnose " +
                            "sleep apnea or any other condition, and it does not replace a " +
                            "sleep study or medical advice. Consult a sleep doctor if you have " +
                            "concerning symptoms.",
                    )
                    PrivacyParagraph(
                        heading = "Your choices",
                        body = "Sleep tracking works without an account. You can delete any " +
                            "night from the History tab at any time, which removes it from the " +
                            "server as well.",
                    )

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Last updated: 29 October 2026",
                        color = Text3,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        // --- Sürüm bilgisi ---
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface1.copy(alpha = 0.6f))
                    .padding(18.dp),
            ) {
                Text("Version ${BuildConfig.VERSION_NAME}", color = Text2, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text("Package: ${BuildConfig.APPLICATION_ID}", color = Text3, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Free sleep tracking and sleep doctor finder by findsleepdoctors.com",
                    color = Text3,
                    fontSize = 11.sp,
                )
            }
        }

        // --- Demo hesap ---
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Surface1)
                    .padding(18.dp),
            ) {
                Text(
                    "DEMO ACCOUNT",
                    color = Accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.9.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "demo@lunara.app",
                    color = Text1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "demo1234",
                    color = Text1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "A shared account with sample data so you can explore Lunara without " +
                        "signing up. It holds no personal information — you can delete " +
                        "anything you add.",
                    color = Text3,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                "Lunara is not affiliated with, endorsed by, or a substitute for " +
                    "professional medical advice, diagnosis or treatment.",
                color = Text3,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

/** Gizlilik politikası başlığı + paragraf. */
@Composable
private fun PrivacyParagraph(heading: String, body: String) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(
            heading.uppercase(),
            color = Accent,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            color = Text2,
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

/** Dış bağlantı butonu. */
@Composable
private fun LinkButton(label: String, url: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Hairline)
            .clickable { openUrl(context, url) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = Text3,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** URL'yi tarayıcıda açar. */
private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (_: Exception) {
        // Tarayıcı yoksa yoksay.
    }
}
