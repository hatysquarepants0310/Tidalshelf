import { notFound } from "next/navigation";
import { getUserByUsername } from "@/lib/users";
import { getShelfForUser } from "@/lib/shelf";
import {
  getRecentTracks,
  getTopAlbums,
  getTopArtists,
  LastfmConfigError,
  LastfmUserNotFoundError,
  type RecentTrack,
  type TopAlbum,
  type TopArtist,
} from "@/lib/lastfm";

export async function generateMetadata({ params }: { params: Promise<{ username: string }> }) {
  const { username } = await params;
  return { title: `${username} · Tidalshelf` };
}

export default async function ProfilePage({ params }: { params: Promise<{ username: string }> }) {
  const { username } = await params;
  const user = getUserByUsername(username);
  if (!user) notFound();

  const shelf = getShelfForUser(user.id);

  let recentTracks: RecentTrack[] = [];
  let topAlbums: TopAlbum[] = [];
  let topArtists: TopArtist[] = [];
  let lastfmError: string | null = null;

  if (user.lastfmUsername) {
    try {
      [recentTracks, topAlbums, topArtists] = await Promise.all([
        getRecentTracks(user.lastfmUsername, 10),
        getTopAlbums(user.lastfmUsername, "overall", 12),
        getTopArtists(user.lastfmUsername, "overall", 8),
      ]);
    } catch (err) {
      if (err instanceof LastfmUserNotFoundError) {
        lastfmError = `No se encontró el usuario "${user.lastfmUsername}" en Last.fm.`;
      } else if (err instanceof LastfmConfigError) {
        lastfmError = "Tidalshelf todavía no tiene configurada la API de Last.fm.";
      } else {
        lastfmError = "No se pudo cargar Last.fm en este momento.";
      }
    }
  }

  return (
    <div className="flex flex-col gap-10">
      <div>
        <h1 className="text-2xl font-semibold">{user.displayName || user.username}</h1>
        <p className="text-neutral-500">@{user.username}</p>
        {user.bio && <p className="mt-2 max-w-lg text-sm text-neutral-700 dark:text-neutral-300">{user.bio}</p>}
      </div>

      {!user.lastfmUsername && (
        <p className="rounded-xl border border-dashed border-neutral-300 p-4 text-sm text-neutral-600 dark:border-neutral-700 dark:text-neutral-400">
          Este perfil aún no conectó Last.fm, así que no se muestran escuchas de Spotify/Tidal
          todavía.
        </p>
      )}

      {lastfmError && (
        <p className="rounded-xl border border-dashed border-red-300 p-4 text-sm text-red-600 dark:border-red-800">
          {lastfmError}
        </p>
      )}

      {recentTracks.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-medium text-neutral-500">Escuchando</h2>
          <ul className="flex flex-col gap-2">
            {recentTracks.map((track, i) => (
              <li
                key={`${track.url}-${i}`}
                className="flex items-center gap-3 rounded-lg border border-neutral-200 p-2 dark:border-neutral-800"
              >
                {track.image ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={track.image} alt={track.album ?? track.name} className="h-10 w-10 rounded object-cover" />
                ) : (
                  <div className="h-10 w-10 rounded bg-neutral-200 dark:bg-neutral-800" />
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{track.name}</p>
                  <p className="truncate text-xs text-neutral-500">{track.artist}</p>
                </div>
                {track.nowPlaying && (
                  <span className="shrink-0 rounded-full bg-green-100 px-2 py-0.5 text-xs text-green-700 dark:bg-green-900 dark:text-green-300">
                    ahora
                  </span>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      {topAlbums.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-medium text-neutral-500">Top álbumes</h2>
          <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6">
            {topAlbums.map((album, i) => (
              <li key={`${album.url}-${i}`} className="flex flex-col gap-1">
                {album.image ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={album.image} alt={album.name} className="aspect-square w-full rounded-lg object-cover" />
                ) : (
                  <div className="aspect-square w-full rounded-lg bg-neutral-200 dark:bg-neutral-800" />
                )}
                <p className="truncate text-xs font-medium">{album.name}</p>
                <p className="truncate text-xs text-neutral-500">{album.artist}</p>
              </li>
            ))}
          </ul>
        </section>
      )}

      {topArtists.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-medium text-neutral-500">Top artistas</h2>
          <ul className="flex flex-wrap gap-2">
            {topArtists.map((artist, i) => (
              <li
                key={`${artist.url}-${i}`}
                className="rounded-full border border-neutral-200 px-3 py-1 text-sm dark:border-neutral-800"
              >
                {artist.name}
              </li>
            ))}
          </ul>
        </section>
      )}

      {shelf.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-medium text-neutral-500">Shelf curado</h2>
          <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4 md:grid-cols-6">
            {shelf.map((item) => (
              <li key={item.id} className="flex flex-col gap-1">
                {item.imageUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element
                  <img src={item.imageUrl} alt={item.album} className="aspect-square w-full rounded-lg object-cover" />
                ) : (
                  <div className="aspect-square w-full rounded-lg bg-neutral-200 dark:bg-neutral-800" />
                )}
                <p className="truncate text-xs font-medium">{item.album}</p>
                <p className="truncate text-xs text-neutral-500">{item.artist}</p>
                {item.note && <p className="truncate text-xs italic text-neutral-500">{item.note}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
