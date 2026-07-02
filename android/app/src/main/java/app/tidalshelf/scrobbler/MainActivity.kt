package app.tidalshelf.scrobbler

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var notifStatus: TextView
    private lateinit var notifButton: Button
    private lateinit var lastfmStatus: TextView
    private lateinit var lastfmButton: Button
    private lateinit var ytStatus: TextView
    private lateinit var ytButton: Button
    private lateinit var ytPasteButton: Button
    private lateinit var batteryStatus: TextView
    private lateinit var batteryButton: Button
    private lateinit var activityStatus: TextView
    private lateinit var flushButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notifStatus = findViewById(R.id.notif_status)
        notifButton = findViewById(R.id.notif_button)
        lastfmStatus = findViewById(R.id.lastfm_status)
        lastfmButton = findViewById(R.id.lastfm_button)
        ytStatus = findViewById(R.id.yt_status)
        ytButton = findViewById(R.id.yt_button)
        ytPasteButton = findViewById(R.id.yt_paste_button)
        batteryStatus = findViewById(R.id.battery_status)
        batteryButton = findViewById(R.id.battery_button)
        activityStatus = findViewById(R.id.activity_status)
        flushButton = findViewById(R.id.flush_button)

        notifButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        lastfmButton.setOnClickListener {
            if (Prefs.lastfmEnabled(this)) confirmLastfmLogout() else showLastfmLogin()
        }
        ytButton.setOnClickListener {
            if (Prefs.ytEnabled(this)) confirmYtLogout()
            else startActivity(Intent(this, YtLoginActivity::class.java))
        }
        ytPasteButton.setOnClickListener { showYtCookiePaste() }
        batteryButton.setOnClickListener { requestBatteryExemption() }
        flushButton.setOnClickListener {
            ScrobbleEngine.scheduleFlush(this)
            Toast.makeText(this, R.string.flush_scheduled, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val notifOk =
            NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notifStatus.text = getString(
            if (notifOk) R.string.status_notif_ok else R.string.status_notif_missing
        )
        notifButton.text = getString(
            if (notifOk) R.string.action_open_settings else R.string.action_grant
        )

        lastfmStatus.text = if (Prefs.lastfmEnabled(this)) {
            getString(R.string.status_lastfm_ok, Prefs.lastfmUsername(this))
        } else {
            getString(R.string.status_lastfm_missing)
        }
        lastfmButton.text = getString(
            if (Prefs.lastfmEnabled(this)) R.string.action_disconnect else R.string.action_connect
        )

        ytStatus.text = getString(
            if (Prefs.ytEnabled(this)) R.string.status_yt_ok else R.string.status_yt_missing
        )
        ytButton.text = getString(
            if (Prefs.ytEnabled(this)) R.string.action_disconnect else R.string.action_connect
        )

        val powerManager = getSystemService(PowerManager::class.java)
        val exempt = powerManager.isIgnoringBatteryOptimizations(packageName)
        batteryStatus.text = getString(
            if (exempt) R.string.status_battery_ok else R.string.status_battery_missing
        )
        batteryButton.isEnabled = !exempt

        val queue = QueueStore.get(this)
        val pending = queue.countWhere(
            "lastfm_state = ${QueueStore.STATE_PENDING} OR yt_state = ${QueueStore.STATE_PENDING}"
        )
        activityStatus.text = getString(
            R.string.status_activity,
            Prefs.lastTrack(this).ifEmpty { "—" },
            Prefs.counter(this, "detected"),
            Prefs.counter(this, "lastfm_ok"),
            Prefs.counter(this, "yt_ok"),
            Prefs.counter(this, "yt_miss"),
            pending,
        )
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    // --- Last.fm ---

    private fun showLastfmLogin() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
        }
        fun field(hint: Int, password: Boolean = false, prefill: String = ""): EditText =
            EditText(this).apply {
                setHint(hint)
                setText(prefill)
                if (password) {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                container.addView(this)
            }

        val apiKeyField = field(R.string.hint_api_key, prefill = Prefs.lastfmApiKey(this))
        val apiSecretField = field(R.string.hint_api_secret, prefill = Prefs.lastfmApiSecret(this))
        val userField = field(R.string.hint_username)
        val passField = field(R.string.hint_password, password = true)

        AlertDialog.Builder(this)
            .setTitle(R.string.lastfm_login_title)
            .setMessage(R.string.lastfm_login_message)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_connect) { _, _ ->
                doLastfmLogin(
                    apiKeyField.text.toString().trim(),
                    apiSecretField.text.toString().trim(),
                    userField.text.toString().trim(),
                    passField.text.toString(),
                )
            }
            .show()
    }

    private fun doLastfmLogin(apiKey: String, apiSecret: String, user: String, pass: String) {
        if (apiKey.isEmpty() || apiSecret.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this, R.string.error_fields_required, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.connecting, Toast.LENGTH_SHORT).show()
        executor.execute {
            try {
                val (name, sessionKey) = LastfmClient(apiKey, apiSecret).getMobileSession(user, pass)
                Prefs.setLastfmCreds(this, apiKey, apiSecret)
                Prefs.setLastfmSession(this, name, sessionKey)
                mainHandler.post {
                    Toast.makeText(
                        this, getString(R.string.lastfm_connected, name), Toast.LENGTH_LONG
                    ).show()
                    refresh()
                }
            } catch (e: Exception) {
                mainHandler.post {
                    Toast.makeText(
                        this, getString(R.string.error_generic, e.message), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun confirmLastfmLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_disconnect_lastfm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_disconnect) { _, _ ->
                Prefs.clearLastfmSession(this)
                refresh()
            }
            .show()
    }

    // --- YouTube Music ---

    private fun confirmYtLogout() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_disconnect_yt)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_disconnect) { _, _ ->
                Prefs.clearYtCookie(this)
                refresh()
            }
            .show()
    }

    private fun showYtCookiePaste() {
        val input = EditText(this).apply {
            setHint(R.string.hint_yt_cookie)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.yt_paste_title)
            .setMessage(R.string.yt_paste_message)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val cookie = input.text.toString().trim()
                if (cookie.contains("__Secure-3PAPISID")) {
                    Prefs.setYtCookie(this, cookie)
                    refresh()
                } else {
                    Toast.makeText(this, R.string.error_cookie_invalid, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
