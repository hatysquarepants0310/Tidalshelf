import { DatabaseSync } from "node:sqlite";
import fs from "node:fs";
import path from "node:path";

const DB_PATH = process.env.DATABASE_PATH ?? path.join(process.cwd(), "data", "tidalshelf.db");

function createDb(): DatabaseSync {
  fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });
  const database = new DatabaseSync(DB_PATH);
  // busy_timeout must be set before any statement that can race with another
  // process initializing the same file (e.g. Next.js's parallel build workers).
  database.exec("PRAGMA busy_timeout = 5000;");
  database.exec("PRAGMA journal_mode = WAL;");
  database.exec("PRAGMA foreign_keys = ON;");
  database.exec(`
    CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      username TEXT UNIQUE NOT NULL,
      email TEXT UNIQUE NOT NULL,
      password_hash TEXT NOT NULL,
      display_name TEXT,
      bio TEXT,
      lastfm_username TEXT,
      created_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);
  database.exec(`
    CREATE TABLE IF NOT EXISTS shelf_items (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      artist TEXT NOT NULL,
      album TEXT NOT NULL,
      image_url TEXT,
      note TEXT,
      added_at TEXT NOT NULL DEFAULT (datetime('now'))
    );
  `);
  database.exec("CREATE INDEX IF NOT EXISTS idx_shelf_items_user ON shelf_items(user_id);");
  return database;
}

// Reuse a single connection across hot reloads in dev so we don't hit
// "database is locked" from repeatedly opening the same WAL-mode file.
const globalForDb = globalThis as unknown as { __tidalshelfDb?: DatabaseSync };

export const db = globalForDb.__tidalshelfDb ?? createDb();

if (process.env.NODE_ENV !== "production") {
  globalForDb.__tidalshelfDb = db;
}
