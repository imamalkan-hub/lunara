/**
 * Klinik tarama anketleri ve risk skorlama.
 *
 * Üç tarama:
 *  - STOP-BANG  (8 soru) — uyku apnesi tarama; uluslararası geçerliliği
 *    en yüksek, 8 puan üzeri "yüksek risk", 3-4 "orta risk".
 *  - Epworth  (8 soru) — gündüz uykululuk ölçeği; toplam >10 anlamlı
 *    gündüz uykululuğu gösterir.
 *  - Berlin  (6 soru) — uyku apnesi tarama (yetişkin).
 *
 * Bunlar tanı değil tarama aracıdır. Sonuçlar "doktora danışın"
 * yönlendirmesi üretir, tanı iddiasında bulunmaz.
 */

/** STOP-BANG: sorular, ağırlıklar, risk eşikleri. */
export const STOP_BANG = {
  id: 'stop_bang',
  name: 'STOP-BANG',
  description: 'Sleep apnea screening — 8 questions, 5 or more indicates high risk.',
  maxScore: 8,
  answers: [
    { key: 'snoring', label: 'Do you snore loudly while you sleep?', hint: 'Has someone else mentioned it?', weight: 1 },
    { key: 'tired', label: 'Do you feel tired or sleepy during the day?', weight: 1 },
    { key: 'observed', label: 'Have you noticed yourself stopping breathing or choking during sleep?', hint: 'Has someone sleeping nearby told you?', weight: 1 },
    { key: 'pressure', label: 'Do you have high blood pressure?', weight: 1 },
    { key: 'bmi', label: 'Are you overweight? (BMI over 35)', hint: 'Your height and weight are combined into a BMI.', weight: 1 },
    { key: 'age', label: 'Are you over 50 years old?', weight: 1 },
    { key: 'neck', label: 'Do you have a large or thick neck?', weight: 1 },
    { key: 'gender', label: 'Are you male?', weight: 1 },
  ],
  // 0-2 düşük, 3-4 orta, 5+ yüksek
  thresholds: { low: 3, high: 5 },
};

export const EPWORTH = {
  id: 'epworth',
  name: 'Epworth Sleepiness Scale',
  description: 'Daytime sleepiness scale — a score above 10 means significant sleepiness.',
  maxScore: 24,
  answers: [
    { key: 'reading', label: 'Sitting in bed reading', weight: 0 },
    { key: 'tv', label: 'Watching television', weight: 0 },
    { key: 'sitting', label: 'Sitting inactive in a public place', weight: 0 },
    { key: 'car', label: 'As a passenger in a car for a short while', weight: 0 },
    { key: 'lying', label: 'Lying down to rest', weight: 0 },
    { key: 'restless', label: 'Sitting quietly after lunch without alcohol', weight: 0 },
    { key: 'driving', label: 'Driving a car', weight: 0 },
    { key: 'work', label: 'Working at a desk or during an activity', weight: 0 },
  ],
  // Her soru 0-4 puan
  scale: [0, 1, 2, 3, 4],
  thresholds: { low: 10, high: 17 },
};

export const BERLIN = {
  id: 'berlin',
  name: 'Berlin Questionnaire',
  description: 'Sleep apnea screening questionnaire — assessed across 3 categories.',
  maxScore: 3,
  answers: [
    { key: 'snoring', label: 'Do you snore while you sleep?', options: [
      { value: 0, label: 'No' },
      { value: 1, label: 'Yes, on a few nights a week' },
      { value: 2, label: 'Yes, three or more nights a week' },
    ] },
    { key: 'tired', label: 'Do you feel tired or sleepy during the day?', options: [
      { value: 0, label: 'No' },
      { value: 1, label: 'Yes, nearly every day' },
      { value: 2, label: 'Yes, three or more nights a week' },
    ] },
    { key: 'witnessed', label: 'Has anyone observed you stopping breathing during sleep?', options: [
      { value: 0, label: 'No' },
      { value: 1, label: 'Yes' },
    ] },
  ],
  thresholds: { low: 2, high: 2 },
};

export const SCREENINGS = { stop_bang: STOP_BANG, epworth: EPWORTH, berlin: BERLIN };

/**
 * Tarama puanlarını hesaplar ve risk seviyesi + yönlendirme üretir.
 *
 * @param {string} kind  stop_bang | epworth | berlin
 * @param {object} answers  { [questionKey]: number }
 */
export function scoreScreening(kind, answers = {}) {
  const spec = SCREENINGS[kind];
  if (!spec) throw new Error(`unknown screening: ${kind}`);

  let score = 0;
  for (const q of spec.answers) {
    const raw = answers[q.key];
    if (raw === undefined || raw === null || raw === '') continue;
    score += Number(raw);
  }

  let level, result, advice;
  const t = spec.thresholds;

  if (kind === 'epworth') {
    if (score >= t.high) {
      level = 'high';
      result = 'Severe daytime sleepiness';
      advice = 'A high score on this scale means daytime sleepiness may be affecting your daily function. We recommend speaking with a sleep doctor.';
    } else if (score >= t.low) {
      level = 'moderate';
      result = 'Meaningful daytime sleepiness';
      advice = 'Mild to moderate daytime sleepiness. Sleep hygiene and sleep restriction may help; see your doctor if it persists.';
    } else {
      level = 'low';
      result = 'Normal daytime sleepiness';
      advice = 'Your score is within the normal range. Keeping consistent sleep habits should be enough.';
    }
  } else if (kind === 'berlin') {
    if (score >= 2) {
      level = 'high';
      result = 'High suspicion of sleep apnea';
      advice = 'The Berlin questionnaire indicates a high risk of sleep apnea. A confirmed diagnosis requires a sleep study — please speak with a sleep doctor.';
    } else if (score >= 1) {
      level = 'moderate';
      result = 'Moderate risk';
      advice = 'Moderate risk. Your observations from a bed partner and a sleep study will be useful for your doctor.';
    } else {
      level = 'low';
      result = 'Low risk';
      advice = 'The questionnaire shows a low risk. Still worth an assessment if clinical symptoms persist.';
    }
  } else {
    // STOP-BANG
    if (score >= t.high) {
      level = 'high';
      result = 'High risk';
      advice = 'A STOP-BANG score of 5 or more — assessment for sleep apnea is recommended. Booking a sleep doctor appointment is a sensible next step.';
    } else if (score >= t.low) {
      level = 'moderate';
      result = 'Moderate risk';
      advice = 'Moderate risk. Sleep hygiene and positional therapy are worth reviewing; see a doctor if symptoms persist.';
    } else {
      level = 'low';
      result = 'Low risk';
      advice = 'Low risk. Keeping consistent sleep and lifestyle habits appears sufficient.';
    }
  }

  return {
    kind,
    score,
    maxScore: spec.maxScore,
    result,
    riskLevel: level,
    advice,
  };
}

/**
 * Uygulama içi ilk yardım: tarama riskine göre kullanıcıya gösterilecek
 * eylem listesi. Doktora yönlendirme her zaman bir seçenek olarak durur.
 */
export function recommendedActions(riskLevel) {
  const base = [
    { id: 'consistent_time', title: 'Go to bed at the same time every night', detail: 'Your regularity score is one of the strongest drivers of sleep quality.' },
    { id: 'morning_light', title: '20 minutes of morning daylight', detail: 'It shifts your circadian rhythm earlier; dim evening light too.' },
    { id: 'caffeine_cutoff', title: 'Cut caffeine after midday', detail: 'Caffeine effects can last 5-6 hours.' },
    { id: 'screen_off', title: 'Turn screens off 60 minutes before bed', detail: 'Blue light suppresses melatonin release.' },
    { id: 'cool_dark', title: 'Keep the room cool and dark', detail: '16-19°C and full darkness speeds up the transition to sleep.' },
  ];

  if (riskLevel === 'high' || riskLevel === 'moderate') {
    base.unshift(
      { id: 'positional', title: 'Avoid sleeping on your back', detail: 'Back sleeping increases snoring and breathing events.' },
      { id: 'nasal_strip', title: 'Try nasal strips or dilators', detail: 'Improving nasal airflow can noticeably reduce daytime sleepiness.' },
    );
  }

  return base;
}
