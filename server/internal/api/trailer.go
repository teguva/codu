package api

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"os"
	"strings"

	"coog/internal/acquire"
	"coog/internal/events"
	"coog/internal/library"
	"coog/internal/meta"
	"coog/internal/probe"
	"coog/internal/store"
)

func catalogTrailerRef(id string) (imdb, kind string) {
	if !strings.HasPrefix(id, "catalog:") {
		return "", ""
	}
	rest := strings.TrimPrefix(id, "catalog:")
	parts := strings.Split(rest, ":")
	if len(parts) == 0 {
		return "", ""
	}
	imdb = strings.TrimSpace(parts[0])
	if !strings.HasPrefix(imdb, "tt") {
		return "", ""
	}
	if len(parts) >= 3 {
		return imdb, "series"
	}
	return imdb, "movie"
}

func (s *Server) resolveTrailerMedia(ctx context.Context, id string) (item store.MediaItem, imdb, kind string, hasFile bool) {
	item, err := s.store.GetMedia(id)
	if err == nil {
		info, ok := s.meta.Peek(item.ID)
		if !ok {
			info = s.meta.Ensure(ctx, item)
		}
		imdb = strings.TrimSpace(info.ImdbID)
		if imdb == "" {
			imdb = meta.FindIMDB(item.Path, item.Title, item.Year)
		}
		return item, imdb, item.Kind, true
	}
	imdb, kind = catalogTrailerRef(id)
	if imdb == "" {
		return store.MediaItem{}, "", "", false
	}
	if local, ok := s.findLocalByIMDB(imdb, kind); ok {
		return local, imdb, local.Kind, true
	}
	if kind == "movie" {
		if local, ok := s.findLocalByIMDB(imdb, "series"); ok {
			return local, imdb, local.Kind, true
		}
	}
	return store.MediaItem{}, imdb, kind, false
}

func (s *Server) handleTrailer(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	item, imdb, kind, hasFile := s.resolveTrailerMedia(r.Context(), id)
	path := ""
	if hasFile {
		path = probe.SidecarTrailer(item.Path)
		if path == "" && imdb != "" {
			path = probe.LibraryTrailer(s.cfg.LibraryPath, imdb)
		}
	} else if imdb != "" {
		path = probe.LibraryTrailer(s.cfg.LibraryPath, imdb)
	}
	if path != "" {
		serveTrailerFile(w, r, path)
		return
	}
	pageURL, err := s.meta.OfficialTrailer(r.Context(), kind, imdb, 0)
	if err != nil || pageURL == "" {
		writeError(w, http.StatusNotFound, "no trailer")
		return
	}
	if r.Method == http.MethodHead {
		w.Header().Set("Content-Type", "video/mp4")
		w.WriteHeader(http.StatusOK)
		return
	}
	stdout, wait, err := acquire.Pipe(r.Context(), s.cfg.YTDLP, pageURL)
	if err != nil {
		slog.Debug("trailer ytdlp", "id", id, "err", err)
		writeError(w, http.StatusNotFound, "no trailer")
		return
	}
	defer stdout.Close()
	w.Header().Set("Content-Type", "video/mp4")
	w.Header().Set("Cache-Control", "no-store")
	w.WriteHeader(http.StatusOK)
	_, _ = io.Copy(w, stdout)
	if err := wait(); err != nil {
		slog.Debug("trailer ytdlp wait", "id", id, "err", err)
	}
}

func serveTrailerFile(w http.ResponseWriter, r *http.Request, path string) {
	f, err := os.Open(path)
	if err != nil {
		writeError(w, http.StatusNotFound, "trailer missing")
		return
	}
	defer f.Close()
	stat, err := f.Stat()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	w.Header().Set("Content-Type", library.ContentType(path))
	w.Header().Set("Accept-Ranges", "bytes")
	clampRangeForExoPlayer(r, stat.Size())
	http.ServeContent(w, r, stat.Name(), stat.ModTime(), f)
}

func (s *Server) handleLibraryDelete(w http.ResponseWriter, r *http.Request) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if !library.WithinRoot(s.cfg.LibraryPath, item.Path) {
		writeError(w, http.StatusForbidden, "path outside library")
		return
	}
	scope := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("scope")))
	removed := []string{item.ID}
	if item.Kind == "episode" && scope == "series" {
		showDir := library.ArtDir(item.Path)
		if !library.WithinRoot(s.cfg.LibraryPath, showDir) {
			writeError(w, http.StatusForbidden, "path outside library")
			return
		}
		items, err := s.store.ListMedia()
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		removed = nil
		for _, it := range items {
			if it.ID == item.ID || library.WithinRoot(showDir, it.Path) {
				removed = append(removed, it.ID)
			}
		}
		if err := library.RemoveTree(s.cfg.LibraryPath, showDir); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
	} else {
		if err := library.RemoveVideo(s.cfg.LibraryPath, item.Path, item.Kind); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
	}
	for _, id := range removed {
		_ = s.store.DeleteMedia(id)
		s.meta.Drop(id)
	}
	s.hub.Broadcast(events.Event{Type: "library.changed"})
	writeJSON(w, http.StatusOK, map[string]any{"ok": true, "removed": removed})
}
