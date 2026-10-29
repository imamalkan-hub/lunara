package com.lunara.sleep.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lunara.sleep.core.SleepEngine
import com.lunara.sleep.core.SleepSession
import com.lunara.sleep.core.SessionSummary
import com.lunara.sleep.data.Doctor
import com.lunara.sleep.data.DoctorResults
import com.lunara.sleep.data.Repository
import com.lunara.sleep.data.ScreeningResult
import com.lunara.sleep.data.SleepStats
import com.lunara.sleep.tracking.SleepTrackingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Uygulama geneli durum.
 *
 * Durum tek bir akışta tutulur — Compose'un yeniden bileşim (recomposition)
 * maliyetini düşürmek ve ekranlar arası tutarlılık sağlamak için.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = Repository(app)

    val authState = repo.authState

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    private val _history = MutableStateFlow<List<SleepSession>>(emptyList())
    val history: StateFlow<List<SleepSession>> = _history.asStateFlow()

    private val _stats = MutableStateFlow<SleepStats?>(null)
    val stats: StateFlow<SleepStats?> = _stats.asStateFlow()

    private val _screenings = MutableStateFlow<List<ScreeningResult>>(emptyList())
    val screenings: StateFlow<List<ScreeningResult>> = _screenings.asStateFlow()

    private val _doctors = MutableStateFlow<DoctorResults?>(null)
    val doctors: StateFlow<DoctorResults?> = _doctors.asStateFlow()

    private val _doctorLoading = MutableStateFlow(false)
    val doctorLoading: StateFlow<Boolean> = _doctorLoading.asStateFlow()

    private val _doctorError = MutableStateFlow<String?>(null)
    val doctorError: StateFlow<String?> = _doctorError.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refreshLocal()
    }

    fun refreshLocal() {
        _history.value = repo.localSessions()
        _screenings.value = repo.localScreenings()
        _tracking.value = SleepTrackingService.isTracking
        _summary.value = _history.value.firstOrNull()?.let { SleepEngine.summarize(it) }
    }

    // --- Kimlik ---
    fun login(email: String, password: String) = viewModelScope.launch {
        repo.login(email, password)
            .onSuccess {
                _message.value = "Welcome back"
                syncNow()
            }
            .onFailure { _message.value = it.message ?: "Sign in failed" }
    }

    fun register(email: String, password: String, name: String) = viewModelScope.launch {
        repo.register(email, password, name)
            .onSuccess {
                _message.value = "Your account is ready"
                syncNow()
            }
            .onFailure { _message.value = it.message ?: "Sign up failed" }
    }

    fun logout() = viewModelScope.launch {
        repo.logout()
        _history.value = emptyList()
        _stats.value = null
        _screenings.value = emptyList()
        _doctors.value = null
    }

    // --- Senkronizasyon ---
    fun syncNow() = viewModelScope.launch {
        repo.syncSessions()
            .onSuccess { count ->
                if (count > 0) {
                    repo.fetchStats()
                        .onSuccess { _stats.value = it }
                }
            }
        repo.fetchStats().onSuccess { _stats.value = it }
        repo.fetchScreenings().onSuccess { remote ->
            if (remote.isNotEmpty()) _screenings.value = remote
        }
    }

    /** Servis durduğunda yeni geceyi kaydet. */
    fun onTrackingStopped() {
        _tracking.value = false
        SleepTrackingService.lastSession?.let { session ->
            repo.saveLocalSession(session)
            _summary.value = SleepEngine.summarize(session)
        }
        refreshLocal()
        syncNow()
    }

    /** Yerel bir gec kaydını siler. */
    fun deleteLocalSession(clientId: String) {
        repo.deleteLocalSession(clientId)
        refreshLocal()
    }

    // --- Tarama ---
    fun submitScreening(kind: String, answers: Map<String, Int>, onDone: (ScreeningResult) -> Unit) = viewModelScope.launch {
        repo.submitScreening(kind, answers)
            .onSuccess {
                _screenings.value = repo.localScreenings()
                onDone(it)
            }
            .onFailure { _message.value = it.message ?: "Could not submit screening" }
    }

    // --- Doktor dizini ---
    fun findDoctorsNear(lat: Double, lng: Double, radiusKm: Int = 50) = viewModelScope.launch {
        _doctorLoading.value = true
        _doctorError.value = null
        repo.doctorsNear(lat, lng, radiusKm)
            .onSuccess { _doctors.value = it }
            .onFailure { _doctorError.value = it.message ?: "Could not load doctors" }
        _doctorLoading.value = false
    }

    fun searchDoctors(query: String) = viewModelScope.launch {
        _doctorLoading.value = true
        _doctorError.value = null
        repo.searchDoctors(query)
            .onSuccess { _doctors.value = it }
            .onFailure { _doctorError.value = it.message ?: "Search failed" }
        _doctorLoading.value = false
    }

    fun clearDoctors() {
        _doctors.value = null
        _doctorError.value = null
    }

    fun toggleSaved(id: Int): Boolean = repo.toggleSavedDoctor(id)

    fun savedDoctorIds(): Set<Int> = repo.savedDoctorIds()

    fun consumeMessage() {
        _message.value = null
    }
}
