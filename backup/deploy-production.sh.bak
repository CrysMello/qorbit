#!/usr/bin/env bash
set -euo pipefail

RELEASE_DIR="${1:-}"
DEPLOY_ROOT="${2:-/opt/qorbit}"
APP_NAME="${3:-qorbit-engine}"
APP_PORT="${4:-8080}"
APP_SERVICE="${5:-qorbit}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
APP_URL="${APP_URL:-https://qorbit.duckdns.org}"
SESSION_COOKIE_SECURE="${SESSION_COOKIE_SECURE:-true}"

if [[ -z "$RELEASE_DIR" ]]; then
  echo "Usage: $0 <release_dir> [deploy_root] [app_name] [app_port] [service_name]" >&2
  exit 1
fi

mkdir -p "$DEPLOY_ROOT/releases" "$DEPLOY_ROOT/current" "$DEPLOY_ROOT/logs"

JAR_FILE=""
for candidate in "$RELEASE_DIR"/*.jar; do
  if [[ -f "$candidate" ]]; then
    JAR_FILE="$candidate"
    break
  fi
done

if [[ -z "$JAR_FILE" ]]; then
  echo "No executable JAR found in $RELEASE_DIR" >&2
  exit 1
fi

TARGET_JAR="$DEPLOY_ROOT/current/${APP_NAME}.jar"
ACTIVE_JAR="$DEPLOY_ROOT/qorbit.jar"
cp "$JAR_FILE" "$TARGET_JAR"
cp "$JAR_FILE" "$ACTIVE_JAR"
chmod +x "$TARGET_JAR" "$ACTIVE_JAR"

if command -v systemctl >/dev/null 2>&1 && systemctl list-units --type=service --all 2>/dev/null | awk '{print $1}' | grep -Fxq "${APP_SERVICE}.service"; then
  if command -v sudo >/dev/null 2>&1; then
    sudo systemctl restart "$APP_SERVICE"
  else
    systemctl restart "$APP_SERVICE"
  fi
else
  pkill -f "${APP_NAME}.jar" >/dev/null 2>&1 || true
  nohup env \
    SPRING_PROFILES_ACTIVE="$SPRING_PROFILES_ACTIVE" \
    APP_URL="$APP_URL" \
    SESSION_COOKIE_SECURE="$SESSION_COOKIE_SECURE" \
    java -jar "$TARGET_JAR" --server.port="$APP_PORT" > "$DEPLOY_ROOT/logs/${APP_NAME}.log" 2>&1 &
fi

echo "Deploy completed successfully. Application running from $TARGET_JAR"
