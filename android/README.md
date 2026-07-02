# Tidalshelf Scrobbler (Android)

App para Android que hace que **todo lo que escuchas en Tidal aparezca en Shelf** — sin PC, sin
servidor: tu teléfono lo hace todo.

## Cómo funciona

```
Tidal suena en tu teléfono
   │  (la app lee la sesión de medios del sistema, como cualquier scrobbler)
   ├──> scrobble a Last.fm            (opcional: tu perfil de Last.fm)
   └──> historial de YouTube Music    (Shelf lo lee → tus escuchas de Tidal en Shelf)
```

- **Detección**: un `NotificationListenerService` con acceso a notificaciones lee la MediaSession
  de Tidal (pista, artista, duración, play/pausa). Android mantiene y resucita este servicio
  mientras el permiso esté concedido — por eso la app "siempre corre" sin gastar batería en un
  servicio permanente.
- **Reglas de scrobbling estándar** (las de Last.fm): la pista debe durar >30 s y se registra al
  sonar la mitad o 4 minutos, lo que llegue primero. Timestamp = cuándo empezó a sonar.
- **YT Music sin reproducir**: registra la canción en tu historial con el mismo mecanismo que la
  librería ytmusicapi (`player` → URL de tracking → señal de reproducción). La canción aparece en
  tu historial como escuchada, y Shelf la recoge.
- **Cola offline**: cada escucha se guarda en SQLite y se envía cuando haya red (WorkManager, con
  reintentos exponenciales). Escuchar en modo avión no pierde nada.
- **Matching difuso** con umbral: si una pista de Tidal no existe en YT Music, se contabiliza como
  "sin match" en vez de registrar una canción equivocada.

## Instalación

**Opción A (recomendada):** descarga el APK del último build en la pestaña
[Actions](../../actions) del repo (artifact `tidalshelf-scrobbler-debug`) e instálalo. Android te
pedirá permitir "instalar apps desconocidas".

**Opción B:** compílalo tú: abre `android/` en Android Studio, o por consola:

```bash
cd android && gradle assembleDebug
# APK en app/build/outputs/apk/debug/app-debug.apk
```

## Configuración (los 4 pasos de la pantalla principal)

1. **Acceso a notificaciones** — obligatorio; es como la app ve qué suena en Tidal.
2. **Last.fm** (opcional) — necesitas una [clave de API gratuita](https://www.last.fm/api/account/create).
   Tu contraseña no se guarda: se usa una sola vez para obtener la sesión (así funciona el
   protocolo oficial de Last.fm para apps móviles).
3. **YouTube Music** — inicia sesión con tu cuenta de Google en el WebView integrado. La app solo
   guarda las cookies de music.youtube.com, localmente. (Si el login de Google se pone difícil en
   WebView, hay opción avanzada de pegar la cookie a mano.)
4. **Batería** — excluye la app de la optimización para que tu fabricante (Xiaomi, Samsung…) no
   la mate en segundo plano. En algunos teléfonos además conviene "bloquear" la app en recientes.

Y verifica en [myaccount.google.com](https://myaccount.google.com) → Datos y privacidad → que el
**historial de YouTube no esté pausado**, o las canciones no se guardarán.

## Notas honestas

- No conectes también Spotify a Last.fm ni scrobblees Spotify con esta app: Shelf ya lee Spotify
  directo y saldría duplicado. Esta app **solo escucha a Tidal** (`com.aspiro.tidal`), así que por
  defecto ya estás protegido.
- La parte de YT Music usa la API interna de YouTube (no pública), igual que ytmusicapi. Es un
  mecanismo maduro y muy usado, pero Google podría cambiarlo; si deja de funcionar, abre un issue.
- Las escuchas aparecen en YT Music con la hora del registro (segundos después de la real).
- El APK de Actions está firmado con clave de debug: para publicar en una tienda haría falta
  firma de release.
