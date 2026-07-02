#!/usr/bin/env python3
"""Puente Tidal -> Shelf.

Shelf solo lee Spotify y YouTube Music, y Tidal no tiene API pública de
historial. Este script cierra ese hueco: lee tus scrobbles de Last.fm
(donde Tidal sí puede registrar cada escucha, con su integración oficial),
busca cada canción en YouTube Music y la marca como escuchada en tu
historial de YT Music — sin reproducirla. Shelf lee ese historial y tus
escuchas de Tidal aparecen ahí como si nada.

Importante: conecta SOLO Tidal a Last.fm (no Spotify). Shelf ya lee
Spotify directo; si Spotify también scrobblea a Last.fm, cada escucha de
Spotify aparecería duplicada en Shelf (una vía Spotify y otra vía YT Music).

Uso:
    python3 tidal_to_ytmusic.py              # ciclo continuo
    python3 tidal_to_ytmusic.py --once       # un solo ciclo y salir
    python3 tidal_to_ytmusic.py --dry-run    # no escribe en YT Music, solo muestra
    python3 tidal_to_ytmusic.py --test-match "Artista" "Canción"

Configuración: variables de entorno o archivo .env junto a este script
(ver .env.example). Autenticación de YT Music: ejecuta `ytmusicapi browser`
y guarda el resultado como browser.json junto a este script.
"""

from __future__ import annotations

import argparse
import difflib
import json
import re
import sys
import time
import unicodedata
from dataclasses import dataclass
from pathlib import Path

import requests

HERE = Path(__file__).resolve().parent
STATE_PATH = HERE / "state.json"
MISSES_PATH = HERE / "misses.log"
LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/"


# ---------------------------------------------------------------------------
# Configuración
# ---------------------------------------------------------------------------

def load_dotenv(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    if not path.exists():
        return values
    for line in path.read_text().splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip().strip("\"'")
    return values


@dataclass
class Config:
    lastfm_api_key: str
    lastfm_username: str
    ytmusic_auth: Path
    poll_seconds: int
    match_threshold: float

    @classmethod
    def load(cls) -> "Config":
        import os

        env = {**load_dotenv(HERE / ".env"), **os.environ}
        missing = [k for k in ("LASTFM_API_KEY", "LASTFM_USERNAME") if not env.get(k)]
        if missing:
            sys.exit(
                f"Falta configurar: {', '.join(missing)}. "
                f"Copia {HERE / '.env.example'} a {HERE / '.env'} y complétalo."
            )
        return cls(
            lastfm_api_key=env["LASTFM_API_KEY"],
            lastfm_username=env["LASTFM_USERNAME"],
            ytmusic_auth=Path(env.get("YTMUSIC_AUTH", str(HERE / "browser.json"))),
            poll_seconds=int(env.get("POLL_SECONDS", "90")),
            match_threshold=float(env.get("MATCH_THRESHOLD", "0.72")),
        )


# ---------------------------------------------------------------------------
# Last.fm
# ---------------------------------------------------------------------------

@dataclass
class Scrobble:
    artist: str
    title: str
    album: str
    uts: int


def fetch_recent_scrobbles(config: Config, limit: int = 30) -> list[Scrobble]:
    response = requests.get(
        LASTFM_API_URL,
        params={
            "method": "user.getrecenttracks",
            "user": config.lastfm_username,
            "api_key": config.lastfm_api_key,
            "format": "json",
            "limit": str(limit),
        },
        timeout=30,
    )
    data = response.json()
    if "error" in data:
        raise RuntimeError(f"Last.fm error {data['error']}: {data.get('message')}")

    tracks = data.get("recenttracks", {}).get("track", [])
    if isinstance(tracks, dict):
        tracks = [tracks]

    scrobbles = []
    for track in tracks:
        date = track.get("date")
        if not date:  # entrada "now playing": aún no es un scrobble definitivo
            continue
        scrobbles.append(
            Scrobble(
                artist=track.get("artist", {}).get("#text", ""),
                title=track.get("name", ""),
                album=track.get("album", {}).get("#text", ""),
                uts=int(date["uts"]),
            )
        )
    scrobbles.sort(key=lambda s: s.uts)
    return scrobbles


# ---------------------------------------------------------------------------
# Matching contra YouTube Music
# ---------------------------------------------------------------------------

_PAREN_RE = re.compile(r"[\(\[][^)\]]*[\)\]]")
_FEAT_RE = re.compile(r"\b(feat|ft|featuring|con|with)\b.*", re.IGNORECASE)
_PUNCT_RE = re.compile(r"[^\w\s]")


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", text)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = _PAREN_RE.sub(" ", text)
    text = _FEAT_RE.sub(" ", text)
    text = _PUNCT_RE.sub(" ", text.casefold())
    return " ".join(text.split())


def similarity(a: str, b: str) -> float:
    return difflib.SequenceMatcher(None, normalize(a), normalize(b)).ratio()


def score_candidate(scrobble: Scrobble, candidate: dict) -> float:
    cand_title = candidate.get("title") or ""
    title_sim = similarity(scrobble.title, cand_title)
    # Los resultados tipo video suelen titularse "Artista - Canción (Official
    # Video)"; si el título buscado aparece completo dentro del candidato,
    # cuenta como buen match de título aunque el ratio global sea bajo.
    wanted = normalize(scrobble.title)
    if wanted and wanted in normalize(cand_title):
        title_sim = max(title_sim, 0.9)
    artists = " ".join(a.get("name", "") for a in candidate.get("artists") or [])
    artist_sim = similarity(scrobble.artist, artists) if artists else 0.0
    if normalize(scrobble.artist) and normalize(scrobble.artist) in normalize(f"{artists} {cand_title}"):
        artist_sim = max(artist_sim, 0.9)
    score = 0.6 * title_sim + 0.4 * artist_sim
    if candidate.get("resultType") == "song":
        score += 0.05
    return min(score, 1.0)


def find_best_match(yt, scrobble: Scrobble, threshold: float) -> tuple[dict | None, float]:
    query = f"{scrobble.artist} {scrobble.title}"
    candidates = yt.search(query, filter="songs", limit=5)
    if not candidates:
        # Sesiones sin cookies de música a veces no devuelven "songs";
        # además hay pistas que solo existen como video.
        candidates = [
            c for c in yt.search(query, limit=10)
            if c.get("resultType") in ("song", "video") and c.get("videoId")
        ]
    best, best_score = None, 0.0
    for candidate in candidates:
        if not candidate.get("videoId"):
            continue
        s = score_candidate(scrobble, candidate)
        if s > best_score:
            best, best_score = candidate, s
    if best is not None and best_score >= threshold:
        return best, best_score
    return None, best_score


# ---------------------------------------------------------------------------
# Estado (marca de agua para no reprocesar scrobbles)
# ---------------------------------------------------------------------------

def load_state() -> dict:
    if STATE_PATH.exists():
        return json.loads(STATE_PATH.read_text())
    return {}


def save_state(state: dict) -> None:
    STATE_PATH.write_text(json.dumps(state))


def log(message: str) -> None:
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {message}", flush=True)


# ---------------------------------------------------------------------------
# Ciclo principal
# ---------------------------------------------------------------------------

def make_ytmusic(config: Config, dry_run: bool):
    from ytmusicapi import YTMusic

    if config.ytmusic_auth.exists():
        return YTMusic(str(config.ytmusic_auth))
    if dry_run:
        log("Sin browser.json: usando sesión anónima (suficiente para --dry-run).")
        return YTMusic()
    sys.exit(
        f"No existe {config.ytmusic_auth}. Ejecuta `ytmusicapi browser` (ver README) "
        "para autenticarte con tu cuenta de YouTube Music."
    )


def process_cycle(yt, config: Config, state: dict, dry_run: bool) -> None:
    scrobbles = fetch_recent_scrobbles(config)
    if not scrobbles:
        return

    if "last_uts" not in state:
        # Primera ejecución: marcar el presente y solo registrar escuchas futuras,
        # para no volcar de golpe todo el historial viejo en YT Music.
        state["last_uts"] = scrobbles[-1].uts
        save_state(state)
        log(
            f"Primera ejecución: marca de agua en {scrobbles[-1].uts}. "
            "Desde ahora, cada escucha nueva en Tidal se registrará en YT Music."
        )
        return

    pending = [s for s in scrobbles if s.uts > state["last_uts"]]
    for scrobble in pending:
        try:
            match, score = find_best_match(yt, scrobble, config.match_threshold)
        except Exception as exc:  # error de red/API: reintentar en el próximo ciclo
            log(f"Error buscando '{scrobble.artist} - {scrobble.title}': {exc}")
            return

        if match is None:
            log(
                f"SIN MATCH ({score:.2f}): {scrobble.artist} - {scrobble.title} "
                "(anotado en misses.log)"
            )
            with MISSES_PATH.open("a") as fh:
                fh.write(f"{scrobble.uts}\t{scrobble.artist}\t{scrobble.title}\n")
        else:
            label = (
                f"{scrobble.artist} - {scrobble.title} -> "
                f"[{match['videoId']}] {match.get('title')} ({score:.2f})"
            )
            if dry_run:
                log(f"DRY-RUN, se registraría: {label}")
            else:
                try:
                    song = yt.get_song(match["videoId"])
                    response = yt.add_history_item(song)
                    if response.status_code == 204:
                        log(f"Registrado en YT Music: {label}")
                    else:
                        log(f"YT Music respondió {response.status_code} para: {label}")
                except Exception as exc:
                    log(f"Error registrando '{label}': {exc}")
                    return  # no avanzar la marca de agua: reintentar luego

        state["last_uts"] = scrobble.uts
        save_state(state)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--once", action="store_true", help="ejecutar un solo ciclo")
    parser.add_argument("--dry-run", action="store_true", help="no escribir en YT Music")
    parser.add_argument(
        "--test-match", nargs=2, metavar=("ARTISTA", "CANCION"),
        help="probar el matching de una canción y salir",
    )
    args = parser.parse_args()

    config = Config.load()
    yt = make_ytmusic(config, dry_run=args.dry_run or bool(args.test_match))

    if args.test_match:
        artist, title = args.test_match
        scrobble = Scrobble(artist=artist, title=title, album="", uts=0)
        match, score = find_best_match(yt, scrobble, threshold=0.0)
        if match:
            print(f"Mejor match ({score:.2f}): [{match['videoId']}] "
                  f"{match.get('title')} - {[a.get('name') for a in match.get('artists') or []]}")
        else:
            print("Sin candidatos.")
        return

    state = load_state()
    log(
        f"Puente iniciado: Last.fm({config.lastfm_username}) -> YT Music "
        f"cada {config.poll_seconds}s{' [DRY-RUN]' if args.dry_run else ''}"
    )
    while True:
        try:
            process_cycle(yt, config, state, dry_run=args.dry_run)
        except Exception as exc:
            log(f"Error en el ciclo: {exc}")
        if args.once:
            break
        time.sleep(config.poll_seconds)


if __name__ == "__main__":
    main()
