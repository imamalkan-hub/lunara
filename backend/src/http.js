/**
 * HTTP katmanı — Node'un yerleşik `node:http` modülü üzerine yazılmış
 * bağımlılıksız bir router. Express/Fastify eklemek yerine küçük ve
 * anlaşılır bir katman tercih edildi: build ortamının RAM sınırı (1.6 GB)
 * ve "bağımlılık yok" ilkesi.
 */
import { createServer } from 'node:http';
import { URL } from 'node:url';

export class Router {
  constructor() {
    this.routes = [];
  }

  add(method, pattern, handler) {
    // '/api/doctors/:id' -> { regex, keys }
    const keys = [];
    const regexSrc = pattern
      .split('/')
      .map((seg) => {
        if (!seg.startsWith(':')) return seg.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
        keys.push(seg.slice(1));
        return '([^/]+)';
      })
      .join('/');
    this.routes.push({ method, regex: new RegExp(`^${regexSrc}/?$`), keys, handler });
    return this;
  }

  get(p, h) { return this.add('GET', p, h); }
  post(p, h) { return this.add('POST', p, h); }
  put(p, h) { return this.add('PUT', p, h); }
  patch(p, h) { return this.add('PATCH', p, h); }
  delete(p, h) { return this.add('DELETE', p, h); }

  match(method, pathname) {
    for (const r of this.routes) {
      if (r.method !== method) continue;
      const m = r.regex.exec(pathname);
      if (!m) continue;
      const params = {};
      r.keys.forEach((k, i) => { params[k] = decodeURIComponent(m[i + 1]); });
      return { handler: r.handler, params };
    }
    return null;
  }
}

/** JSON gövde okur. */
export async function readJson(req, { limit = 8 * 1024 * 1024 } = {}) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > limit) throw new HttpError(413, 'payload too large');
    chunks.push(chunk);
  }
  if (!chunks.length) return null;
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    throw new HttpError(400, 'invalid JSON body');
  }
}

export class HttpError extends Error {
  constructor(status, message, details = null) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

/** JSON yanıt yazar. */
export function sendJson(res, status, payload) {
  const body = JSON.stringify(payload);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
  });
  res.end(body);
}

/** CORS ön kontrolü. */
export function handlePreflight(req, res) {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
      'Access-Control-Max-Age': '86400',
    });
    res.end();
    return true;
  }
  return false;
}

/**
 * Sunucu kurar. `routes` bir Router örneğidir.
 * `context(req)` ile her istek için handler'a ek bağlam (ör. auth) verilir.
 */
export function createApp({ router, context = () => ({}) }) {
  return createServer(async (req, res) => {
    if (handlePreflight(req, res)) return;

    let url;
    try {
      url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
    } catch {
      sendJson(res, 400, { error: 'bad request url' });
      return;
    }

    const route = router.match(req.method, url.pathname);
    if (!route) {
      sendJson(res, 404, { error: `no route for ${req.method} ${url.pathname}` });
      return;
    }

    try {
      const ctx = await context(req, url);
      const result = await route.handler({ req, res, url, params: route.params, ...ctx });
      if (res.writableEnded) return;
      if (result === undefined) {
        sendJson(res, 204, null);
        return;
      }
      sendJson(res, result?.__status ?? 200, result?.__body ?? result);
    } catch (err) {
      if (err instanceof HttpError) {
        sendJson(res, err.status, { error: err.message, details: err.details });
        return;
      }
      console.error('[error]', req.method, url.pathname, err);
      sendJson(res, 500, { error: 'internal server error' });
    }
  });
}

/** Status kodu ile birlikte gövde döndürmek için yardımcı. */
export function status(code, body) {
  return { __status: code, __body: body };
}
