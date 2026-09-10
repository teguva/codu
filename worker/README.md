# Coog worker sidecar

`coog-worker` claims queued yt-dlp jobs from the same SQLite database as `coog-api`, downloads out of process, and writes growing HLS under `$COOG_DATA_PATH/jobs/{id}/hls/`.

```bash
export COOG_LIBRARY_PATH="$HOME/Videos"
export COOG_DATA_PATH="$HOME/.local/share/coog"
cd server
go run ./cmd/coog-worker
```

Needs `yt-dlp` and `ffmpeg` on `PATH`. Real-Debrid and torrents are not implemented yet.
