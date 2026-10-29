/**
 * node:sqlite tabanlı veritabanı katmanı.
 *
 * Node 24'ün yerleşik `node:sqlite` modülünü kullanır — native derleme
 * gerektiren better-sqlite3/ORM bağımlılığı yok. Build ortamında RAM
 * sınırlı olduğu için harici bağımlılıktan kaçınmak bu projede bilinçli
 * bir mimari karardır.
 */
import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
export const DATA_DIR = process.env.LUNARA_DATA_DIR || join(HERE, '..', 'data');

export const APP_DB_PATH = join(DATA_DIR, 'lunara.db');
export const DIRECTORY_DB_PATH = process.env.LUNARA_DIRECTORY_DB || join(DATA_DIR, 'directory.db');

mkdirSync(DATA_DIR, { recursive: true });

/**
 * WAL modu + busy_timeout: eşzamanlı yazma (kayıt senkronizasyonu) ve
 * okuma (dizin sorguları) birbirini bloklamasın diye.
 */
function open(path) {
  const db = new DatabaseSync(path);
  db.exec('PRAGMA journal_mode = WAL');
  db.exec('PRAGMA busy_timeout = 5000');
  db.exec('PRAGMA foreign_keys = ON');
  return db;
}

let appDb = null;
let directoryDb = null;

export function getAppDb() {
  if (!appDb) appDb = open(APP_DB_PATH);
  return appDb;
}

export function getDirectoryDb() {
  if (!directoryDb) directoryDb = open(DIRECTORY_DB_PATH);
  return directoryDb;
}

/** Test/seed sonrası bağlantıları kapatır. */
export function closeAll() {
  if (appDb) { appDb.close(); appDb = null; }
  if (directoryDb) { directoryDb.close(); directoryDb = null; }
}

/** Satır dizisi döndüren yardımcı. */
export function all(db, sql, ...params) {
  return db.prepare(sql).all(...params);
}

/** Tek satır döndüren yardımcı. */
export function one(db, sql, ...params) {
  return db.prepare(sql).get(...params) ?? null;
}

/** Değişen satır sayısını döndüren yardımcı. */
export function run(db, sql, ...params) {
  return db.prepare(sql).run(...params);
}
