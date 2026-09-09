# Coog architecture

```
 Google TV (Compose + Media3)     Svelte admin
        |                               |
        +----------- REST/WS -----------+
                        |
                   coog-api (Go)
                    |        \
              SQLite+fs    ffmpeg (probe / later remux|transcode)
                    |
              coog-worker (Phase 5)
                    |
            yt-dlp / RD / torrents
```

## Run modes

Both are first-class. The API **does not require Docker**.

- **Native:** `go run ./cmd/coog-api` or `coog-api` under systemd. FFmpeg on the host PATH.
- **Compose:** `deploy/docker-compose.yml` builds the same binary and mounts `COOG_LIBRARY_PATH`.

## Library layout (from linux-tv-interface)

`COOG_LIBRARY_PATH` defaults to `~/Videos`:

```
Videos/Movies/Title (Year)/Title (Year).mkv
Videos/Series/Show Name/Season 01/Show Name S01E02.mkv
```

Trailers directories, dotfiles, `*.incompatible*`, and files under 64 KiB are skipped.

## Playback

`POST /api/v1/playback/sessions` returns `{ method, url, mediaId, expectedDurationMs, bufferedMs }`.

Today `method` is `direct` (or a 409 if the client profile cannot play the codecs). Logs include `playback_method=direct|remux|transcode|progressive`.

Progressive play (Phase 5) must be a Media3-friendly growing stream — prefer live-growing HLS. Do not assume a raw growing MPEG-TS works like local mpv.

## Auth

v1: shared `COOG_AUTH_TOKEN` as `Authorization: Bearer …`. Empty token = open (dev). Multi-user comes later.

## See also

- [HANDOFF.md](HANDOFF.md) — product source of truth
- [API.md](API.md) — endpoint list
- `openapi/coog.yaml`
