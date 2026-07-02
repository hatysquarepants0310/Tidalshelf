import { randomUUID } from "node:crypto";
import { db } from "./db";

export interface ShelfItem {
  id: string;
  userId: string;
  artist: string;
  album: string;
  imageUrl: string | null;
  note: string | null;
  addedAt: string;
}

interface ShelfItemRow {
  id: string;
  user_id: string;
  artist: string;
  album: string;
  image_url: string | null;
  note: string | null;
  added_at: string;
}

function rowToItem(row: ShelfItemRow): ShelfItem {
  return {
    id: row.id,
    userId: row.user_id,
    artist: row.artist,
    album: row.album,
    imageUrl: row.image_url,
    note: row.note,
    addedAt: row.added_at,
  };
}

export function getShelfForUser(userId: string): ShelfItem[] {
  const rows = db
    .prepare("SELECT * FROM shelf_items WHERE user_id = ? ORDER BY added_at DESC")
    .all(userId) as unknown as ShelfItemRow[];
  return rows.map(rowToItem);
}

export function addShelfItem(input: {
  userId: string;
  artist: string;
  album: string;
  imageUrl?: string;
  note?: string;
}): ShelfItem {
  const id = randomUUID();
  db.prepare(
    `INSERT INTO shelf_items (id, user_id, artist, album, image_url, note)
     VALUES (?, ?, ?, ?, ?, ?)`
  ).run(id, input.userId, input.artist, input.album, input.imageUrl ?? null, input.note ?? null);
  const row = db.prepare("SELECT * FROM shelf_items WHERE id = ?").get(id) as unknown as ShelfItemRow;
  return rowToItem(row);
}

export function deleteShelfItem(id: string, userId: string): boolean {
  const result = db
    .prepare("DELETE FROM shelf_items WHERE id = ? AND user_id = ?")
    .run(id, userId);
  return result.changes > 0;
}
