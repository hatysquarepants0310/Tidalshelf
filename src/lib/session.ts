import { cookies } from "next/headers";
import { getIronSession, type SessionOptions } from "iron-session";

export interface SessionData {
  userId?: string;
  username?: string;
}

const FALLBACK_DEV_SECRET = "tidalshelf-insecure-dev-secret-change-me-32chars";

function resolveSecret(): string {
  const secret = process.env.SESSION_SECRET;
  if (secret && secret.length >= 32) return secret;
  if (process.env.NODE_ENV === "production") {
    throw new Error(
      "SESSION_SECRET debe estar definido (>= 32 caracteres) en producción. Revisa .env.example."
    );
  }
  console.warn(
    "[tidalshelf] SESSION_SECRET no definido o demasiado corto: usando un secreto de desarrollo inseguro."
  );
  return FALLBACK_DEV_SECRET;
}

const sessionOptions: SessionOptions = {
  password: resolveSecret(),
  cookieName: "tidalshelf_session",
  cookieOptions: {
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
  },
};

export async function getSession() {
  const cookieStore = await cookies();
  return getIronSession<SessionData>(cookieStore, sessionOptions);
}
