package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"

	"coog/internal/api"
	"coog/internal/config"
	"coog/internal/library"
	"coog/internal/probe"
	"coog/internal/store"
)

func main() {
	log := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{Level: slog.LevelInfo}))
	slog.SetDefault(log)

	cfg := config.FromEnv()
	flag.StringVar(&cfg.Listen, "listen", cfg.Listen, "HTTP listen address (COOG_LISTEN)")
	flag.StringVar(&cfg.LibraryPath, "library", cfg.LibraryPath, "Library root (COOG_LIBRARY_PATH)")
	flag.StringVar(&cfg.DataPath, "data", cfg.DataPath, "Data directory for SQLite (COOG_DATA_PATH)")
	flag.StringVar(&cfg.FFmpeg, "ffmpeg", cfg.FFmpeg, "ffmpeg binary (COOG_FFMPEG)")
	flag.StringVar(&cfg.FFprobe, "ffprobe", cfg.FFprobe, "ffprobe binary (COOG_FFPROBE)")
	flag.StringVar(&cfg.AuthToken, "token", cfg.AuthToken, "Shared bearer token (COOG_AUTH_TOKEN)")
	flag.StringVar(&cfg.AdminDir, "admin", cfg.AdminDir, "Optional directory of built admin static files (COOG_ADMIN_DIR)")
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

	prober := probe.New(cfg.FFmpeg, cfg.FFprobe)
	scanner := library.NewScanner(db, prober, cfg.LibraryPath)

	srv := api.New(cfg, db, scanner, prober)

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	if err := srv.Run(ctx); err != nil {
		log.Error("server exited", "err", err)
		os.Exit(1)
	}
}
