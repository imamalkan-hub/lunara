package com.lunara.sleep.data

import android.content.Context
import android.content.SharedPreferences
import com.lunara.sleep.core.SleepEvent
import com.lunara.sleep.core.SleepSession
import com.lunara.sleep.core.Stage
import org.json.JSONArray
import org.json.JSONObject

/**
 * Yerel kalıcı depolama.
 *
 * Oturumlar, tarama sonuçları ve oturum belirteci SharedPreferences'ta
 * JSON olarak tutulur. Veri boyutu küçüktür (gece başına birkaç KB);
 * Room eklemek bu ölçekte gereksiz bağımlılık olurdu.
 */
class LocalStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lunara", Context.MODE_PRIVATE)

    // --- Kimlik ---
    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    // --- Tercihler ---
    var bedtimeHour: Int
        get() = prefs.getInt(KEY_BEDTIME, 23)
        set(value) = prefs.edit().putInt(KEY_BEDTIME, value).apply()

    var wakeHour: Int
        get() = prefs.getInt(KEY_WAKE, 7)
        set(value) = prefs.edit().putInt(KEY_WAKE, value).apply()

    var audioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO, true)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO, value).apply()

    var remindersEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER, value).apply()

    /** Renk körlüğü / yüksek kontrast evre paleti. */
    var highContrastStages: Boolean
        get() = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_CONTRAST, value).apply()

    /** "Bana doktor öner" akışının kapalı olup olmadığı. */
    var doctorPromptsEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOCTOR_PROMPTS, true)
        set(value) = prefs.edit().putBoolean(KEY_DOCTOR_PROMPTS, value).apply()

    // --- Kayıtlı klinikler ---
    fun savedDoctorIds(): Set<Int> =
        prefs.getStringSet(KEY_SAVED_DOCTORS, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    fun toggleSavedDoctor(id: Int): Boolean {
        val current = savedDoctorIds().toMutableSet()
        val nowSaved = if (current.contains(id)) {
            current.remove(id); false
        } else {
            current.add(id); true
        }
        prefs.edit().putStringSet(KEY_SAVED_DOCTORS, current.map { it.toString() }.toSet()).apply()
        return nowSaved
    }

    // --- Yerel uyku oturumları ---
    fun saveSession(session: SleepSession) {
        val list = loadSessions().toMutableList()
        val idx = list.indexOfFirst { it.clientId.isNotBlank() && it.clientId == session.clientId }
        if (idx >= 0) list[idx] = session else list.add(session)
        list.sortByDescending { it.startedAt }
        persistSessions(list)
    }

    fun loadSessions(): List<SleepSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { sessionFromJson(it) }
            }
        }.getOrElse { emptyList() }
    }

    fun deleteSession(clientId: String) {
        persistSessions(loadSessions().filterNot { it.clientId == clientId })
    }

    private fun persistSessions(list: List<SleepSession>) {
        val arr = JSONArray()
        list.forEach { arr.put(sessionToJson(it)) }
        prefs.edit().putString(KEY_SESSIONS, arr.toString()).apply()
    }

    /** Sunucuya gönderilmemiş oturumların listesi. */
    fun unsyncedSessions(): List<SleepSession> =
        loadSessions().filter { !syncedIds.contains(it.clientId) }

    private val syncedIds: Set<String>
        get() = prefs.getStringSet(KEY_SYNCED, emptySet()) ?: emptySet()

    fun markSynced(clientId: String) {
        prefs.edit().putStringSet(KEY_SYNCED, syncedIds + clientId).apply()
    }

    // --- Yerel tarama sonuçları ---
    fun saveScreening(result: ScreeningResult) {
        val list = loadScreenings().toMutableList()
        list.removeAll { it.id == result.id }
        list.add(0, result)
        val arr = JSONArray()
        list.forEach { arr.put(screeningToJson(it)) }
        prefs.edit().putString(KEY_SCREENINGS, arr.toString()).apply()
    }

    fun loadScreenings(): List<ScreeningResult> {
        val raw = prefs.getString(KEY_SCREENINGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { screeningFromJson(it) }
            }
        }.getOrElse { emptyList() }
    }

    // --- JSON dönüşümleri ---
    fun sessionToJson(s: SleepSession): JSONObject = JSONObject().apply {
        put("clientId", s.clientId)
        put("startedAt", s.startedAt)
        put("endedAt", s.endedAt)
        put("timezone", s.timezone)
        put("deviceModel", s.deviceModel)
        put("note", s.note)
        put("snoreMin", s.snoreMin)
        put("snoreEvents", s.snoreEvents)
        put("peakDb", s.peakDb.toDouble())
        put("avgDb", s.avgDb.toDouble())
        put("noiseEvents", s.noiseEvents)
        val ev = JSONArray()
        s.events.forEach { e ->
            ev.put(JSONObject().apply {
                put("offset_min", e.offsetMin.toDouble())
                put("duration_min", e.durationMin.toDouble())
                put("stage", e.stage.key)
                put("intensity", e.intensity.toDouble())
                put("sound", e.sound.toDouble())
            })
        }
        put("events", ev)
        put("factors", JSONArray(s.factors))
        put("tags", JSONArray(s.tags))
    }

    private fun sessionFromJson(j: JSONObject): SleepSession = SleepSession(
        clientId = j.optString("clientId"),
        startedAt = j.optLong("startedAt"),
        endedAt = j.optLong("endedAt"),
        timezone = j.optString("timezone", "UTC"),
        deviceModel = j.optString("deviceModel"),
        events = j.optJSONArray("events")?.mapEach { e ->
            SleepEvent(
                offsetMin = e.optDouble("offset_min", 0.0).toFloat(),
                durationMin = e.optDouble("duration_min", 0.0).toFloat(),
                stage = Stage.fromKey(e.optString("stage")) ?: Stage.LIGHT,
                intensity = e.optDouble("intensity", 0.0).toFloat(),
                sound = e.optDouble("sound", 0.0).toFloat(),
            )
        } ?: emptyList(),
        note = j.optString("note"),
        factors = j.optJSONArray("factors")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        tags = j.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        snoreMin = j.optInt("snoreMin"),
        snoreEvents = j.optInt("snoreEvents"),
        peakDb = j.optDouble("peakDb", 0.0).toFloat(),
        avgDb = j.optDouble("avgDb", 0.0).toFloat(),
        noiseEvents = j.optInt("noiseEvents"),
    )

    private fun screeningToJson(s: ScreeningResult): JSONObject = JSONObject().apply {
        put("id", s.id)
        put("kind", s.kind)
        put("name", s.name)
        put("score", s.score)
        put("maxScore", s.maxScore)
        put("result", s.result)
        put("riskLevel", s.riskLevel)
        put("advice", s.advice)
        put("actions", JSONArray().apply {
            s.actions.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("title", a.title)
                    put("detail", a.detail)
                })
            }
        })
    }

    private fun screeningFromJson(j: JSONObject): ScreeningResult = ScreeningResult(
        id = j.optString("id"),
        kind = j.optString("kind"),
        name = j.optString("name"),
        score = j.optInt("score"),
        maxScore = j.optInt("maxScore"),
        result = j.optString("result"),
        riskLevel = j.optString("riskLevel"),
        advice = j.optString("advice"),
        actions = j.optJSONArray("actions")?.mapEach { a ->
            RecommendedAction(a.optString("id"), a.optString("title"), a.optString("detail"))
        } ?: emptyList(),
    )

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "name"
        const val KEY_BEDTIME = "bedtime_hour"
        const val KEY_WAKE = "wake_hour"
        const val KEY_AUDIO = "audio_enabled"
        const val KEY_REMINDER = "reminder_enabled"
        const val KEY_HIGH_CONTRAST = "high_contrast_stages"
        const val KEY_DOCTOR_PROMPTS = "doctor_prompts"
        const val KEY_SESSIONS = "sessions"
        const val KEY_SCREENINGS = "screenings"
        const val KEY_SYNCED = "synced_ids"
        const val KEY_SAVED_DOCTORS = "saved_doctors"
    }
}
