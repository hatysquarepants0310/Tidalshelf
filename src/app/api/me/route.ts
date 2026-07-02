import { NextResponse } from "next/server";
import { z } from "zod";
import { getSession } from "@/lib/session";
import { getUserById, toPublicUser, updateUserProfile } from "@/lib/users";

export async function GET() {
  const session = await getSession();
  if (!session.userId) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  const user = getUserById(session.userId);
  if (!user) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  return NextResponse.json({ user: toPublicUser(user) });
}

const patchSchema = z.object({
  displayName: z.string().trim().max(60).optional(),
  bio: z.string().trim().max(280).optional(),
  lastfmUsername: z.string().trim().max(60).optional(),
});

export async function PATCH(request: Request) {
  const session = await getSession();
  if (!session.userId) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  const body = await request.json().catch(() => null);
  const parsed = patchSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: parsed.error.issues[0]?.message ?? "Datos inválidos" }, { status: 400 });
  }
  const user = updateUserProfile(session.userId, parsed.data);
  if (!user) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  return NextResponse.json({ user: toPublicUser(user) });
}
