package config

import (
	"os"
	"path/filepath"
	"strings"
)

const Version = "0.1.0"

type Config struct {
	Listen      string
	LibraryPath string
	DataPath    string
	FFmpeg      string
	FFprobe     string
	AuthToken   string
	AdminDir    string
}

func FromEnv() Config {
	home, _ := os.UserHomeDir()
	if home == "" {
		home = "."
	}
	return Config{
		Listen:      env("COOG_LISTEN", ":8090"),
		LibraryPath: env("COOG_LIBRARY_PATH", filepath.Join(home, "Videos")),
		DataPath:    env("COOG_DATA_PATH", filepath.Join(home, ".local", "share", "coog")),
		FFmpeg:      env("COOG_FFMPEG", "ffmpeg"),
		FFprobe:     env("COOG_FFPROBE", "ffprobe"),
		AuthToken:   os.Getenv("COOG_AUTH_TOKEN"),
		AdminDir:    os.Getenv("COOG_ADMIN_DIR"),
	}
}

func (c Config) DBPath() string {
	return filepath.Join(c.DataPath, "coog.db")
}

func env(key, fallback string) string {
	if v := strings.TrimSpace(os.Getenv(key)); v != "" {
		return v
	}
	return fallback
}
