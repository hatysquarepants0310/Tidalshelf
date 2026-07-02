"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";

export default function ProfileSearch() {
  const [username, setUsername] = useState("");
  const router = useRouter();

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmed = username.trim();
    if (trimmed) router.push(`/u/${trimmed}`);
  }

  return (
    <form onSubmit={handleSubmit} className="flex w-full max-w-sm gap-2">
      <input
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        placeholder="nombre de usuario en Tidalshelf"
        className="flex-1 rounded-lg border border-neutral-300 bg-white px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900"
      />
      <button
        type="submit"
        className="rounded-lg bg-neutral-900 px-4 py-2 text-sm text-white hover:bg-neutral-700 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
      >
        Ver perfil
      </button>
    </form>
  );
}
