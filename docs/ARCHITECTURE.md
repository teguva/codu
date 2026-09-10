# Coog architecture

```
 Google TV (Compose + Media3)     Svelte admin
        |                               |
        +----------- REST/WS -----------+
                        |
                   coog-api (Go)
                    |        \
              SQLite+fs    ffmpeg (probe / HLS packager / later remux)
                    |
              coog-worker
                    |
            yt-dlp / Real-Debrid (Torrentio)
```

## Run modes

Both are first-class. The API **does not require Docker**.

- **Native:** `go run ./cmd/coog-api` and `go run ./cmd/coog-worker`, or both under systemd. FFmpeg and yt-dlp on the host PATH.
- **Compose:** `deploy/docker-compose.yml` builds the same binary and mounts `COOG_LIBRARY_PATH`.

## Library layout (from linux-tv-interface)

`COOG_LIBRARY_PATH` defaults to `~/Videos`:

```
Videos/Movies/Title (Year)/Title (Year).mkv
Videos/Series/Show Name/Season 01/Show Name S01E02.mkv
```

Trailers directories, dotfiles, `*.incompatible*`, and files under 64 KiB are skipped.

## Playback

`POST /api/v1/playback/sessions` returns `{ method, url, mediaId, jobId, expectedDurationMs, bufferedMs }`.

`method` is `direct` for finished library files, or `progressive` for a ready yt-dlp job (growing HLS at `/api/v1/jobs/{id}/progressive/index.m3u8`). A 409 means remux/transcode is required and not implemented. Logs include `playback_method=direct|remux|transcode|progressive`.

Metadata: IMDB id only from `coog.json`, NFO, or `tt…` in the path — never from a title search. On a confirmed match, posters, fanart, and logos are stored beside the media file. Unmatched files keep the folder/file name and a local still; they do not get movie-database taglines or plots. `/poster`, `/backdrop`, and `/logo` prefer those sidecar images.

The Svelte admin is an ops console (Overview, Activity, Downloads, Library, Streaming) on the same API. `/ws` carries live job updates and activity events.

## Auth

v1: shared `COOG_AUTH_TOKEN` as `Authorization: Bearer …`. Empty token = open (dev). Multi-user comes later.

## See also

- [HANDOFF.md](HANDOFF.md) — product source of truth
- [API.md](API.md) — endpoint list
- `openapi/coog.yaml`
