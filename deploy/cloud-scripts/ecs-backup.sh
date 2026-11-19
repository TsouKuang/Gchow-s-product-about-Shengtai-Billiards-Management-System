#!/bin/bash
set -euo pipefail
umask 077
directory=/opt/shengtai/backups
mkdir -p "$directory"
stamp=$(date +%Y%m%d-%H%M%S)
target="$directory/billiard-$stamp.sql.gz"
temporary=$(mktemp "$directory/.billiard-$stamp.XXXXXX")
trap 'rm -f "$temporary"' EXIT
mysqldump --protocol=socket --single-transaction --routines --triggers --events --no-tablespaces billiard_db | gzip > "$temporary"
gzip -t "$temporary"
mv "$temporary" "$target"
echo "Backup saved: $target"
