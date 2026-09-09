# Coog API (v1)

Base: `/api/v1`  
Auth: `Authorization: Bearer <COOG_AUTH_TOKEN>` when the token is set. `GET /health` is public.

Machine-readable: [`../openapi/coog.yaml`](../openapi/coog.yaml)

| Method | Path | Status |
|--------|------|--------|
| GET | `/health` | implemented |
| GET | `/api/v1/library` | implemented |
| GET | `/api/v1/library/{id}` | implemented |
| POST | `/api/v1/library/rescan` | implemented |
| GET | `/api/v1/media/{id}/stream` | implemented (HTTP Range) |
| POST | `/api/v1/playback/sessions` | implemented (direct only) |
| GET | `/api/v1/jobs` | empty list stub |
| GET | `/api/v1/server/stats` | implemented |
| POST | `/api/v1/jobs` | Phase 5 |
| GET | `/api/v1/jobs/{id}` | Phase 5 |
| GET | `/api/v1/jobs/{id}/progressive/*` | Phase 5 |
| WS | `/ws` | Phase 5 |

## Playback session

Request:

```json
{
  "mediaId": "…",
  "jobId": null,
  "clientCapabilities": {
    "videoCodecs": ["h264", "hevc", "vp9", "av1"],
    "audioCodecs": ["aac", "ac3", "eac3"],
    "containers": ["mp4", "mkv"],
    "hdr": ["hdr10", "dolbyvision"]
  }
}
```

Success:

```json
{
  "id": "…",
  "method": "direct",
  "url": "http://host:8090/api/v1/media/{id}/stream",
  "mediaId": "…",
  "expectedDurationMs": 7200000,
  "bufferedMs": 7200000
}
```

If the profile cannot direct-play, the server returns **409** with `method: transcode` and an error string. Remux/transcode are not implemented yet.

Concurrent transcode cap (Phase 6, not enforced): **2**.
