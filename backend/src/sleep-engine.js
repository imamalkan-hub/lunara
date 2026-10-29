/**
 * Uyku evresi çıkarımı ve uyku skoru motoru.
 *
 * Bu modül cihazda (Android, Kotlin tarafı) ve sunucuda (Node) aynı
 * kuralları uygular. Algoritma iki sinyale dayanır:
 *
 *  1. HAREKET (accelerometer RMS) — vücut hareketi evre geçişlerinin en
 *     güçlü göstergesidir. REM'de kas atonus düşer, hareketsizlik artar;
 *     hafif uykuda kısa hareketler; derin uykuda neredeyse hiç hareket yoktur.
 *  2. SES (mikrofon RMS + apnea-benzeri "sessizlik boşlukları") — horlama
 *     ve nefes kesilmesi aralıkları.
 *
 * Skor, uyku mimarisi için standart kabul gören bileşenlerden türetilir:
 * süre, verimlilik, gecikme, derin/REM oranı, uyku bütünlüğü (kesinti sayısı)
 * ve tutarlılık (önceki günlere göre).
 */

/** Uyku evreleri — grafiklerde ve hesaplarda bu sıra kullanılır. */
export const STAGES = ['awake', 'light', 'deep', 'rem'];

export const STAGE_LABELS = {
  awake: 'Awake',
  light: 'Light',
  deep: 'Deep',
  rem: 'REM',
};

const STAGE_WEIGHTS = { awake: 0, light: 1, deep: 2, rem: 3 };

/**
 * 24 saatlik zaman damgalarından gece penceresi çıkarır.
 *
 * Kullanıcı yatmadan önce "uyku başlangıcı" bildirir; buna rağmen kayıt
 * gece yarısını geçebilir. 20:00-12:00 aralığı "gece penceresi" kabul
 * edilir: bu pencerenin dışındaki ilk 45 dakika uyanık sayılır (uyku
 * başlangıcı gecikmesi), sonrası ise uyku olarak değerlendirilir.
 */
export function detectSleepWindow(events, { minMinutes = 60, maxMinutes = 900 } = {}) {
  if (!events.length) return null;

  const sorted = [...events].sort((a, b) => a.offset_min - b.offset_min);
  const first = sorted[0].offset_min;
  const last = sorted.reduce((m, e) => Math.max(m, e.offset_min + (e.duration_min || 0)), 0);

  // Uyanık blokların ilk bölümü "yatış ama uyuyamama" olarak ayrılır.
  let awakeLeading = 0;
  for (const e of sorted) {
    if (e.stage !== 'awake') break;
    awakeLeading += e.duration_min || 0;
  }
  // 45 dakikadan uzun uyanık başlangıç, uyku başlangıcı gecikmesi sayılır.
  const latencyMin = Math.min(awakeLeading, 45);

  const inBedMin = Math.round(last - first);
  const sleepMinutes = Math.max(0, Math.round(inBedMin - latencyMin));

  return {
    firstOffset: first,
    lastOffset: last,
    inBedMin,
    latencyMin,
    sleepMinutes,
    minMinutes,
    maxMinutes,
    plausible: sleepMinutes >= minMinutes && inBedMin <= maxMinutes,
  };
}

/**
 * Hareket yoğunluğu serisinden uyku evrelerini çıkarır.
 *
 * Yaklaşım: olay dizisini 5 dakikalık kovalara indirger, her koval için
 * hareket yoğunluğu ortalaması hesaplar, sonra bu profili dört bantta
 * kuantile göre sınıflandırır ve yumuşatma (median filter) uygular.
 * Kovalar ayrıca minimum "minimum uyku uzunluğu" kuralına göre
 * atlanır — kısa uyanıklıklar evre geçişi sayılmaz.
 */
export function classifyStages(events, { bucketMinutes = 5, minWakeMinutes = 4 } = {}) {
  if (!events.length) return [];

  const buckets = new Map();
  for (const e of events) {
    const idx = Math.floor(e.offset_min / bucketMinutes);
    const b = buckets.get(idx) || { idx, movement: 0, weight: 0, sound: 0, count: 0, wake: 0 };
    const dur = e.duration_min || 0;
    const w = Math.max(dur, 0.001);
    b.movement += (e.intensity ?? 0) * w;
    b.weight += w;
    b.sound = Math.max(b.sound, e.sound ?? 0);
    b.count += 1;
    if (e.stage === 'awake') b.wake += w;
    buckets.set(idx, b);
  }

  const list = [...buckets.values()].sort((a, b) => a.idx - a.idx);
  const density = list.map((b) => (b.weight > 0 ? b.movement / b.weight : 0));

  // Uyku yoğunluğu eşikleri: dört bant çeyreklik ile belirlenir.
  const sortedDensity = [...density].sort((a, b) => a - b);
  const q = (p) => sortedDensity[Math.min(sortedDensity.length - 1, Math.floor(p * sortedDensity.length))] ?? 0;

  const tLow = q(0.2);
  const tMid = q(0.5);
  const tHigh = q(0.85);

  const classified = list.map((b, i) => {
    const d = density[i];
    let stage;
    if (d <= tLow) stage = 'deep';
    else if (d <= tMid) stage = 'light';
    else if (d <= tHigh) stage = 'rem';
    else stage = 'awake';

    // Uyanık bölümler hareketle doğrulanmamışsa (hareket düşük ama kova
    // sesi yüksek) yine uyanık sayılır — gürültü uykuyu bölüyorsa bu önemli.
    if (stage === 'deep' && b.wake >= minWakeMinutes && b.sound > 0.6) stage = 'awake';

    return {
      offset_min: b.idx * bucketMinutes,
      duration_min: bucketMinutes,
      stage,
      intensity: d,
      sound: b.sound,
    };
  });

  return smoothStages(classified, minWakeMinutes);
}

/** Kısa uyanıklıkları (alarms, kıpırdanma) birleştirerek gürültüyü azaltır. */
function smoothStages(stages, minWakeMinutes) {
  const out = stages.map((s) => ({ ...s }));
  for (let i = 0; i < out.length; i++) {
    if (out[i].stage !== 'awake') continue;
    let j = i;
    let total = out[i].duration_min;
    while (j + 1 < out.length && out[j + 1].stage === 'awake') {
      j++;
      total += out[j].duration_min;
    }
    if (total < minWakeMinutes) {
      for (let k = i; k <= j; k++) out[k].stage = 'light';
    }
    i = j;
  }
  return out;
}

/** Evre sürelerini dakika cinsinden toplamlar. */
export function summarizeStages(stages) {
  const totals = { awake: 0, light: 0, deep: 0, rem: 0 };
  for (const s of stages) totals[s.stage] = (totals[s.stage] || 0) + (s.duration_min || 0);
  for (const k of Object.keys(totals)) totals[k] = Math.round(totals[k]);
  return totals;
}

/**
 * Uyku skoru (0-100).
 *
 * Bileşenler ve ağırlıkları:
 *   süre          %25  — 7-9 saat ideal; uyku borcu olarak bilinen sapma
 *   verimlilik   %20  — uykuda geçen / yatakta geçen süre
 *   derin uyku    %20  — toplam uykunun %13-23'ü hedef aralık
 *   REM           %15  — toplam uykunun %20-25'i hedef aralık
 *   gecikme       %10  — uykuya dalma süresi
 *   bütünlük     %10  — uyanma sayısı ve toplam uyanık süre
 */
export function computeSleepScore({ totals, latencyMin = 0, wakeCount = 0, totalSleepMin = 0, inBedMin = 0 }) {
  if (!totalSleepMin) return 0;

  // Süre: 7.5 saat ideal, 420 dk merkez, tolerans 150 dk.
  const durScore = Math.max(0, 100 - Math.abs(totalSleepMin - 420) / 150 * 100);

  // Verimlilik: %85 ideal.
  const eff = inBedMin > 0 ? (totalSleepMin / inBedMin) * 100 : 100;
  const effScore = Math.max(0, Math.min(100, (eff - 60) / 30 * 100));

  // Derin uyku oranı: %13-23 ideal.
  const deepPct = totals.deep / totalSleepMin * 100;
  const deepScore = deepPct >= 13 && deepPct <= 23 ? 100 : Math.max(0, 100 - Math.abs(deepPct - 18) * 7);

  // REM oranı: %20-25 ideal.
  const remPct = totals.rem / totalSleepMin * 100;
  const remScore = remPct >= 20 && remPct <= 25 ? 100 : Math.max(0, 100 - Math.abs(remPct - 22.5) * 8);

  // Gecikme: 0-10 dk ideal, üstü hızla düşer.
  const latScore = Math.max(0, 100 - Math.max(0, latencyMin - 10) * 5);

  // Bütünlük: her uyanma 8 puan, uyanık süre 1 dk başına 1.5 puan düşer.
  const wakeScore = Math.max(0, 100 - wakeCount * 8 - totals.awake * 1.5);

  const score =
    durScore * 0.25 +
    effScore * 0.20 +
    deepScore * 0.20 +
    remScore * 0.15 +
    latScore * 0.10 +
    wakeScore * 0.10;

  return Math.round(Math.max(0, Math.min(100, score)));
}

/**
 * Uyku düzenliliği (Sleep Regularity Index benzeri ölçüm).
 * Aynı saat dilimlerindeki uyku başlangıç saatlerinin standart sapması
 * tersine çevrilerek hesaplanır. Düzenlilik > 80 iyi kabul edilir.
 */
export function computeRegularity(startTimesMinutes) {
  if (startTimesMinutes.length < 2) return 0;
  const mean = startTimesMinutes.reduce((a, b) => a + b, 0) / startTimesMinutes.length;
  const variance = startTimesMinutes.reduce((a, b) => a + (b - mean) ** 2, 0) / startTimesMinutes.length;
  const sd = Math.sqrt(variance);
  // 0 dk sapma = 100; 120 dk sapma = 0.
  return Math.round(Math.max(0, Math.min(100, 100 - (sd / 120) * 100)));
}

/**
 * Uyku borcu: son 14 günün 8 saatlik hedefe göre farkı.
 * Negatif değer = borç (uyku eksikliği), pozitif = fazla uyku.
 */
export function computeSleepDebt(sessions, { nights = 14, targetMinutes = 480 } = {}) {
  const recent = sessions.slice(0, nights);
  if (!recent.length) return { debtMinutes: 0, nightsTracked: 0, avgMinutes: 0 };
  const total = recent.reduce((a, s) => a + (s.total_sleep_min || 0), 0);
  const avg = Math.round(total / recent.length);
  return {
    debtMinutes: Math.round(avg - targetMinutes),
    nightsTracked: recent.length,
    avgMinutes: avg,
  };
}

/**
 * Kronotip (circadian type) tahmini.
 * Uyku başlangıç saati + ortalama süreye göre 4 kronotip sınıfı.
 *
 * "Uyku ortası" (mid-sleep) hesaplanır: gece yarısını geçen başlangıçlarda
 * 24 saatlik göreli hesap yapılır — 23:00 yatıp 07:00 kalkmak 02:00 orta
 * nokta demektir, 26:00 değil.
 */
export function classifyChronotype(startMinutes, totalSleepMin) {
  const startHours = ((startMinutes % 1440) + 1440) % 1440 / 60;
  const midSleep = (startHours + (totalSleepMin / 60) / 2) % 24;
  if (midSleep < 3.0) return 'extreme_early';
  if (midSleep < 4.3) return 'early';
  if (midSleep <= 5.7) return 'intermediate';
  return 'late';
}

export const CHRONOTYPE_LABELS = {
  extreme_early: 'Definite Early Bird',
  early: 'Early Bird',
  intermediate: 'Intermediate',
  late: 'Night Owl',
};

/**
 * Apne-benzeri olay tespiti.
 *
 * Horlama sesi düzenli aralıklarla kesiliyorsa (yüksek ses -> sessizlik ->
 * yüksek ses), nefesin durduğu dönem işaretlidir. Klasik "desaturation" yok
 * (sensör gerektirir), ama ses kesintisi paterni güçlü bir göstergedir.
 * Tıbbi tanı değildir — tarama (screening) amaçlıdır.
 */
export function detectApneaEvents(events, { minGapSec = 10, maxGapSec = 120 } = {}) {
  const sorted = [...events].sort((a, b) => a.offset_min - b.offset_min);
  const gaps = [];
  let lastSoundEnd = null;

  for (const e of sorted) {
    const loud = (e.sound ?? 0) >= 0.35 || (e.stage === 'awake');
    if (loud) {
      if (lastSoundEnd !== null) {
        const gapMin = e.offset_min - lastSoundEnd;
        const gapSec = gapMin * 60;
        if (gapSec >= minGapSec && gapSec <= maxGapSec) {
          gaps.push({ offset_min: lastSoundEnd, duration_min: gapMin, duration_sec: Math.round(gapSec) });
        }
      }
      lastSoundEnd = e.offset_min + (e.duration_min || 0);
    }
  }

  const perHour = gaps.length;
  const longest = gaps.reduce((m, g) => Math.max(m, g.duration_sec), 0);
  return { gapCount: gaps.length, longestGapSec: longest, gaps };
}

/**
 * Apne risk tahmini (ses tabanlı, tarama amaçlı).
 * Horlama şiddeti + apne-benzeri boşluklar + gün içi uyuklama girdisi.
 */
export function estimateApneaRisk({ snoreMinutes, totalSleepMin, apneaGaps, longestGapSec, sleepinessScore = 0 }) {
  const sleepHours = totalSleepMin / 60;
  if (sleepHours <= 0) return { level: 'unknown', score: 0, label: 'Unknown' };

  const snoreRatio = Math.min(1, snoreMinutes / Math.max(totalSleepMin, 1) / 0.4);
  const gapPerHour = apneaGaps / sleepHours;
  const gapScore = Math.min(1, gapPerHour / 8);
  const longGapScore = Math.min(1, longestGapSec / 60);
  const sleepScore = Math.min(1, sleepinessScore / 16);

  const score = Math.round((snoreRatio * 0.30 + gapScore * 0.35 + longGapScore * 0.20 + sleepScore * 0.15) * 100);

  let level, label;
  if (score >= 65) { level = 'high'; label = 'High risk'; }
  else if (score >= 35) { level = 'moderate'; label = 'Moderate risk'; }
  else { level = 'low'; label = 'Low risk'; }

  return { level, score, label, metrics: { snoreRatio, gapPerHour, longGapScore, sleepScore } };
}
