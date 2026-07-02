import type { Metadata } from "next";
import Link from "next/link";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { getSession } from "@/lib/session";
import NavAuthLinks from "@/components/NavAuthLinks";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "Tidalshelf",
  description: "Tu historial de Spotify y Tidal, unificado en un solo perfil.",
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const session = await getSession();

  return (
    <html
      lang="es"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-neutral-50 text-neutral-900 dark:bg-neutral-950 dark:text-neutral-100">
        <header className="border-b border-neutral-200 dark:border-neutral-800">
          <nav className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
            <Link href="/" className="text-lg font-semibold tracking-tight">
              🎧 Tidalshelf
            </Link>
            <div className="flex items-center gap-4 text-sm">
              <Link href="/conectar" className="hover:underline">
                Cómo conectar
              </Link>
              <NavAuthLinks username={session.username} />
            </div>
          </nav>
        </header>
        <main className="mx-auto flex w-full max-w-4xl flex-1 flex-col px-4 py-8">{children}</main>
        <footer className="border-t border-neutral-200 px-4 py-6 text-center text-xs text-neutral-500 dark:border-neutral-800">
          Tidalshelf usa la API pública de Last.fm para unificar Spotify y Tidal. No está afiliado a Spotify, Tidal ni Last.fm.
        </footer>
      </body>
    </html>
  );
}
