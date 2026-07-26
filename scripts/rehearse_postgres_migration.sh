#!/bin/bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
container_name="victoryfairy-migration-$RANDOM"
database_name="victoryfairy_rehearsal_$RANDOM"
database_user="vf_rehearsal"
database_password="vf_rehearsal_only"
server_port="${VF_REHEARSAL_SERVER_PORT:-18081}"
app_log="$(mktemp /tmp/victoryfairy-rehearsal.XXXXXX)"
app_pid=""

cleanup_app() {
  if [[ -n "$app_pid" ]] && kill -0 "$app_pid" 2>/dev/null; then
    kill -TERM "$app_pid"
    wait "$app_pid" || true
  fi
  app_pid=""
}

cleanup() {
  cleanup_app
  docker stop "$container_name" >/dev/null 2>&1 || true
  rm -f "$app_log"
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null || { echo "Docker is required." >&2; exit 1; }
command -v curl >/dev/null || { echo "curl is required." >&2; exit 1; }
if (exec 3<>"/dev/tcp/127.0.0.1/$server_port") 2>/dev/null; then
  exec 3>&-
  echo "Port $server_port is already in use." >&2
  exit 1
fi

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

jar_path="$repo_root/build/libs/VictoryFairySpringServer-1.0.0.jar"

start_app() {
  : >"$app_log"
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
  java -jar "$jar_path" >"$app_log" 2>&1 &
  app_pid=$!

  for _ in {1..60}; do
    curl -fsS "http://127.0.0.1:${server_port}/ready" >/dev/null 2>&1 && return
    if ! kill -0 "$app_pid" 2>/dev/null; then
      echo "Application exited before readiness." >&2
      tail -n 80 "$app_log" >&2
      exit 1
    fi
    sleep 1
  done
  echo "Application readiness timed out." >&2
  tail -n 80 "$app_log" >&2
  exit 1
}

echo "== First migration and startup =="
start_app
curl -fsS "http://127.0.0.1:${server_port}/health" >/dev/null
curl -fsS "http://127.0.0.1:${server_port}/ready" >/dev/null

applied_version="$(
  docker exec "$container_name" \
    psql -U "$database_user" -d "$database_name" -Atc \
    "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1"
)"
[[ "$applied_version" == "1" ]] || { echo "Expected Flyway version 1." >&2; exit 1; }

table_count="$(
  docker exec "$container_name" \
    psql -U "$database_user" -d "$database_name" -Atc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name <> 'flyway_schema_history'"
)"
[[ "$table_count" == "7" ]] || { echo "Expected 7 application tables." >&2; exit 1; }

cleanup_app

echo "== Idempotent restart and validation =="
start_app
curl -fsS "http://127.0.0.1:${server_port}/ready" >/dev/null
cleanup_app

echo "VictoryFairy PostgreSQL migration rehearsal passed."
