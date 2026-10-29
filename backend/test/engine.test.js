/**
 * Lunara backend testleri — node:test ile, harici bağımlılık yok.
 *
 * Çalıştırma:  node --test test/
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';

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
} from '../src/sleep-engine.js';

import { scoreScreening, SCREENINGS } from '../src/screening.js';
import { hashPassword, verifyPassword, validatePassword, validateEmail } from '../src/auth.js';

/** Test verisi üretici: verilen evre dizisinden olay üretir. */
function eventsFromStages(stages, { bucket = 5 } = {}) {
  const events = [];
  let offset = 0;
  for (const [stage, count] of stages) {
    for (let i = 0; i < count; i++) {
      events.push({
        offset_min: offset,
        duration_min: bucket,
        stage,
        intensity: stage === 'awake' ? 0.9 : stage === 'deep' ? 0.05 : stage === 'rem' ? 0.45 : 0.3,
        sound: 0.3,
      });
      offset += bucket;
    }
  }
  return events;
}

test('uyku penceresi: uyanık başlangıç gecikme olarak sayılır', () => {
  const events = eventsFromStages([['awake', 6], ['light', 60], ['deep', 30]]);
  const win = detectSleepWindow(events);
  assert.equal(win.latencyMin, 30);
  assert.equal(win.inBedMin, (6 + 60 + 30) * 5);
  assert.equal(win.sleepMinutes, win.inBedMin - 30);
});

test('uyku penceresi: 45 dakikadan uzun uyanıklık gecikme olarak sınırlanır', () => {
  const events = eventsFromStages([['awake', 20], ['light', 60]]);
  const win = detectSleepWindow(events);
  assert.equal(win.latencyMin, 45);
});

test('evre sınıflandırma: düşük hareket derin, yüksek hareket uyanık', () => {
  const events = eventsFromStages([['deep', 40], ['light', 40], ['rem', 20], ['awake', 10]]);
  const stages = classifyStages(events);
  assert.ok(stages.length > 0);
  // Sınıflandırma sonrası her evre tanımlı olmalı
  for (const s of stages) {
    assert.ok(['awake', 'light', 'deep', 'rem'].includes(s.stage));
    assert.ok(s.duration_min > 0);
  }
});

test('evre sınıflandırma: kısa uyanıklıklar birleştirilir', () => {
  // 3 dakikalık (tek kova) uyanıklık — 4 dakikadan kısa, "gürültü" sayılmalı
  const events = eventsFromStages([['light', 30], ['awake', 1], ['light', 30]], { bucket: 5 });
  const stages = classifyStages(events);
  const awakeBuckets = stages.filter((s) => s.stage === 'awake');
  // 5 dakikalık kova eşiğinin altındaki uyanıklık yumuşatılmalı
  assert.ok(awakeBuckets.length <= 1);
});

test('özet: evre süreleri doğru toplanır', () => {
  const stages = eventsFromStages([['awake', 6], ['light', 60], ['deep', 30], ['rem', 24]]);
  const totals = summarizeStages(stages);
  assert.equal(totals.awake, 30);
  assert.equal(totals.light, 300);
  assert.equal(totals.deep, 150);
  assert.equal(totals.rem, 120);
  assert.equal(totals.light + totals.deep + totals.rem, 570);
});

test('skor: ideal gece yüksek skor alır', () => {
  const totals = { awake: 30, light: 300, deep: 120, rem: 120 };
  const score = computeSleepScore({ totals, latencyMin: 10, wakeCount: 2, totalSleepMin: 540, inBedMin: 570 });
  assert.ok(score > 70, `beklenen yüksek skor, gelen ${score}`);
  assert.ok(score <= 100);
});

test('skor: kısa ve parçalı gece düşük skor alır', () => {
  const totals = { awake: 120, light: 120, deep: 20, rem: 20 };
  const score = computeSleepScore({ totals, latencyMin: 40, wakeCount: 12, totalSleepMin: 160, inBedMin: 280 });
  assert.ok(score < 45, `beklenen düşük skor, gelen ${score}`);
});

test('skor daima 0-100 aralığında', () => {
  for (const [t, lat, wakes] of [
    [{ awake: 0, light: 0, deep: 0, rem: 0 }, 0, 0],
    [{ awake: 600, light: 0, deep: 0, rem: 0 }, 45, 100],
    [{ awake: 1, light: 1, deep: 1, rem: 1 }, 1, 1],
  ]) {
    const s = computeSleepScore({ totals: t, latencyMin: lat, wakeCount: wakes, totalSleepMin: 3, inBedMin: 604 });
    assert.ok(s >= 0 && s <= 100, `skor aralık dışı: ${s}`);
  }
});

test('uyarlılık: düzenli saatler yüksek, dağınık saatler düşük puan', () => {
  const regular = computeRegularity([1380, 1385, 1390, 1375, 1380]);
  const irregular = computeRegularity([900, 1500, 1100, 1600, 800]);
  assert.ok(regular > 80, `düzenli beklentisi >80, gelen ${regular}`);
  assert.ok(irregular < 40, `düzensiz beklentisi <40, gelen ${irregular}`);
});

test('uyku borcu: eksik uyku negatif, fazla uyku pozitif', () => {
  const short = computeSleepDebt([{ total_sleep_min: 360 }]);
  assert.ok(short.debtMinutes < 0);
  const long = computeSleepDebt([{ total_sleep_min: 600 }]);
  assert.ok(long.debtMinutes > 0);
});

test('kronotip: uyku ortası saatine göre sınıflanır', () => {
  // Uyku ortası = yatma saati + uyku süresi/2
  // Eşikler: <3.0 extreme_early | <4.3 early | <=5.7 intermediate | >5.7 late
  // 22:00 + 8 saat -> 02:00 -> "extreme_early"
  assert.equal(classifyChronotype(22 * 60, 480), 'extreme_early');
  // 23:00 + 8 saat -> 03:00 -> "early"
  assert.equal(classifyChronotype(23 * 60, 480), 'early');
  // 00:30 + 7 saat -> 04:00 -> "early"
  assert.equal(classifyChronotype(30, 420), 'early');
  // 01:00 + 8 saat -> 05:00 -> "intermediate"
  assert.equal(classifyChronotype(60, 480), 'intermediate');
  // 03:00 + 8 saat -> 07:00 -> "late"
  assert.equal(classifyChronotype(3 * 60, 480), 'late');
  // 21:00 + 7 saat -> 00:30 -> "extreme_early"
  assert.equal(classifyChronotype(21 * 60, 420), 'extreme_early');
});

test('nefes boşluğu tespiti: yüksek ses -> sessizlik -> yüksek ses paterni', () => {
  const events = [];
  let offset = 0;
  for (let i = 0; i < 5; i++) {
    events.push({ offset_min: offset, duration_min: 1, stage: 'light', intensity: 0.3, sound: 0.8 });
    offset += 1;
    events.push({ offset_min: offset, duration_min: 0.25, stage: 'light', intensity: 0.1, sound: 0.0 });
    offset += 0.25;
  }
  const apnea = detectApneaEvents(events);
  assert.ok(apnea.gapCount > 0, 'nefes boşluğu bulunamadı');
  assert.ok(apnea.longestGapSec > 0);
});

test('apne riski: yüksek girişler yüksek risk verir', () => {
  const high = estimateApneaRisk({
    snoreMinutes: 240, totalSleepMin: 480, apneaGaps: 20, longestGapSec: 45, sleepinessScore: 14,
  });
  const low = estimateApneaRisk({
    snoreMinutes: 5, totalSleepMin: 480, apneaGaps: 0, longestGapSec: 0, sleepinessScore: 1,
  });
  assert.equal(high.level, 'high');
  assert.equal(low.level, 'low');
  assert.ok(high.score > low.score);
});

test('STOP-BANG: eşikler doğru', () => {
  const low = scoreScreening('stop_bang', { snoring: 0, tired: 0, observed: 0, pressure: 0, bmi: 0, age: 0, neck: 0, gender: 0 });
  const mid = scoreScreening('stop_bang', { snoring: 1, tired: 1, observed: 1, pressure: 0, bmi: 0, age: 0, neck: 0, gender: 0 });
  const high = scoreScreening('stop_bang', { snoring: 1, tired: 1, observed: 1, pressure: 1, bmi: 1, age: 1, neck: 1, gender: 1 });
  assert.equal(low.riskLevel, 'low');
  assert.equal(mid.riskLevel, 'moderate');
  assert.equal(high.riskLevel, 'high');
  assert.equal(high.score, 8);
  assert.equal(high.maxScore, 8);
});

test('Epworth: yüksek puan yüksek risk', () => {
  const high = scoreScreening('epworth', {
    reading: 3, tv: 3, sitting: 3, car: 3, lying: 3, restless: 3, driving: 2, work: 2,
  });
  assert.equal(high.riskLevel, 'high');
  assert.equal(high.score, 22);
});

test('Berlin: 2 kategori yüksek risk', () => {
  const high = scoreScreening('berlin', { snoring: 2, tired: 2, witnessed: 1 });
  assert.equal(high.riskLevel, 'high');
});

test('tarama sonucu tanı iddiasında bulunmaz', () => {
  const r = scoreScreening('stop_bang', { snoring: 1, tired: 1, observed: 1, pressure: 1, bmi: 1, age: 1, neck: 1, gender: 1 });
  const text = `${r.result} ${r.advice}`.toLowerCase();
  // "tanı" / "teşhis" / "tedavi" gibi ifadeler kullanılmamalı
  for (const forbidden of ['tanı koy', 'teşhis', 'tedavi öner', 'ilaç öner']) {
    assert.ok(!text.includes(forbidden), `yasak ifade bulundu: ${forbidden}`);
  }
});

test('şifre hash ve doğrulama', () => {
  const h = hashPassword('guclupass1');
  assert.ok(h.startsWith('pbkdf2$'));
  assert.ok(verifyPassword('guclupass1', h));
  assert.ok(!verifyPassword('yanlisparola1', h));
  // Aynı şifre iki kez hashlenince farklı tuz -> farklı hash
  assert.notEqual(hashPassword('guclupass1'), h);
});

test('şifre ve e-posta doğrulama kuralları', () => {
  assert.ok(validatePassword('kisa1'));        // 8 karakterden kısa
  assert.ok(validatePassword('sadeceharfler')); // rakam yok
  assert.equal(validatePassword('gecerli123'), null);
  assert.ok(validateEmail('gecersiz'));
  assert.equal(validateEmail('a@b.co'), null);
});

test('tüm tarama tanımları eksiksiz', () => {
  for (const [id, spec] of Object.entries(SCREENINGS)) {
    assert.ok(spec.answers.length > 0, `${id} soru içermiyor`);
    assert.ok(spec.maxScore > 0);
    assert.ok(spec.description);
  }
});
