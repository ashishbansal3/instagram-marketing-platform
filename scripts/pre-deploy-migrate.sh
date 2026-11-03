#!/usr/bin/env bash
# Pre-deploy Flyway migrate — run ONCE against RDS before rolling ECS services.
# Expand/contract: only ship additive migrations in the same release as readers.
# Never run this concurrently from N Fargate tasks.
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
API_DIR="$ROOT/apps/api"

: "${SPRING_DATASOURCE_URL:?Set SPRING_DATASOURCE_URL (RDS JDBC URL)}"
: "${SPRING_DATASOURCE_USERNAME:?Set SPRING_DATASOURCE_USERNAME}"
: "${SPRING_DATASOURCE_PASSWORD:?Set SPRING_DATASOURCE_PASSWORD}"

export SPRING_FLYWAY_ENABLED=true

echo "==> RozPost pre-deploy Flyway (expand/contract-safe migrations only)"
echo "    datasource=${SPRING_DATASOURCE_URL%%\?*}"
echo "    migrations=apps/api/src/main/resources/db/migration"

cd "$API_DIR"

if [[ -x ./gradlew ]]; then
  # Prefer wrapper when present; fall back to system gradle.
  if ./gradlew -q help >/dev/null 2>&1; then
    ./gradlew flywayMigrate --no-daemon \
      -Dspring.datasource.url="$SPRING_DATASOURCE_URL" \
      -Dspring.datasource.username="$SPRING_DATASOURCE_USERNAME" \
      -Dspring.datasource.password="$SPRING_DATASOURCE_PASSWORD" \
      || {
        echo "flywayMigrate task missing or failed — falling back to bootRun one-shot migrate profile sketch" >&2
        echo "Ensure org.flywaydb:flyway-gradle-plugin is applied, or run a one-off ECS migrate task with SPRING_FLYWAY_ENABLED=true then exit." >&2
        exit 1
      }
  else
    echo "gradlew shim without Gradle distribution; use CI image with Gradle 8.11+ or a migrate ECS task." >&2
    exit 1
  fi
else
  echo "No apps/api/gradlew — use a one-off container:" >&2
  echo "  SPRING_FLYWAY_ENABLED=true java -jar app.jar  # then stop after migrate" >&2
  exit 1
fi

echo "==> Migrate complete. Deploy API → workers → remotion → web."
echo "    Set SPRING_FLYWAY_ENABLED=false on long-running API tasks."
