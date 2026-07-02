package app.tidalshelf.scrobbler

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Máquina de estados de scrobbling. Recibe eventos de metadata/playback del
 * MediaSession de Tidal y aplica las reglas estándar de Last.fm:
 *
 *  - la pista debe durar más de 30 s
 *  - se registra al reproducirse la mitad de la pista o 4 minutos (lo primero)
 *  - el timestamp es el momento en que EMPEZÓ a sonar
 *
 * El registro se hace exactamente una vez por reproducción, en el instante en
 * que se cruza el umbral (no al cambiar de pista), igual que los scrobblers
 * clásicos. Todo corre en un HandlerThread propio; nada toca el hilo principal.
 */
object ScrobbleEngine {

    private const val TAG = "ScrobbleEngine"
    private const val MIN_TRACK_SEC = 30
    private const val FOUR_MINUTES_MS = 4 * 60 * 1000L

    private val thread = HandlerThread("scrobble-engine").apply { start() }
    private val handler = Handler(thread.looper)

    private var appContext: Context? = null

    private data class Playing(
        val artist: String,
        val title: String,
        val album: String,
        val durationMs: Long,
        val startedAtEpochSec: Long,
        var accumulatedMs: Long = 0,
        var resumedAtElapsed: Long? = null, // SystemClock-free: usamos wall clock
        var scrobbled: Boolean = false,
        var nowPlayingSent: Boolean = false,
    )

    private var current: Playing? = null
    private val thresholdRunnable = Runnable { onThresholdReached() }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun onMetadataChanged(artist: String, title: String, album: String, durationMs: Long) {
        handler.post {
            val curr = current
            if (curr != null && curr.artist == artist && curr.title == title) {
                return@post // misma pista re-anunciada; ignorar
            }
            finalizeCurrent()
            if (artist.isBlank() || title.isBlank()) {
                current = null
                return@post
            }
            current = Playing(
                artist = artist,
                title = title,
                album = album,
                durationMs = durationMs,
                startedAtEpochSec = System.currentTimeMillis() / 1000,
            )
            Log.i(TAG, "Nueva pista: $artist - $title (${durationMs / 1000}s)")
            appContext?.let { Prefs.setLastTrack(it, "$artist — $title") }
            notifyStatus()
        }
    }

    fun onPlaybackChanged(isPlaying: Boolean) {
        handler.post {
            val curr = current ?: return@post
            if (isPlaying) {
                if (curr.resumedAtElapsed == null) {
                    curr.resumedAtElapsed = System.currentTimeMillis()
                    scheduleThreshold(curr)
                    if (!curr.nowPlayingSent) {
                        curr.nowPlayingSent = true
                        sendNowPlaying(curr)
                    }
                }
            } else {
                accumulate(curr)
                handler.removeCallbacks(thresholdRunnable)
            }
            notifyStatus()
        }
    }

    fun onSessionGone() {
        handler.post {
            finalizeCurrent()
            current = null
            appContext?.let { StatusNotifier.idle(it) }
        }
    }

    /** Refleja el estado actual en la notificación opcional. */
    private fun notifyStatus() {
        val ctx = appContext ?: return
        val curr = current
        if (curr == null) {
            StatusNotifier.idle(ctx)
        } else {
            StatusNotifier.update(
                ctx, curr.artist, curr.title,
                playing = curr.resumedAtElapsed != null,
                scrobbled = curr.scrobbled,
            )
        }
    }

    private fun accumulate(playing: Playing) {
        val resumedAt = playing.resumedAtElapsed ?: return
        playing.accumulatedMs += System.currentTimeMillis() - resumedAt
        playing.resumedAtElapsed = null
    }

    private fun playedSoFarMs(playing: Playing): Long {
        val running = playing.resumedAtElapsed?.let { System.currentTimeMillis() - it } ?: 0
        return playing.accumulatedMs + running
    }

    private fun thresholdMs(playing: Playing): Long =
        if (playing.durationMs > 0) minOf(playing.durationMs / 2, FOUR_MINUTES_MS)
        else FOUR_MINUTES_MS

    private fun scheduleThreshold(playing: Playing) {
        if (playing.scrobbled) return
        handler.removeCallbacks(thresholdRunnable)
        val remaining = thresholdMs(playing) - playedSoFarMs(playing)
        if (remaining <= 0) {
            onThresholdReached()
        } else {
            handler.postDelayed(thresholdRunnable, remaining)
        }
    }

    private fun onThresholdReached() {
        val curr = current ?: return
        if (curr.scrobbled) return
        if (curr.durationMs in 1 until MIN_TRACK_SEC * 1000L) return
        curr.scrobbled = true
        enqueue(curr)
        notifyStatus()
    }

    private fun finalizeCurrent() {
        val curr = current ?: return
        accumulate(curr)
        handler.removeCallbacks(thresholdRunnable)
        // Red de seguridad: si el umbral se cruzó pero el runnable no llegó a
        // dispararse (proceso dormido), registrar al cambiar de pista.
        if (!curr.scrobbled &&
            curr.durationMs >= MIN_TRACK_SEC * 1000L &&
            playedSoFarMs(curr) >= thresholdMs(curr)
        ) {
            curr.scrobbled = true
            enqueue(curr)
        }
    }

    private fun enqueue(playing: Playing) {
        val ctx = appContext ?: return
        val lastfmOn = Prefs.lastfmEnabled(ctx)
        val ytOn = Prefs.ytEnabled(ctx)
        if (!lastfmOn && !ytOn) return
        QueueStore.get(ctx).enqueue(
            artist = playing.artist,
            title = playing.title,
            album = playing.album,
            durationSec = (playing.durationMs / 1000).toInt(),
            timestamp = playing.startedAtEpochSec,
            lastfmEnabled = lastfmOn,
            ytEnabled = ytOn,
        )
        Prefs.bumpCounter(ctx, "detected")
        Log.i(TAG, "Encolado: ${playing.artist} - ${playing.title}")
        scheduleFlush(ctx)
    }

    private fun sendNowPlaying(playing: Playing) {
        val ctx = appContext ?: return
        if (!Prefs.lastfmEnabled(ctx)) return
        // mejor esfuerzo: si falla no pasa nada, el scrobble va por la cola
        try {
            LastfmClient(Prefs.lastfmApiKey(ctx), Prefs.lastfmApiSecret(ctx)).updateNowPlaying(
                Prefs.lastfmSessionKey(ctx),
                playing.artist, playing.title, playing.album,
                (playing.durationMs / 1000).toInt(),
            )
        } catch (e: Exception) {
            Log.w(TAG, "now playing falló: ${e.message}")
        }
    }

    fun scheduleFlush(context: Context) {
        val request = OneTimeWorkRequest.Builder(FlushWorker::class.java)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("flush-queue", ExistingWorkPolicy.KEEP, request)
    }
}
