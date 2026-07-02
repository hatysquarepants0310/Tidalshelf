package app.tidalshelf.scrobbler

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Cliente mínimo de la API interna de YouTube Music (innertube), replicando el
 * comportamiento de ytmusicapi con autenticación por cookies de navegador:
 *
 *  - Authorization: SAPISIDHASH <ts>_<sha1(ts + " " + SAPISID + " " + origin)>
 *  - search  -> candidatos (videoId, título, artistas)
 *  - player  -> playbackTracking.videostatsPlaybackUrl
 *  - GET a esa URL con ver=2&c=WEB_REMIX&cpn=<16 chars> -> 204 = la canción
 *    queda registrada en el historial de YT Music sin reproducirse.
 */
class YtMusicClient(private val cookie: String) {

    companion object {
        private const val BASE = "https://music.youtube.com/youtubei/v1/"
        private const val ORIGIN = "https://music.youtube.com"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:88.0) Gecko/20100101 Firefox/88.0"
        private const val SONGS_FILTER_PARAMS = "EgWKAQIIAWoMEA4QChADEAQQCRAF"
        private const val CPN_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
        private val JSON = "application/json".toMediaType()

        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    class YtException(message: String) : Exception(message)

    private fun sapisid(): String {
        for (part in cookie.split(";")) {
            val trimmed = part.trim()
            if (trimmed.startsWith("__Secure-3PAPISID=")) {
                return trimmed.substringAfter("=").trim('"')
            }
        }
        throw YtException("La cookie no contiene __Secure-3PAPISID; vuelve a iniciar sesión.")
    }

    private fun authorizationHeader(): String {
        val timestamp = System.currentTimeMillis() / 1000
        val payload = "$timestamp ${sapisid()} $ORIGIN"
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$sha1"
    }

    private fun requestBuilder(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .header("Cookie", cookie)
            .header("Authorization", authorizationHeader())

    private fun context(): JSONObject {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return JSONObject().put(
            "context",
            JSONObject()
                .put(
                    "client",
                    JSONObject()
                        .put("clientName", "WEB_REMIX")
                        .put("clientVersion", "1.${fmt.format(Date())}.01.00")
                )
                .put("user", JSONObject())
        )
    }

    private fun postJson(endpoint: String, body: JSONObject): JSONObject {
        val merged = context()
        for (key in body.keys()) merged.put(key, body.get(key))
        val request = requestBuilder("$BASE$endpoint?alt=json")
            .post(merged.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: ""
            if (response.code >= 400) {
                throw YtException("YT Music devolvió HTTP ${response.code} en $endpoint")
            }
            return JSONObject(text)
        }
    }

    /** Recorre el JSON buscando musicResponsiveListItemRenderer y extrae candidatos. */
    private fun collectCandidates(node: Any?, out: MutableList<YtCandidate>) {
        when (node) {
            is JSONObject -> {
                if (node.has("musicResponsiveListItemRenderer")) {
                    parseListItem(node.getJSONObject("musicResponsiveListItemRenderer"))?.let(out::add)
                }
                for (key in node.keys()) collectCandidates(node.opt(key), out)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) collectCandidates(node.opt(i), out)
            }
        }
    }

    private fun parseListItem(item: JSONObject): YtCandidate? {
        val videoId = item.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
        if (videoId.isEmpty()) return null

        val flexColumns = item.optJSONArray("flexColumns") ?: return null
        fun runsOf(index: Int): JSONArray? =
            flexColumns.optJSONObject(index)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs")

        val title = runsOf(0)?.optJSONObject(0)?.optString("text").orEmpty()
        if (title.isEmpty()) return null

        var isSong = false
        val artistParts = mutableListOf<String>()
        val secondColumn = runsOf(1)
        if (secondColumn != null) {
            for (i in 0 until secondColumn.length()) {
                val run = secondColumn.optJSONObject(i) ?: continue
                val text = run.optString("text")
                if (text == " • " || text.isBlank()) continue
                if (text == "Song" || text == "Canción") { isSong = true; continue }
                if (text == "Video") continue
                if (text.matches(Regex("""\d+:\d+([:]\d+)?"""))) continue // duración
                if (text.matches(Regex("""[\d,.]+[KM]? (views|vistas|plays|reproducciones)"""))) continue
                artistParts.add(text)
            }
        }
        // La primera parte de la segunda columna es el artista; lo que sigue suele
        // ser el álbum. Nos quedamos con las dos primeras por si hay varios artistas.
        val artists = artistParts.take(2).joinToString(" ")
        return YtCandidate(videoId = videoId, title = title, artists = artists, isSong = isSong)
    }

    fun search(query: String, songsOnly: Boolean): List<YtCandidate> {
        val body = JSONObject().put("query", query)
        if (songsOnly) body.put("params", SONGS_FILTER_PARAMS)
        val response = postJson("search", body)
        val candidates = mutableListOf<YtCandidate>()
        collectCandidates(response, candidates)
        return candidates
    }

    /** URL de tracking de reproducción de un video (endpoint player). */
    fun getPlaybackTrackingUrl(videoId: String): String {
        val daysSinceEpoch = System.currentTimeMillis() / 86_400_000L
        val body = JSONObject()
            .put("video_id", videoId)
            .put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject().put("signatureTimestamp", daysSinceEpoch - 1)
                )
            )
        val response = postJson("player", body)
        val url = response.optJSONObject("playbackTracking")
            ?.optJSONObject("videostatsPlaybackUrl")
            ?.optString("baseUrl")
            .orEmpty()
        if (url.isEmpty()) {
            val status = response.optJSONObject("playabilityStatus")?.optString("status")
            throw YtException("Sin URL de tracking para $videoId (playabilityStatus=$status)")
        }
        return url
    }

    /** Registra la reproducción en el historial. 204 = éxito. */
    fun markPlayed(trackingBaseUrl: String): Boolean {
        val cpn = (1..16).map { CPN_ALPHABET[Random.nextInt(64)] }.joinToString("")
        val url = trackingBaseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("ver", "2")
            .addQueryParameter("c", "WEB_REMIX")
            .addQueryParameter("cpn", cpn)
            .build()
        val request = requestBuilder(url.toString()).get().build()
        http.newCall(request).execute().use { response ->
            return response.code in 200..299
        }
    }

    /** Flujo completo: buscar, matchear y registrar. Null si no hubo match. */
    fun registerPlay(artist: String, title: String): Pair<YtCandidate?, Double> {
        var candidates = search("$artist $title", songsOnly = true)
        if (candidates.isEmpty()) {
            candidates = search("$artist $title", songsOnly = false)
        }
        val (best, score) = Matcher.findBest(artist, title, candidates)
        if (best != null) {
            val trackingUrl = getPlaybackTrackingUrl(best.videoId)
            if (!markPlayed(trackingUrl)) {
                throw YtException("El registro de reproducción no devolvió éxito")
            }
        }
        return best to score
    }
}
