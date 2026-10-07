// GET ?e=email&t=token: stop all Ranti birthday emails to this address.
const { sb, unsubToken } = require('./_lib');

module.exports = async (req, res) => {
  const email = String(req.query.e || '').toLowerCase().slice(0, 120);
  const ok = email && req.query.t === unsubToken(email);
  if (ok) await sb('ranti_email_optouts', { method: 'POST', body: { email }, prefer: 'return=minimal,resolution=ignore-duplicates' });
  res.setHeader('Content-Type', 'text/html; charset=utf-8');
  res.status(ok ? 200 : 400).send(`<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Ranti</title>
<body style="margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#24163A;font-family:Inter,Arial,sans-serif;color:#fff;text-align:center;padding:24px">
<div><div style="font-size:48px">${ok ? '👋' : '🤔'}</div><h1 style="font-family:Georgia,serif;font-weight:600">${ok ? 'You are unsubscribed' : 'This link did not work'}</h1>
<p style="opacity:.8">${ok ? 'You will not get any more birthday emails sent through Ranti.' : 'Please use the link from the email.'}</p></div></body>`);
};
