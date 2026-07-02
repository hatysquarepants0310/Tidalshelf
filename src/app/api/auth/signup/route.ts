import { NextResponse } from "next/server";
import { z } from "zod";
import { createUser, getUserByUsername, toPublicUser } from "@/lib/users";
import { getSession } from "@/lib/session";

const signupSchema = z.object({
  username: z
    .string()
    .trim()
    .toLowerCase()
    .regex(/^[a-z0-9_]{3,20}$/, "El usuario debe tener 3-20 caracteres: letras, números o _"),
  email: z.string().trim().toLowerCase().email("Email inválido"),
  password: z.string().min(8, "La contraseña debe tener al menos 8 caracteres"),
  displayName: z.string().trim().max(60).optional(),
});

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const parsed = signupSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: parsed.error.issues[0]?.message ?? "Datos inválidos" }, { status: 400 });
  }
  const { username, email, password, displayName } = parsed.data;

  if (getUserByUsername(username)) {
    return NextResponse.json({ error: "Ese nombre de usuario ya está en uso" }, { status: 409 });
  }

  let user;
  try {
    user = await createUser({ username, email, password, displayName });
  } catch {
    return NextResponse.json({ error: "Ese email ya está registrado" }, { status: 409 });
  }

  const session = await getSession();
  session.userId = user.id;
  session.username = user.username;
  await session.save();

  return NextResponse.json({ user: toPublicUser(user) }, { status: 201 });
}
