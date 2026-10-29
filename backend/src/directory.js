/**
 * Uyku doktoru / klinik dizini araması.
 *
 * Veri kaynağı: findsleepdoctors.com dizini (21.343 uyku uzmanı, 6 ülke,
 * 4.186+ şehir). Kaynak veritabanı `data/directory.db` içinde; her kayıt
 * kendi `source` alanı ile hangi kamu kaynağından doğrulandığını taşır
 * (npiregistry.cms.hhs.gov, ouh.nhs.uk-consultant-directory vb.).
 *
 * Kaynak gösterimi: API yanıtlarında her doktor için `source` ve
 * `sourceUrl` döner; uygulama bu alanı doktor kartında "Kaynak" olarak
 * gösterir.
 */
import { getDirectoryDb, all, one } from './db.js';

/** findsleepdoctors.com'daki kaynak alan adlarını kamu kaynağı URL'lerine eşler. */
const SOURCE_URLS = {
  'npiregistry.cms.hhs.gov': 'https://npiregistry.cms.hhs.gov/',
  'google.com/place': 'https://www.google.com/maps',
  'sleepclinicdirectory.com': 'https://sleepclinicdirectory.com/',
  'avustralya-uyku': 'https://www.healthdirect.gov.au/',
  'spirehealthcare.com': 'https://spirehealthcare.com/',
  'ouh.nhs.uk-consultant-directory': 'https://www.ouh.nhs.uk/',
  'healthpoint.co.nz': 'https://www.healthpoint.co.nz/',
  'sleep-apnoea-trust.org': 'https://www.sleep-apnoea-trust.org/',
  'cumming.ucalgary.ca': 'https://cumming.ucalgary.ca/',
  'yellowpages.com.au': 'https://www.yellowpages.com.au/',
  'medsleep.com': 'https://www.medsleep.com/',
  'cpsbc.ca-directory': 'https://www.cpsbc.ca/',
  'newcastle-hospitals.nhs.uk': 'https://www.newcastle-hospitals.nhs.uk/',
  'blackrockhealth.com': 'https://blackrockhealth.com/',
  'stvincents.ie': 'https://www.stvincents.ie/',
};

export function sourceUrlFor(source) {
  return SOURCE_URLS[source] || null;
}

/** Haversine mesafe (km). */
export function haversineKm(lat1, lng1, lat2, lng2) {
  const R = 6371;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}

const DOCTOR_SELECT = `
  SELECT d.id, d.slug, d.name, d.credentials, d.specialty, d.clinic,
         d.address_line, d.postcode, d.phone, d.fax, d.email, d.website,
         d.lat, d.lng, d.languages, d.telehealth, d.source, d.source_id,
         d.profile_url, d.medical_school, d.residency, d.fellowship,
         d.board_certifications, d.years_experience, d.accepting_patients,
         d.education, d.featured, d.precision,
         ci.name AS city, ci.slug AS city_slug,
         rg.name AS region, rg.slug AS region_slug, rg.code AS region_code,
         co.name AS country, co.code AS country_code, co.domain_tld
    FROM doctors d
    LEFT JOIN cities ci   ON ci.id = d.city_id
    LEFT JOIN regions rg  ON rg.id = d.region_id
    LEFT JOIN countries co ON co.code = d.country_code
`;

function decorate(row) {
  if (!row) return null;
  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    credentials: row.credentials || '',
    specialty: row.specialty || 'Sleep Medicine',
    clinic: row.clinic || '',
    address: [row.address_line, row.city, row.region, row.postcode]
      .filter(Boolean)
      .join(', '),
    addressLine: row.address_line || '',
    city: row.city || '',
    citySlug: row.city_slug || '',
    region: row.region || '',
    regionSlug: row.region_slug || '',
    regionCode: row.region_code || '',
    country: row.country || '',
    countryCode: row.country_code || '',
    phone: row.phone || '',
    fax: row.fax || '',
    email: row.email || '',
    website: row.website || '',
    lat: row.lat,
    lng: row.lng,
    languages: row.languages ? row.languages.split(/[,;|]/).map((s) => s.trim()).filter(Boolean) : [],
    telehealth: !!row.telehealth,
    source: row.source || '',
    sourceUrl: sourceUrlFor(row.source),
    sourceId: row.source_id || '',
    profileUrl: row.profile_url || '',
    education: {
      medicalSchool: row.medical_school || '',
      residency: row.residency || '',
      fellowship: row.fellowship || '',
      boardCertifications: row.board_certifications || '',
      yearsExperience: row.years_experience || '',
    },
    acceptingPatients: row.accepting_patients,
    featured: !!row.featured,
    precision: row.precision || '',
    directoryUrl: row.country_code
      ? `https://findsleepdoctors.com/${row.country_code}/${row.city_slug || ''}`.replace(/\/$/, '')
      : 'https://findsleepdoctors.com/',
  };
}

/**
 * Konuma göre en yakın doktorlar.
 *
 * Performans: 21k kayıt için tam tarama yerine önce kutu (bounding box)
 * ön filtresi uygulanır, sonra mesafe hesaplanır. Yarıçap büyüdükçe kutu
 * genişler (cos/sin ile enlem sapması düzeltilir).
 */
export function nearestDoctors({ lat, lng, radiusKm = 50, limit = 50, countryCode = null, telehealthOnly = false }) {
  const db = getDirectoryDb();
  const latDelta = radiusKm / 111.32;
  const lngDelta = radiusKm / (111.32 * Math.max(Math.cos((lat * Math.PI) / 180), 0.01));

  const params = [lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta];
  let where = `d.lat IS NOT NULL AND d.lng IS NOT NULL
               AND d.lat BETWEEN ? AND ? AND d.lng BETWEEN ? AND ?`;
  if (countryCode) {
    where += ' AND d.country_code = ?';
    params.push(countryCode);
  }
  if (telehealthOnly) where += ' AND d.telehealth = 1';

  const rows = all(db, `${DOCTOR_SELECT} WHERE ${where}`, ...params);

  const withDistance = rows
    .map((r) => {
      const d = decorate(r);
      d.distanceKm = Math.round(haversineKm(lat, lng, r.lat, r.lng) * 10) / 10;
      return d;
    })
    .filter((d) => d.distanceKm <= radiusKm)
    .sort((a, b) => a.distanceKm - b.distanceKm);

  return withDistance.slice(0, limit);
}

/** Serbest metin arama: isim, klinik, şehir, bölge. */
export function searchDoctors({ q = '', countryCode = null, limit = 30, offset = 0 }) {
  const db = getDirectoryDb();
  const term = `%${q.trim()}%`;
  const params = [];
  let where = '';
  if (q.trim()) {
    where = `WHERE (d.name LIKE ? OR IFNULL(d.clinic,'') LIKE ? OR IFNULL(ci.name,'') LIKE ?
              OR IFNULL(rg.name,'') LIKE ? OR IFNULL(d.specialty,'') LIKE ?)`;
    params.push(term, term, term, term, term);
  }
  if (countryCode) {
    where += where ? ' AND d.country_code = ?' : 'WHERE d.country_code = ?';
    params.push(countryCode);
  }

  const rows = all(
    db,
    `${DOCTOR_SELECT} ${where} ORDER BY d.featured DESC, d.name ASC LIMIT ? OFFSET ?`,
    ...params,
    limit,
    offset,
  );
  return rows.map(decorate);
}

/** Tek doktor detayı. */
export function getDoctor(id) {
  const db = getDirectoryDb();
  const row = one(db, `${DOCTOR_SELECT} WHERE d.id = ?`, id);
  return decorate(row);
}

/** Ülke listesi. */
export function listCountries() {
  const db = getDirectoryDb();
  return all(
    db,
    `SELECT co.code, co.name, co.domain_tld,
            COUNT(d.id) AS doctor_count
       FROM countries co
       LEFT JOIN doctors d ON d.country_code = co.code
      GROUP BY co.code
      ORDER BY doctor_count DESC`,
  );
}

/**
 * Konuma göre en yakın şehirler — "şehir seç" akışı için.
 */
export function nearestCities({ lat, lng, limit = 20, countryCode = null }) {
  const db = getDirectoryDb();
  const latDelta = 2.5;
  const lngDelta = 2.5 / Math.max(Math.cos((lat * Math.PI) / 180), 0.01);
  const params = [lat - latDelta, lat + latDelta, lng - lngDelta, lng + lngDelta];
  let where = `ci.lat BETWEEN ? AND ? AND ci.lng BETWEEN ? AND ?`;
  if (countryCode) {
    where += ' AND co.code = ?';
    params.push(countryCode);
  }

  const rows = all(
    db,
    `SELECT ci.id, ci.name, ci.slug, ci.lat, ci.lng,
            rg.name AS region, rg.slug AS region_slug,
            co.code AS country_code, co.name AS country,
            (SELECT COUNT(*) FROM doctors d WHERE d.city_id = ci.id) AS doctor_count
       FROM cities ci
       JOIN regions rg ON rg.id = ci.region_id
       JOIN countries co ON co.code = rg.country_code
      WHERE ${where}
      LIMIT 500`,
    ...params,
  );

  return rows
    .map((r) => ({
      ...r,
      distanceKm: Math.round(haversineKm(lat, lng, r.lat, r.lng) * 10) / 10,
    }))
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .slice(0, limit);
}

/**
 * Şehir bazlı doktor listesi (bölüm -> şehir -> doktorlar gezinmesi).
 */
export function doctorsByCity(countryCode, regionSlug, citySlug) {
  const db = getDirectoryDb();
  const rows = all(
    db,
    `${DOCTOR_SELECT}
      WHERE d.country_code = ?
        AND rg.slug = ?
        AND ci.slug = ?
      ORDER BY d.name`,
    countryCode,
    regionSlug,
    citySlug,
  );
  return rows.map(decorate);
}

/** Bölge (state/province) listesi. */
export function listRegions(countryCode) {
  const db = getDirectoryDb();
  return all(
    db,
    `SELECT rg.id, rg.code, rg.name, rg.slug, co.name AS country,
            (SELECT COUNT(*) FROM doctors d WHERE d.region_id = rg.id) AS doctor_count
       FROM regions rg
       JOIN countries co ON co.code = rg.country_code
      WHERE rg.country_code = ?
      ORDER BY rg.name`,
    countryCode,
  );
}

/** Şehir listesi (bölgeye göre). */
export function listCities(countryCode, regionSlug) {
  const db = getDirectoryDb();
  return all(
    db,
    `SELECT ci.id, ci.name, ci.slug, ci.lat, ci.lng,
            (SELECT COUNT(*) FROM doctors d WHERE d.city_id = ci.id) AS doctor_count
       FROM cities ci
       JOIN regions rg ON rg.id = ci.region_id
      WHERE rg.country_code = ? AND rg.slug = ?
      ORDER BY ci.name`,
    countryCode,
    regionSlug,
  );
}

/**
 * Coğrafi arama için öneri listesi — kullanıcı yazmaya başladığında
 * ülke/bölge/şehir önerileri döner.
 */
export function suggestPlaces(q, limit = 10) {
  const db = getDirectoryDb();
  const term = `%${q.trim()}%`;
  if (!q.trim()) return [];
  return all(
    db,
    `SELECT 'city' AS type, ci.name AS label, rg.name AS sublabel,
            co.code AS country_code, rg.slug AS region_slug, ci.slug AS city_slug,
            ci.lat, ci.lng
       FROM cities ci
       JOIN regions rg ON rg.id = ci.region_id
       JOIN countries co ON co.code = rg.country_code
      WHERE ci.name LIKE ?
      UNION ALL
     SELECT 'region' AS type, rg.name AS label, co.name AS sublabel,
            co.code AS country_code, rg.slug AS region_slug, NULL AS city_slug,
            NULL AS lat, NULL AS lng
       FROM regions rg
       JOIN countries co ON co.code = rg.country_code
      WHERE rg.name LIKE ?
      UNION ALL
     SELECT 'country' AS type, co.name AS label, co.domain_tld AS sublabel,
            co.code AS country_code, NULL AS region_slug, NULL AS city_slug,
            NULL AS lat, NULL AS lng
       FROM countries co
      WHERE co.name LIKE ?
      LIMIT ?`,
    term,
    term,
    term,
    limit,
  );
}

/**
 * Bölgedeki doktor yoğunluğu haritası için özet — "hangi şehirlerde çok
 * uzman var" bilgisini verir.
 */
export function topCitiesBySpecialty(countryCode, limit = 20) {
  const db = getDirectoryDb();
  return all(
    db,
    `SELECT ci.name AS city, rg.name AS region, co.name AS country,
            co.code AS country_code, rg.slug AS region_slug, ci.slug AS city_slug,
            COUNT(d.id) AS doctor_count
       FROM doctors d
       JOIN cities ci   ON ci.id = d.city_id
       JOIN regions rg  ON rg.id = d.region_id
       JOIN countries co ON co.code = d.country_code
      WHERE d.country_code = ?
      GROUP BY ci.id
      ORDER BY doctor_count DESC
      LIMIT ?`,
    countryCode,
    limit,
  );
}
