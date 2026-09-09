package library

import (
	"context"
	"io/fs"
	"log/slog"
	"os"
	"path/filepath"
	"strings"
	"time"

	"coog/internal/probe"
	"coog/internal/store"
)

const minIndexBytes = 64 * 1024

type Scanner struct {
	store   *store.Store
	prober  *probe.Prober
	library string
}

func NewScanner(st *store.Store, prober *probe.Prober, libraryPath string) *Scanner {
	return &Scanner{store: st, prober: prober, library: libraryPath}
}

type ScanResult struct {
	Indexed int `json:"indexed"`
	Probed  int `json:"probed"`
	Removed int `json:"removed"`
	Skipped int `json:"skipped"`
}

func (s *Scanner) Scan(ctx context.Context) (ScanResult, error) {
	var result ScanResult
	root, err := filepath.Abs(s.library)
	if err != nil {
		return result, err
	}
	if err := os.MkdirAll(filepath.Join(root, "Movies"), 0o755); err != nil {
		slog.Warn("ensure Movies dir", "err", err)
	}
	if err := os.MkdirAll(filepath.Join(root, "Series"), 0o755); err != nil {
		slog.Warn("ensure Series dir", "err", err)
	}

	known, err := s.store.KnownByPath()
	if err != nil {
		return result, err
	}

	keep := make([]string, 0)
	err = filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		default:
		}
		name := d.Name()
		if d.IsDir() {
			if name == "." || name == ".." {
				return nil
			}
			if strings.HasPrefix(name, ".") || strings.EqualFold(name, "Trailers") {
				return fs.SkipDir
			}
			return nil
		}
		if strings.HasPrefix(name, ".") || strings.Contains(strings.ToLower(name), ".incompatible") {
			result.Skipped++
			return nil
		}
		if !IsVideo(path) {
			return nil
		}
		info, err := d.Info()
		if err != nil {
			result.Skipped++
			return nil
		}
		if info.Size() < minIndexBytes {
			result.Skipped++
			return nil
		}
		rel, err := filepath.Rel(root, path)
		if err != nil {
			return nil
		}
		parsed := ParseRelative(rel)
		id := MediaID(rel)
		item := store.MediaItem{
			ID:           id,
			Kind:         parsed.Kind,
			Title:        parsed.Title,
			Year:         parsed.Year,
			Season:       parsed.Season,
			Episode:      parsed.Episode,
			ShowTitle:    parsed.ShowTitle,
			Path:         path,
			RelativePath: filepath.ToSlash(rel),
			SizeBytes:    info.Size(),
			MtimeUnix:    info.ModTime().Unix(),
			ContentType:  parsed.ContentType,
			UpdatedAt:    time.Now().Unix(),
		}
		if prev, ok := known[path]; ok && prev.SizeBytes == item.SizeBytes && prev.MtimeUnix == item.MtimeUnix && len(prev.Probe) > 2 {
			item.DurationMs = prev.DurationMs
			item.Probe = prev.Probe
			item.CodecVideo = prev.CodecVideo
			item.CodecAudio = prev.CodecAudio
			item.Width = prev.Width
			item.Height = prev.Height
			item.HDR = prev.HDR
		} else {
			infoProbe, err := s.prober.Probe(ctx, path)
			if err != nil {
				slog.Warn("ffprobe failed", "path", path, "err", err)
			} else {
				item.DurationMs = infoProbe.DurationMs
				item.Probe = infoProbe.Raw
				item.CodecVideo = infoProbe.VideoCodec
				item.CodecAudio = infoProbe.AudioCodec
				item.Width = infoProbe.Width
				item.Height = infoProbe.Height
				item.HDR = infoProbe.HDR
				result.Probed++
			}
		}
		if err := s.store.UpsertMedia(item); err != nil {
			slog.Error("upsert media", "path", path, "err", err)
			return nil
		}
		keep = append(keep, id)
		result.Indexed++
		return nil
	})
	if err != nil {
		return result, err
	}
	before, _ := s.store.Stats()
	if err := s.store.DeleteMissing(keep); err != nil {
		return result, err
	}
	after, _ := s.store.Stats()
	if before > after {
		result.Removed = before - after
	}
	slog.Info("library scan complete", "indexed", result.Indexed, "probed", result.Probed, "removed", result.Removed, "root", root)
	return result, nil
}

func (s *Scanner) LibraryRoot() string {
	return s.library
}
