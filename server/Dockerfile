# syntax=docker/dockerfile:1
FROM golang:1.24-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /coog-api ./cmd/coog-api \
 && CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /coog-worker ./cmd/coog-worker

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates python3 python3-pip \
    && pip3 install --break-system-packages --no-cache-dir yt-dlp \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /coog-api /usr/local/bin/coog-api
COPY --from=build /coog-worker /usr/local/bin/coog-worker
EXPOSE 8090
ENV COOG_LISTEN=:8090 \
    COOG_LIBRARY_PATH=/library \
    COOG_DATA_PATH=/data \
    COOG_YTDLP=yt-dlp
VOLUME ["/library", "/data"]
ENTRYPOINT ["/usr/local/bin/coog-api"]
