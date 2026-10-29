package com.lunara.sleep.data

import com.lunara.sleep.core.ApneaScreening
import com.lunara.sleep.core.SleepEvent
import com.lunara.sleep.core.SleepSession
import com.lunara.sleep.core.Stage
import org.json.JSONArray
import org.json.JSONObject

/** API'den gelen doktor kaydı. */
data class Doctor(
    val id: Int,
    val name: String,
    val credentials: String,
    val clinic: String,
    val address: String,
    val city: String,
    val region: String,
    val country: String,
    val phone: String,
    val website: String,
    val email: String,
    val lat: Double?,
    val lng: Double?,
    val distanceKm: Double?,
    val telehealth: Boolean,
    val languages: List<String>,
    val source: String,
    val sourceUrl: String?,
    val directoryUrl: String,
) {
    val displayName: String
        get() = if (clinic.isNotBlank() && !clinic.equals(name, ignoreCase = true)) clinic else name
}

/** Dizin kaynak gösterimi — her yanıtta gelir. */
data class Attribution(
    val source: String,
    val sourceUrl: String,
    val note: String,
)

/** Bir doktor listesinin sonucu — kaynak gösterimi ile birlikte. */
data class DoctorResults(
    val doctors: List<Doctor>,
    val attribution: Attribution?,
)

/** API yanıtlarını Kotlin modellerine çevirir. */
object Mappers {

    fun doctor(j: JSONObject): Doctor = Doctor(
        id = j.optInt("id"),
        name = j.optString("name"),
        credentials = j.optString("credentials"),
        clinic = j.optString("clinic"),
        address = j.optString("address"),
        city = j.optString("city"),
        region = j.optString("region"),
        country = j.optString("country"),
        phone = j.optString("phone"),
        website = j.optString("website"),
        email = j.optString("email"),
        lat = if (j.isNull("lat")) null else j.optDouble("lat"),
        lng = if (j.isNull("lng")) null else j.optDouble("lng"),
        distanceKm = if (j.isNull("distanceKm")) null else j.optDouble("distanceKm"),
        telehealth = j.optBoolean("telehealth"),
        languages = jsonArrayToStrings(j.optJSONArray("languages")),
        source = j.optString("source"),
        sourceUrl = if (j.isNull("sourceUrl")) null else j.optString("sourceUrl"),
        directoryUrl = j.optString("directoryUrl"),
    )

    fun attribution(j: JSONObject?): Attribution? {
        if (j == null) return null
        return Attribution(
            source = j.optString("source"),
            sourceUrl = j.optString("sourceUrl"),
            note = j.optString("note"),
        )
    }

    fun session(j: JSONObject): SleepSession = SleepSession(
        id = j.optString("id"),
        clientId = j.optString("clientId"),
        startedAt = parseTime(j.optString("startedAt")),
        endedAt = parseTime(j.optString("endedAt")),
        timezone = j.optString("timezone", "UTC"),
        deviceModel = j.optString("deviceModel"),
        events = emptyList(),
        note = j.optString("note"),
        factors = jsonArrayToStrings(j.optJSONArray("factors")),
        tags = jsonArrayToStrings(j.optJSONArray("tags")),
        snoreMin = j.optInt("snoreMin"),
        snoreEvents = j.optInt("snoreEvents"),
        peakDb = j.optDouble("peakDb", 0.0).toFloat(),
        avgDb = j.optDouble("avgDb", 0.0).toFloat(),
        noiseEvents = j.optInt("noiseEvents"),
    )

    fun sessionDetail(session: JSONObject, events: JSONArray?): SleepSession {
        val base = session(session)
        val list = events?.mapEach { e ->
            SleepEvent(
                offsetMin = e.optDouble("offset_min", 0.0).toFloat(),
                durationMin = e.optDouble("duration_min", 0.0).toFloat(),
                stage = Stage.fromKey(e.optString("stage")) ?: Stage.LIGHT,
                intensity = e.optDouble("intensity", 0.0).toFloat(),
            )
        } ?: emptyList()
        return base.copy(events = list)
    }

    fun screening(j: JSONObject): ScreeningResult {
        val s = j.optJSONObject("screening") ?: j
        return ScreeningResult(
            id = s.optString("id"),
            kind = s.optString("kind"),
            name = s.optString("name"),
            score = s.optInt("score"),
            maxScore = s.optInt("maxScore"),
            result = s.optString("result"),
            riskLevel = s.optString("riskLevel"),
            advice = s.optString("advice"),
            actions = j.optJSONArray("actions")?.mapEach { a ->
                RecommendedAction(
                    id = a.optString("id"),
                    title = a.optString("title"),
                    detail = a.optString("detail"),
                )
            } ?: emptyList(),
        )
    }

    fun stats(j: JSONObject): SleepStats {
        val s = j.optJSONObject("stats") ?: JSONObject()
        val averages = s.optJSONObject("averages") ?: JSONObject()
        val debt = s.optJSONObject("debt") ?: JSONObject()
        val series = s.optJSONArray("series")?.mapEach { p ->
            TrendPoint(
                date = p.optString("date"),
                score = p.optInt("score"),
                totalSleepMin = p.optInt("totalSleepMin"),
                deepMin = p.optInt("deepMin"),
                remMin = p.optInt("remMin"),
                awakeMin = p.optInt("awakeMin"),
            )
        } ?: emptyList()

        return SleepStats(
            nights = s.optInt("nights"),
            avgScore = averages.optInt("score"),
            avgTotalSleepMin = averages.optInt("totalSleepMin"),
            avgEfficiencyPct = averages.optInt("efficiencyPct"),
            avgDeepMin = averages.optInt("deepMin"),
            avgRemMin = averages.optInt("remMin"),
            avgLatencyMin = averages.optInt("latencyMin"),
            avgWakeCount = averages.optInt("wakeCount"),
            regularity = s.optInt("regularity"),
            debtMinutes = debt.optInt("debtMinutes"),
            chronotype = s.optString("chronotype", "unknown"),
            series = series,
        )
    }

    private fun jsonArrayToStrings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            a.optString(i).takeIf { it.isNotBlank() }
        }
    }

    private fun parseTime(s: String): Long =
        if (s.isBlank()) 0L else runCatching {
            java.time.Instant.parse(s).toEpochMilli()
        }.getOrElse { 0L }
}

/** Tarama sonucu. */
data class ScreeningResult(
    val id: String,
    val kind: String,
    val name: String,
    val score: Int,
    val maxScore: Int,
    val result: String,
    val riskLevel: String,
    val advice: String,
    val actions: List<RecommendedAction>,
)

/** Önerilen eylem. */
data class RecommendedAction(
    val id: String,
    val title: String,
    val detail: String,
)

/** Trend istatistikleri. */
data class SleepStats(
    val nights: Int,
    val avgScore: Int,
    val avgTotalSleepMin: Int,
    val avgEfficiencyPct: Int,
    val avgDeepMin: Int,
    val avgRemMin: Int,
    val avgLatencyMin: Int,
    val avgWakeCount: Int,
    val regularity: Int,
    val debtMinutes: Int,
    val chronotype: String,
    val series: List<TrendPoint>,
)

/** Trend grafiğinde tek bir nokta. */
data class TrendPoint(
    val date: String,
    val score: Int,
    val totalSleepMin: Int,
    val deepMin: Int,
    val remMin: Int,
    val awakeMin: Int,
)
