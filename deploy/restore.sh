#!/usr/bin/env bash
# Restore an ELOKA backup made by deploy/backup.sh. DESTRUCTIVE: replaces the current
# database and documents. Needs the same deploy/.env (crypto keys) the backup was made with.
#   deploy/restore.sh <backup-dir> <STAMP>      e.g. deploy/restore.sh backups 20261016T020000Z
set -euo pipefail

cd "$(dirname "$0")"
DIR="$(cd "${1:?backup dir}" && pwd)" # absolute: it is mounted into a container below
STAMP="${2:?backup stamp, e.g. 20261016T020000Z}"
DB="$DIR/eloka-db-$STAMP.sql.gz"
FILES="$DIR/eloka-files-$STAMP.tar.gz"
COMPOSE=(docker compose -f docker-compose.yml --env-file "${ELOKA_ENV_FILE:-.env}")
[[ -n "${ELOKA_COMPOSE_PROJECT:-}" ]] && COMPOSE+=(-p "$ELOKA_COMPOSE_PROJECT")

( cd "$DIR" && shasum -a 256 -c "eloka-$STAMP.sha256" )

if [[ "${ELOKA_RESTORE_CONFIRM:-}" != "yes" ]]; then
	read -r -p "This replaces the live database and documents with $STAMP. Type 'restore' to continue: " answer
	[[ "$answer" == "restore" ]] || { echo "aborted"; exit 1; }
fi

echo "[restore] stopping traffic (edge, client, server)"
"${COMPOSE[@]}" stop edge client server

echo "[restore] database (recreated, so tables from newer migrations do not linger)"
"${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -e "DROP DATABASE IF EXISTS eloka; CREATE DATABASE eloka"'
gunzip -c "$DB" | "${COMPOSE[@]}" exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot'

echo "[restore] documents"
"${COMPOSE[@]}" run --rm --no-deps --entrypoint sh -v "$DIR:/restore:ro" server -c \
	'rm -rf /app/data/* && tar -xzf "/restore/eloka-files-'"$STAMP"'.tar.gz" -C /app'

echo "[restore] migrating (in case the backup is older than the code) and starting"
"${COMPOSE[@]}" up -d --wait
echo "[restore] done"
