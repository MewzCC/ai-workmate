#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_ROOT="${DEPLOY_ROOT:-$PACKAGE_DIR}"
MAIN_ZIP="${MAIN_ZIP:-$PACKAGE_DIR/fronted-main.zip}"
OA_ZIP="${OA_ZIP:-$PACKAGE_DIR/fonted-oa.zip}"

case "$MAIN_ZIP" in
  /*) ;;
  *) MAIN_ZIP="$PACKAGE_DIR/$MAIN_ZIP" ;;
esac

case "$OA_ZIP" in
  /*) ;;
  *) OA_ZIP="$PACKAGE_DIR/$OA_ZIP" ;;
esac

extract_zip() {
  local zip_file="$1"
  local target_dir="$2"

  mkdir -p "$target_dir"
  if command -v unzip >/dev/null 2>&1; then
    unzip -oq "$zip_file" -d "$target_dir"
  elif command -v python3 >/dev/null 2>&1; then
    python3 - "$zip_file" "$target_dir" <<'PY'
import sys, zipfile
zip_path, target = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(zip_path) as zf:
    zf.extractall(target)
PY
  else
    echo "ERROR: unzip or python3 is required to extract $zip_file" >&2
    exit 1
  fi
}

deploy_app() {
  local app_name="$1"
  local zip_file="$2"
  local port="$3"
  local app_dir="$DEPLOY_ROOT/$app_name"
  local release_dir="$DEPLOY_ROOT/.release-$app_name"
  local backup_dir="$DEPLOY_ROOT/.backup-$app_name"

  if [ ! -f "$zip_file" ]; then
    echo "ERROR: package not found: $zip_file" >&2
    exit 1
  fi

  echo "==> Deploy $app_name from $zip_file"
  rm -rf "$release_dir"
  extract_zip "$zip_file" "$release_dir"

  if [ ! -f "$release_dir/server.js" ]; then
    echo "ERROR: $zip_file is not a standalone package, missing server.js" >&2
    exit 1
  fi

  chmod +x "$release_dir/start-pm2.sh" 2>/dev/null || true
  mkdir -p "$DEPLOY_ROOT"

  rm -rf "$backup_dir"
  if [ -d "$app_dir" ]; then
    mv "$app_dir" "$backup_dir"
  fi
  mv "$release_dir" "$app_dir"

  if [ -f "$app_dir/start-pm2.sh" ]; then
    (cd "$app_dir" && bash ./start-pm2.sh)
  else
    pm2 delete "$app_name" 2>/dev/null || true
    (cd "$app_dir" && PORT="$port" HOSTNAME=0.0.0.0 NODE_ENV=production pm2 start ./server.js --name "$app_name" --update-env)
    pm2 save
  fi

  sleep 2
  echo "==> Check $app_name port $port"
  if command -v ss >/dev/null 2>&1; then
    ss -lntp | grep ":$port " || true
  elif command -v netstat >/dev/null 2>&1; then
    netstat -lntp 2>/dev/null | grep ":$port " || true
  fi

  pm2 describe "$app_name" | grep -E "status|script path|exec cwd|node.js version|unstable restarts" || true

  if command -v curl >/dev/null 2>&1; then
    if [ "$app_name" = "fonted-oa" ]; then
      if ! curl -fsS -I "http://127.0.0.1:$port/oa" >/dev/null; then
        echo "ERROR: local health check failed for $app_name on http://127.0.0.1:$port/oa" >&2
        pm2 logs "$app_name" --lines 80 --nostream || true
        [ -f "$app_dir/pm2-error.log" ] && tail -n 80 "$app_dir/pm2-error.log" || true
        exit 1
      fi
    else
      if ! curl -fsS -I "http://127.0.0.1:$port/" >/dev/null; then
        echo "ERROR: local health check failed for $app_name on http://127.0.0.1:$port/" >&2
        pm2 logs "$app_name" --lines 80 --nostream || true
        [ -f "$app_dir/pm2-error.log" ] && tail -n 80 "$app_dir/pm2-error.log" || true
        exit 1
      fi
    fi
  fi
}

mkdir -p "$DEPLOY_ROOT"
deploy_app "fronted-main" "$MAIN_ZIP" "3000"
deploy_app "fonted-oa" "$OA_ZIP" "3001"

echo "==> Deployment completed"
echo "fronted-main: http://127.0.0.1:3000/"
echo "fonted-oa:    http://127.0.0.1:3001/oa"
pm2 status
