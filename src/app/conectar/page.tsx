import Link from "next/link";

export const metadata = { title: "Cómo conectar · Tidalshelf" };

export default function ConectarPage() {
  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-2xl font-semibold">Cómo funciona Tidalshelf</h1>
        <p className="mt-2 text-neutral-600 dark:text-neutral-400">
          Ni Spotify ni Tidal dejan que otras apps lean el historial de la otra. Y Tidal ni
          siquiera tiene una API pública para leer tu historial de escucha (sus términos de
          desarrollador lo prohíben explícitamente). El único servicio al que{" "}
          <strong>ambas</strong> apps pueden scrobblear de forma nativa es{" "}
          <a
            className="underline"
            href="https://www.last.fm"
            target="_blank"
            rel="noopener noreferrer"
          >
            Last.fm
          </a>
          . Por eso Tidalshelf no se conecta directamente a Spotify o Tidal: usa Last.fm como
          puente neutral y lee de ahí con su API pública.
        </p>
      </div>

      <ol className="flex flex-col gap-6">
        <li className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <h2 className="font-medium">1. Crea una cuenta de Last.fm (si no tienes)</h2>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            Es gratis y solo toma un minuto.
          </p>
          <a
            className="mt-2 inline-block text-sm underline"
            href="https://www.last.fm/join"
            target="_blank"
            rel="noopener noreferrer"
          >
            last.fm/join ↗
          </a>
        </li>

        <li className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <h2 className="font-medium">2. Conecta Tidal y Spotify a Last.fm</h2>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            Desde la app de Tidal: <em>Ajustes → Cuenta → Enlazar cuentas → Last.fm</em>. Desde
            Spotify: <em>Ajustes → Redes sociales → Last.fm</em>. Last.fm también tiene una guía
            oficial con los pasos actualizados para cada app:
          </p>
          <a
            className="mt-2 inline-block text-sm underline"
            href="https://www.last.fm/about/trackmymusic"
            target="_blank"
            rel="noopener noreferrer"
          >
            last.fm/about/trackmymusic ↗
          </a>
        </li>

        <li className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <h2 className="font-medium">3. Escucha música normalmente por un rato</h2>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            Cada canción que termines de escuchar en Tidal o Spotify se registra (&quot;scrobblea&quot;)
            automáticamente en tu perfil de Last.fm, sin que tengas que hacer nada más.
          </p>
        </li>

        <li className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <h2 className="font-medium">4. Enlaza tu usuario de Last.fm en Tidalshelf</h2>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            Crea tu cuenta de Tidalshelf, entra a Ajustes y pega tu nombre de usuario de Last.fm.
            Tidalshelf va a mostrar en tu perfil público tus reproducciones recientes y tus top
            álbumes, sin importar si vinieron de Tidal o de Spotify.
          </p>
          <div className="mt-3 flex gap-3 text-sm">
            <Link className="underline" href="/signup">
              Crear cuenta
            </Link>
            <Link className="underline" href="/settings">
              Ir a Ajustes
            </Link>
          </div>
        </li>
      </ol>
    </div>
  );
}
