// POST {c} with x-ranti-key: deletes everything the server holds for this phone
// (its birthday link, friends' entries waiting there, auto-email list and send log). v1.0, used by
// Settings -> "Delete my cloud data". Email opt-outs are kept (they protect the recipients).
const { sb, ownerFor, send } = require('./_lib');

module.exports = async (req, res) => {
  if (req.method !== 'POST') return send(res, 405, { error: 'POST only' });
  const owner = await ownerFor(req);
  if (!owner) return send(res, 404, { error: 'nothing to delete' });
  const code = owner.owner_code;
  for (const t of ['ranti_submissions', 'ranti_auto_emails', 'ranti_email_log', 'ranti_owners']) {
    const r = await sb(t + '?owner_code=eq.' + code, { method: 'DELETE', prefer: 'return=minimal' });
    if (!r.ok) return send(res, 502, { error: 'store unavailable', table: t });
  }
  return send(res, 200, { ok: true });
};
