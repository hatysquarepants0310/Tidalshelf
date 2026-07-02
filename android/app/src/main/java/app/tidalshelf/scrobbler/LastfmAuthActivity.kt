package app.tidalshelf.scrobbler

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

/**
 * Autorización web de Last.fm: la app pide un token, el usuario inicia sesión
 * en last.fm (dentro del WebView) y toca "Yes, allow access" — sin escribir
 * nunca su contraseña en la app. Mientras tanto sondeamos auth.getSession
 * hasta que el token quede aprobado (error 14 = todavía no).
 */
class LastfmAuthActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var polling = false
    private var finished = false
    private var token: String? = null
    private lateinit var client: LastfmClient

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val apiKey = Prefs.lastfmApiKey(this)
        val apiSecret = Prefs.lastfmApiSecret(this)
        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            Toast.makeText(this, R.string.error_no_api_key, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        client = LastfmClient(apiKey, apiSecret)

        val webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // el usuario ya está navegando: empezar a sondear la aprobación
                startPolling()
            }
        }

        executor.execute {
            try {
                val newToken = client.getToken()
                token = newToken
                mainHandler.post { webView.loadUrl(client.authUrl(newToken)) }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(
                        this, getString(R.string.error_generic, e.message), Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        pollOnce()
    }

    private fun pollOnce() {
        if (finished) return
        val currentToken = token ?: return
        executor.execute {
            try {
                val (name, sessionKey) = client.getSession(currentToken)
                finished = true
                Prefs.setLastfmSession(this, name, sessionKey)
                mainHandler.post {
                    Toast.makeText(
                        this, getString(R.string.lastfm_connected, name), Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            } catch (e: LastfmClient.LastfmApiError) {
                if (e.code == 14 || e.code == 16) {
                    // aún sin aprobar (14) o servicio ocupado (16): reintentar
                    mainHandler.postDelayed({ pollOnce() }, 3000)
                } else {
                    finished = true
                    mainHandler.post {
                        Toast.makeText(
                            this, getString(R.string.error_generic, e.message), Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                }
            } catch (e: Exception) {
                mainHandler.postDelayed({ pollOnce() }, 3000)
            }
        }
    }

    override fun onDestroy() {
        finished = true
        super.onDestroy()
    }
}
