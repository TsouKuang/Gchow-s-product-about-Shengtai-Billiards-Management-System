import pathlib,subprocess,os
p=pathlib.Path('/usr/local/sbin/shengtai-backup')
p.write_text('''#!/bin/sh
set -eu
umask 077
mkdir -p /opt/shengtai/backups
out=/opt/shengtai/backups/billiard-$(date +%Y%m%d-%H%M%S).sql.gz
mysqldump --defaults-extra-file=/opt/shengtai/mysql-admin.cnf --single-transaction --routines --triggers --events --no-tablespaces billiard_db | gzip > "$out"
test -s "$out"
''')
os.chmod(str(p),0o700)
pathlib.Path('/etc/systemd/system/shengtai-backup.service').write_text('''[Unit]
Description=Shengtai database backup
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/shengtai-backup
''')
pathlib.Path('/etc/systemd/system/shengtai-backup.timer').write_text('''[Unit]
Description=Daily Shengtai database backup
[Timer]
OnCalendar=*-*-* 04:15:00
Persistent=true
[Install]
WantedBy=timers.target
''')
subprocess.check_call(['systemctl','daemon-reload'])
subprocess.check_call(['systemctl','enable','--now','shengtai-backup.timer'])
subprocess.check_call(['/usr/local/sbin/shengtai-backup'])
# Verify the configured WeChat secret with its own official API without logging credentials or tokens.
import json,urllib.request,urllib.parse
c=json.loads(pathlib.Path('/opt/shengtai/incoming/cloud-secrets.json').read_text())
try:
    query=urllib.parse.urlencode({'grant_type':'client_credential','appid':c['wechat_appid'],'secret':c['wechat_secret']})
    result=json.load(urllib.request.urlopen('https://api.weixin.qq.com/cgi-bin/token?'+query,timeout=15))
    print('WECHAT_SECRET_CHECK', 'OK' if result.get('access_token') else 'ERROR '+str(result.get('errcode')))
except Exception:
    print('WECHAT_SECRET_CHECK NETWORK_ERROR')
print('BACKUP_ENABLED')