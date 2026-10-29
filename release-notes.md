## Lunara 1.0

**Free sleep tracking + free sleep doctor finder.**
Developed by the team behind [findsleepdoctors.com](https://findsleepdoctors.com/).

---

### ✨ 100% free — forever

No ads, no subscriptions, no premium tier, no paywall. Every feature is available at no cost:

- Overnight sleep tracking with hypnogram and sleep score
- STOP-BANG, Epworth and Berlin risk screening
- The complete sleep doctor directory — 21,343 specialists across 6 countries

### 🩺 21,343 sleep specialists, 6 countries

Real clinics and doctors from the free [findsleepdoctors.com](https://findsleepdoctors.com/) directory. Search near you, by name, or browse by country → region → city.

**Every listing shows its source** — the NPI registry, sleepclinicdirectory.com, the NHS and more — with a direct link so you can verify it yourself.

### 🔒 Privacy first

- **Raw microphone audio never leaves your device.** It is analysed in memory to detect snore and breathing events; only numeric counts are sent.
- **Your location never leaves your device** — it is used locally to sort nearby clinics.
- **Sleep tracking works without an account.** An account is only needed for backup and sharing reports with your doctor.

### ⚕️ Screening, never diagnosis

Lunara is a wellness and screening tool, not a medical device. It cannot diagnose sleep apnea — only a sleep study can do that. It helps you understand your nights and tells you when it is worth talking to a sleep doctor.

---

### 📥 Download

**`Lunara-1.0.apk`** — 11 MB

Requires **Android 8.0 (API 26) or newer**.

> Android will warn you that the file came from outside the Play Store — this is normal for any APK. Enable *Install unknown apps* for your browser to continue.

### 🧪 Try it instantly — demo account

No signup needed. The sign-in screen has a **Try the demo** button that fills the credentials in for you:

```
demo@lunara.app
demo1234
```

The demo account comes with sample sleep sessions, so the hypnogram, sleep score, 30-day trends and doctor search are all populated from the first launch. It is a public account with no personal data — you can delete anything you add.

---

### 📋 Full changelog

**Sleep tracking**
- Foreground service keeps tracking with the screen off
- Sleep stages (Awake / Light / Deep / REM) from a high-pass filtered accelerometer with an adaptive per-night threshold
- Snore and breathing-gap detection via `AudioRecord`
- Hypnogram, sleep score, efficiency, latency, awakenings, regularity, sleep debt and chronotype
- 30-second epochs with batched sensor delivery for low battery use

**Screening**
- STOP-BANG, Epworth Sleepiness Scale and Berlin Questionnaire
- Plain-language results with actionable steps
- Explicit "screening, not diagnosis" framing throughout

**Doctor directory**
- Near-me search with real distance sorting
- Free-text search by name, clinic or city, plus place autocomplete
- Browse by country → region → city
- Per-listing source attribution

**App**
- About screen with the free-forever notice, findsleepdoctors.com attribution and the full privacy policy
- Branded launcher icon, adaptive icon and monochrome icon layer
- Dark AMOLED-friendly design; sleep stages encoded by height as well as colour, and score bands ramp monotonically in lightness for colour-blind accessibility

---

**Source code:** [github.com/imamalkan-hub/lunara](https://github.com/imamalkan-hub/lunara) · MIT licensed

Doctor and clinic listings are provided as-is. Always contact the clinic directly for appointments and fees.
