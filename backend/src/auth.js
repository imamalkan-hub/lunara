/**
 * Kimlik doğrulama — e-posta + şifre, PBKDF2-HMAC-SHA256, oturum token'ı.
 *
 * Node'un yerleşik `node:crypto` modülü kullanılır; argon2/bcrypt gibi
 * native bağımlılık gerektiren kütüphaneler yoktur. PBKDF2 210k tur
 * (OWASP 2023 önerisi) — donanım hızlandırması olmadan bile
 * doğrulama ~100 ms sürer, kaba kuvvet saldırısını pahalı kılar.
 */
import {
  randomUUID,
  randomBytes,
  timingSafeEqual,
  createHash,
  pbkdf2Sync,
} from 'node:crypto';

const PBKDF2_ITERATIONS = 210_000;
const PBKDF2_KEYLEN = 64;
const PBKDF2_DIGEST = 'sha256';
const SALT_BYTES = 16;
const SESSION_DAYS = 30;

/** "scrypt" yerine PBKDF2 seçildi: parametreler açık ve taşınabilir. */
export function hashPassword(password) {
  const salt = randomBytes(SALT_BYTES);
  const hash = hashWith(password, salt);
  return `pbkdf2$${PBKDF2_ITERATIONS}$${salt.toString('hex')}$${hash.toString('hex')}`;
}

function hashWith(password, salt) {
  return pbkdf2Sync(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEYLEN, PBKDF2_DIGEST);
}

export function verifyPassword(password, stored) {
  try {
    const [scheme, iterations, saltHex, hashHex] = String(stored).split('$');
    if (scheme !== 'pbkdf2') return false;
    const salt = Buffer.from(saltHex, 'hex');
    const expected = Buffer.from(hashHex, 'hex');
    const actual = pbkdf2Sync(password, salt, Number(iterations), expected.length, PBKDF2_DIGEST);
    return actual.length === expected.length && timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}

export function newSessionToken() {
  return randomBytes(32).toString('base64url');
}

export function newId(prefix = '') {
  return prefix ? `${prefix}_${randomUUID()}` : randomUUID();
}

/** Token hash'i — ham token veritabanında saklanmaz. */
export function hashToken(token) {
  return createHash('sha256').update(token).digest('hex');
}

export function sessionExpiry() {
  return new Date(Date.now() + SESSION_DAYS * 86400_000).toISOString();
}

/** Şifre gücü kontrolü. */
export function validatePassword(password) {
  const p = String(password || '');
  if (p.length < 8) return 'Password must be at least 8 characters.';
  if (p.length > 200) return 'That password is too long.';
  if (!/[a-zA-Z]/.test(p) || !/[0-9]/.test(p)) {
    return 'Password must contain at least one letter and one number.';
  }
  return null;
}

export function validateEmail(email) {
  const e = String(email || '').trim();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(e)) return 'Enter a valid email address.';
  if (e.length > 254) return 'That email address is too long.';
  return null;
}
