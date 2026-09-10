package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"coog/internal/acquire"
	"coog/internal/config"
	"coog/internal/probe"
	"coog/internal/store"
)

func main() {
	log := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(log)

	cfg := config.FromEnv()
	flag.StringVar(&cfg.LibraryPath, "library", cfg.LibraryPath, "Library root (COOG_LIBRARY_PATH)")
	flag.StringVar(&cfg.DataPath, "data", cfg.DataPath, "Data directory for SQLite (COOG_DATA_PATH)")
	flag.StringVar(&cfg.FFmpeg, "ffmpeg", cfg.FFmpeg, "ffmpeg binary (COOG_FFMPEG)")
	flag.StringVar(&cfg.FFprobe, "ffprobe", cfg.FFprobe, "ffprobe binary (COOG_FFPROBE)")
	flag.StringVar(&cfg.YTDLP, "ytdlp", cfg.YTDLP, "yt-dlp binary (COOG_YTDLP)")
	flag.Parse()

	if err := os.MkdirAll(cfg.DataPath, 0o755); err != nil {
		log.Error("create data dir", "path", cfg.DataPath, "err", err)
		os.Exit(1)
	}

	db, err := store.Open(cfg.DBPath())
	if err != nil {
		log.Error("open sqlite", "path", cfg.DBPath(), "err", err)
		os.Exit(1)
	}
	defer db.Close()

	log.Info("coog-worker starting",
		"data", cfg.DataPath,
		"library", cfg.LibraryPath,
		"ytdlp", cfg.YTDLP,
		"ffmpeg", cfg.FFmpeg,
	)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	runner := acquire.New(cfg, db, probe.New(cfg.FFmpeg, cfg.FFprobe))
	if err := runner.Loop(ctx); err != nil {
		log.Error("worker exited", "err", err)
		os.Exit(1)
	}
}
