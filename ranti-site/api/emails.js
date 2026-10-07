// POST {c, name, people:[{id,name,email,day,month,message}]} with x-ranti-key:
// replaces the owner's list of people who get an automatic birthday email. Only opted-in people are sent.
const { sb, ownerFor, EMAIL_RE, clean, validDate, send } = require('./_lib');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return send(res, 405, { error: 'POST only' });
  const owner = await ownerFor(req);
  if (!owner) return send(res, 401, { error: 'not authorised' });
  const b = req.body || {};
  const people = Array.isArray(b.people) ? b.people.slice(0, 500) : [];
  const rows = [];
  for (const p of people) {
    const email = clean(p.email, 120).toLowerCase();
    const name = clean(p.name, 60);
    const id = Number(p.id);
    if (!Number.isInteger(id) || !name || !EMAIL_RE.test(email) || !validDate(p.day, p.month, null)) continue;
    rows.push({ owner_code: owner.owner_code, local_id: id, name, email, day: Number(p.day), month: Number(p.month), message: String(p.message || '').slice(0, 1500), updated_at: new Date().toISOString() });
  }
  const name = clean(b.name, 60);
  if (name) await sb('ranti_owners?owner_code=eq.' + owner.owner_code, { method: 'PATCH', body: { name }, prefer: 'return=minimal' });
  const del = await sb('ranti_auto_emails?owner_code=eq.' + owner.owner_code, { method: 'DELETE', prefer: 'return=minimal' });
  if (!del.ok) return send(res, 502, { error: 'store unavailable' });
  if (rows.length) {
    const ins = await sb('ranti_auto_emails', { method: 'POST', body: rows, prefer: 'return=minimal' });
    if (!ins.ok) return send(res, 502, { error: 'store unavailable' });
  }
  return send(res, 200, { ok: true, count: rows.length });
};
