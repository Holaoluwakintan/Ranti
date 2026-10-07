// Shared helpers for Ranti's tiny backend (Supabase REST + small validators). Not a route (leading underscore).
const crypto = require('crypto');

const SB_URL = process.env.SUPABASE_URL;
const SB_SERVICE = process.env.SUPABASE_SERVICE_KEY; // server only
const SB_ANON = process.env.SUPABASE_ANON_KEY;       // publishable key: insert-only via RLS

async function sb(path, { method = 'GET', body, anon = false, prefer } = {}) {
  const key = anon ? SB_ANON : SB_SERVICE;
  const headers = { apikey: key, 'Content-Type': 'application/json' };
  if (!anon || !key.startsWith('sb_')) headers.Authorization = 'Bearer ' + key;
  if (prefer) headers.Prefer = prefer;
  const r = await fetch(SB_URL + '/rest/v1/' + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await r.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch (_) { data = text; }
  return { ok: r.ok, status: r.status, data };
}

const sha256 = (s) => crypto.createHash('sha256').update(String(s)).digest('hex');
const CODE_RE = /^[a-z0-9]{8,16}$/;
const EMAIL_RE = /^[^\s@<>"']{1,64}@[^\s@<>"']+\.[^\s@<>"']{2,}$/;

function clean(s, max) {
  if (s === undefined || s === null) return '';
  return String(s).replace(/[\u0000-\u001f<>]/g, ' ').replace(/\s+/g, ' ').trim().slice(0, max);
}

function validDate(day, month, year) {
  day = Number(day); month = Number(month);
  if (!Number.isInteger(day) || !Number.isInteger(month) || month < 1 || month > 12 || day < 1) return false;
  const y = year ? Number(year) : 2000; // leap year when unknown, so 29 Feb is allowed
  if (year && (!Number.isInteger(y) || y < 1900 || y > new Date().getUTCFullYear())) return false;
  const dim = new Date(Date.UTC(y, month, 0)).getUTCDate();
  return day <= dim;
}

async function ownerFor(req) {
  const code = String((req.query && req.query.c) || (req.body && req.body.c) || '').toLowerCase();
  const key = req.headers['x-ranti-key'] || '';
  if (!CODE_RE.test(code) || typeof key !== 'string' || key.length < 24) return null;
  const r = await sb('ranti_owners?select=owner_code,key_hash,name&owner_code=eq.' + code);
  const row = r.ok && Array.isArray(r.data) ? r.data[0] : null;
  if (!row) return null;
  const a = Buffer.from(row.key_hash), b = Buffer.from(sha256(key));
  if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) return null;
  return row;
}

function unsubToken(email) {
  return crypto.createHmac('sha256', process.env.UNSUB_SECRET || 'x').update(String(email).toLowerCase()).digest('hex').slice(0, 32);
}

function send(res, status, obj) {
  res.setHeader('Cache-Control', 'no-store');
  res.status(status).json(obj);
}

module.exports = { sb, sha256, CODE_RE, EMAIL_RE, clean, validDate, ownerFor, unsubToken, send };
