package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
)

const Version = "0.1.4"

type Config struct {
	Listen      string
	LibraryPath string
	DataPath    string
	FFmpeg      string
	FFprobe     string
	AuthToken   string
	AdminDir    string
	TMDBKey     string
	YTDLP       string
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
		TMDBKey:     firstNonEmpty(env("COOG_TMDB_API_KEY", os.Getenv("TMDB_API_KEY")), tmdbKeyFromDisk(home)),
		YTDLP:       env("COOG_YTDLP", "yt-dlp"),
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

func firstNonEmpty(values ...string) string {
	for _, v := range values {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

func tmdbKeyFromDisk(home string) string {
	paths := []string{
		filepath.Join(home, ".config", "coog", "tmdb.json"),
		filepath.Join(home, ".config", "tv-shell", "tmdb.json"),
	}
	for _, p := range paths {
		b, err := os.ReadFile(p)
		if err != nil {
			continue
		}
		var wrap struct {
			APIKey string `json:"apiKey"`
			Key    string `json:"key"`
		}
		if json.Unmarshal(b, &wrap) != nil {
			continue
		}
		if k := firstNonEmpty(wrap.APIKey, wrap.Key); k != "" {
			return k
		}
	}
	return ""
}
