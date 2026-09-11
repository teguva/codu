package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"coog/internal/jobs"
	"coog/internal/meta"
	"coog/internal/settings"
	"coog/internal/store"
)

// handlePrefetchNext queues the next episode when autoDownloadNextEpisode is on.
// TV can call this near end-of-playback; no background scheduler required.
func (s *Server) handlePrefetchNext(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ImdbID  string `json:"imdbId"`
		Season  int    `json:"season"`
		Episode int    `json:"episode"`
		Title   string `json:"title"`
		Year    int    `json:"year"`
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	imdb := strings.ToLower(strings.TrimSpace(body.ImdbID))
	if !strings.HasPrefix(imdb, "tt") {
		writeError(w, http.StatusBadRequest, "imdb id required")
		return
	}
	cfg := settings.Load(s.cfg.DataPath)
	if !cfg.AutoDownloadNext {
		writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"skipped": true,
			"reason":  "autoDownloadNextEpisode is off",
		})
		return
	}
	next, ok := s.resolveNextEpisode(r, imdb, body.Season, body.Episode)
	if !ok {
		writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"skipped": true,
			"reason":  "no next episode",
		})
		return
	}
	if next.InLibrary && next.MediaID != "" {
		writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"skipped": true,
			"reason":  "next episode already in library",
			"item":    s.mediaJSON(next),
		})
		return
	}
	resource := imdb + ":" + strconv.Itoa(next.Season) + ":" + strconv.Itoa(next.Episode)
	if job, err := s.store.FindActiveJobByIMDB(imdb); err == nil {
		if strings.Contains(job.URL, resource) || episodeJobMatches(job, next.Season, next.Episode) {
			writeJSON(w, http.StatusOK, map[string]any{
				"ok":      true,
				"skipped": true,
				"reason":  "next episode already queued",
				"jobId":   job.ID,
				"item":    s.mediaJSON(next),
			})
			return
		}
	}
	id, err := randomID()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	title := strings.TrimSpace(body.Title)
	if title == "" {
		title = next.ShowTitle
	}
	if title == "" {
		title = next.Title
	}
	if next.Season > 0 && next.Episode > 0 {
		title = strings.TrimSpace(title + " S" + strconv.Itoa(next.Season) + "E" + strconv.Itoa(next.Episode))
	}
	job := store.Job{
		ID:      id,
		Type:    jobs.TypeDebrid,
		URL:     "imdb:" + resource,
		Title:   title,
		Status:  jobs.StatusQueued,
		ImdbID:  imdb,
		Year:    body.Year,
		WorkDir: jobs.Dir(s.cfg.DataPath, id),
	}
	if job.Year == 0 {
		job.Year = next.Year
	}
	if err := s.store.InsertJob(job); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	s.note("info", "api", "job.queued", "prefetch queued "+job.Title, job.ID, "")
	writeJSON(w, http.StatusOK, map[string]any{
		"ok":    true,
		"jobId": job.ID,
		"item":  s.mediaJSON(next),
	})
}

func episodeJobMatches(job store.Job, season, episode int) bool {
	want := ":" + strconv.Itoa(season) + ":" + strconv.Itoa(episode)
	return strings.HasSuffix(job.URL, want) || strings.Contains(job.URL, want+"/")
}

func (s *Server) resolveNextEpisode(r *http.Request, imdb string, season, episode int) (meta.CatalogItem, bool) {
	if season <= 0 {
		season = 1
	}
	if episode <= 0 {
		episode = 1
	}
	_, eps, err := s.meta.CatalogShow(r.Context(), imdb)
	if err != nil {
		return meta.CatalogItem{}, false
	}
	eps = s.attachEpisodeLibrary(eps, imdb)
	var next meta.CatalogItem
	found := false
	for _, ep := range eps {
		if ep.Season < season {
			continue
		}
		if ep.Season == season && ep.Episode <= episode {
			continue
		}
		if !found || ep.Season < next.Season || (ep.Season == next.Season && ep.Episode < next.Episode) {
			next = ep
			found = true
		}
	}
	return next, found
}
