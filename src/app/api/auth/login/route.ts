import { NextResponse } from "next/server";
import { z } from "zod";
import { getUserByEmailOrUsername, toPublicUser, verifyPassword } from "@/lib/users";
import { getSession } from "@/lib/session";

const loginSchema = z.object({
  identifier: z.string().trim().min(1, "Ingresa tu usuario o email"),
  password: z.string().min(1, "Ingresa tu contraseña"),
});

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const parsed = loginSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: parsed.error.issues[0]?.message ?? "Datos inválidos" }, { status: 400 });
  }

  const user = getUserByEmailOrUsername(parsed.data.identifier);
  const ok = user ? await verifyPassword(user, parsed.data.password) : false;
  if (!user || !ok) {
    return NextResponse.json({ error: "Usuario o contraseña incorrectos" }, { status: 401 });
  }

  const session = await getSession();
  session.userId = user.id;
  session.username = user.username;
  await session.save();

  return NextResponse.json({ user: toPublicUser(user) });
}
