"use client";

import { useState } from "react";
import type { ShelfItem } from "@/lib/shelf";

export default function ShelfManager({ initialItems }: { initialItems: ShelfItem[] }) {
  const [items, setItems] = useState(initialItems);
  const [artist, setArtist] = useState("");
  const [album, setAlbum] = useState("");
  const [imageUrl, setImageUrl] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleAdd(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch("/api/shelf", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ artist, album, imageUrl, note }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error ?? "No se pudo agregar");
        return;
      }
      setItems((prev) => [data.item, ...prev]);
      setArtist("");
      setAlbum("");
      setImageUrl("");
      setNote("");
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(id: string) {
    setItems((prev) => prev.filter((item) => item.id !== id));
    await fetch(`/api/shelf?id=${id}`, { method: "DELETE" });
  }

  return (
    <div className="flex flex-col gap-4 rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
      <h2 className="font-medium">Tu shelf curado</h2>
      <p className="text-sm text-neutral-600 dark:text-neutral-400">
        Álbumes que quieres destacar en tu perfil, más allá de tus top álbumes automáticos.
      </p>

      <form onSubmit={handleAdd} className="grid gap-3 sm:grid-cols-2">
        <input
          required
          value={artist}
          onChange={(e) => setArtist(e.target.value)}
          placeholder="Artista"
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
        />
        <input
          required
          value={album}
          onChange={(e) => setAlbum(e.target.value)}
          placeholder="Álbum"
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
        />
        <input
          value={imageUrl}
          onChange={(e) => setImageUrl(e.target.value)}
          placeholder="URL de portada (opcional)"
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm sm:col-span-2 dark:border-neutral-700 dark:bg-neutral-900"
        />
        <input
          value={note}
          onChange={(e) => setNote(e.target.value)}
          placeholder="Nota (opcional)"
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm sm:col-span-2 dark:border-neutral-700 dark:bg-neutral-900"
        />
        {error && <p className="text-sm text-red-600 sm:col-span-2">{error}</p>}
        <button
          type="submit"
          disabled={loading}
          className="self-start rounded-full bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-700 disabled:opacity-50 sm:col-span-2 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
        >
          {loading ? "Agregando…" : "Agregar al shelf"}
        </button>
      </form>

      {items.length > 0 && (
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {items.map((item) => (
            <li key={item.id} className="flex flex-col gap-1 rounded-lg border border-neutral-200 p-2 text-xs dark:border-neutral-800">
              {item.imageUrl && (
                // eslint-disable-next-line @next/next/no-img-element
                <img src={item.imageUrl} alt={item.album} className="aspect-square w-full rounded object-cover" />
              )}
              <span className="font-medium">{item.album}</span>
              <span className="text-neutral-500">{item.artist}</span>
              <button onClick={() => handleDelete(item.id)} className="mt-1 self-start text-red-600 hover:underline">
                Quitar
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
