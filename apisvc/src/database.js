import Database from 'better-sqlite3';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const dbPath = path.join(__dirname, '..', 'tasks.db');

const db = new Database(dbPath);

// Enable WAL mode for better concurrent access
db.pragma('journal_mode = WAL');

// Initialize tables using prepare and run
const initSchema = `
  CREATE TABLE IF NOT EXISTS tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    body TEXT DEFAULT '',
    completed INTEGER DEFAULT 0,
    user_id INTEGER DEFAULT 1,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
  )
`;

const initDeviceTokens = `
  CREATE TABLE IF NOT EXISTS device_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token TEXT UNIQUE NOT NULL,
    platform TEXT NOT NULL,
    user_id INTEGER DEFAULT 1,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
  )
`;

const initIndexTasks = `CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON tasks(user_id)`;
const initIndexTokens = `CREATE INDEX IF NOT EXISTS idx_device_tokens_user_id ON device_tokens(user_id)`;

// Users table for authentication
const initUsers = `
  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document TEXT UNIQUE NOT NULL,
    email TEXT UNIQUE,
    password_hash TEXT NOT NULL,
    name TEXT NOT NULL,
    phone TEXT,
    mfa_enabled INTEGER DEFAULT 0,
    mfa_secret TEXT,
    biometric_enabled INTEGER DEFAULT 0,
    biometric_public_key TEXT,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until INTEGER,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    updated_at INTEGER DEFAULT (strftime('%s', 'now') * 1000)
  )
`;

// Sessions table for tracking active sessions and refresh tokens
const initSessions = `
  CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    refresh_token TEXT UNIQUE NOT NULL,
    device_id TEXT,
    device_name TEXT,
    platform TEXT,
    ip_address TEXT,
    user_agent TEXT,
    expires_at INTEGER NOT NULL,
    created_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
  )
`;

// Token revocation list for logged out tokens
const initRevokedTokens = `
  CREATE TABLE IF NOT EXISTS revoked_tokens (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    token_jti TEXT UNIQUE NOT NULL,
    user_id INTEGER NOT NULL,
    revoked_at INTEGER DEFAULT (strftime('%s', 'now') * 1000),
    expires_at INTEGER NOT NULL
  )
`;

const initIndexUsers = `CREATE INDEX IF NOT EXISTS idx_users_document ON users(document)`;
const initIndexSessions = `CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON sessions(user_id)`;
const initIndexSessionsToken = `CREATE INDEX IF NOT EXISTS idx_sessions_refresh_token ON sessions(refresh_token)`;
const initIndexRevokedTokens = `CREATE INDEX IF NOT EXISTS idx_revoked_tokens_jti ON revoked_tokens(token_jti)`;

// Run schema initialization
db.prepare(initSchema).run();
db.prepare(initDeviceTokens).run();
db.prepare(initIndexTasks).run();
db.prepare(initIndexTokens).run();
db.prepare(initUsers).run();
db.prepare(initSessions).run();
db.prepare(initRevokedTokens).run();
db.prepare(initIndexUsers).run();
db.prepare(initIndexSessions).run();
db.prepare(initIndexSessionsToken).run();
db.prepare(initIndexRevokedTokens).run();

export default db;
