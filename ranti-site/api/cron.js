// Daily at 06:00 UTC (07:00 Lagos): send opted-in birthday emails via Brevo.
// Auth: Vercel Cron sends "Authorization: Bearer $CRON_SECRET". ?dry=1 lists who would get mail without sending.
const { sb, unsubToken, stripLinks, send } = require('./_lib');

// Abuse guards (audit 2026-10-08): Brevo's free 300/day is shared with Blossom, and one phone must not be able to mass-mail.
const MAX_PER_RUN = 150;
const MAX_PER_OWNER = 20;
// Retention (privacy policy): friends' entries 90 days, phones not seen for 12 months, send log after the next year.
const SUBMISSION_DAYS = 90;
const OWNER_IDLE_DAYS = 365;

async function retention(year) {
  const iso = (days) => new Date(Date.now() - days * 86400000).toISOString();
  const out = {};
  const r1 = await sb('ranti_submissions?created_at=lt.' + iso(SUBMISSION_DAYS), { method: 'DELETE', prefer: 'return=minimal' });
  out.submissions = r1.ok;
  const r2 = await sb('ranti_email_log?year=lt.' + (year - 1), { method: 'DELETE', prefer: 'return=minimal' });
  out.log = r2.ok;
  const idle = await sb('ranti_owners?select=owner_code&last_seen=lt.' + iso(OWNER_IDLE_DAYS) + '&limit=200');
  out.idleOwners = idle.ok ? idle.data.length : null;
  if (idle.ok) {
    for (const o of idle.data) {
      for (const t of ['ranti_submissions', 'ranti_auto_emails', 'ranti_email_log', 'ranti_owners']) {
        await sb(t + '?owner_code=eq.' + o.owner_code, { method: 'DELETE', prefer: 'return=minimal' });
      }
    }
  }
  return out;
}

const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function lagosToday(now = new Date()) {
  const d = new Date(now.getTime() + 60 * 60 * 1000); // Africa/Lagos is UTC+1 all year
  return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate() };
}

function defaultMessage(first, from) {
  return `Happy birthday, ${first}! 🎉\n\nToday I'm celebrating you. Thank you for the gift you are. May this new year bring you joy, good health, peace and every good thing your heart has been praying for.\n\nHave a beautiful day!`;
}

function buildEmail(p, fromName, base) {
  const first = p.name.trim().split(/\s+/)[0];
  const custom = stripLinks(p.message || '').trim();
  const msg = custom ? custom : defaultMessage(first, fromName);
  const paras = msg.split(/\n{2,}/).map((t) => `<p style="margin:0 0 16px;font:16px/1.6 Inter,Arial,sans-serif;color:#1E1633">${esc(t).replace(/\n/g, '<br>')}</p>`).join('');
  const unsub = `${base}/api/unsub?e=${encodeURIComponent(p.email)}&t=${unsubToken(p.email)}`;
  const html = `<!doctype html><html><body style="margin:0;background:#FBF3F7;padding:24px 12px">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">
<table role="presentation" width="100%" style="max-width:520px;background:#ffffff;border-radius:24px;overflow:hidden" cellpadding="0" cellspacing="0">
<tr><td style="background:#24163A;background-image:linear-gradient(135deg,#FF6A3D,#FFB23F);padding:36px 28px;text-align:center">
<div style="font-size:52px;line-height:1">🎂</div>
<div style="font:600 30px/1.2 Georgia,'Times New Roman',serif;color:#ffffff;margin-top:12px">Happy birthday, ${esc(first)}!</div>
</td></tr>
<tr><td style="padding:28px 28px 8px">${paras}
<p style="margin:8px 0 0;font:600 16px/1.5 Inter,Arial,sans-serif;color:#1E1633">With love,<br>${esc(fromName)}</p>
</td></tr>
<tr><td style="padding:20px 28px 28px;font:12px/1.5 Inter,Arial,sans-serif;color:#A79FB8">
${esc(fromName)} asked the Ranti app to remember your birthday and send this note. Replies go to the Ranti team, not to ${esc(fromName)}, so to thank them, message them directly.<br>
Don't want birthday emails? <a href="${unsub}" style="color:#FF6A3D">Unsubscribe</a>.
</td></tr></table></td></tr></table></body></html>`;
  const text = `${msg}\n\nWith love,\n${fromName}\n\n--\n${fromName} asked the Ranti app to send this note. Replies go to the Ranti team, not to ${fromName}.\nDon't want birthday emails? Unsubscribe: ${unsub}`;
  return { subject: `Happy birthday, ${first}! 🎂`, html, text, unsub };
}

module.exports = async (req, res) => {
  const auth = req.headers.authorization || '';
  if (!process.env.CRON_SECRET || auth !== 'Bearer ' + process.env.CRON_SECRET) return send(res, 401, { error: 'not authorised' });
  const dry = req.query.dry === '1';
  const sandbox = req.query.sandbox === '1'; // Brevo validates and drops the mail (test only)
  const base = process.env.PUBLIC_BASE || 'https://ranti-ng.vercel.app';
  const t = lagosToday(req.query.date ? new Date(req.query.date + 'T12:00:00Z') : new Date());
  const leap = (t.y % 4 === 0 && t.y % 100 !== 0) || t.y % 400 === 0;
  let filter = `month=eq.${t.m}&day=eq.${t.d}`;
  if (t.m === 2 && t.d === 28 && !leap) filter = `month=eq.2&day=in.(28,29)`;
  const people = await sb('ranti_auto_emails?select=owner_code,local_id,name,email,message&' + filter + '&limit=500');
  if (!people.ok) return send(res, 502, { error: 'store unavailable' });
  const out = [];
  const perOwner = {};
  let sentCount = 0;
  for (const p of people.data) {
    if (sentCount >= MAX_PER_RUN) { out.push({ to: p.email, skipped: 'daily cap' }); continue; }
    perOwner[p.owner_code] = (perOwner[p.owner_code] || 0) + 1;
    if (perOwner[p.owner_code] > MAX_PER_OWNER) { out.push({ to: p.email, skipped: 'owner cap' }); continue; }
    const opt = await sb('ranti_email_optouts?select=email&email=eq.' + encodeURIComponent(p.email));
    if (opt.ok && opt.data.length) { out.push({ to: p.email, skipped: 'unsubscribed' }); continue; }
    const done = await sb(`ranti_email_log?select=status&owner_code=eq.${p.owner_code}&local_id=eq.${p.local_id}&year=eq.${t.y}`);
    if (done.ok && done.data.length) { out.push({ to: p.email, skipped: 'already sent' }); continue; }
    const owner = await sb('ranti_owners?select=name&owner_code=eq.' + p.owner_code);
    const fromName = (owner.ok && owner.data[0] && owner.data[0].name) || 'A friend';
    const mail = buildEmail(p, fromName, base);
    if (dry) { out.push({ to: p.email, name: p.name, from: fromName, subject: mail.subject, dry: true }); continue; }
    const r = await fetch('https://api.brevo.com/v3/smtp/email', {
      method: 'POST',
      headers: { 'api-key': process.env.BREVO_API_KEY, 'Content-Type': 'application/json', accept: 'application/json' },
      body: JSON.stringify({
        sender: { name: (fromName + ' via Ranti').slice(0, 70), email: process.env.FROM_EMAIL },
        replyTo: { email: process.env.FROM_EMAIL, name: 'Ranti' },
        to: [{ email: p.email, name: p.name }],
        subject: mail.subject, htmlContent: mail.html, textContent: mail.text,
        headers: Object.assign({ 'List-Unsubscribe': `<${mail.unsub}>` }, sandbox ? { 'X-Sib-Sandbox': 'drop' } : {}),
        tags: ['ranti-birthday'],
      }),
    });
    sentCount++;
    const status = (r.ok ? 'sent' : 'failed ' + r.status) + (sandbox ? ' (sandbox)' : '');
    await sb('ranti_email_log', { method: 'POST', body: { owner_code: p.owner_code, local_id: p.local_id, year: t.y, email: p.email, status }, prefer: 'return=minimal,resolution=merge-duplicates' });
    out.push({ to: p.email, status });
  }
  const kept = (!dry && !sandbox) ? await retention(t.y) : null;
  return send(res, 200, { date: `${t.y}-${t.m}-${t.d}`, count: out.length, retention: kept, results: (dry || sandbox) ? out : out.map((o) => ({ status: o.status || o.skipped })) });
};
module.exports.buildEmail = buildEmail;
module.exports.lagosToday = lagosToday;
module.exports.retention = retention;
