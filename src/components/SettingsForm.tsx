"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import type { PublicUser } from "@/lib/users";

export default function SettingsForm({ user }: { user: PublicUser }) {
  const router = useRouter();
  const [displayName, setDisplayName] = useState(user.displayName ?? "");
  const [bio, setBio] = useState(user.bio ?? "");
  const [lastfmUsername, setLastfmUsername] = useState(user.lastfmUsername ?? "");
  const [status, setStatus] = useState<"idle" | "saving" | "saved" | "error">("idle");
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setStatus("saving");
    setError(null);
    const res = await fetch("/api/me", {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ displayName, bio, lastfmUsername }),
    });
    const data = await res.json();
    if (!res.ok) {
      setStatus("error");
      setError(data.error ?? "No se pudo guardar");
      return;
    }
    setStatus("saved");
    router.refresh();
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4 rounded-2xl border border-neutral-200 p-5 dark:border-neutral-800">
      <h2 className="font-medium">Perfil</h2>
      <label className="flex flex-col gap-1 text-sm">
        Nombre visible
        <input
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Bio
        <textarea
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          maxLength={280}
          rows={3}
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Usuario de Last.fm
        <input
          value={lastfmUsername}
          onChange={(e) => setLastfmUsername(e.target.value)}
          placeholder="ej: rj"
          className="rounded-lg border border-neutral-300 bg-white px-3 py-2 dark:border-neutral-700 dark:bg-neutral-900"
        />
        <span className="text-xs text-neutral-500">
          Conecta Tidal y Spotify a Last.fm primero — ver{" "}
          <a href="/conectar" className="underline">
            guía
          </a>
          .
        </span>
      </label>
      {error && <p className="text-sm text-red-600">{error}</p>}
      <button
        type="submit"
        disabled={status === "saving"}
        className="self-start rounded-full bg-neutral-900 px-4 py-2 text-sm font-medium text-white hover:bg-neutral-700 disabled:opacity-50 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
      >
        {status === "saving" ? "Guardando…" : "Guardar"}
      </button>
      {status === "saved" && <p className="text-sm text-green-600">Guardado ✓</p>}
    </form>
  );
}
