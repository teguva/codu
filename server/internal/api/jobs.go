package api

import (
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"unicode"

	"coog/internal/jobs"
	"coog/internal/playback"
	"coog/internal/store"
	"coog/internal/streams"
)

type createJobRequest struct {
	Type     string `json:"type"`
	URL      string `json:"url"`
	Title    string `json:"title"`
	ImdbID   string `json:"imdbId"`
	InfoHash string `json:"infoHash"`
	Kind     string `json:"kind"`
	Season   int    `json:"season"`
	Episode  int    `json:"episode"`
	Year     int    `json:"year"`
}

func (s *Server) handleJobs(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		list, err := s.store.ListJobs()
		if err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		out := make([]store.Job, 0, len(list))
		for _, job := range list {
			out = append(out, publicJob(job))
		}
		writeJSON(w, http.StatusOK, map[string]any{"items": out})
	case http.MethodPost:
		s.handleCreateJob(w, r)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) handleCreateJob(w http.ResponseWriter, r *http.Request) {
	var req createJobRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	req.URL = strings.TrimSpace(req.URL)
	req.ImdbID = strings.TrimSpace(req.ImdbID)
	req.InfoHash = streams.InfoHash(req.InfoHash)
	if req.Type == "" {
		req.Type = jobs.TypeYTDLP
	}
	if req.Type != jobs.TypeYTDLP && req.Type != jobs.TypeHTTP && req.Type != jobs.TypeDebrid {
		writeError(w, http.StatusBadRequest, "unsupported job type")
		return
	}
	if req.Type == jobs.TypeDebrid {
		if req.ImdbID == "" && strings.HasPrefix(req.URL, "imdb:") {
			req.ImdbID = strings.TrimPrefix(req.URL, "imdb:")
			if i := strings.Index(req.ImdbID, ":"); i > 0 {
				req.ImdbID = req.ImdbID[:i]
			}
		}
		if req.ImdbID == "" && req.InfoHash == "" {
			writeError(w, http.StatusBadRequest, "imdbId or infoHash is required")
			return
		}
		if req.InfoHash != "" {
			if existing, err := s.store.FindActiveJobByHash(req.InfoHash); err == nil {
				writeJSON(w, http.StatusOK, publicJob(existing))
				return
			}
		}
		resource := req.ImdbID
		if req.Kind == "series" || req.Kind == "episode" {
			season, episode := req.Season, req.Episode
			if season <= 0 {
				season = 1
			}
			if episode <= 0 {
				episode = 1
			}
			resource = req.ImdbID + ":" + strconv.Itoa(season) + ":" + strconv.Itoa(episode)
		}
		if resource != "" {
			req.URL = "imdb:" + resource
		}
	}
	if req.URL == "" && req.Type != jobs.TypeDebrid {
		writeError(w, http.StatusBadRequest, "url is required")
		return
	}
	id, err := randomID()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	job := store.Job{
		ID:       id,
		Type:     req.Type,
		URL:      req.URL,
		Title:    strings.TrimSpace(req.Title),
		Status:   jobs.StatusQueued,
		WorkDir:  jobs.Dir(s.cfg.DataPath, id),
		ImdbID:   req.ImdbID,
		InfoHash: req.InfoHash,
		Year:     req.Year,
	}
	if err := s.store.InsertJob(job); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	queued := "queued " + job.Type + " download"
	if title := strings.TrimSpace(job.Title); title != "" {
		queued += " for " + title
	}
	s.note("info", "api", "job.queued", queued, job.ID, "")
	writeJSON(w, http.StatusCreated, publicJob(job))
}

func (s *Server) handleJobCancel(w http.ResponseWriter, r *http.Request) {
	job, err := s.store.GetJob(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "job not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if job.Status == jobs.StatusFinished {
		writeError(w, http.StatusConflict, "job already finished")
		return
	}
	job.Status = jobs.StatusCancelled
	job.Error = "cancelled"
	job.Ready = false
	if err := s.store.UpdateJob(job); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	s.note("warn", "api", "job.cancelled", "cancelled "+job.Title, job.ID, job.MediaID)
	writeJSON(w, http.StatusOK, publicJob(job))
}

func (s *Server) handleJobPause(w http.ResponseWriter, r *http.Request) {
	job, err := s.store.GetJob(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "job not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	switch job.Status {
	case jobs.StatusFinished, jobs.StatusCancelled, jobs.StatusError, jobs.StatusPaused:
		writeError(w, http.StatusConflict, "job cannot be paused")
		return
	}
	job.Status = jobs.StatusPaused
	job.Error = ""
	if err := s.store.UpdateJob(job); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	s.note("info", "api", "job.paused", "paused "+job.Title, job.ID, job.MediaID)
	writeJSON(w, http.StatusOK, publicJob(job))
}

func (s *Server) handleJobRetry(w http.ResponseWriter, r *http.Request) {
	job, err := s.store.GetJob(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "job not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if job.Status != jobs.StatusError && job.Status != jobs.StatusCancelled && job.Status != jobs.StatusPaused {
		writeError(w, http.StatusConflict, "job is not retryable")
		return
	}
	job.Status = jobs.StatusQueued
	job.Error = ""
	job.Ready = false
	job.Progress = 0
	job.BufferedMs = 0
	if err := s.store.UpdateJob(job); err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	s.note("info", "api", "job.retry", "re-queued "+job.Title, job.ID, job.MediaID)
	writeJSON(w, http.StatusOK, publicJob(job))
}

func (s *Server) handleJobGet(w http.ResponseWriter, r *http.Request) {
	job, err := s.store.GetJob(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "job not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, publicJob(job))
}

func (s *Server) handleProgressive(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	file := r.PathValue("file")
	if id == "" || !safeHLSFile(file) {
		writeError(w, http.StatusNotFound, "not found")
		return
	}
	if _, err := s.store.GetJob(id); err != nil {
		writeError(w, http.StatusNotFound, "job not found")
		return
	}
	path := filepath.Join(jobs.HLSDir(s.cfg.DataPath, id), file)
	f, err := os.Open(path)
	if err != nil {
		writeError(w, http.StatusNotFound, "not found")
		return
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if strings.HasSuffix(strings.ToLower(file), ".m3u8") {
		w.Header().Set("Content-Type", "application/vnd.apple.mpegurl")
		w.Header().Set("Cache-Control", "no-cache, no-store")
		w.WriteHeader(http.StatusOK)
		_, _ = io.Copy(w, f)
		return
	}
	w.Header().Set("Content-Type", "video/mp2t")
	w.Header().Set("Cache-Control", "public, max-age=60")
	http.ServeContent(w, r, file, st.ModTime(), f)
}

func (s *Server) handleJobPlayback(w http.ResponseWriter, r *http.Request, jobID string) {
	job, err := s.store.GetJob(jobID)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "job not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	if job.Status == jobs.StatusError {
		writeError(w, http.StatusConflict, job.Error)
		return
	}
	if job.Status == jobs.StatusFinished && job.MediaID != "" {
		item, err := s.store.GetMedia(job.MediaID)
		if err == nil {
			s.writeDirectSession(w, r, item, nil)
			return
		}
	}
	if !job.Ready && job.Status != jobs.StatusReady && job.Status != jobs.StatusFinished {
		writeJSON(w, http.StatusConflict, map[string]any{
			"error":   "job is not ready for playback yet",
			"method":  playback.MethodProgressive,
			"jobId":   job.ID,
			"ready":   false,
			"status":  job.Status,
			"mediaId": job.MediaID,
		})
		return
	}
	sid, err := randomID()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	url := publicURL(r, "/api/v1/jobs/"+job.ID+"/progressive/"+jobs.PlaylistName)
	slog.Info("playback session", "playback_method", playback.MethodProgressive, "job_id", job.ID, "session_id", sid)
	writeJSON(w, http.StatusOK, map[string]any{
		"id":                 sid,
		"method":             playback.MethodProgressive,
		"reason":             "growing HLS while download continues",
		"url":                url,
		"mediaId":            job.MediaID,
		"jobId":              job.ID,
		"expectedDurationMs": job.ExpectedDurationMs,
		"bufferedMs":         job.BufferedMs,
	})
}

func safeHLSFile(name string) bool {
	if name == "" || strings.Contains(name, "/") || strings.Contains(name, "\\") || strings.Contains(name, "..") {
		return false
	}
	if name == jobs.PlaylistName {
		return true
	}
	if !strings.HasPrefix(name, "seg_") || !strings.HasSuffix(name, ".ts") {
		return false
	}
	for _, c := range strings.TrimSuffix(strings.TrimPrefix(name, "seg_"), ".ts") {
		if !unicode.IsDigit(c) {
			return false
		}
	}
	return true
}
