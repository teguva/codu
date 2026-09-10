#!/usr/bin/env bash
# Install coog-api (and optional coog-worker) plus systemd units.
#   ./deploy/install-linux.sh          # ~/.local + systemd --user
#   ./deploy/install-linux.sh --system # /usr/local + systemd (needs write access)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="user"
for arg in "$@"; do
  case "$arg" in
    --system) MODE="system" ;;
    --user) MODE="user" ;;
    *) echo "unknown arg: $arg" >&2; exit 1 ;;
  esac
done

if [[ "$MODE" == "system" ]]; then
  PREFIX="${PREFIX:-/usr/local}"
  UNIT_DIR="${UNIT_DIR:-/etc/systemd/system}"
  ENV_DIR="${ENV_DIR:-/etc/coog}"
  API_UNIT="$ROOT/deploy/systemd/coog-api.service"
  WORKER_UNIT="$ROOT/deploy/systemd/coog-worker.service"
else
  PREFIX="${PREFIX:-$HOME/.local}"
  UNIT_DIR="${UNIT_DIR:-$HOME/.config/systemd/user}"
  ENV_DIR="${ENV_DIR:-$HOME/.config/coog}"
  API_UNIT="$ROOT/deploy/systemd/coog-api.user.service"
  WORKER_UNIT="$ROOT/deploy/systemd/coog-worker.user.service"
fi

export PATH="${HOME}/.local/go/bin:/usr/local/go/bin:${PATH}"
if ! command -v go >/dev/null 2>&1; then
  echo "go is required on PATH to build coog-api" >&2
  exit 1
fi

mkdir -p "$PREFIX/bin" "$UNIT_DIR" "$ENV_DIR"
( cd "$ROOT/server" && go build -trimpath -ldflags="-s -w" -o "$PREFIX/bin/coog-api" ./cmd/coog-api )
( cd "$ROOT/server" && go build -trimpath -ldflags="-s -w" -o "$PREFIX/bin/coog-worker" ./cmd/coog-worker )

install -m 0644 "$API_UNIT" "$UNIT_DIR/coog-api.service"
install -m 0644 "$WORKER_UNIT" "$UNIT_DIR/coog-worker.service"
if [[ ! -f "$ENV_DIR/coog.env" ]]; then
  sed "s|/home/REPLACE|$HOME|g" "$ROOT/deploy/coog.env.example" > "$ENV_DIR/coog.env"
fi

if [[ "$MODE" == "system" ]]; then
  systemctl daemon-reload
  echo "Installed $PREFIX/bin/coog-api and coog-worker. Enable with: sudo systemctl enable --now coog-api coog-worker"
else
  systemctl --user daemon-reload
  echo "Installed $PREFIX/bin/coog-api and coog-worker. Enable with: systemctl --user enable --now coog-api coog-worker"
fi
