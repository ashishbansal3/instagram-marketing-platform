#!/usr/bin/env bash
# Create a local .env from .env.example without committing secrets.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXAMPLE="$ROOT/.env.example"
TARGET="$ROOT/.env"

if [[ ! -f "$EXAMPLE" ]]; then
  echo "Missing .env.example" >&2
  exit 1
fi

if [[ -f "$TARGET" ]]; then
  echo ".env already exists — leave untouched"
  exit 0
fi

cp "$EXAMPLE" "$TARGET"
echo "Created .env from .env.example (gitignored). Edit values for your machine."
echo "Compose also works without .env (built-in defaults; META_MODE=mock)."
