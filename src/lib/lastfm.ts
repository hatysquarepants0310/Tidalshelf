// Tidal has no public API for reading a user's listening history (their
// developer terms explicitly forbid mining play history), and Spotify's
// "recently played" endpoint only ever covers Spotify. Last.fm is the one
// service both Tidal and Spotify natively scrobble to, so we use Last.fm's
// public API as the neutral hub: users link Tidal -> Last.fm and
// Spotify -> Last.fm themselves (see /conectar), and we just read the
// merged result from Last.fm.

const LASTFM_API_URL = "https://ws.audioscrobbler.com/2.0/";

export class LastfmUserNotFoundError extends Error {}
export class LastfmConfigError extends Error {}

interface LastfmImage {
  "#text": string;
  size: string;
}

function pickImage(images?: LastfmImage[]): string | undefined {
  if (!images || images.length === 0) return undefined;
  const preferred =
    images.find((i) => i.size === "extralarge") ??
    images.find((i) => i.size === "large") ??
    images[images.length - 1];
  return preferred?.["#text"] || undefined;
}

async function lastfmRequest(params: Record<string, string>): Promise<Record<string, unknown>> {
  const apiKey = process.env.LASTFM_API_KEY;
  if (!apiKey) {
    throw new LastfmConfigError(
      "Falta LASTFM_API_KEY. Consigue una clave gratis en https://www.last.fm/api/account/create y agrégala a tu .env"
    );
  }
  const url = new URL(LASTFM_API_URL);
  const search = new URLSearchParams({ ...params, api_key: apiKey, format: "json" });
  url.search = search.toString();

  const res = await fetch(url, { next: { revalidate: 60 } });
  const data = (await res.json()) as Record<string, unknown>;

  if (typeof data.error === "number") {
    if (data.error === 6) {
      throw new LastfmUserNotFoundError(String(data.message ?? "Usuario de Last.fm no encontrado"));
    }
    throw new Error(String(data.message ?? "Error de la API de Last.fm"));
  }
  if (!res.ok) {
    throw new Error(`Error de la API de Last.fm (${res.status})`);
  }
  return data;
}

export interface RecentTrack {
  artist: string;
  name: string;
  album?: string;
  image?: string;
  url: string;
  nowPlaying: boolean;
  playedAt?: string;
}

export async function getRecentTracks(user: string, limit = 12): Promise<RecentTrack[]> {
  const data = await lastfmRequest({
    method: "user.getrecenttracks",
    user,
    limit: String(limit),
  });
  const recenttracks = data.recenttracks as
    | { track?: Array<Record<string, unknown>> }
    | undefined;
  const tracks = recenttracks?.track ?? [];
  const list = Array.isArray(tracks) ? tracks : [tracks];

  return list.map((t) => {
    const attr = t["@attr"] as { nowplaying?: string } | undefined;
    const date = t.date as { uts?: string } | undefined;
    const artist = t.artist as { "#text"?: string } | undefined;
    const album = t.album as { "#text"?: string } | undefined;
    return {
      artist: artist?.["#text"] ?? "Desconocido",
      name: String(t.name ?? ""),
      album: album?.["#text"] || undefined,
      image: pickImage(t.image as LastfmImage[] | undefined),
      url: String(t.url ?? ""),
      nowPlaying: attr?.nowplaying === "true",
      playedAt: date?.uts ? new Date(Number(date.uts) * 1000).toISOString() : undefined,
    };
  });
}

export interface TopAlbum {
  artist: string;
  name: string;
  image?: string;
  playcount: number;
  url: string;
}

export async function getTopAlbums(
  user: string,
  period: "overall" | "7day" | "1month" | "3month" | "6month" | "12month" = "overall",
  limit = 12
): Promise<TopAlbum[]> {
  const data = await lastfmRequest({
    method: "user.gettopalbums",
    user,
    period,
    limit: String(limit),
  });
  const topalbums = data.topalbums as { album?: Array<Record<string, unknown>> } | undefined;
  const albums = topalbums?.album ?? [];
  const list = Array.isArray(albums) ? albums : [albums];

  return list.map((a) => {
    const artist = a.artist as { name?: string } | undefined;
    return {
      artist: artist?.name ?? "Desconocido",
      name: String(a.name ?? ""),
      image: pickImage(a.image as LastfmImage[] | undefined),
      playcount: Number(a.playcount ?? 0),
      url: String(a.url ?? ""),
    };
  });
}

export interface TopArtist {
  name: string;
  playcount: number;
  image?: string;
  url: string;
}

export async function getTopArtists(
  user: string,
  period: "overall" | "7day" | "1month" | "3month" | "6month" | "12month" = "overall",
  limit = 8
): Promise<TopArtist[]> {
  const data = await lastfmRequest({
    method: "user.gettopartists",
    user,
    period,
    limit: String(limit),
  });
  const topartists = data.topartists as { artist?: Array<Record<string, unknown>> } | undefined;
  const artists = topartists?.artist ?? [];
  const list = Array.isArray(artists) ? artists : [artists];

  return list.map((a) => ({
    name: String(a.name ?? ""),
    playcount: Number(a.playcount ?? 0),
    image: pickImage(a.image as LastfmImage[] | undefined),
    url: String(a.url ?? ""),
  }));
}

export interface LastfmUserInfo {
  playcount: number;
  registeredAt?: string;
  image?: string;
  url: string;
}

export async function getUserInfo(user: string): Promise<LastfmUserInfo> {
  const data = await lastfmRequest({ method: "user.getinfo", user });
  const info = data.user as Record<string, unknown>;
  const registered = info.registered as { unixtime?: string } | undefined;
  return {
    playcount: Number(info.playcount ?? 0),
    registeredAt: registered?.unixtime
      ? new Date(Number(registered.unixtime) * 1000).toISOString()
      : undefined,
    image: pickImage(info.image as LastfmImage[] | undefined),
    url: String(info.url ?? ""),
  };
}
