# Tidalshelf

Herramientas para que **todo lo que escuchas en Tidal quede registrado** aunque tus apps de
tracking (como Shelf) solo soporten Spotify y YouTube Music.

## El problema

Shelf y apps similares leen tu historial de Spotify y YouTube Music, pero no el de Tidal — y no
pueden: Tidal no tiene API pública de historial de escucha (sus términos de desarrollador lo
prohíben explícitamente), y la API de Spotify no permite escribirle escuchas externas. Lo único
que Tidal sí permite oficialmente es scrobblear a [Last.fm](https://www.last.fm).

## Componente 1: la app Android (`android/`) — lo principal

**Tidalshelf Scrobbler**: detecta en el propio teléfono lo que suena en Tidal (vía MediaSession,
como cualquier scrobbler) y lo registra en dos destinos, sin PC ni servidor:

```
Tidal suena en tu teléfono ──┬──> Last.fm (scrobbler clásico, opcional)
                             └──> historial de YouTube Music ──> Shelf
Spotify ──────────(conexión nativa de Shelf)───────────────────> Shelf
```

Corre siempre en segundo plano (servicio de acceso a notificaciones, cola offline con
reintentos). Instalación y detalles en [`android/README.md`](android/README.md). El APK se
compila automáticamente en GitHub Actions.

## Componente 2: el puente de escritorio (`bridge/`)

La alternativa sin app: un script Python que lee tus scrobbles de Tidal desde Last.fm y registra
cada canción en tu historial de YT Music. Útil si prefieres correrlo en una Raspberry/servidor o
si usas Tidal principalmente en PC/iOS (donde la integración Tidal → Last.fm sigue siendo nativa).
Instrucciones en [`bridge/README.md`](bridge/README.md).

## Componente 3: perfil web unificado (opcional)

Una app web (este repo, Next.js) con perfiles públicos que muestran reproducciones recientes,
top álbumes y top artistas leyendo directo de Last.fm — útil si quieres una vista combinada
Tidal+Spotify sin depender de Shelf. Los pasos para conectar cada app están en `/conectar`
dentro de la propia app.

## Stack

- Next.js (App Router) + TypeScript + Tailwind CSS
- SQLite vía el módulo nativo `node:sqlite` de Node (sin dependencias binarias externas)
- Sesiones firmadas con `iron-session`, contraseñas con `bcryptjs`
- Datos de escucha en vivo desde la API pública de Last.fm

## Desarrollo local (app web)

1. Copia `.env.example` a `.env` y completa:
   - `LASTFM_API_KEY`: clave gratuita en https://www.last.fm/api/account/create
   - `SESSION_SECRET`: cadena aleatoria de al menos 32 caracteres (`openssl rand -base64 32`)
2. Instala dependencias y levanta el servidor:

   ```bash
   npm install
   npm run dev
   ```

3. Abre http://localhost:3000

La base de datos SQLite se crea automáticamente en `./data/tidalshelf.db` la primera vez que se
usa (esa carpeta está en `.gitignore`).

## Estructura

- `src/lib/lastfm.ts` — cliente de la API pública de Last.fm (recientes, top álbumes, top artistas)
- `src/lib/db.ts`, `src/lib/users.ts`, `src/lib/shelf.ts` — capa de datos sobre SQLite
- `src/lib/session.ts` — sesiones de usuario
- `src/app/u/[username]` — perfil público
- `src/app/settings` — ajustes de cuenta y shelf curado
- `src/app/conectar` — guía para enlazar Tidal y Spotify a Last.fm
