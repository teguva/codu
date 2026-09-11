package api

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"strings"

	"coog/internal/events"
	"coog/internal/meta"
	"coog/internal/store"
)

func (s *Server) handleLibraryIgnore(w http.ResponseWriter, r *http.Request) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	sc, _ := meta.ReadSidecar(item.Path)
	sc.MatchStatus = "ignored"
	sc.ImdbID = ""
	if sc.Title == "" {
		sc.Title = libraryMatchTitle(item)
	}
	if sc.Year == 0 {
		sc.Year = item.Year
	}
	if err := meta.WriteSidecar(item.Path, sc); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	s.meta.Drop(item.ID)
	info := s.meta.Ensure(r.Context(), item)
	s.hub.Broadcast(events.Event{Type: "library.changed"})
	writeJSON(w, http.StatusOK, viewItem(item, info, strings.TrimRight(publicURL(r, "/"), "/")))
}

func (s *Server) handleLibraryRematch(w http.ResponseWriter, r *http.Request) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	var body struct {
		ImdbID string `json:"imdbId"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil && !errors.Is(err, io.EOF) {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	imdb := strings.ToLower(strings.TrimSpace(body.ImdbID))
	sc, _ := meta.ReadSidecar(item.Path)
	title := libraryMatchTitle(item)
	if sc.Title == "" {
		sc.Title = title
	}
	if sc.Year == 0 {
		sc.Year = item.Year
	}
	if imdb != "" {
		if !strings.HasPrefix(imdb, "tt") {
			writeError(w, http.StatusBadRequest, "imdbId must look like tt1234567")
			return
		}
		sc.MatchStatus = "matched"
		sc.ImdbID = imdb
	} else {
		// Clear blocking status so FindIMDB can use path / NFO again.
		sc.MatchStatus = ""
		sc.ImdbID = ""
	}
	if err := meta.WriteSidecar(item.Path, sc); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	s.meta.Drop(item.ID)
	info := s.meta.Ensure(r.Context(), item)
	s.hub.Broadcast(events.Event{Type: "library.changed"})
	writeJSON(w, http.StatusOK, viewItem(item, info, strings.TrimRight(publicURL(r, "/"), "/")))
}

func libraryMatchTitle(item store.MediaItem) string {
	if item.Kind == "episode" && strings.TrimSpace(item.ShowTitle) != "" {
		return strings.TrimSpace(item.ShowTitle)
	}
	return strings.TrimSpace(item.Title)
}
