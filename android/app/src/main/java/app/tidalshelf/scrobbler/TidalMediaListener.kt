package app.tidalshelf.scrobbler

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Detección de reproducción de Tidal vía MediaSession. Un
 * NotificationListenerService con permiso de acceso a notificaciones puede
 * leer las sesiones de medios activas del sistema; ahí Tidal publica pista,
 * artista, duración y estado play/pausa. El sistema mantiene (y resucita)
 * este servicio mientras el permiso esté concedido: eso es lo que hace que la
 * app "siempre corra en segundo plano" sin necesitar un servicio foreground.
 */
class TidalMediaListener : NotificationListenerService() {

    companion object {
        private const val TAG = "TidalMediaListener"
        private val TIDAL_PACKAGES = setOf("com.aspiro.tidal")
    }

    private var sessionManager: MediaSessionManager? = null
    private var tidalController: MediaController? = null

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            attachTidal(controllers ?: emptyList())
        }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            metadata ?: return
            ScrobbleEngine.onMetadataChanged(
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty(),
                durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION),
            )
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            state ?: return
            ScrobbleEngine.onPlaybackChanged(state.state == PlaybackState.STATE_PLAYING)
        }

        override fun onSessionDestroyed() {
            detachTidal()
            ScrobbleEngine.onSessionGone()
        }
    }

    override fun onListenerConnected() {
        ScrobbleEngine.init(this)
        val component = ComponentName(this, TidalMediaListener::class.java)
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            sessionManager = manager
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            attachTidal(manager.getActiveSessions(component))
            Log.i(TAG, "Listener conectado; esperando sesiones de Tidal")
        } catch (e: SecurityException) {
            Log.e(TAG, "Sin permiso de acceso a notificaciones", e)
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        detachTidal()
        ScrobbleEngine.onSessionGone()
    }

    private fun attachTidal(controllers: List<MediaController>) {
        val tidal = controllers.firstOrNull { it.packageName in TIDAL_PACKAGES }
        if (tidal == null) {
            if (tidalController != null) {
                detachTidal()
                ScrobbleEngine.onSessionGone()
            }
            return
        }
        if (tidalController?.sessionToken == tidal.sessionToken) return
        detachTidal()
        tidalController = tidal
        tidal.registerCallback(controllerCallback)
        Log.i(TAG, "Sesión de Tidal enganchada")
        // estado inicial: puede que ya esté sonando algo
        controllerCallback.onMetadataChanged(tidal.metadata)
        controllerCallback.onPlaybackStateChanged(tidal.playbackState)
    }

    private fun detachTidal() {
        tidalController?.unregisterCallback(controllerCallback)
        tidalController = null
    }
}
