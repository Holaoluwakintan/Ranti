
import os, sys, json, hashlib, requests
ROOT='/home/user/work/ranti-site'
env={}
for l in open('/home/user/tab/files/.voicepad-secrets.env'):
    l=l.strip()
    if '=' in l and not l.startswith('#'):
        k,v=l.split('=',1); env[k.strip()]=v.strip().strip('"').strip("'")
TOKEN=os.environ.get('VERCEL_TOKEN') or env['VERCEL_TOKEN']
TEAM=os.environ.get('VERCEL_TEAM','team_8Jkc3ymdudr2aiHuFsdspZDE')
PROJECT=os.environ.get('VERCEL_PROJECT','ranti-ng')
H={'Authorization':'Bearer '+TOKEN}
skip={'index.html.tmpl','deploy.py'}
files=[]
for dp,dn,fn in os.walk(ROOT):
    dn[:]=[d for d in dn if not d.startswith('.') and d!='node_modules']
    for f in fn:
        if f in skip or f.startswith('.'): continue
        p=os.path.join(dp,f); rel=os.path.relpath(p,ROOT); b=open(p,'rb').read()
        sha=hashlib.sha1(b).hexdigest()
        r=requests.post(f'https://api.vercel.com/v2/files?teamId={TEAM}',headers={**H,'x-vercel-digest':sha,'Content-Type':'application/octet-stream'},data=b,timeout=120)
        if r.status_code not in (200,201): print('UPLOAD_FAIL',rel,r.status_code,r.text[:300]); sys.exit(2)
        files.append({'file':rel,'sha':sha,'size':len(b)})
body={'name':PROJECT,'project':PROJECT,'target':'production','files':files}
r=requests.post(f'https://api.vercel.com/v13/deployments?teamId={TEAM}&forceNew=1',headers={**H,'Content-Type':'application/json'},json=body,timeout=120)
print(r.status_code); j=r.json()
print(json.dumps({k:j.get(k) for k in ('id','url','readyState','alias','error')})[:800])
