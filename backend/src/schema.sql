-- Lunara uygulama veritabanı şeması.
-- Uyku oturumu kayıtları, ses kayıtları, tarama (screening) sonuçları,
-- kullanıcı tercihleri ve senkronizasyon durumu.

PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
  id            TEXT PRIMARY KEY,
  email         TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,
  display_name  TEXT NOT NULL DEFAULT '',
  country_code  TEXT NOT NULL DEFAULT 'us',
  created_at    TEXT NOT NULL,
  last_seen_at  TEXT
);

CREATE TABLE IF NOT EXISTS sessions_auth (
  token_hash TEXT PRIMARY KEY,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  device     TEXT DEFAULT ''
);
CREATE INDEX IF NOT EXISTS idx_auth_user ON sessions_auth(user_id);

-- Bir gece = bir uyku oturumu.
CREATE TABLE IF NOT EXISTS sleep_sessions (
  id              TEXT PRIMARY KEY,
  user_id         TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  client_id       TEXT,
  started_at      TEXT NOT NULL,
  ended_at        TEXT,
  timezone        TEXT NOT NULL DEFAULT 'UTC',
  source          TEXT NOT NULL DEFAULT 'phone',
  device_model    TEXT DEFAULT '',

  -- Türetilmiş ölçümler
  in_bed_min     INTEGER DEFAULT 0,
  total_sleep_min INTEGER DEFAULT 0,
  efficiency_pct INTEGER DEFAULT 0,
  latency_min    INTEGER DEFAULT 0,
  wake_count     INTEGER DEFAULT 0,
  deep_min       INTEGER DEFAULT 0,
  light_min      INTEGER DEFAULT 0,
  rem_min        INTEGER DEFAULT 0,
  awake_min      INTEGER DEFAULT 0,
  score          INTEGER DEFAULT 0,

  -- Ses / ortam ölçümleri
  snore_min        INTEGER DEFAULT 0,
  snore_events     INTEGER DEFAULT 0,
  peak_db          REAL DEFAULT 0,
  avg_db           REAL DEFAULT 0,
  noise_events     INTEGER DEFAULT 0,
  breathing_gaps   INTEGER DEFAULT 0,
  longest_gap_sec INTEGER DEFAULT 0,

  -- Kullanıcı girdisi
  note           TEXT DEFAULT '',
  factors_json   TEXT DEFAULT '[]',
  tags_json      TEXT DEFAULT '[]',

  synced_at      TEXT,
  created_at     TEXT NOT NULL,
  updated_at     TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_user_time ON sleep_sessions(user_id, started_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_sessions_client ON sleep_sessions(user_id, client_id) WHERE client_id IS NOT NULL;

-- 5 dakikalık dilimler: uyku evreleri, hareket ve ses olayları.
CREATE TABLE IF NOT EXISTS sleep_events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  session_id  TEXT NOT NULL REFERENCES sleep_sessions(id) ON DELETE CASCADE,
  offset_min  REAL NOT NULL,
  duration_min REAL DEFAULT 0,
  stage       TEXT NOT NULL,
  intensity   REAL DEFAULT 0,
  source      TEXT DEFAULT 'sensor'
);
CREATE INDEX IF NOT EXISTS idx_events_session ON sleep_events(session_id, offset_min);

-- Horlama / gece konuşma ses kayıtları (sunucuda saklanan meta + göreli yol).
CREATE TABLE IF NOT EXISTS recordings (
  id           TEXT PRIMARY KEY,
  session_id   TEXT NOT NULL REFERENCES sleep_sessions(id) ON DELETE CASCADE,
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  started_at   TEXT NOT NULL,
  duration_sec REAL DEFAULT 0,
  size_bytes   INTEGER DEFAULT 0,
  peak_db      REAL DEFAULT 0,
  avg_db       REAL DEFAULT 0,
  label        TEXT DEFAULT 'snoring',
  storage_path TEXT DEFAULT '',
  uploaded_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_recordings_user ON recordings(user_id, started_at DESC);

-- STOP-BANG, Epworth, Berlin tarama sonuçları.
CREATE TABLE IF NOT EXISTS screenings (
  id          TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind        TEXT NOT NULL,
  answers_json TEXT NOT NULL,
  score       INTEGER NOT NULL,
  max_score   INTEGER NOT NULL,
  result      TEXT NOT NULL,
  risk_level  TEXT NOT NULL,
  advice      TEXT DEFAULT '',
  created_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_screenings_user ON screenings(user_id, created_at DESC);

-- Kullanıcı tercihleri (JSON blob — sık değişmeyen ayarlar).
CREATE TABLE IF NOT EXISTS preferences (
  user_id      TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  data_json    TEXT NOT NULL DEFAULT '{}',
  updated_at   TEXT NOT NULL
);

-- Kullanıcı -> doktor bağlantısı (kaydedilen klinikler, "doktoruma sor" geçmişi)
CREATE TABLE IF NOT EXISTS saved_clinics (
  user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  doctor_id    INTEGER NOT NULL,
  created_at   TEXT NOT NULL,
  PRIMARY KEY (user_id, doctor_id)
);
