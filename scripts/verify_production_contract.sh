#!/bin/bash

set -euo pipefail

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_nonempty() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required"
}

require_value() {
  local name="$1"
  local expected="$2"
  [[ "${!name:-}" == "$expected" ]] || fail "$name must be $expected"
}

require_nonempty SPRING_DATASOURCE_URL
require_nonempty SPRING_DATASOURCE_USERNAME
require_nonempty SPRING_DATASOURCE_PASSWORD
require_nonempty PUBLIC_BASE_URL

[[ "$SPRING_DATASOURCE_URL" == jdbc:postgresql://* ]] \
  || fail "SPRING_DATASOURCE_URL must use PostgreSQL"
[[ "$PUBLIC_BASE_URL" == https://* ]] \
  || fail "PUBLIC_BASE_URL must use HTTPS"

require_value SPRING_PROFILES_ACTIVE production
require_value SERVER_PORT 8081
require_value FLYWAY_BASELINE_ON_MIGRATE false
require_value KBO_SOURCE_LABEL_MODE production
require_value KBO_SCRAPED_DEV_ENABLED false
require_value KBO_SCRAPED_DEV_SCHEDULER_ENABLED false
require_value COMMUNITY_ENABLED false
require_value PROFILE_IMAGE_UPLOAD_ENABLED false

echo "VictoryFairy production environment contract passed."
