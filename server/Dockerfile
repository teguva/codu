# syntax=docker/dockerfile:1
FROM golang:1.24-bookworm AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /coog-api ./cmd/coog-api

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /coog-api /usr/local/bin/coog-api
EXPOSE 8090
ENV COOG_LISTEN=:8090 \
    COOG_LIBRARY_PATH=/library \
    COOG_DATA_PATH=/data
VOLUME ["/library", "/data"]
ENTRYPOINT ["/usr/local/bin/coog-api"]
