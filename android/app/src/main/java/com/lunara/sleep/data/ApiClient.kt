package com.lunara.sleep.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bağımlılıksız HTTP istemcisi.
 *
 * Retrofit/OkHttp eklemedik: build ortamının RAM sınırı (1.6 GB) ve
 * bağımlılık yok ilkesi. `HttpURLConnection` bu iş için yeterli.
 */
class ApiClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String? = { null },
) {

    private companion object {
        const val TAG = "LunaraApi"
        const val TIMEOUT_MS = 20_000
    }

    /** API hatası — HTTP durum kodu ve sunucu mesajı ile. */
    class ApiException(val status: Int, message: String) : Exception(message)

    suspend fun get(path: String, query: Map<String, String?> = emptyMap()): JSONObject =
        request("GET", path, query, null)

    suspend fun post(path: String, body: JSONObject? = null): JSONObject =
        request("POST", path, emptyMap(), body)

    suspend fun put(path: String, body: JSONObject? = null): JSONObject =
        request("PUT", path, emptyMap(), body)

    suspend fun delete(path: String): JSONObject =
        request("DELETE", path, emptyMap(), null)

    private suspend fun request(
        method: String,
        path: String,
        query: Map<String, String?>,
        body: JSONObject?,
    ): JSONObject = withContext(Dispatchers.IO) {
        val url = buildString {
            append(baseUrl.trimEnd('/'))
            append(path)
            val qs = query.entries
                .mapNotNull { (k, v) ->
                    if (v.isNullOrBlank()) null else "${enc(k)}=${enc(v)}"
                }
                .joinToString("&")
            if (qs.isNotEmpty()) append("?").append(qs)
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            tokenProvider()?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }

        try {
            if (body != null) {
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            if (status !in 200..299) {
                val msg = runCatching { JSONObject(text).optString("error") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: "HTTP $status"
                throw ApiException(status, msg)
            }

            if (text.isBlank()) return@withContext JSONObject()
            JSONObject(text)
        } catch (e: ApiException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "network error: $path", e)
            throw ApiException(0, "Could not connect. Check your internet connection.")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/** JSON dizisini Kotlin listesine çevirir. */
inline fun <T> JSONArray.mapEach(transform: (JSONObject) -> T): List<T> {
    val out = ArrayList<T>(length())
    for (i in 0 until length()) {
        optJSONObject(i)?.let { out.add(transform(it)) }
    }
    return out
}
