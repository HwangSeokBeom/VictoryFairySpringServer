#!/bin/bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if [[ -z "$(git ls-files)" ]]; then
  echo "No tracked files to scan."
  exit 0
fi

patterns='BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|(jdbc:postgresql://[^[:space:]]+:[^[:space:]@]+@)|GROQ_API_KEY=[^[:space:]]{12,}|NAVER_CLIENT_SECRET=[^[:space:]]{12,}'

raw_matches="$(git grep -nEI "$patterns" -- . || true)"
filtered_matches="$(
  printf '%s\n' "$raw_matches" \
    | grep -vE 'replace-with-|rehearsal-only|example|placeholder' \
    | grep -v 'scripts/scan_sensitive_patterns.sh' \
    || true
)"

if [[ -n "$filtered_matches" ]]; then
  printf '%s\n' "$filtered_matches" | cut -d: -f1-2 | sort -u
  echo "Potential committed secret detected." >&2
  exit 1
fi

echo "Heuristic tracked-file secret scan passed."
