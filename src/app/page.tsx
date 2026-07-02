import Link from "next/link";
import ProfileSearch from "@/components/ProfileSearch";

export default function Home() {
  return (
    <div className="flex flex-1 flex-col gap-16">
      <section className="flex flex-col items-center gap-6 py-12 text-center">
        <h1 className="max-w-2xl text-4xl font-semibold tracking-tight sm:text-5xl">
          Todo lo que escuchas, en un solo perfil.
        </h1>
        <p className="max-w-xl text-lg text-neutral-600 dark:text-neutral-400">
          Tidalshelf junta tu historial de <strong>Spotify</strong> y <strong>Tidal</strong>{" "}
          en un único perfil público, tipo &quot;shelf&quot; de música — algo que ninguna de las
          dos apps hace sola.
        </p>
        <div className="flex flex-wrap items-center justify-center gap-3">
          <Link
            href="/signup"
            className="rounded-full bg-neutral-900 px-5 py-2.5 text-sm font-medium text-white hover:bg-neutral-700 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
          >
            Crear mi perfil
          </Link>
          <Link
            href="/conectar"
            className="rounded-full border border-neutral-300 px-5 py-2.5 text-sm font-medium hover:bg-neutral-100 dark:border-neutral-700 dark:hover:bg-neutral-900"
          >
            Cómo funciona
          </Link>
        </div>
      </section>

      <section className="flex flex-col items-center gap-4 rounded-2xl border border-neutral-200 bg-white p-6 dark:border-neutral-800 dark:bg-neutral-900">
        <h2 className="text-sm font-medium text-neutral-500">Buscar un perfil</h2>
        <ProfileSearch />
      </section>

      <section className="grid gap-6 sm:grid-cols-3">
        <div className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <div className="mb-2 text-2xl">1️⃣</div>
          <h3 className="font-medium">Conecta Tidal y Spotify a Last.fm</h3>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            Ambas apps ya saben scrobblear a Last.fm de forma nativa. Es el único punto en común
            entre las dos.
          </p>
        </div>
        <div className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <div className="mb-2 text-2xl">2️⃣</div>
          <h3 className="font-medium">Enlaza tu usuario de Last.fm</h3>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            En Ajustes, pega tu nombre de usuario de Last.fm. Tidalshelf lee tus scrobbles en vivo
            desde su API pública.
          </p>
        </div>
        <div className="rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
          <div className="mb-2 text-2xl">3️⃣</div>
          <h3 className="font-medium">Listo, tienes tu shelf</h3>
          <p className="mt-1 text-sm text-neutral-600 dark:text-neutral-400">
            Reproducciones recientes, top álbumes y una estantería curada, sin importar de qué
            app venga cada escucha.
          </p>
        </div>
      </section>
    </div>
  );
}
