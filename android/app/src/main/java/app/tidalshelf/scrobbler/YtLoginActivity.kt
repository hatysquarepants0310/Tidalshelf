package app.tidalshelf.scrobbler

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Login de YouTube Music dentro de la app: el usuario inicia sesión en
 * music.youtube.com en un WebView y capturamos las cookies (__Secure-3PAPISID
 * y compañía), que son lo único que la app necesita para escribir en el
 * historial. Mismo mecanismo que `ytmusicapi browser`, sin salir del teléfono.
 */
class YtLoginActivity : AppCompatActivity() {

    companion object {
        private const val MUSIC_URL = "https://music.youtube.com"
        // UA de Chrome Android sin el sufijo "; wv" de WebView, para que Google
        // no bloquee el login como "navegador inseguro".
        private const val UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this)
        setContentView(webView)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = UA

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val cookie = CookieManager.getInstance().getCookie(MUSIC_URL).orEmpty()
                if (cookie.contains("__Secure-3PAPISID")) {
                    Prefs.setYtCookie(this@YtLoginActivity, cookie)
                    Toast.makeText(
                        this@YtLoginActivity,
                        getString(R.string.yt_login_ok),
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            }
        }
        webView.loadUrl(MUSIC_URL)
    }
}
