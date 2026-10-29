package com.lunara.sleep

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lunara.sleep.tracking.SleepTrackingService
import com.lunara.sleep.data.Repository
import com.lunara.sleep.ui.MainViewModel
import com.lunara.sleep.ui.screens.AboutScreen
import com.lunara.sleep.ui.screens.AuthScreen
import com.lunara.sleep.ui.screens.DashboardScreen
import com.lunara.sleep.ui.screens.DoctorsScreen
import com.lunara.sleep.ui.screens.HistoryScreen
import com.lunara.sleep.ui.screens.ScreeningScreen
import com.lunara.sleep.ui.theme.Accent
import com.lunara.sleep.ui.theme.Canvas
import com.lunara.sleep.ui.theme.Surface1
import com.lunara.sleep.ui.theme.Text2
import com.lunara.sleep.ui.theme.Text3
import com.lunara.sleep.ui.theme.LunaraTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* sonuç MainViewModel tarafında izleniyor */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LunaraTheme {
                LunaraApp(
                    onStartTracking = ::startTracking,
                    onStopTracking = ::stopTracking,
                    requestPermissions = ::requestPermissions,
                )
            }
        }
    }

    /**
     * Takibi başlatır.
     *
     * Android 12+ ön plan servisini arka plandan başlatmayı yasaklar; bu
     * yüzden servis yalnızca kullanıcının bu ekrandaki eylemiyle başlar.
     */
    private fun startTracking() {
        requestPermissions()
        val intent = Intent(this, SleepTrackingService::class.java).setAction(SleepTrackingService.ACTION_START)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopTracking() {
        val intent = Intent(this, SleepTrackingService::class.java).setAction(SleepTrackingService.ACTION_STOP)
        startService(intent)
    }

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.RECORD_AUDIO
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            needed += Manifest.permission.ACTIVITY_RECOGNITION
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}

/** Alt sekmeler. */
private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    DOCTORS("Doctors", Icons.Filled.PersonSearch),
    SCREENING("Screening", Icons.Filled.TrackChanges),
    HISTORY("History", Icons.Filled.CalendarMonth),
}

@Composable
fun LunaraApp(
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    requestPermissions: () -> Unit,
    vm: MainViewModel = viewModel(),
) {
    val authState by vm.authState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showAbout by remember { mutableStateOf(false) }

    // Servis durduğunda yeni kaydı işle.
    LaunchedEffect(Unit) {
        vm.refreshLocal()
    }

    when (authState) {
        is Repository.AuthState.Loading -> Box(
            Modifier
                .fillMaxSize()
                .background(Canvas),
        )
        is Repository.AuthState.SignedOut -> AuthScreen(vm)
        is Repository.AuthState.SignedIn -> if (showAbout) {
            AboutScreen(onBack = { showAbout = false })
        } else {
            Scaffold(
                containerColor = Canvas,
                bottomBar = {
                    NavigationBar(containerColor = Surface1) {
                        Tab.entries.forEach { item ->
                            NavigationBarItem(
                                selected = tab == item,
                                onClick = { tab = item },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    selectedTextColor = Accent,
                                    unselectedIconColor = Text3,
                                    unselectedTextColor = Text3,
                                    indicatorColor = Accent.copy(alpha = 0.12f),
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    when (tab) {
                        Tab.HOME -> DashboardScreen(
                            vm = vm,
                            onStartTracking = onStartTracking,
                            onStopTracking = {
                                onStopTracking()
                                vm.onTrackingStopped()
                            },
                            onOpenDoctors = { tab = Tab.DOCTORS },
                            onOpenScreening = { tab = Tab.SCREENING },
                            onOpenHistory = { tab = Tab.HISTORY },
                            onOpenAbout = { showAbout = true },
                        )
                        Tab.DOCTORS -> DoctorsScreen(vm)
                        Tab.SCREENING -> ScreeningScreen(vm, onFindDoctor = { tab = Tab.DOCTORS })
                        Tab.HISTORY -> HistoryScreen(vm)
                    }
                }
            }
        }
    }
}
