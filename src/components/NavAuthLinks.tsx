"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";

export default function NavAuthLinks({ username }: { username?: string }) {
  const router = useRouter();

  if (!username) {
    return (
      <div className="flex items-center gap-4">
        <Link href="/login" className="hover:underline">
          Iniciar sesión
        </Link>
        <Link
          href="/signup"
          className="rounded-full bg-neutral-900 px-3 py-1.5 text-white hover:bg-neutral-700 dark:bg-white dark:text-neutral-900 dark:hover:bg-neutral-200"
        >
          Crear cuenta
        </Link>
      </div>
    );
  }

  async function handleLogout() {
    await fetch("/api/auth/logout", { method: "POST" });
    router.push("/");
    router.refresh();
  }

  return (
    <div className="flex items-center gap-4">
      <Link href={`/u/${username}`} className="hover:underline">
        Mi perfil
      </Link>
      <Link href="/settings" className="hover:underline">
        Ajustes
      </Link>
      <button onClick={handleLogout} className="text-neutral-500 hover:underline">
        Salir
      </button>
    </div>
  );
}
