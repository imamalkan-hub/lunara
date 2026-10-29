/**
 * Veritabanı hazırlığı:
 *  1. Uygulama şemasını uygular.
 *  2. Uyku doktoru dizinini (findsleepdoctors.com kaynak veritabanı)
 *     kopyalar/bağlar.
 *  3. Örnek kullanıcı oluşturur (geliştirme kolaylığı).
 *
 * Kullanım:
 *   node src/seed.js --dir=/root/sleep-doctor-nearme/data/directory.db
 */
import { existsSync, copyFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { getAppDb, getDirectoryDb, DATA_DIR, APP_DB_PATH, DIRECTORY_DB_PATH, one, run } from './db.js';
import { initSchema } from './server.js';
import { hashPassword, newId } from './auth.js';

function arg(name, fallback) {
  const hit = process.argv.find((a) => a.startsWith(`--${name}=`));
  return hit ? hit.split('=').slice(1).join('=') : fallback;
}

const sourceDir = arg('dir', '/root/sleep-doctor-nearme/data/directory.db');

console.log('[seed] applying app schema...');
initSchema();

console.log('[seed] preparing directory database...');
if (existsSync(sourceDir)) {
  const src = statSync(sourceDir);
  const dstExists = existsSync(DIRECTORY_DB_PATH);
  if (!dstExists || statSync(DIRECTORY_DB_PATH).size < src.size) {
    console.log(`[seed] copying: ${sourceDir} -> ${DIRECTORY_DB_PATH}`);
    copyFileSync(sourceDir, DIRECTORY_DB_PATH);
  } else {
    console.log('[seed] directory database already up to date, skipping.');
  }
} else {
  console.warn(`[seed] WARNING: source not found: ${sourceDir}`);
}

const db = getDirectoryDb();
const doctors = one(db, 'SELECT COUNT(*) AS c FROM doctors').c;
const cities = one(db, 'SELECT COUNT(*) AS c FROM cities').c;
const articles = one(db, 'SELECT COUNT(*) AS c FROM articles').c;
console.log(`[seed] directory: ${doctors} doctors, ${cities} cities, ${articles} articles`);

console.log('[seed] development user...');
const appDb = getAppDb();
const demoEmail = 'demo@lunara.app';
if (!one(appDb, 'SELECT id FROM users WHERE email = ?', demoEmail)) {
  const id = newId('usr');
  run(
    appDb,
    `INSERT INTO users (id, email, password_hash, display_name, country_code, created_at, last_seen_at)
     VALUES (?,?,?,?,?,?,?)`,
    id,
    demoEmail,
    hashPassword('demo1234'),
    'Demo User',
    'us',
    new Date().toISOString(),
    new Date().toISOString(),
  );
  console.log('[seed] created demo@lunara.app / demo1234');
} else {
  console.log('[seed] demo user already exists');
}

console.log('[seed] done.');
console.log(`[seed] app db: ${APP_DB_PATH}`);
console.log(`[seed] directory db:    ${DIRECTORY_DB_PATH}`);
