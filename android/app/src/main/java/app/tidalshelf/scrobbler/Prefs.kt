package app.tidalshelf.scrobbler

import android.content.Context
import android.content.SharedPreferences

/** Configuración y estado ligero de la app. Todo local al dispositivo. */
object Prefs {
    private const val FILE = "tidalshelf_prefs"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // --- Last.fm ---
    // La clave integrada al compilar tiene prioridad salvo que el usuario haya
    // guardado la suya propia.
    fun lastfmApiKey(ctx: Context): String =
        sp(ctx).getString("lastfm_api_key", "")!!.ifEmpty { BuildConfig.LASTFM_API_KEY }
    fun lastfmApiSecret(ctx: Context): String =
        sp(ctx).getString("lastfm_api_secret", "")!!.ifEmpty { BuildConfig.LASTFM_API_SECRET }
    fun lastfmSessionKey(ctx: Context): String = sp(ctx).getString("lastfm_sk", "") ?: ""
    fun lastfmUsername(ctx: Context): String = sp(ctx).getString("lastfm_user", "") ?: ""

    fun setLastfmCreds(ctx: Context, apiKey: String, apiSecret: String) =
        sp(ctx).edit().putString("lastfm_api_key", apiKey).putString("lastfm_api_secret", apiSecret).apply()

    fun setLastfmSession(ctx: Context, username: String, sessionKey: String) =
        sp(ctx).edit().putString("lastfm_user", username).putString("lastfm_sk", sessionKey).apply()

    fun clearLastfmSession(ctx: Context) =
        sp(ctx).edit().remove("lastfm_user").remove("lastfm_sk").apply()

    fun lastfmEnabled(ctx: Context): Boolean = lastfmSessionKey(ctx).isNotEmpty()

    // --- YouTube Music ---
    fun ytCookie(ctx: Context): String = sp(ctx).getString("yt_cookie", "") ?: ""
    fun setYtCookie(ctx: Context, cookie: String) = sp(ctx).edit().putString("yt_cookie", cookie).apply()
    fun clearYtCookie(ctx: Context) = sp(ctx).edit().remove("yt_cookie").apply()
    fun ytEnabled(ctx: Context): Boolean = ytCookie(ctx).contains("__Secure-3PAPISID")

    // --- Estado para la UI ---
    fun lastTrack(ctx: Context): String = sp(ctx).getString("status_last_track", "") ?: ""
    fun setLastTrack(ctx: Context, value: String) =
        sp(ctx).edit().putString("status_last_track", value).apply()

    fun counter(ctx: Context, name: String): Long = sp(ctx).getLong("count_$name", 0)
    fun bumpCounter(ctx: Context, name: String) =
        sp(ctx).edit().putLong("count_$name", counter(ctx, name) + 1).apply()
}
