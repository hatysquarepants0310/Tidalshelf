import bcrypt from "bcryptjs";
import { randomUUID } from "node:crypto";
import { db } from "./db";

export interface User {
  id: string;
  username: string;
  email: string;
  passwordHash: string;
  displayName: string | null;
  bio: string | null;
  lastfmUsername: string | null;
  createdAt: string;
}

export type PublicUser = Omit<User, "passwordHash" | "email">;

interface UserRow {
  id: string;
  username: string;
  email: string;
  password_hash: string;
  display_name: string | null;
  bio: string | null;
  lastfm_username: string | null;
  created_at: string;
}

function rowToUser(row: UserRow): User {
  return {
    id: row.id,
    username: row.username,
    email: row.email,
    passwordHash: row.password_hash,
    displayName: row.display_name,
    bio: row.bio,
    lastfmUsername: row.lastfm_username,
    createdAt: row.created_at,
  };
}

export function toPublicUser(user: User): PublicUser {
  const { passwordHash: _passwordHash, email: _email, ...rest } = user;
  void _passwordHash;
  void _email;
  return rest;
}

export function getUserByUsername(username: string): User | null {
  const row = db
    .prepare("SELECT * FROM users WHERE username = ? COLLATE NOCASE")
    .get(username) as unknown as UserRow | undefined;
  return row ? rowToUser(row) : null;
}

export function getUserById(id: string): User | null {
  const row = db.prepare("SELECT * FROM users WHERE id = ?").get(id) as unknown as UserRow | undefined;
  return row ? rowToUser(row) : null;
}

export function getUserByEmailOrUsername(identifier: string): User | null {
  const row = db
    .prepare("SELECT * FROM users WHERE email = ? COLLATE NOCASE OR username = ? COLLATE NOCASE")
    .get(identifier, identifier) as unknown as UserRow | undefined;
  return row ? rowToUser(row) : null;
}

export async function createUser(input: {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}): Promise<User> {
  const passwordHash = await bcrypt.hash(input.password, 10);
  const id = randomUUID();
  db.prepare(
    `INSERT INTO users (id, username, email, password_hash, display_name)
     VALUES (?, ?, ?, ?, ?)`
  ).run(id, input.username, input.email, passwordHash, input.displayName ?? input.username);
  const user = getUserById(id);
  if (!user) throw new Error("No se pudo crear el usuario");
  return user;
}

export async function verifyPassword(user: User, password: string): Promise<boolean> {
  return bcrypt.compare(password, user.passwordHash);
}

export function updateUserProfile(
  id: string,
  input: { displayName?: string; bio?: string; lastfmUsername?: string }
): User | null {
  const current = getUserById(id);
  if (!current) return null;
  db.prepare(
    `UPDATE users SET display_name = ?, bio = ?, lastfm_username = ? WHERE id = ?`
  ).run(
    input.displayName ?? current.displayName,
    input.bio ?? current.bio,
    input.lastfmUsername === undefined ? current.lastfmUsername : input.lastfmUsername || null,
    id
  );
  return getUserById(id);
}
