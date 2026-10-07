// GET ?c=code -> { name } (first name only) for the public "add your birthday" page.
const { sb, CODE_RE, send } = require('./_lib');

module.exports = async (req, res) => {
  const code = String(req.query.c || '').toLowerCase();
  if (!CODE_RE.test(code)) return send(res, 404, { error: 'unknown link' });
  const r = await sb('ranti_owners?select=name&owner_code=eq.' + code);
  if (!r.ok) return send(res, 502, { error: 'store unavailable' });
  if (!r.data.length) return send(res, 404, { error: 'unknown link' });
  const first = String(r.data[0].name || '').trim().split(/\s+/)[0] || '';
  res.setHeader('Cache-Control', 'public, max-age=300');
  return res.status(200).json({ name: first });
};
