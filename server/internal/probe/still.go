package probe

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"
)

func SidecarPoster(mediaPath string) string {
	dir := filepath.Dir(mediaPath)
	stem := strings.TrimSuffix(filepath.Base(mediaPath), filepath.Ext(mediaPath))
	names := []string{
		stem + "-poster.jpg", stem + "-poster.png", stem + "-poster.webp",
		"poster.jpg", "poster.png", "poster.webp",
		"cover.jpg", "cover.png", "folder.jpg", "folder.png",
		stem + ".jpg", stem + ".png",
	}
	return firstExisting(dir, names)
}

func SidecarBackdrop(mediaPath string) string {
	dir := filepath.Dir(mediaPath)
	stem := strings.TrimSuffix(filepath.Base(mediaPath), filepath.Ext(mediaPath))
	names := []string{
		stem + "-backdrop.jpg", stem + "-backdrop.png", stem + "-fanart.jpg",
		"fanart.jpg", "fanart.png", "backdrop.jpg", "backdrop.png",
		"background.jpg", "background.png", "banner.jpg",
	}
	return firstExisting(dir, names)
}

func firstExisting(dir string, names []string) string {
	for _, name := range names {
		p := filepath.Join(dir, name)
		if st, err := os.Stat(p); err == nil && st.Size() > 32 {
			return p
		}
	}
	return ""
}

func SidecarTrailer(mediaPath string) string {
	dir := filepath.Dir(mediaPath)
	stem := strings.TrimSuffix(filepath.Base(mediaPath), filepath.Ext(mediaPath))
	names := []string{
		stem + "-trailer.mp4", stem + ".trailer.mp4",
		"trailer.mp4", "trailer.mkv", "trailer.webm", "Trailer.mp4",
		"trailer-1.mp4", "official-trailer.mp4",
	}
	return firstExistingMin(dir, names, 64*1024)
}

func LibraryTrailer(libraryRoot, imdb string) string {
	if imdb == "" || libraryRoot == "" {
		return ""
	}
	dirs := []string{
		filepath.Join(libraryRoot, "Movies", "Trailers"),
		filepath.Join(libraryRoot, "Trailers"),
		filepath.Join(libraryRoot, "Series", "Trailers"),
	}
	for _, dir := range dirs {
		matches, _ := filepath.Glob(filepath.Join(dir, "movie_"+imdb+"*"))
		for _, p := range matches {
			if st, err := os.Stat(p); err == nil && st.Size() > 64*1024 {
				return p
			}
		}
		matches, _ = filepath.Glob(filepath.Join(dir, "*"+imdb+"*"))
		for _, p := range matches {
			ext := strings.ToLower(filepath.Ext(p))
			if ext != ".mp4" && ext != ".mkv" && ext != ".webm" {
				continue
			}
			if st, err := os.Stat(p); err == nil && st.Size() > 64*1024 {
				return p
			}
		}
	}
	return ""
}

func firstExistingMin(dir string, names []string, min int64) string {
	for _, name := range names {
		p := filepath.Join(dir, name)
		if st, err := os.Stat(p); err == nil && st.Size() > min {
			return p
		}
	}
	return ""
}

func (p *Prober) ExtractStill(ctx context.Context, src, dest string, durationMs int64) error {
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	seek := 8.0
	if durationMs > 2000 {
		seek = float64(durationMs) / 1000.0 * 0.12
		if seek > 40 {
			seek = 40
		}
		if seek < 2 {
			seek = 2
		}
	}
	stillCtx, cancelStill := context.WithTimeout(ctx, 25*time.Second)
	defer cancelStill()
	return p.runFFmpeg(stillCtx, dest,
		"-y", "-ss", fmt.Sprintf("%.1f", seek), "-i", src,
		"-frames:v", "1", "-q:v", "4",
		"-vf", "scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080",
		dest,
	)
}

func (p *Prober) MaterializeImage(ctx context.Context, src, dest string) error {
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	ext := strings.ToLower(filepath.Ext(src))
	if ext == ".jpg" || ext == ".jpeg" {
		in, err := os.ReadFile(src)
		if err != nil {
			return err
		}
		return os.WriteFile(dest, in, 0o644)
	}
	ctx, cancel := context.WithTimeout(ctx, 15*time.Second)
	defer cancel()
	return p.runFFmpeg(ctx, dest, "-y", "-i", src, "-frames:v", "1", "-q:v", "4", dest)
}

func (p *Prober) runFFmpeg(ctx context.Context, dest string, args ...string) error {
	cmd := exec.CommandContext(ctx, p.ffmpeg, args...)
	out, err := cmd.CombinedOutput()
	if err != nil {
		return fmt.Errorf("ffmpeg still: %w (%s)", err, strings.TrimSpace(string(out)))
	}
	if st, err := os.Stat(dest); err != nil || st.Size() < 32 {
		return fmt.Errorf("ffmpeg still: empty output")
	}
	return nil
}
