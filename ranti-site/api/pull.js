// GET ?c=code&after=id with header x-ranti-key: the owner's app collects new friend entries.
const { sb, ownerFor, send } = require('./_lib');

module.exports = async (req, res) => {
  const owner = await ownerFor(req);
  if (!owner) return send(res, 401, { error: 'not authorised' });
  const after = Math.max(0, parseInt(req.query.after || '0', 10) || 0);
  const r = await sb('ranti_submissions?select=id,name,day,month,year,phone,email,created_at&owner_code=eq.' + owner.owner_code + '&id=gt.' + after + '&order=id.asc&limit=200');
  if (!r.ok) return send(res, 502, { error: 'store unavailable' });
  sb('ranti_owners?owner_code=eq.' + owner.owner_code, { method: 'PATCH', body: { last_seen: new Date().toISOString() }, prefer: 'return=minimal' }).catch(() => {});
  return send(res, 200, { entries: r.data });
};
