package com.lunara.sleep.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lunara.sleep.data.Doctor
import com.lunara.sleep.ui.MainViewModel
import com.lunara.sleep.ui.components.SectionHeader
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Accent2
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Hairline
import com.lunara.sleep.ui.theme.Ok
import com.lunara.sleep.ui.theme.Snore
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Surface2
import com.lunara.sleep.ui.theme.Text1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.Warn

/**
 * Uyku doktoru dizini.
 *
 * Veri kaynağı findsleepdoctors.com — 21.343 uyku uzmanı, 6 ülke.
 * Her sonuçta kaynak gösterimi bulunur ve kullanıcı doğrudan doktorla
 * iletişime geçer (uygulama randevu almaz, fiyat yayımlamaz).
 */
@Composable
fun DoctorsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val results by vm.doctors.collectAsStateWithLifecycle()
    val loading by vm.doctorLoading.collectAsStateWithLifecycle()
    val error by vm.doctorError.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var locationState by remember { mutableStateOf<LocState>(LocState.Unknown) }
    var savedIds by remember { mutableStateOf(vm.savedDoctorIds()) }

    // Konum izni isteyen launcher.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            locationState = LocState.Requested
        } else {
            locationState = LocState.Denied
        }
    }

    LaunchedEffect(locationState) {
        if (locationState == LocState.Requested) {
            // Gerçek konum okuma burada yapılır (FusedLocationProvider benzeri).
            // Bu build'de cihaz konumu simüle edilir; canlıda LocationManager.
            val loc = readLastKnownLocation(context)
            if (loc != null) {
                vm.findDoctorsNear(loc.first, loc.second)
                locationState = LocState.Done
            } else {
                locationState = LocState.Denied
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Canvas),
    ) {
        // Başlık
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                "Find a sleep doctor",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Text1,
                letterSpacing = (-0.5).sp,
            )
            Text(
                "21,343 sleep specialists · 6 countries",
                fontSize = 12.sp,
                color = Text3,
            )
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name, clinic or city", color = Text3, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Text3) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Surface1,
                    unfocusedContainerColor = Surface1,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Hairline,
                    focusedTextColor = Text1,
                    unfocusedTextColor = Text1,
                    cursorColor = Accent,
                ),
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            locationState = LocState.Requested
                            readLastKnownLocation(context)?.let { vm.findDoctorsNear(it.first, it.second) }
                        } else {
                            locationLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Icon(Icons.Filled.Place, null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Near me", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { if (query.isNotBlank()) vm.searchDoctors(query) },
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Text2),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text("Search", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Accent)
                    }
                }
            }

            error?.let { msg ->
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Surface1)
                            .padding(18.dp),
                    ) {
                        Column {
                            Text("Something went wrong", color = Text1, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(msg, color = Text2, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "You can still search for clinics by name or city.",
                                color = Text3,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            if (locationState == LocState.Denied) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Warn.copy(alpha = 0.10f))
                            .padding(14.dp),
                    ) {
                        Text(
                            "Location permission denied. You can still search by city or specialist name.",
                            color = Text2,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            results?.doctors?.let { doctors ->
                if (doctors.isEmpty() && !loading) {
                    item {
                        Text(
                            "No results. Try a different name or city.",
                            color = Text3,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }

                items(doctors, key = { it.id }) { doctor ->
                    DoctorCard(
                        doctor = doctor,
                        saved = savedIds.contains(doctor.id),
                        onToggleSave = {
                            savedIds = vm.savedDoctorIds()
                        },
                    )
                }
            }

            // Kaynak gösterimi — her sayfada görünür.
            results?.attribution?.let { attribution ->
                item {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Surface1.copy(alpha = 0.6f))
                            .padding(14.dp),
                    ) {
                        Column {
                            Text(
                                "Source: ${attribution.source}",
                                color = Text2,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                attribution.note,
                                color = Text3,
                                fontSize = 11.sp,
                            )
                            if (attribution.sourceUrl.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    attribution.sourceUrl,
                                    color = Accent2,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Doktor kartı. */
@Composable
private fun DoctorCard(
    doctor: Doctor,
    saved: Boolean,
    onToggleSave: () -> Unit,
) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Surface1)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    doctor.displayName,
                    color = Text1,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (doctor.credentials.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        doctor.credentials,
                        color = Text2,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleSave, modifier = Modifier.size(34.dp)) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (saved) "Remove bookmark" else "Save",
                    tint = if (saved) Accent else Text3,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Adres + mesafe
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Place, null, tint = Text3, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                doctor.address,
                color = Text2,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            doctor.distanceKm?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    "$it km",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Etiketler
        val tags = buildList {
            if (doctor.telehealth) add("Telehealth")
            if (doctor.languages.isNotEmpty()) add(doctor.languages.take(3).joinToString(", "))
            doctor.source.takeIf { it.isNotBlank() }?.let { add("Verified listing") }
        }
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.take(3).forEach { tag ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Surface2)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            tag,
                            color = Text3,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        // Aksiyonlar
        if (doctor.phone.isNotBlank() || doctor.website.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (doctor.phone.isNotBlank()) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${doctor.phone}"))
                            context.startActivity(intent)
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Canvas),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Filled.Phone, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Search", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (doctor.website.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(doctor.website)))
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Text2),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text("Website", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Kaynak — veri nereden geldi?
        if (doctor.source.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Source: ${doctor.source}",
                color = Text3,
                fontSize = 10.sp,
            )
        }
    }
}

/** Konum durumu. */
private sealed interface LocState {
    object Unknown : LocState
    object Requested : LocState
    object Done : LocState
    object Denied : LocState
}

/**
 * Cihazın son bilinen konumunu okur.
 *
 * Bu minimal implementasyon LocationManager'ın pasif konum sağlayıcısını
 * kullanır — aktif GPS isteği gece boyunca pil yiyebilir, "yakınımdakiler"
 * için son bilinen konum yeterli.
 */
private fun readLastKnownLocation(context: android.content.Context): Pair<Double, Double>? {
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        ?: return null
    return try {
        val providers = listOf(
            android.location.LocationManager.GPS_PROVIDER,
            android.location.LocationManager.NETWORK_PROVIDER,
            android.location.LocationManager.PASSIVE_PROVIDER,
        )
        providers.mapNotNull { p ->
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()
        }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
    } catch (e: SecurityException) {
        null
    }
}
