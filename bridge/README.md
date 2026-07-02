# Puente Tidal → Shelf

Hace que **todo lo que escuchas en Tidal aparezca en la app Shelf**, aprovechando que Shelf
lee YouTube Music.

## Cómo funciona

```
Tidal ──(integración oficial)──> Last.fm ──(este script)──> historial de YouTube Music ──> Shelf
Spotify ─────────────────────────────────(conexión nativa de Shelf)─────────────────────> Shelf
```

1. Tidal registra cada canción que escuchas en Last.fm (Tidal sí permite esto oficialmente).
2. Este script revisa Last.fm cada ~90 segundos.
3. Por cada escucha nueva, busca la canción en YouTube Music y la **marca como escuchada en tu
   historial de YT Music sin reproducirla**.
4. Shelf lee tu historial de YT Music → tus escuchas de Tidal aparecen en Shelf.

Tus escuchas de Spotify no pasan por aquí: Shelf ya las lee directo. Por eso es importante
conectar **solo Tidal** a Last.fm — si conectas también Spotify, esas escuchas llegarían a Shelf
por partida doble.

## Configuración (una sola vez)

1. **Haz que Tidal scrobblee a Last.fm** — el método depende del dispositivo, porque Tidal
   quitó la integración nativa de la app de Android (en iOS y escritorio sigue existiendo).
   Si no tienes cuenta de Last.fm: [last.fm/join](https://www.last.fm/join), es gratis.

   | Dónde escuchas Tidal | Cómo scrobblear |
   |---|---|
   | **Android** | Instala [Pano Scrobbler](https://play.google.com/store/apps/details?id=com.arn.scrobble) (gratis): lee la notificación de reproducción del sistema y scrobblea a Last.fm todo lo que suene, Tidal incluido. Conéctale tu cuenta de Last.fm y en sus ajustes deja habilitada solo la app de Tidal (para no scrobblear también Spotify y duplicar en Shelf). |
   | **iPhone/iPad** | La integración nativa sigue: app de Tidal → Perfil → ⚙️ → Last.fm. |
   | **PC/Mac (app o web)** | Integración nativa: Ajustes de Tidal → Enlazar cuentas → Last.fm. Para el reproductor web también sirve la extensión [Web Scrobbler](https://web-scrobbler.com/). |

   El puente no distingue de dónde vino el scrobble — con que llegue a Last.fm, fluye a Shelf.

2. **Consigue una clave de API de Last.fm** (gratis, un formulario):
   [last.fm/api/account/create](https://www.last.fm/api/account/create)

3. **Instala dependencias** (Python 3.10+):

   ```bash
   cd bridge
   pip install -r requirements.txt
   ```

4. **Autentícate en YouTube Music**:

   ```bash
   ytmusicapi browser
   ```

   Sigue las instrucciones (copiar los headers de una petición de music.youtube.com desde las
   DevTools del navegador con tu sesión iniciada) y guarda el resultado como `browser.json` en
   esta carpeta. Guía oficial: [ytmusicapi.readthedocs.io](https://ytmusicapi.readthedocs.io/en/stable/setup/browser.html)

5. **Crea tu `.env`**: copia `.env.example` a `.env` y rellena `LASTFM_API_KEY` y
   `LASTFM_USERNAME`.

6. En YouTube Music, verifica que el **historial de reproducción esté activado**
   (myaccount.google.com → Datos y privacidad → Historial de YouTube). Si está pausado, las
   canciones no se guardan.

## Uso

```bash
python3 tidal_to_ytmusic.py              # corre en bucle, revisando cada 90s
python3 tidal_to_ytmusic.py --dry-run    # simula sin escribir en YT Music
python3 tidal_to_ytmusic.py --once       # un solo ciclo (útil para cron)
python3 tidal_to_ytmusic.py --test-match "Bad Bunny" "Monaco"   # probar el matching
```

Para que corra siempre, déjalo en una Raspberry Pi, un servidor, o prográmalo con cron/systemd:

```
# crontab: cada 2 minutos
*/2 * * * * cd /ruta/a/Tidalshelf/bridge && python3 tidal_to_ytmusic.py --once >> bridge.log 2>&1
```

## Detalles y limitaciones honestas

- **La primera ejecución no registra nada**: solo marca "desde aquí en adelante", para no volcar
  todo tu historial viejo de golpe. A partir de ahí, cada escucha nueva fluye.
- Las canciones aparecen en YT Music con la hora en que el script las procesa (1-2 min después
  de la escucha real). Para las estadísticas de Shelf esto es irrelevante.
- Si una canción de Tidal no existe en YouTube Music (o el matching no está seguro), se anota en
  `misses.log` en vez de registrar algo incorrecto.
- `ytmusicapi` es una librería no oficial (la API de YT Music no es pública). Es un proyecto
  maduro y muy usado, pero Google podría romperla algún día; si eso pasa, actualiza la librería.
- El scrobbling de Tidal a Last.fm ocurre cuando la canción avanza lo suficiente (regla estándar
  de Last.fm: la mitad de la canción o 4 minutos, lo que ocurra primero).
