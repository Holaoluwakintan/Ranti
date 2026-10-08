// POST from the public page: a friend adds their birthday to one owner's Ranti.
// The route validates every field and caps entries per owner; the insert uses the server key so the
// public anon key needs no write access at all (revoke: see audit 2026-10-08).
const { sb, CODE_RE, EMAIL_RE, clean, validDate, send } = require('./_lib');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return send(res, 405, { error: 'POST only' });
  const b = req.body || {};
  if (b.website) return send(res, 200, { ok: true }); // honeypot: bots fill every field
  const code = String(b.c || '').toLowerCase();
  const name = clean(b.name, 60);
  const day = Number(b.day), month = Number(b.month);
  const year = b.year ? Number(b.year) : null;
  const phone = clean(b.phone, 24).replace(/[^0-9+ ()-]/g, '');
  const email = clean(b.email, 120).toLowerCase();
  if (!CODE_RE.test(code)) return send(res, 400, { error: 'This link is not valid.' });
  if (!name) return send(res, 400, { error: 'Please enter your name.' });
  if (!validDate(day, month, year)) return send(res, 400, { error: 'Please check the date.' });
  if (email && !EMAIL_RE.test(email)) return send(res, 400, { error: 'Please check the email address.' });

  const owner = await sb('ranti_owners?select=owner_code&owner_code=eq.' + code);
  if (!owner.ok) return send(res, 502, { error: 'Something went wrong. Please try again.' });
  if (!owner.data.length) return send(res, 404, { error: 'This link is not valid.' });

  // Light abuse guard: at most 300 entries per owner per day, and no exact repeats.
  const since = new Date(Date.now() - 86400000).toISOString();
  const recent = await sb('ranti_submissions?select=name,day,month&owner_code=eq.' + code + '&created_at=gte.' + since + '&limit=301');
  if (recent.ok && recent.data.length >= 300) return send(res, 429, { error: 'Too many entries today. Please try tomorrow.' });
  if (recent.ok && recent.data.some(r => r.name.toLowerCase() === name.toLowerCase() && r.day === day && r.month === month)) return send(res, 200, { ok: true });

  const ins = await sb('ranti_submissions', {
    method: 'POST', prefer: 'return=minimal', // server key: this route is the only way in (audit 2026-10-08)
    body: { owner_code: code, name, day, month, year, phone: phone || null, email: email || null },
  });
  if (!ins.ok) return send(res, 400, { error: 'Could not save. Please check your details.' });
  return send(res, 200, { ok: true });
};
