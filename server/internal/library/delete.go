package library

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func WithinRoot(root, path string) bool {
	if strings.TrimSpace(root) == "" || strings.TrimSpace(path) == "" {
		return false
	}
	absRoot, err := filepath.Abs(root)
	if err != nil {
		return false
	}
	absPath, err := filepath.Abs(path)
	if err != nil {
		return false
	}
	absRoot = filepath.Clean(absRoot)
	absPath = filepath.Clean(absPath)
	rel, err := filepath.Rel(absRoot, absPath)
	if err != nil {
		return false
	}
	if rel == "." {
		return false
	}
	return rel != ".." && !strings.HasPrefix(rel, ".."+string(os.PathSeparator))
}

func stemSidecars(mediaPath string) []string {
	dir := filepath.Dir(mediaPath)
	stem := strings.TrimSuffix(filepath.Base(mediaPath), filepath.Ext(mediaPath))
	names := []string{
		stem + ".nfo",
		stem + ".coog.json",
		"." + stem + ".coog.json",
		stem + "-poster.jpg",
		stem + "-fanart.jpg",
		stem + "-logo.png",
		stem + "-thumb.jpg",
		stem + ".jpg",
		stem + "-trailer.mp4",
		stem + ".trailer.mp4",
		stem + ".srt",
		stem + ".en.srt",
	}
	out := make([]string, 0, len(names))
	for _, name := range names {
		out = append(out, filepath.Join(dir, name))
	}
	return out
}

var folderMetaNames = []string{
	"poster.jpg", "fanart.jpg", "logo.png", "clearlogo.png",
	"coog.json", "movie.nfo", "tvshow.nfo",
	"trailer.mp4", "Trailer.mp4", "official-trailer.mp4", "trailer-1.mp4",
}

func RemoveVideo(root, mediaPath, kind string) error {
	if !WithinRoot(root, mediaPath) {
		return fmt.Errorf("path outside library")
	}
	for _, p := range stemSidecars(mediaPath) {
		if WithinRoot(root, p) {
			_ = os.Remove(p)
		}
	}
	if err := os.Remove(mediaPath); err != nil && !os.IsNotExist(err) {
		return err
	}
	if kind == "episode" {
		return nil
	}
	dir := filepath.Dir(mediaPath)
	if !WithinRoot(root, dir) {
		return nil
	}
	if CountVideos(dir) > 0 {
		return nil
	}
	for _, name := range folderMetaNames {
		p := filepath.Join(dir, name)
		if WithinRoot(root, p) {
			_ = os.Remove(p)
		}
	}
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil
	}
	if len(entries) == 0 {
		_ = os.Remove(dir)
	}
	return nil
}

func RemoveTree(root, dir string) error {
	if !WithinRoot(root, dir) {
		return fmt.Errorf("path outside library")
	}
	return os.RemoveAll(dir)
}
