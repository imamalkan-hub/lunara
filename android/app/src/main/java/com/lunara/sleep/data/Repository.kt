package com.lunara.sleep.data

import android.content.Context
import com.lunara.sleep.BuildConfig
import com.lunara.sleep.core.SleepSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Uygulama veri katmanı.
 *
 * Tasarım kuralı: **ham ses asla cihaz dışına çıkmaz.** Sunucuya yalnızca
 * türetilmiş özetler gönderilir (süre, evre dağılımı, horlama olay sayısı,
 * tarama yanıtları). Bu hem gizlilik avantajı hem de regülasyon açısından
 * en düşük riskli yol.
 */
class Repository(context: Context) {

    private val store = LocalStore(context)
    private val api = ApiClient(BuildConfig.API_BASE_URL) { store.token }

    // --- Oturum durumu (uygulama genelinde paylaşılan) ---
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    sealed class AuthState {
        object Loading : AuthState()
        object SignedOut : AuthState()
        data class SignedIn(val userId: String, val email: String, val displayName: String) : AuthState()
    }

    init {
        refreshAuthState()
    }

    private fun refreshAuthState() {
        val token = store.token
        _authState.value = if (token.isNullOrBlank()) {
            AuthState.SignedOut
        } else {
            AuthState.SignedIn(store.userId.orEmpty(), store.userEmail.orEmpty(), store.userName.orEmpty())
        }
    }

    // --- Kimlik ---
    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        val res = api.post("/api/auth/login", org.json.JSONObject().apply {
            put("email", email)
            put("password", password)
        })
        store.token = res.getString("token")
        res.optJSONObject("user")?.let { u ->
            store.userId = u.optString("id")
            store.userEmail = u.optString("email")
            store.userName = u.optString("displayName")
        }
        refreshAuthState()
    }

    suspend fun register(email: String, password: String, displayName: String): Result<Unit> = runCatching {
        val res = api.post("/api/auth/register", org.json.JSONObject().apply {
            put("email", email)
            put("password", password)
            put("displayName", displayName)
        })
        store.token = res.getString("token")
        res.optJSONObject("user")?.let { u ->
            store.userId = u.optString("id")
            store.userEmail = u.optString("email")
            store.userName = u.optString("displayName")
        }
        refreshAuthState()
    }

    suspend fun logout(): Result<Unit> = runCatching {
        runCatching { api.post("/api/auth/logout") }
        store.clearAll()
        refreshAuthState()
    }

    // --- Uyku oturumları ---
    fun localSessions(): List<SleepSession> = store.loadSessions()

    fun saveLocalSession(session: SleepSession) = store.saveSession(session)

    fun deleteLocalSession(clientId: String) = store.deleteSession(clientId)

    /**
     * Yerel oturumları sunucuya gönderir.
     *
     * Gönderilen payload **türetilmiş özettir**: olay listesi (evre +
     * yoğunluk) dahil edilir çünkü hipnogram çizimi için gerekli, ancak
     * ham ses verisi hiç gönderilmez.
     */
    suspend fun syncSessions(): Result<Int> = runCatching {
        var count = 0
        for (session in store.unsyncedSessions()) {
            val body = org.json.JSONObject().apply {
                put("clientId", session.clientId)
                put("startedAt", isoTime(session.startedAt))
                put("endedAt", isoTime(session.endedAt))
                put("timezone", session.timezone)
                put("deviceModel", session.deviceModel)
                put("note", session.note)
                put("snoreMin", session.snoreMin)
                put("snoreEvents", session.snoreEvents)
                put("peakDb", session.peakDb.toDouble())
                put("avgDb", session.avgDb.toDouble())
                put("noiseEvents", session.noiseEvents)
                put("factors", org.json.JSONArray(session.factors))
                put("tags", org.json.JSONArray(session.tags))
                val ev = org.json.JSONArray()
                session.events.forEach { e ->
                    ev.put(org.json.JSONObject().apply {
                        put("offset_min", e.offsetMin.toDouble())
                        put("duration_min", e.durationMin.toDouble())
                        put("stage", e.stage.key)
                        put("intensity", e.intensity.toDouble())
                        put("sound", e.sound.toDouble())
                    })
                }
                put("events", ev)
            }
            api.post("/api/sessions", body)
            store.markSynced(session.clientId)
            count++
        }
        count
    }

    suspend fun fetchStats(days: Int = 30): Result<SleepStats> = runCatching {
        Mappers.stats(api.get("/api/stats", mapOf("days" to days.toString())))
    }

    suspend fun fetchSession(clientId: String): Result<SleepSession> = runCatching {
        val res = api.get("/api/sessions", mapOf("limit" to "365"))
        val sessions = res.optJSONArray("sessions")?.mapEach { Mappers.session(it) } ?: emptyList()
        sessions.firstOrNull { it.clientId == clientId }
            ?: error("This night was not found on the server")
    }

    // --- Tarama ---
    suspend fun submitScreening(kind: String, answers: Map<String, Int>): Result<ScreeningResult> = runCatching {
        val body = org.json.JSONObject().apply {
            put("kind", kind)
            put("answers", org.json.JSONObject().apply {
                answers.forEach { (k, v) -> put(k, v) }
            })
        }
        val result = Mappers.screening(api.post("/api/screenings", body))
        store.saveScreening(result)
        result
    }

    suspend fun fetchScreenings(): Result<List<ScreeningResult>> = runCatching {
        val res = api.get("/api/screenings")
        res.optJSONArray("screenings")?.mapEach { s ->
            ScreeningResult(
                id = s.optString("id"),
                kind = s.optString("kind"),
                name = "",
                score = s.optInt("score"),
                maxScore = s.optInt("maxScore"),
                result = s.optString("result"),
                riskLevel = s.optString("riskLevel"),
                advice = s.optString("advice"),
                actions = emptyList(),
            )
        } ?: emptyList()
    }

    fun localScreenings(): List<ScreeningResult> = store.loadScreenings()

    // --- Doktor dizini ---
    suspend fun doctorsNear(
        lat: Double,
        lng: Double,
        radiusKm: Int = 50,
        limit: Int = 50,
        country: String? = null,
    ): Result<DoctorResults> = runCatching {
        val res = api.get("/api/doctors", mapOf(
            "lat" to lat.toString(),
            "lng" to lng.toString(),
            "radius" to radiusKm.toString(),
            "limit" to limit.toString(),
            "country" to country,
        ))
        DoctorResults(
            doctors = res.optJSONArray("doctors")?.mapEach { Mappers.doctor(it) } ?: emptyList(),
            attribution = Mappers.attribution(res.optJSONObject("attribution")),
        )
    }

    suspend fun searchDoctors(query: String, country: String? = null, limit: Int = 30): Result<DoctorResults> = runCatching {
        val res = api.get("/api/doctors", mapOf(
            "q" to query,
            "country" to country,
            "limit" to limit.toString(),
        ))
        DoctorResults(
            doctors = res.optJSONArray("doctors")?.mapEach { Mappers.doctor(it) } ?: emptyList(),
            attribution = Mappers.attribution(res.optJSONObject("attribution")),
        )
    }

    suspend fun doctor(id: Int): Result<Doctor> = runCatching {
        Mappers.doctor(api.get("/api/doctors/$id").getJSONObject("doctor"))
    }

    suspend fun countries(): Result<List<Country>> = runCatching {
        api.get("/api/countries").optJSONArray("countries")?.mapEach { c ->
            Country(c.optString("code"), c.optString("name"), c.optInt("doctor_count"))
        } ?: emptyList()
    }

    suspend fun regions(countryCode: String): Result<List<Region>> = runCatching {
        api.get("/api/countries/$countryCode/regions").optJSONArray("regions")?.mapEach { r ->
            Region(r.optString("slug"), r.optString("name"), r.optInt("doctor_count"))
        } ?: emptyList()
    }

    suspend fun cities(countryCode: String, regionSlug: String): Result<List<City>> = runCatching {
        api.get("/api/countries/$countryCode/regions/$regionSlug/cities")
            .optJSONArray("cities")?.mapEach { c ->
                City(c.optString("slug"), c.optString("name"), c.optInt("doctor_count"))
            } ?: emptyList()
    }

    suspend fun doctorsInCity(countryCode: String, regionSlug: String, citySlug: String): Result<List<Doctor>> = runCatching {
        api.get("/api/countries/$countryCode/regions/$regionSlug/cities/$citySlug/doctors")
            .optJSONArray("doctors")?.mapEach { Mappers.doctor(it) } ?: emptyList()
    }

    suspend fun suggestPlaces(query: String): Result<List<PlaceSuggestion>> = runCatching {
        api.get("/api/places/suggest", mapOf("q" to query)).optJSONArray("places")?.mapEach { p ->
            PlaceSuggestion(
                type = p.optString("type"),
                label = p.optString("label"),
                sublabel = p.optString("sublabel"),
                countryCode = p.optString("country_code"),
                regionSlug = p.optString("region_slug").takeIf { it.isNotBlank() && it != "null" },
                citySlug = p.optString("city_slug").takeIf { it.isNotBlank() && it != "null" },
                lat = if (p.isNull("lat")) null else p.optDouble("lat"),
                lng = if (p.isNull("lng")) null else p.optDouble("lng"),
            )
        } ?: emptyList()
    }

    // --- Kayıtlı klinikler ---
    fun savedDoctorIds(): Set<Int> = store.savedDoctorIds()
    fun toggleSavedDoctor(id: Int): Boolean = store.toggleSavedDoctor(id)

    // --- Tercihler ---
    var bedtimeHour: Int
        get() = store.bedtimeHour
        set(v) { store.bedtimeHour = v }
    var wakeHour: Int
        get() = store.wakeHour
        set(v) { store.wakeHour = v }
    var audioEnabled: Boolean
        get() = store.audioEnabled
        set(v) { store.audioEnabled = v }
    var highContrastStages: Boolean
        get() = store.highContrastStages
        set(v) { store.highContrastStages = v }

    private fun isoTime(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis).toString()
}

/** Ülke. */
data class Country(val code: String, val name: String, val doctorCount: Int)

/** Bölge (state/province). */
data class Region(val slug: String, val name: String, val doctorCount: Int)

/** Şehir. */
data class City(val slug: String, val name: String, val doctorCount: Int)

/** Yer önerisi (şehir / bölge / ülke). */
data class PlaceSuggestion(
    val type: String,
    val label: String,
    val sublabel: String,
    val countryCode: String,
    val regionSlug: String?,
    val citySlug: String?,
    val lat: Double?,
    val lng: Double?,
)
