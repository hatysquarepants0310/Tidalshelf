import { NextResponse } from "next/server";
import { z } from "zod";
import { getSession } from "@/lib/session";
import { addShelfItem, deleteShelfItem, getShelfForUser } from "@/lib/shelf";

const addSchema = z.object({
  artist: z.string().trim().min(1).max(120),
  album: z.string().trim().min(1).max(120),
  imageUrl: z.string().trim().url().max(500).optional().or(z.literal("")),
  note: z.string().trim().max(280).optional(),
});

export async function POST(request: Request) {
  const session = await getSession();
  if (!session.userId) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  const body = await request.json().catch(() => null);
  const parsed = addSchema.safeParse(body);
  if (!parsed.success) {
    return NextResponse.json({ error: parsed.error.issues[0]?.message ?? "Datos inválidos" }, { status: 400 });
  }
  const item = addShelfItem({
    userId: session.userId,
    artist: parsed.data.artist,
    album: parsed.data.album,
    imageUrl: parsed.data.imageUrl || undefined,
    note: parsed.data.note,
  });
  return NextResponse.json({ item }, { status: 201 });
}

export async function GET() {
  const session = await getSession();
  if (!session.userId) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  return NextResponse.json({ items: getShelfForUser(session.userId) });
}

export async function DELETE(request: Request) {
  const session = await getSession();
  if (!session.userId) {
    return NextResponse.json({ error: "No autenticado" }, { status: 401 });
  }
  const { searchParams } = new URL(request.url);
  const id = searchParams.get("id");
  if (!id) {
    return NextResponse.json({ error: "Falta el id" }, { status: 400 });
  }
  const deleted = deleteShelfItem(id, session.userId);
  if (!deleted) {
    return NextResponse.json({ error: "No encontrado" }, { status: 404 });
  }
  return NextResponse.json({ ok: true });
}
