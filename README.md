# Coog

Google TV client + Linux media server + Svelte admin. Jellyfin-style split: the TV app browses and plays; the server owns the library, auth, acquire jobs, and playback URLs.

Predecessor: [`linux-tv-interface`](https://github.com/) (Debian kiosk). That repo is a **feature reference**, not the runtime. Coog does not ship Qt, Sway, or embedded mpv on the TV.

**Play while downloading is a product requirement** (Phase 5). This tree currently implements Phases 0–2 and the start of Phase 3: native API, library scan, HTTP range streaming, direct-play sessions, admin, and a Compose for TV app with ExoPlayer.

## Layout

| Path | What |
|------|------|
| `server/` | Go module — `coog-api` (and placeholder `coog-worker`) |
| `admin/` | Vite + Svelte 5 ops UI |
| `client/` | Android TV / Google TV app (`tv.coog.app`) |
| `deploy/` | systemd units, `install-linux.sh`, optional Compose |
| `docs/` | Architecture, API, full handoff |
| `openapi/coog.yaml` | Endpoints as they land |

## Native quick start (no Docker)

Needs: Go 1.24+, FFmpeg/ffprobe on `PATH`.

```bash
# API
export COOG_LIBRARY_PATH="$HOME/Videos"   # Movies/ + Series/ layout
export COOG_DATA_PATH="$HOME/.local/share/coog"
# export COOG_AUTH_TOKEN="change-me"      # optional in v1
cd server
go run ./cmd/coog-api
# GET http://127.0.0.1:8090/health
```

Scan and stream:

```bash
curl -X POST http://127.0.0.1:8090/api/v1/library/rescan
curl http://127.0.0.1:8090/api/v1/library
# Play with VLC / mpv / curl Range:
# curl -H 'Range: bytes=0-1023' http://127.0.0.1:8090/api/v1/media/<id>/stream
```

Admin (proxies `/health` and `/api` to the API):

```bash
cd admin
npm install
npm run dev
# http://127.0.0.1:5173
```

Install as a user systemd service:

```bash
./deploy/install-linux.sh
systemctl --user enable --now coog-api
```

## Docker Compose (optional)

Docker is packaging, not a runtime dependency.

```bash
export COOG_LIBRARY_PATH="$HOME/Videos"
docker compose -f deploy/docker-compose.yml up --build
```

## Google TV client

Package id: `tv.coog.app`. minSdk **23** (Compose for TV is 21; Media3 HLS requires 23). Open `client/` in Android Studio or:

```bash
cd client
./gradlew :app:assembleDebug
```

On the emulator, the default server URL is `http://10.0.2.2:8090`. On a TCL / Google TV on LAN, set **Settings → Server URL** to `http://<host-lan-ip>:8090`. If `COOG_AUTH_TOKEN` is set, paste the same token there.

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `COOG_LISTEN` | `:8090` | HTTP bind |
| `COOG_LIBRARY_PATH` | `~/Videos` | Library root (`Movies/`, `Series/`) |
| `COOG_DATA_PATH` | `~/.local/share/coog` | SQLite + future job state |
| `COOG_FFMPEG` | `ffmpeg` | Binary on PATH or absolute |
| `COOG_FFPROBE` | `ffprobe` | Probe on ingest |
| `COOG_AUTH_TOKEN` | empty (open) | Shared bearer token for `/api/*` |
| `COOG_ADMIN_DIR` | empty | Serve a built admin SPA from this directory |

`GET /health` is always unauthenticated.

## Playback ladder

1. **direct** — HTTP Range on the original file (implemented).
2. **remux** — stream-copy to fMP4/HLS (stub; API returns a clear error).
3. **transcode** — last resort, cap later (documented as 2 concurrent).
4. **progressive** — in-progress downloads as growing HLS for Media3 (**Phase 5**, required).

## What is not done yet

- Acquire jobs (yt-dlp / Real-Debrid / torrents)
- Play-while-downloading on ExoPlayer
- Remux/transcode pipeline
- TMDB / posters / next-episode

See [docs/HANDOFF.md](docs/HANDOFF.md) for the full plan and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the condensed map.

License: MIT.
