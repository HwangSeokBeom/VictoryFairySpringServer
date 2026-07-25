#!/bin/bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/recovery.mv.db" >&2
  exit 1
fi

source_file="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
[[ -f "$source_file" ]] || { echo "H2 source file not found." >&2; exit 1; }
[[ "$source_file" == *.mv.db ]] || { echo "Source must be an H2 .mv.db file." >&2; exit 1; }

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container_name="victoryfairy-h2-migration-$RANDOM"
database_name="victoryfairy_h2_rehearsal_$RANDOM"
database_user="vf_rehearsal"
database_password="vf_rehearsal_only"
server_port="${VF_H2_REHEARSAL_SERVER_PORT:-18082}"
app_log="$(mktemp /tmp/victoryfairy-h2-rehearsal.XXXXXX)"
source_copy_dir="$(mktemp -d /tmp/victoryfairy-h2-source.XXXXXX)"
source_copy="$source_copy_dir/recovery.mv.db"
app_pid=""

cleanup() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill -TERM "$app_pid"
    wait "$app_pid" || true
  fi
  docker stop "$container_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

cp "$source_file" "$source_copy"
cd "$repo_root"
./gradlew bootJar

docker run --rm -d \
  --name "$container_name" \
  -e POSTGRES_USER="$database_user" \
  -e POSTGRES_PASSWORD="$database_password" \
  -e POSTGRES_DB="$database_name" \
  -p 127.0.0.1::5432 \
  postgres:16 >/dev/null
database_port="$(docker port "$container_name" 5432/tcp | awk -F: '{print $NF}')"

for _ in {1..45}; do
  docker exec "$container_name" pg_isready -U "$database_user" -d "$database_name" >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$container_name" pg_isready -U "$database_user" -d "$database_name" >/dev/null

SPRING_PROFILES_ACTIVE=production \
SERVER_PORT="$server_port" \
PUBLIC_BASE_URL=https://victoryfairy.invalid \
SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${database_port}/${database_name}" \
SPRING_DATASOURCE_USERNAME="$database_user" \
SPRING_DATASOURCE_PASSWORD="$database_password" \
FLYWAY_BASELINE_ON_MIGRATE=false \
KBO_SOURCE_LABEL_MODE=production \
KBO_REFRESH_ENABLED=false \
KBO_SCRAPED_DEV_ENABLED=false \
KBO_SCRAPED_DEV_SCHEDULER_ENABLED=false \
COMMUNITY_ENABLED=false \
PROFILE_IMAGE_UPLOAD_ENABLED=false \
java -jar build/libs/VictoryFairySpringServer-1.0.0.jar >"$app_log" 2>&1 &
app_pid=$!

for _ in {1..60}; do
  curl -fsS "http://127.0.0.1:${server_port}/ready" >/dev/null 2>&1 && break
  if ! kill -0 "$app_pid" 2>/dev/null; then
    echo "Application exited before schema readiness." >&2
    tail -n 80 "$app_log" >&2
    exit 1
  fi
  sleep 1
done
curl -fsS "http://127.0.0.1:${server_port}/ready" >/dev/null
kill -TERM "$app_pid"
wait "$app_pid" || true
app_pid=""

VF_H2_SOURCE_PATH="$source_copy" \
VF_POSTGRES_URL="jdbc:postgresql://127.0.0.1:${database_port}/${database_name}" \
VF_POSTGRES_USER="$database_user" \
VF_POSTGRES_PASSWORD="$database_password" \
VF_DATA_MIGRATION_ACK=EMPTY_LOCAL_REHEARSAL \
./gradlew migrateH2RecoveryToPostgres

echo "VictoryFairy H2-to-PostgreSQL recovery rehearsal passed."
