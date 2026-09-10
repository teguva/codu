package api

import (
	"errors"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"

	"coog/internal/library"
	"coog/internal/meta"
	"coog/internal/probe"
	"coog/internal/store"
)

var artworkGate = make(chan struct{}, 2)

func (s *Server) handleArtwork(w http.ResponseWriter, r *http.Request) {
	s.serveArt(w, r, "backdrop")
}

func (s *Server) handlePoster(w http.ResponseWriter, r *http.Request) {
	s.serveArt(w, r, "poster")
}

func (s *Server) handleBackdrop(w http.ResponseWriter, r *http.Request) {
	s.serveArt(w, r, "backdrop")
}

func (s *Server) serveArt(w http.ResponseWriter, r *http.Request, kind string) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	dest := filepath.Join(s.cfg.DataPath, "artwork", item.ID+"-"+kind+".jpg")
	if err := s.ensureArt(r, item, dest, kind); err != nil {
		slog.Debug("artwork", "id", item.ID, "kind", kind, "err", err)
		writeError(w, http.StatusNotFound, "no artwork")
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	http.ServeFile(w, r, dest)
}

func (s *Server) handleTrailer(w http.ResponseWriter, r *http.Request) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	path := probe.SidecarTrailer(item.Path)
	if path == "" {
		info := s.meta.Ensure(r.Context(), item)
		path = probe.LibraryTrailer(s.cfg.LibraryPath, info.ImdbID)
	}
	if path == "" {
		writeError(w, http.StatusNotFound, "no trailer")
		return
	}
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

func (s *Server) ensureArt(r *http.Request, item store.MediaItem, dest, kind string) error {
	if fresh(dest, item.MtimeUnix) {
		return nil
	}
	select {
	case artworkGate <- struct{}{}:
		defer func() { <-artworkGate }()
	case <-r.Context().Done():
		return r.Context().Err()
	}
	if fresh(dest, item.MtimeUnix) {
		return nil
	}

	if kind == "poster" {
		if img := probe.SidecarPoster(item.Path); img != "" {
			return s.prober.MaterializeImage(r.Context(), img, dest)
		}
	} else {
		if img := probe.SidecarBackdrop(item.Path); img != "" {
			return s.prober.MaterializeImage(r.Context(), img, dest)
		}
	}

	info := s.meta.Ensure(r.Context(), item)
	remote := info.PosterURL
	if kind != "poster" {
		remote = info.BackdropURL
	}
	if remote != "" {
		if err := s.meta.FetchFile(r.Context(), remote, dest); err == nil && fresh(dest, 0) {
			return nil
		}
	}

	if kind == "poster" {
		return errors.New("no poster")
	}
	return s.prober.ExtractStill(r.Context(), item.Path, dest, item.DurationMs)
}

func fresh(path string, itemMtime int64) bool {
	st, err := os.Stat(path)
	if err != nil || st.Size() <= 32 {
		return false
	}
	if itemMtime > 0 && st.ModTime().Unix() < itemMtime {
		return false
	}
	return true
}

func viewItem(item store.MediaItem, info meta.Info, origin string) map[string]any {
	year := item.Year
	if year == 0 && info.Year > 0 {
		year = info.Year
	}
	out := map[string]any{
		"id":           item.ID,
		"kind":         item.Kind,
		"title":        item.Title,
		"year":         year,
		"season":       item.Season,
		"episode":      item.Episode,
		"showTitle":    item.ShowTitle,
		"path":         item.Path,
		"relativePath": item.RelativePath,
		"sizeBytes":    item.SizeBytes,
		"mtimeUnix":    item.MtimeUnix,
		"durationMs":   item.DurationMs,
		"codecVideo":   item.CodecVideo,
		"codecAudio":   item.CodecAudio,
		"width":        item.Width,
		"height":       item.Height,
		"hdr":          item.HDR,
		"contentType":  item.ContentType,
		"imdbId":       info.ImdbID,
		"tagline":      info.Tagline,
		"plot":         info.Plot,
		"genres":       info.Genres,
		"rating":       info.Rating,
		"posterUrl":    origin + "/api/v1/media/" + item.ID + "/poster",
		"backdropUrl":  origin + "/api/v1/media/" + item.ID + "/backdrop",
	}
	if info.Genres == nil {
		out["genres"] = []string{}
	}
	return out
}
