# Phase 5 sidecar (yt-dlp / torrents / Real-Debrid). Not used in Phases 0–4.
FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*
CMD ["sleep", "infinity"]
