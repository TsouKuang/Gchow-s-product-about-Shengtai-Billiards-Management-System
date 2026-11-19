#!/bin/bash
# Run on the existing /opt/shengtai server after uploading the JAR and web tarball.
set -euo pipefail
jar=${1:?Usage: update-server.sh JAR WEB_TAR}
web=${2:?Usage: update-server.sh JAR WEB_TAR}
test -s "$jar"
test -s "$web"
cd /opt/shengtai
/usr/local/sbin/shengtai-backup
backup="backups/server-before-update-$(date +%Y%m%d-%H%M%S).jar"
cp -p backend/server.jar "$backup"
staging=$(mktemp -d /opt/shengtai/incoming/web-update-XXXXXX)
tar -xzf "$web" -C "$staging"
test -f "$staging/index.html"
cp -r "$staging/assets/." web/assets/
systemctl stop shengtai
cp "$jar" backend/server.jar
chown shengtai:shengtai backend/server.jar
systemctl start shengtai
for i in $(seq 1 50); do
  if curl -fsS http://127.0.0.1:8080/api/brands > /dev/null 2>&1; then
    cp "$staging/index.html" web/index.html
    curl -fsS http://127.0.0.1:8080/api/health
    exit 0
  fi
  sleep 1
done
cp "$backup" backend/server.jar
chown shengtai:shengtai backend/server.jar
systemctl restart shengtai
echo 'Update failed; restored previous backend.' >&2
exit 1
