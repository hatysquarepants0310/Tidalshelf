# Tidalshelf

Un perfil público que junta tu historial de escucha de **Spotify** y **Tidal** en un solo lugar
— algo que ninguna de las dos apps hace por su cuenta.

## Por qué existe

Tidal no tiene una API pública para leer el historial de escucha de un usuario (sus términos de
desarrollador lo prohíben explícitamente), y la API de Spotify solo cubre Spotify. El único
servicio al que ambas apps pueden scrobblear de forma nativa es [Last.fm](https://www.last.fm).
Tidalshelf no se conecta directamente a Spotify ni a Tidal: usa Last.fm como puente neutral y lee
de ahí con su API pública. Los pasos para conectar cada app están en `/conectar` dentro de la app.

## Stack

- Next.js (App Router) + TypeScript + Tailwind CSS
- SQLite vía el módulo nativo `node:sqlite` de Node (sin dependencias binarias externas)
- Sesiones firmadas con `iron-session`, contraseñas con `bcryptjs`
- Datos de escucha en vivo desde la API pública de Last.fm

## Desarrollo local

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
