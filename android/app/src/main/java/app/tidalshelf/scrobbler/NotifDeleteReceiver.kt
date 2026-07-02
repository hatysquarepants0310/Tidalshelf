package app.tidalshelf.scrobbler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Se dispara cuando el usuario desliza la notificación de estado. Mientras el
 * interruptor de la app esté encendido, la reponemos de inmediato — el único
 * lugar donde se apaga de verdad es el switch. (El deleteIntent NO se dispara
 * cuando la app la cancela programáticamente, así que apagar el switch no
 * entra en bucle.)
 */
class NotifDeleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.statusNotifEnabled(context)) {
            StatusNotifier.repost(context)
        }
    }
}
