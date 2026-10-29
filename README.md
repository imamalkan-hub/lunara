<div align="center">

# 🌙 Lunara

**Free sleep tracking + free sleep doctor finder.**

Developed by the team behind **[findsleepdoctors.com](https://findsleepdoctors.com/)**

[![License: MIT](https://img.shields.io/badge/License-MIT-4FD1E6.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-4FD1E6.svg)](https://findsleepdoctors.com/api/health)
![Android](https://img.shields.io/badge/Android-26%2B-8BE28B.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-6C7BFF.svg)

</div>

---

## What is Lunara?

Lunara is a **completely free** Android app that does two things well:

1. **Tracks your sleep overnight** using only your phone's accelerometer and microphone — producing sleep stages, a hypnogram, a sleep score, and nightly trends.
2. **Finds sleep doctors and clinics near you** from the free [findsleepdoctors.com](https://findsleepdoctors.com/) directory of **21,343 specialists across 6 countries** — with the source of every single listing shown to you.

There is **no advertising, no subscription, no premium tier and no paywall.** Every feature is available at no cost. Lunara is developed and published by the findsleepdoctors.com team, and the doctor listings you see in the app come directly from that directory.

**Lunara is not a medical device.** It performs *screening*, never diagnosis. It cannot tell you that you have sleep apnea — only a sleep study can do that. It will, however, help you understand your nights better and tell you when it is worth talking to a sleep doctor.

---

## 📱 Download

Releases are published on the [GitHub Releases page](https://github.com/imamalkan-hub/lunara/releases).

| Version | File | Notes |
|---|---|---|
| 1.0 | [`app-release.apk`](https://github.com/imamalkan-hub/lunara/releases/latest) | Release-signed, connects to `https://findsleepdoctors.com/api` |

**Requirements:** Android 8.0 (API 26) or newer. ~16 MB.

The APK is signed with the Lunara release key. If Android warns you, enable *Install unknown apps* for your browser — the warning appears for any APK downloaded outside the Play Store.

---

## ✨ Features

### Sleep tracking
- **Overnight capture** in a foreground service — continues with the screen off.
- **Sleep stage classification** (Awake / Light / Deep / REM) from a high-pass filtered accelerometer signal with an adaptive per-night threshold.
- **Snore & breathing-gap detection** via `AudioRecord` — raw audio is analysed in memory and **never leaves the device**.
- **Hypnogram** visualisation, encoding stage by *height as well as colour* so it survives greyscale and colour blindness.
- **Sleep score** (0–100) with monotonic lightness ramps, plus **efficiency**, **latency**, **awakenings**, **regularity**, **sleep debt** and **chronotype**.
- 30-second epochs, batched sensor delivery to keep battery use low.

### Risk screening
- **STOP-BANG** — the standard 8-item obstructive sleep apnea questionnaire.
- **Epworth Sleepiness Scale** — daytime sleepiness.
- **Berlin Questionnaire** — self-reported apnea risk.
- Each result produces **plain-language guidance** and actionable steps, always framed as *screening, never diagnosis*.

### Sleep doctor directory
- **21,343 doctors / 4,193 cities / 6 countries**, sourced from findsleepdoctors.com.
- **Near-me search** with real distance sorting.
- Free-text search by name, clinic or city; place autocomplete.
- Browse by country → region → city.
- **Every listing shows its source** — NPI registry, sleepclinicdirectory.com, NHS and more — with a direct link so you can verify it yourself.

### Privacy
- **Raw audio never leaves your phone.** Only numeric aggregates (snore counts, relative levels) are sent.
- **Location never leaves your phone** — it is used locally to sort nearby clinics.
- **Sleep tracking works without an account.** An account is only needed for backup and sharing reports.
- Free and neutral directory — **no paid placement, no ranking games.**

---

## 🏗️ Architecture

```
lunara/
├── android/                    # Kotlin + Jetpack Compose (Material 3)
│   └── app/src/main/java/com/lunara/sleep/
│       ├── core/               # Models, SleepEngine (scoring, chronotype, apnea gaps)
│       ├── data/               # ApiClient, Repository, LocalStore, DTO models
│       ├── tracking/           # MovementTracker, SnoreTracker, SleepTrackingService, BootReceiver
│       ├── ui/                 # Theme, components (Hypnogram, ScoreRing), screens
│       └── MainActivity.kt     # Compose navigation, 4 tabs
├── backend/                    # Node 24 + node:sqlite — ZERO external dependencies
│   ├── src/
│   │   ├── server.js           # All API routes, auth guard, attribution
│   │   ├── sleep-engine.js     # Stage classification, score, regularity, debt, chronotype
│   │   ├── screening.js        # STOP-BANG / Epworth / Berlin specs + scoring
│   │   ├── directory.js        # Doctor search, near-me, geocoding, source map
│   │   ├── db.js / schema.sql  # SQLite schema
│   │   ├── auth.js             # PBKDF2 password hashing, token sessions
│   │   └── http.js             # Minimal router on node:http
│   └── test/engine.test.js     # 20 tests
├── brand/                      # Logo, favicon, launcher icon generation
└── keystore/                   # Release signing key (NOT in git)
```

**Zero dependencies, on purpose.** The backend uses Node's built-in `node:sqlite` and a hand-rolled router. No Express, no ORM, no `node_modules`. It installs and runs anywhere Node 22.5+ is available.

### Privacy architecture

```
Phone                          findsleepdoctors.com
  ├─ Accelerometer ──┐
  │                   ├─► derived aggregates ──► POST /api/sessions
  ├─ Microphone ─────┘        (stage epochs, snore counts)
  │      │
  │      └── raw PCM ──► analysed in memory ──► DISCARDED
  │
  └─ Location ──► local distance sort only ──► never transmitted
```

---

## 🔌 API

Base URL: `https://findsleepdoctors.com/api`

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| GET | `/health` | — | Service status + directory counts |
| POST | `/auth/register` | — | Create account |
| POST | `/auth/login` | — | Exchange credentials for a token |
| POST | `/auth/logout` | ✔ | Invalidate the session |
| GET | `/auth/me` | ✔ | Current user |
| GET | `/sessions` | ✔ | List sleep sessions |
| POST | `/sessions` | ✔ | Upsert a session with derived metrics |
| GET | `/sessions/:id` | ✔ | Single session |
| GET | `/sessions/:id/report` | ✔ | Doctor-facing report payload |
| DELETE | `/sessions/:id` | ✔ | Delete a session |
| GET | `/stats` | ✔ | Trends, regularity, sleep debt, chronotype |
| GET | `/screenings/spec` | — | Questionnaire definitions |
| POST | `/screenings` | ✔ | Score a questionnaire |
| GET | `/screenings` | ✔ | Screening history |
| GET | `/doctors` | — | Near-me search (`lat`, `lng`, `radius`) or text search (`q`) |
| GET | `/doctors/:id` | — | Single doctor detail |
| GET | `/countries` | — | Countries + doctor counts |
| GET | `/countries/:code/regions` | — | Regions in a country |
| GET | `/countries/:code/regions/:slug/cities` | — | Cities in a region |
| GET | `/countries/:code/regions/:slug/cities/:slug/doctors` | — | Doctors in a city |
| GET | `/places/suggest` | — | Place autocomplete |
| GET | `/articles` | — | Sleep education articles |

`✔` = requires `Authorization: Bearer <token>`.

**Try it:**
```bash
curl https://findsleepdoctors.com/api/health
curl "https://findsleepdoctors.com/api/doctors?lat=40.7128&lng=-74.006&limit=3"
```

---

## 🛠️ Development

### Backend
```bash
cd backend
node src/server.js          # http://localhost:4000
node --test "test/**/*.test.js"
```

Requires **Node ≥ 22.5** (for `node:sqlite`). No `npm install` needed — there are no dependencies.

```bash
PORT=4000 LUNARA_DATA_DIR=./data node src/server.js
```

| Variable | Default | Purpose |
|---|---|---|
| `PORT` | `4000` | Listen port |
| `HOST` | `0.0.0.0` | Bind address |
| `LUNARA_DATA_DIR` | `./data` | SQLite file location |
| `LUNARA_DIRECTORY_DB` | `<data>/directory.db` | Doctor directory database |

### Android
```bash
cd android
ANDROID_HOME=/path/to/android-sdk ./gradlew :app:assembleDebug
```

The emulator reaches a host-local backend at `http://10.0.2.2:4000` (configured as `debugApiBaseUrl`). Release builds use `https://findsleepdoctors.com`.

```bash
# Custom API endpoint
./gradlew :app:assembleDebug -PdebugApiBaseUrl=http://10.0.2.2:4000
```

### Release signing
Signing credentials live in `android/keystore.properties` (git-ignored) or in the `LUNARA_*` environment variables:

```
LUNARA_STORE_FILE / LUNARA_KEY_ALIAS / LUNARA_STORE_PASSWORD / LUNARA_KEY_PASSWORD
```

```bash
./gradlew :app:assembleRelease
```

### Brand assets
```bash
cd brand && python3 make_brand.py
```

Regenerates the SVG logo, favicons, PWA icons and Android launcher icons from a single source of truth.

---

## 🎨 Design

Dark, AMOLED-friendly canvas (`#0B0B0F`) with an accent of `#4FD1E6`. Sleep stages get their own colours — Awake `#FFB74D`, Light `#4FC3F7`, Deep `#3F51B5`, REM `#E85A9E` — and score bands ramp **monotonically in lightness** so they remain distinguishable in greyscale and for colour-blind users. Every chart is double-encoded: stage is shown by height as well as hue, and every score band carries a word label.

---

## 📄 License

MIT — see [LICENSE](LICENSE).

Doctor and clinic listings are compiled by findsleepdoctors.com and are provided as-is. Listings are verified against public sources (NPI registry, sleepclinicdirectory.com, NHS) but **Lunara is not responsible for the accuracy of third-party listings** — always contact the clinic directly for appointments and fees.
