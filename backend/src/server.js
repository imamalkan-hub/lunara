/**
 * Lunara API — ana giriş noktası.
 *
 * Uç noktalar:
 *   /api/health
 *   /api/auth/register | login | logout | me
 *   /api/sessions            (GET list, POST upsert)
 *   /api/sessions/:id
 *   /api/sessions/:id/report
 *   /api/stats               (trends, regularity, debt, chronotype)
 *   /api/screenings          (POST score, GET history)
 *   /api/screenings/spec     (questionnaire definitions)
 *   /api/doctors             (near-me / search)
 *   /api/doctors/:id
 *   /api/countries | /api/countries/:code/regions | .../cities
 *   /api/places/suggest
 *   /api/articles
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createApp, Router, readJson, HttpError, status } from './http.js';
import { getAppDb, getDirectoryDb, all, one, run, DATA_DIR } from './db.js';
import {
  hashPassword,
  verifyPassword,
  newSessionToken,
  newId,
  hashToken,
  sessionExpiry,
  validatePassword,
  validateEmail,
} from './auth.js';
import {
  classifyStages,
  detectSleepWindow,
  summarizeStages,
  computeSleepScore,
  computeRegularity,
  computeSleepDebt,
  classifyChronotype,
  detectApneaEvents,
  estimateApneaRisk,
} from './sleep-engine.js';
import { SCREENINGS, scoreScreening, recommendedActions } from './screening.js';
import * as directory from './directory.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 4000);
const HOST = process.env.HOST || '0.0.0.0';

/* ------------------------------------------------------------------ */
/* Şema uygulama                                                      */
/* ------------------------------------------------------------------ */

export function initSchema() {
  const db = getAppDb();
  const schema = readFileSync(join(HERE, 'schema.sql'), 'utf8');
  db.exec(schema);
  return db;
}

/* ------------------------------------------------------------------ */
/* Yardımcılar                                                        */
/* ------------------------------------------------------------------ */

function now() {
  return new Date().toISOString();
}

/** Authorization başlığından token'ı çıkarır. */
function bearerToken(req) {
  const h = req.headers.authorization || '';
  if (!h.toLowerCase().startsWith('bearer ')) return null;
  return h.slice(7).trim() || null;
}

/** Oturum doğrulaması — kullanıcı yoksa null döner. */
function resolveUser(req) {
  const token = bearerToken(req);
  if (!token) return null;
  const db = getAppDb();
  const sess = one(
    db,
    `SELECT s.user_id, s.expires_at FROM sessions_auth s WHERE s.token_hash = ?`,
    hashToken(token),
  );
  if (!sess) return null;
  if (new Date(sess.expires_at).getTime() < Date.now()) {
    run(db, 'DELETE FROM sessions_auth WHERE token_hash = ?', hashToken(token));
    return null;
  }
  const user = one(
    db,
    'SELECT id, email, display_name, country_code, created_at FROM users WHERE id = ?',
    sess.user_id,
  );
  if (!user) return null;
  run(db, 'UPDATE users SET last_seen_at = ? WHERE id = ?', now(), user.id);
  return user;
}

/** Kimlik doğrulama gerektiren rotalar için guard. */
function requireUser(ctx) {
  if (!ctx.user) throw new HttpError(401, 'authentication required');
  return ctx.user;
}

/**
 * Oturum verisini normalize eder: ham sensör olaylarından evreleri
 * çıkarır, skoru hesaplar, sunucu tarafı türetilmiş alanları doldurur.
 */
function normalizeSession(input) {
  const rawEvents = Array.isArray(input.events) ? input.events : [];
  const window = detectSleepWindow(rawEvents);
  const stages = classifyStages(rawEvents);
  const totals = summarizeStages(stages);

  const inBedMin = window?.inBedMin ?? input.inBedMin ?? 0;
  const latencyMin = window?.latencyMin ?? input.latencyMin ?? 0;
  const totalSleepMin = Math.max(0, totals.light + totals.deep + totals.rem) || input.totalSleepMin || 0;

  const wakeCount = stages.filter((s) => s.stage === 'awake').length;

  const apnea = detectApneaEvents(rawEvents);
  const risk = estimateApneaRisk({
    snoreMinutes: input.snoreMin ?? 0,
    totalSleepMin,
    apneaGaps: apnea.gapCount,
    longestGapSec: apnea.longestGapSec,
    sleepinessScore: input.sleepinessScore ?? 0,
  });

  const score = input.score ?? computeSleepScore({
    totals,
    latencyMin,
    wakeCount,
    totalSleepMin,
    inBedMin,
  });

  return {
    totals,
    stages,
    window,
    derived: {
      inBedMin,
      totalSleepMin,
      latencyMin,
      wakeCount,
      score,
      apneaGapCount: apnea.gapCount,
      longestGapSec: apnea.longestGapSec,
      apneaRisk: risk,
      efficiencyPct: inBedMin > 0 ? Math.round((totalSleepMin / inBedMin) * 100) : 0,
    },
  };
}

function sessionRowToJson(row) {
  return {
    id: row.id,
    clientId: row.client_id,
    startedAt: row.started_at,
    endedAt: row.ended_at,
    timezone: row.timezone,
    source: row.source,
    deviceModel: row.device_model,
    inBedMin: row.in_bed_min,
    totalSleepMin: row.total_sleep_min,
    efficiencyPct: row.efficiency_pct,
    latencyMin: row.latency_min,
    wakeCount: row.wake_count,
    deepMin: row.deep_min,
    lightMin: row.light_min,
    remMin: row.rem_min,
    awakeMin: row.awake_min,
    score: row.score,
    snoreMin: row.snore_min,
    snoreEvents: row.snore_events,
    peakDb: row.peak_db,
    avgDb: row.avg_db,
    noiseEvents: row.noise_events,
    breathingGaps: row.breathing_gaps,
    longestGapSec: row.longest_gap_sec,
    note: row.note,
    factors: safeParse(row.factors_json),
    tags: safeParse(row.tags_json),
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function safeParse(s) {
  try { return JSON.parse(s || '[]'); } catch { return []; }
}

/* ------------------------------------------------------------------ */
/* Router                                                             */
/* ------------------------------------------------------------------ */

const router = new Router();

// --- Sağlık ---
router.get('/api/health', () => ({
  status: 'ok',
  service: 'lunara-api',
  version: '1.0.0',
  directory: (() => {
    try {
      const db = getDirectoryDb();
      return {
        doctors: one(db, 'SELECT COUNT(*) AS c FROM doctors').c,
        countries: one(db, 'SELECT COUNT(*) AS c FROM countries').c,
        source: 'findsleepdoctors.com',
      };
    } catch {
      return null;
    }
  })(),
}));

// --- Kimlik ---
router.post('/api/auth/register', async ({ req }) => {
  const body = await readJson(req);
  const emailErr = validateEmail(body?.email);
  if (emailErr) throw new HttpError(400, emailErr);
  const passErr = validatePassword(body?.password);
  if (passErr) throw new HttpError(400, passErr);

  const db = getAppDb();
  const email = String(body.email).trim().toLowerCase();
  if (one(db, 'SELECT id FROM users WHERE email = ?', email)) {
    throw new HttpError(409, 'That email address is already registered.');
  }

  const id = newId('usr');
  const ts = now();
  run(
    db,
    `INSERT INTO users (id, email, password_hash, display_name, country_code, created_at, last_seen_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`,
    id,
    email,
    hashPassword(body.password),
    String(body.displayName || '').slice(0, 120),
    String(body.countryCode || 'us').slice(0, 4),
    ts,
    ts,
  );

  const token = newSessionToken();
  run(
    db,
    'INSERT INTO sessions_auth (token_hash, user_id, created_at, expires_at, device) VALUES (?, ?, ?, ?, ?)',
    hashToken(token),
    id,
    ts,
    sessionExpiry(),
    String(body.device || '').slice(0, 120),
  );

  return status(201, { token, user: { id, email, displayName: body.displayName || '', countryCode: body.countryCode || 'us' } });
});

router.post('/api/auth/login', async ({ req }) => {
  const body = await readJson(req);
  const email = String(body?.email || '').trim().toLowerCase();
  const db = getAppDb();
  const user = one(db, 'SELECT * FROM users WHERE email = ?', email);
  // Kullanıcı yok ile şifre yanlış aynı mesajı döner — hesap sayımına
  // karşı kimlik bilgisi sızıntısını (user enumeration) önler.
  if (!user || !verifyPassword(String(body?.password || ''), user.password_hash)) {
    throw new HttpError(401, 'Incorrect email or password.');
  }

  const token = newSessionToken();
  const ts = now();
  run(
    db,
    'INSERT INTO sessions_auth (token_hash, user_id, created_at, expires_at, device) VALUES (?, ?, ?, ?, ?)',
    hashToken(token),
    user.id,
    ts,
    sessionExpiry(),
    String(body.device || '').slice(0, 120),
  );
  run(db, 'UPDATE users SET last_seen_at = ? WHERE id = ?', ts, user.id);

  return {
    token,
    user: {
      id: user.id,
      email: user.email,
      displayName: user.display_name,
      countryCode: user.country_code,
    },
  };
});

router.post('/api/auth/logout', async ({ req }) => {
  const token = bearerToken(req);
  if (token) {
    const db = getAppDb();
    run(db, 'DELETE FROM sessions_auth WHERE token_hash = ?', hashToken(token));
  }
  return { ok: true };
});

router.get('/api/auth/me', ({ user }) => {
  if (!user) throw new HttpError(401, 'authentication required');
  return {
    user: {
      id: user.id,
      email: user.email,
      displayName: user.display_name,
      countryCode: user.country_code,
    },
  };
});

// --- Uyku oturumları ---
router.get('/api/sessions', ({ user, url }) => {
  const u = requireUser({ user });
  const db = getAppDb();
  const limit = Math.min(Number(url.searchParams.get('limit')) || 60, 365);
  const offset = Number(url.searchParams.get('offset')) || 0;

  const rows = all(
    db,
    `SELECT * FROM sleep_sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT ? OFFSET ?`,
    u.id,
    limit,
    offset,
  );
  return { sessions: rows.map(sessionRowToJson), total: one(db, 'SELECT COUNT(*) AS c FROM sleep_sessions WHERE user_id = ?', u.id).c };
});

router.post('/api/sessions', async ({ req, user }) => {
  const u = requireUser({ user });
  const body = await readJson(req);
  if (!body?.startedAt) throw new HttpError(400, 'startedAt is required');

  const db = getAppDb();
  const id = newId('ses');
  const ts = now();
  const norm = normalizeSession(body);
  const d = norm.derived;

  // Aynı istemci kaydı tekrar gönderilirse (idempotent senkronizasyon)
  // mevcut kayıt güncellenir, kopya oluşmaz.
  if (body.clientId) {
    const existing = one(db, 'SELECT id FROM sleep_sessions WHERE user_id = ? AND client_id = ?', u.id, body.clientId);
    if (existing) {
      return status(200, { session: sessionRowToJson(one(db, 'SELECT * FROM sleep_sessions WHERE id = ?', existing.id)), updated: true });
    }
  }

  run(
    db,
    `INSERT INTO sleep_sessions (
      id, user_id, client_id, started_at, ended_at, timezone, source, device_model,
      in_bed_min, total_sleep_min, efficiency_pct, latency_min, wake_count,
      deep_min, light_min, rem_min, awake_min, score,
      snore_min, snore_events, peak_db, avg_db, noise_events, breathing_gaps, longest_gap_sec,
      note, factors_json, tags_json, synced_at, created_at, updated_at
    ) VALUES (?,?,?,?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?, ?,?,?,?,?,?,?, ?,?,?,?,?,?)`,
    id, u.id, body.clientId || null, body.startedAt, body.endedAt || null,
    body.timezone || 'UTC', body.source || 'phone', body.deviceModel || '',
    d.inBedMin, d.totalSleepMin, d.efficiencyPct, d.latencyMin, d.wakeCount,
    norm.totals.deep, norm.totals.light, norm.totals.rem, norm.totals.awake, d.score,
    body.snoreMin || 0, body.snoreEvents || 0, body.peakDb || 0, body.avgDb || 0,
    body.noiseEvents || 0, d.apneaGapCount, d.longestGapSec,
    String(body.note || '').slice(0, 2000),
    JSON.stringify(Array.isArray(body.factors) ? body.factors : []),
    JSON.stringify(Array.isArray(body.tags) ? body.tags : []),
    ts, ts, ts,
  );

  // Sensör olaylarını sakla (evre grafiği için).
  const insertEvent = db.prepare(
    'INSERT INTO sleep_events (session_id, offset_min, duration_min, stage, intensity, source) VALUES (?,?,?,?,?,?)',
  );
  for (const e of norm.stages) {
    insertEvent.run(id, e.offset_min, e.duration_min, e.stage, e.intensity, body.source || 'phone');
  }

  return status(201, { session: sessionRowToJson(one(db, 'SELECT * FROM sleep_sessions WHERE id = ?', id)) });
});

router.get('/api/sessions/:id', ({ user, params }) => {
  const u = requireUser({ user });
  const db = getAppDb();
  const row = one(db, 'SELECT * FROM sleep_sessions WHERE id = ? AND user_id = ?', params.id, u.id);
  if (!row) throw new HttpError(404, 'session not found');
  const events = all(
    db,
    'SELECT offset_min, duration_min, stage, intensity FROM sleep_events WHERE session_id = ? ORDER BY offset_min',
    params.id,
  );
  return { session: sessionRowToJson(row), events };
});

router.delete('/api/sessions/:id', ({ user, params }) => {
  const u = requireUser({ user });
  const db = getAppDb();
  run(db, 'DELETE FROM sleep_sessions WHERE id = ? AND user_id = ?', params.id, u.id);
  return { ok: true };
});

// --- Rapor (PDF benzeri JSON, cihazda PDF'e çevrilir) ---
router.get('/api/sessions/:id/report', ({ user, params, url }) => {
  const u = requireUser({ user });
  const db = getAppDb();
  const row = one(db, 'SELECT * FROM sleep_sessions WHERE id = ? AND user_id = ?', params.id, u.id);
  if (!row) throw new HttpError(404, 'session not found');

  const events = all(
    db,
    'SELECT offset_min, duration_min, stage, intensity FROM sleep_events WHERE session_id = ? ORDER BY offset_min',
    params.id,
  );
  const totals = summarizeStages(events);
  const risk = estimateApneaRisk({
    snoreMinutes: row.snore_min,
    totalSleepMin: row.total_sleep_min,
    apneaGaps: row.breathing_gaps,
    longestGapSec: row.longest_gap_sec,
  });

  return {
    report: {
      generatedAt: now(),
      patient: { email: u.email, displayName: u.display_name },
      session: sessionRowToJson(row),
      stages: totals,
      events,
      apneaScreening: {
        level: risk.level,
        score: risk.score,
        label: risk.label,
        note: 'This is a screening result, not a diagnosis. A confirmed diagnosis requires a sleep study (polysomnography).',
      },
      disclaimer:
        'Lunara is not a medical device and does not make diagnoses. This report is for personal sleep tracking only. If you have concerning symptoms, speak to a sleep doctor.',
    },
  };
});

// --- İstatistikler / trendler ---
router.get('/api/stats', ({ user, url }) => {
  const u = requireUser({ user });
  const db = getAppDb();
  const days = Math.min(Number(url.searchParams.get('days')) || 30, 365);
  const rows = all(
    db,
    `SELECT * FROM sleep_sessions WHERE user_id = ? ORDER BY started_at DESC LIMIT ?`,
    u.id,
    days,
  );
  if (!rows.length) {
    return { stats: { nights: 0, averages: {}, regularity: 0, debt: { debtMinutes: 0 }, chronotype: 'unknown', series: [] } };
  }

  const avg = (key) => Math.round(rows.reduce((a, r) => a + (r[key] || 0), 0) / rows.length);
  const startTimes = rows.map((r) => {
    const d = new Date(r.started_at);
    let mins = d.getHours() * 60 + d.getMinutes();
    // Gece yarısından sonraki başlangıçlar "gece" sayılır -> 24 saat ekle.
    if (mins < 12 * 60) mins += 24 * 60;
    return mins;
  });

  const debt = computeSleepDebt(rows);
  const chronotype = classifyChronotype(startTimes[0], rows[0].total_sleep_min);

  return {
    stats: {
      nights: rows.length,
      averages: {
        score: avg('score'),
        totalSleepMin: avg('total_sleep_min'),
        inBedMin: avg('in_bed_min'),
        efficiencyPct: avg('efficiency_pct'),
        latencyMin: avg('latency_min'),
        deepMin: avg('deep_min'),
        lightMin: avg('light_min'),
        remMin: avg('rem_min'),
        awakeMin: avg('awake_min'),
        wakeCount: avg('wake_count'),
      },
      regularity: computeRegularity(startTimes),
      debt,
      chronotype,
      series: rows.slice().reverse().map((r) => ({
        date: r.started_at,
        score: r.score,
        totalSleepMin: r.total_sleep_min,
        deepMin: r.deep_min,
        remMin: r.rem_min,
        awakeMin: r.awake_min,
        inBedMin: r.in_bed_min,
      })),
    },
  };
});

// --- Tarama ---
router.get('/api/screenings/spec', ({ url }) => {
  const kind = url.searchParams.get('kind');
  if (kind) {
    const s = SCREENINGS[kind];
    if (!s) throw new HttpError(404, 'unknown screening');
    return { screening: s };
  }
  return { screenings: Object.values(SCREENINGS).map((s) => ({ id: s.id, name: s.name, description: s.description, maxScore: s.maxScore })) };
});

router.post('/api/screenings', async ({ req, user }) => {
  const u = requireUser({ user });
  const body = await readJson(req);
  const kind = body?.kind;
  if (!SCREENINGS[kind]) throw new HttpError(400, 'unknown screening kind');

  const result = scoreScreening(kind, body.answers || {});
  const db = getAppDb();
  const id = newId('scr');
  run(
    db,
    `INSERT INTO screenings (id, user_id, kind, answers_json, score, max_score, result, risk_level, advice, created_at)
     VALUES (?,?,?,?,?,?,?,?,?,?)`,
    id, u.id, kind, JSON.stringify(body.answers || {}), result.score, result.maxScore,
    result.result, result.riskLevel, result.advice, now(),
  );

  return status(201, {
    screening: { id, ...result, createdAt: now() },
    actions: recommendedActions(result.riskLevel),
  });
});

router.get('/api/screenings', ({ user }) => {
  const u = requireUser({ user });
  const db = getAppDb();
  const rows = all(
    db,
    'SELECT * FROM screenings WHERE user_id = ? ORDER BY created_at DESC LIMIT 50',
    u.id,
  );
  return {
    screenings: rows.map((r) => ({
      id: r.id,
      kind: r.kind,
      score: r.score,
      maxScore: r.max_score,
      result: r.result,
      riskLevel: r.risk_level,
      advice: r.advice,
      createdAt: r.created_at,
    })),
  };
});

// --- Doktor dizini ---
router.get('/api/doctors', ({ url }) => {
  const p = url.searchParams;
  const lat = p.get('lat');
  const lng = p.get('lng');
  const q = (p.get('q') || '').trim();

  if (lat && lng) {
    const doctors = directory.nearestDoctors({
      lat: Number(lat),
      lng: Number(lng),
      radiusKm: Math.min(Number(p.get('radius')) || 50, 500),
      limit: Math.min(Number(p.get('limit')) || 50, 200),
      countryCode: p.get('country') || null,
      telehealthOnly: p.get('telehealth') === '1',
    });
    return { doctors, mode: 'near-me', attribution: directoryAttribution() };
  }

  if (q) {
    const doctors = directory.searchDoctors({
      q,
      countryCode: p.get('country') || null,
      limit: Math.min(Number(p.get('limit')) || 30, 100),
      offset: Number(p.get('offset')) || 0,
    });
    return { doctors, mode: 'search', attribution: directoryAttribution() };
  }

  throw new HttpError(400, 'either lat/lng or q is required');
});

router.get('/api/doctors/:id', ({ params }) => {
  const doctor = directory.getDoctor(Number(params.id));
  if (!doctor) throw new HttpError(404, 'doctor not found');
  return { doctor, attribution: directoryAttribution() };
});

router.get('/api/countries', () => ({
  countries: directory.listCountries(),
  attribution: directoryAttribution(),
}));

router.get('/api/countries/:code/regions', ({ params }) => ({
  regions: directory.listRegions(params.code),
}));

router.get('/api/countries/:code/regions/:regionSlug/cities', ({ params }) => ({
  cities: directory.listCities(params.code, params.regionSlug),
}));

router.get('/api/countries/:code/cities/:regionSlug/doctors', ({ params }) => ({
  doctors: directory.doctorsByCity(params.code, params.regionSlug, params.citySlug),
  attribution: directoryAttribution(),
}));

router.get('/api/places/suggest', ({ url }) => ({
  places: directory.suggestPlaces(url.searchParams.get('q') || '', Number(url.searchParams.get('limit')) || 10),
}));

router.get('/api/cities/near', ({ url }) => {
  const lat = Number(url.searchParams.get('lat'));
  const lng = Number(url.searchParams.get('lng'));
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) throw new HttpError(400, 'lat and lng required');
  return {
    cities: directory.nearestCities({ lat, lng, limit: Number(url.searchParams.get('limit')) || 20, countryCode: url.searchParams.get('country') || null }),
  };
});

router.get('/api/countries/:code/top-cities', ({ params, url }) => ({
  cities: directory.topCitiesBySpecialty(params.code, Number(url.searchParams.get('limit')) || 20),
}));

/** Kaynak gösterimi — her dizin yanıtında döner. */
function directoryAttribution() {
  return {
    source: 'findsleepdoctors.com',
    sourceUrl: 'https://findsleepdoctors.com/',
    note: 'Doctor and clinic details are sourced from the findsleepdoctors.com directory. Listings are verified against public sources; contact the clinic directly for appointments and fees.',
  };
}

/* ------------------------------------------------------------------ */
/* Sunucu                                                             */
/* ------------------------------------------------------------------ */

export function start(port = PORT, host = HOST) {
  initSchema();
  const app = createApp({
    router,
    context: async (req) => ({ user: resolveUser(req) }),
  });
  return new Promise((resolve) => {
    const server = app.listen(port, host, () => {
      console.log(`[lunara-api] listening on http://${host}:${port}`);
      resolve(server);
    });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  start();
}

export { router, normalizeSession };
