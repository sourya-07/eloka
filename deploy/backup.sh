#!/usr/bin/env bash
# Back up the ELOKA stack: MySQL (consistent dump, no downtime) + uploaded documents.
#   deploy/backup.sh [backup-dir]          default dir: deploy/backups, keeps 14 days
# Restore with deploy/restore.sh. The documents are encrypted with ELOKA_CRYPTO_FIELD_KEY:
# keep that key (and the rest of deploy/.env) in the secrets manager, NOT next to the backups.
set -euo pipefail

cd "$(dirname "$0")"
DIR="${1:-backups}"
KEEP_DAYS="${ELOKA_BACKUP_KEEP_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
COMPOSE=(docker compose -f docker-compose.yml --env-file "${ELOKA_ENV_FILE:-.env}")
[[ -n "${ELOKA_COMPOSE_PROJECT:-}" ]] && COMPOSE+=(-p "$ELOKA_COMPOSE_PROJECT")

mkdir -p "$DIR"
DIR="$(cd "$DIR" && pwd)"
umask 077 # backups hold personal data

echo "[backup] database → $DIR/eloka-db-$STAMP.sql.gz"
"${COMPOSE[@]}" exec -T mysql sh -c \
	'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --routines --triggers --events --set-gtid-purged=OFF --databases eloka' \
	| gzip -9 > "$DIR/eloka-db-$STAMP.sql.gz"

echo "[backup] documents → $DIR/eloka-files-$STAMP.tar.gz"
"${COMPOSE[@]}" cp server:/app/data - | gzip -9 > "$DIR/eloka-files-$STAMP.tar.gz"

( cd "$DIR" && shasum -a 256 "eloka-db-$STAMP.sql.gz" "eloka-files-$STAMP.tar.gz" > "eloka-$STAMP.sha256" )
gzip -t "$DIR/eloka-db-$STAMP.sql.gz" "$DIR/eloka-files-$STAMP.tar.gz"

find "$DIR" -name 'eloka-*' -type f -mtime "+$KEEP_DAYS" -delete
echo "[backup] done: $STAMP (copy $DIR off this host, e.g. to object storage)"
