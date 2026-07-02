# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

## What this is

The end goal: make Tidal listens show up in tracking apps (specifically **Shelf**) that only
support Spotify and YouTube Music. Two hard constraints shape everything here: Tidal has no
public API for reading a user's play history (their developer terms explicitly forbid mining
it), and Spotify's API cannot *write* plays into a user's history. The only sanctioned outlet
Tidal has is scrobbling to **Last.fm**. Don't add direct Spotify/Tidal OAuth integrations
without revisiting these constraints.

Two components:

1. **`bridge/` (the core deliverable)** — a standalone Python daemon
   (`bridge/tidal_to_ytmusic.py`, stdlib + `requests` + `ytmusicapi`, no packaging). It polls
   Last.fm for new scrobbles (which come from Tidal), fuzzy-matches each track on YouTube Music,
   and registers it in the user's YT Music listening history *without playing it* (via
   `ytmusicapi`'s `get_song` + `add_history_item`). Shelf reads YT Music history, so Tidal plays
   appear in Shelf. Users must link **only Tidal** (not Spotify) to Last.fm — Shelf already reads
   Spotify natively, so bridging Spotify scrobbles too would double-count. A `state.json`
   watermark (`last_uts`) prevents reprocessing; the first run only sets the watermark so old
   history isn't bulk-dumped. Unmatched tracks go to `misses.log` rather than registering a wrong
   song. `--dry-run` works without YT Music auth (anonymous session; note `filter="songs"`
   returns empty anonymously, hence the unfiltered-search fallback in `find_best_match`).

2. **The Next.js web app** — an optional public-profile viewer (`/u/[username]`) showing recent
   tracks / top albums / top artists read live from Last.fm's public API.

## Commands

```bash
npm run dev      # start dev server (Turbopack)
npm run build    # production build (also runs the TypeScript check)
npm run start    # run a production build
npm run lint     # eslint (flat config: eslint-config-next core-web-vitals + typescript)
```

There is no test suite configured.

### Local setup

Copy `.env.example` to `.env` and set:
- `LASTFM_API_KEY` — free key from https://www.last.fm/api/account/create. Without it,
  `src/lib/lastfm.ts` throws `LastfmConfigError` (caught and shown as a friendly message on
  profile pages, not a crash).
- `SESSION_SECRET` — random string, >= 32 chars. In `NODE_ENV=production` a missing/short secret
  throws at request time; in dev it falls back to a hardcoded insecure secret with a console
  warning (see `src/lib/session.ts`).

The SQLite file is created on first access at `./data/tidalshelf.db` (`DATABASE_PATH` env var
overrides the path). That directory is gitignored.

## Architecture

Next.js App Router, TypeScript, Tailwind CSS v4. Import alias `@/*` → `./src/*`.

**Data layer (`src/lib/`)** — no ORM. `db.ts` opens a single `node:sqlite` `DatabaseSync`
connection (Node's built-in SQLite, chosen specifically to avoid native/binary deps like
`better-sqlite3` or Prisma's engine downloads) and creates the `users` / `shelf_items` tables on
first import. The connection is cached on `globalThis` so dev hot-reload doesn't reopen the file
repeatedly. **`PRAGMA busy_timeout` must be set before `PRAGMA journal_mode = WAL`** — Next's
build runs page-data collection across several parallel workers that all open this file at once,
and the first statement executed races other processes; if `busy_timeout` isn't already active by
then you'll intermittently get `SQLITE_BUSY: database is locked` during `npm run build`. If you
hit that error again, check statement ordering in `createDb()` first.

`users.ts` and `shelf.ts` each hand-map SQLite rows (snake_case columns) to camelCase TS
interfaces; there's no shared row-mapping abstraction, so add new columns/queries following the
existing `rowToUser` / `rowToItem` pattern. `node:sqlite`'s `.get()`/`.all()` return types don't
structurally match the row interfaces, so casts go through `as unknown as RowType` — this is
required, not a lint bypass to clean up.

`toPublicUser()` strips `passwordHash` and `email` before any user object crosses into a
server/client boundary (API responses, page props) — always use it rather than passing the raw
`User` type outward.

**Auth** — `src/lib/session.ts` wraps `iron-session` around `next/headers` cookies
(`tidalshelf_session`). Session only carries `userId`/`username`; there's no separate session
table. Routes that require auth check `session.userId` themselves (see any file under
`src/app/api/`) — there's no middleware-based route protection, and the `/settings` page enforces
login itself via `redirect("/login")` in the server component.

**Last.fm client (`src/lib/lastfm.ts`)** — thin wrapper around `ws.audioscrobbler.com/2.0`. Throws
typed errors (`LastfmUserNotFoundError`, `LastfmConfigError`) that calling code (currently just
`src/app/u/[username]/page.tsx`) catches to render a graceful in-page message instead of a 500.
Follow that pattern for any new Last.fm-backed page — a user's profile should still render (name,
bio, curated shelf) even when Last.fm is unreachable or their `lastfmUsername` is wrong.

**Pages vs. components** — profile/settings pages under `src/app/` are async server components
that fetch data directly from `src/lib/*` (no internal fetch-to-own-API round trip). Interactive
pieces (forms, the shelf editor, nav auth state) are split out into client components in
`src/components/` and talk to the route handlers under `src/app/api/` via `fetch`.
