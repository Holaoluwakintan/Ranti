// POST {c, key, name}: the app claims its owner code once (trust on first use) and keeps its name fresh.
const { sb, sha256, CODE_RE, clean, send } = require('./_lib');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return send(res, 405, { error: 'POST only' });
  const b = req.body || {};
  const code = String(b.c || '').toLowerCase();
  const key = String(b.key || '');
  const name = clean(b.name, 60);
  if (!CODE_RE.test(code) || key.length < 24 || key.length > 128) return send(res, 400, { error: 'bad code or key' });
  const hash = sha256(key);
  const got = await sb('ranti_owners?select=key_hash&owner_code=eq.' + code);
  if (!got.ok) return send(res, 502, { error: 'store unavailable' });
  if (!got.data.length) {
    const ins = await sb('ranti_owners', { method: 'POST', body: { owner_code: code, key_hash: hash, name, last_seen: new Date().toISOString() }, prefer: 'return=minimal' });
    if (!ins.ok) return send(res, ins.status === 409 ? 409 : 502, { error: 'could not register' });
    return send(res, 200, { ok: true, created: true });
  }
  if (got.data[0].key_hash !== hash) return send(res, 409, { error: 'code taken' });
  await sb('ranti_owners?owner_code=eq.' + code, { method: 'PATCH', body: { name, last_seen: new Date().toISOString() }, prefer: 'return=minimal' });
  return send(res, 200, { ok: true, created: false });
};
