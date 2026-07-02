package app.tidalshelf.scrobbler

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Vacía la cola persistente hacia los dos destinos. Corre con restricción de
 * red, con backoff exponencial, y WorkManager lo reintenta solo — así una
 * escucha en el metro sin señal llega a Last.fm/YT Music cuando vuelva la red.
 */
class FlushWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    companion object {
        private const val TAG = "FlushWorker"
    }

    override fun doWork(): Result {
        val ctx = applicationContext
        val queue = QueueStore.get(ctx)
        var hadFailure = false

        // --- Last.fm: por lotes de hasta 50 ---
        if (Prefs.lastfmEnabled(ctx)) {
            val pending = queue.pendingLastfm()
            if (pending.isNotEmpty()) {
                try {
                    val client = LastfmClient(Prefs.lastfmApiKey(ctx), Prefs.lastfmApiSecret(ctx))
                    client.scrobble(Prefs.lastfmSessionKey(ctx), pending)
                    for (play in pending) {
                        queue.setLastfmState(play.id, QueueStore.STATE_DONE)
                        Prefs.bumpCounter(ctx, "lastfm_ok")
                    }
                    Log.i(TAG, "Last.fm: ${pending.size} scrobbles enviados")
                } catch (e: Exception) {
                    Log.w(TAG, "Last.fm falló: ${e.message}")
                    for (play in pending) queue.bumpAttempts(play.id)
                    hadFailure = true
                }
            }
        }

        // --- YT Music: de a uno (búsqueda + match + registro) ---
        if (Prefs.ytEnabled(ctx)) {
            val client = YtMusicClient(Prefs.ytCookie(ctx))
            for (play in queue.pendingYt()) {
                try {
                    val (match, score) = client.registerPlay(play.artist, play.title)
                    if (match == null) {
                        queue.setYtState(play.id, QueueStore.STATE_MISS)
                        Prefs.bumpCounter(ctx, "yt_miss")
                        Log.i(TAG, "YT sin match (%.2f): %s - %s".format(score, play.artist, play.title))
                    } else {
                        queue.setYtState(play.id, QueueStore.STATE_DONE)
                        Prefs.bumpCounter(ctx, "yt_ok")
                        Log.i(TAG, "YT registrado (%.2f): %s -> %s".format(score, play.title, match.videoId))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "YT falló para '${play.artist} - ${play.title}': ${e.message}")
                    queue.bumpAttempts(play.id)
                    hadFailure = true
                }
            }
        }

        queue.prune()
        return if (hadFailure) Result.retry() else Result.success()
    }
}
