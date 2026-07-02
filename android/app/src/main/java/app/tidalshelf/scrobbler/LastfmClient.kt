package app.tidalshelf.scrobbler

import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Cliente mínimo de la API de scrobbling de Last.fm.
 * Protocolo: https://www.last.fm/api/scrobbling
 * Cada petición firmada lleva api_sig = md5(params ordenados + secret);
 * `format` se agrega a la URL pero nunca entra en la firma.
 */
class LastfmClient(private val apiKey: String, private val apiSecret: String) {

    companion object {
        private const val API_ROOT = "https://ws.audioscrobbler.com/2.0/"
        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    class LastfmException(message: String) : Exception(message)

    private fun md5(text: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
        return BigInteger(1, digest).toString(16).padStart(32, '0')
    }

    private fun sign(params: Map<String, String>): String {
        val concatenated = params.toSortedMap().entries.joinToString("") { "${it.key}${it.value}" }
        return md5(concatenated + apiSecret)
    }

    private fun post(params: Map<String, String>): JSONObject {
        val signed = params + ("api_sig" to sign(params))
        val body = FormBody.Builder().apply {
            signed.forEach { (k, v) -> add(k, v) }
            add("format", "json")
        }.build()
        val request = Request.Builder().url(API_ROOT).post(body).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw LastfmException("Respuesta ilegible de Last.fm (HTTP ${response.code})")
            }
            if (json.has("error")) {
                throw LastfmException(json.optString("message", "Error ${json.optInt("error")}"))
            }
            return json
        }
    }

    /** Devuelve (username, sessionKey). La sesión de Last.fm no caduca. */
    fun getMobileSession(username: String, password: String): Pair<String, String> {
        val json = post(
            mapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
                "api_key" to apiKey,
            )
        )
        val session = json.getJSONObject("session")
        return session.getString("name") to session.getString("key")
    }

    fun updateNowPlaying(sessionKey: String, artist: String, title: String, album: String, durationSec: Int) {
        val params = mutableMapOf(
            "method" to "track.updateNowPlaying",
            "artist" to artist,
            "track" to title,
            "api_key" to apiKey,
            "sk" to sessionKey,
        )
        if (album.isNotEmpty()) params["album"] = album
        if (durationSec > 0) params["duration"] = durationSec.toString()
        post(params)
    }

    /** Scrobblea hasta 50 reproducciones en una sola petición. */
    fun scrobble(sessionKey: String, plays: List<PendingPlay>) {
        require(plays.size <= 50)
        val params = mutableMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to sessionKey,
        )
        plays.forEachIndexed { i, play ->
            params["artist[$i]"] = play.artist
            params["track[$i]"] = play.title
            params["timestamp[$i]"] = play.timestamp.toString()
            if (play.album.isNotEmpty()) params["album[$i]"] = play.album
            if (play.durationSec > 0) params["duration[$i]"] = play.durationSec.toString()
        }
        post(params)
    }
}
