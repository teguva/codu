package api

import (
	"encoding/json"
	"net/http"
	"strings"
	"time"

	"coog/internal/events"
	"coog/internal/store"
)

func publicJob(job store.Job) store.Job {
	job.URL = events.Redact(job.URL)
	job.Error = events.Redact(job.Error)
	job.LogTail = events.Redact(job.LogTail)
	return job
}

func (s *Server) handleActivity(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"items": s.hub.Recent()})
}

type clientEventRequest struct {
	Level     string `json:"level"`
	Type      string `json:"type"`
	Message   string `json:"message"`
	MediaID   string `json:"mediaId"`
	JobID     string `json:"jobId"`
	SessionID string `json:"sessionId"`
}

func (s *Server) handleClientEvents(w http.ResponseWriter, r *http.Request) {
	var req clientEventRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	req.Message = strings.TrimSpace(req.Message)
	if req.Message == "" {
		writeError(w, http.StatusBadRequest, "message is required")
		return
	}
	if req.Type == "" {
		req.Type = "client.event"
	}
	if req.Level == "" {
		req.Level = "error"
	}
	ev := s.hub.Record(events.Activity{
		Level:     req.Level,
		Source:    "tv",
		Type:      req.Type,
		Message:   req.Message,
		MediaID:   req.MediaID,
		JobID:     req.JobID,
		SessionID: req.SessionID,
	})
	writeJSON(w, http.StatusCreated, ev)
}

func (s *Server) note(level, source, typ, message, jobID, mediaID string) {
	s.hub.Record(events.Activity{
		Level:   level,
		Source:  source,
		Type:    typ,
		Message: message,
		JobID:   jobID,
		MediaID: mediaID,
	})
}

func (s *Server) setCatalogError(msg string) {
	s.catalogMu.Lock()
	defer s.catalogMu.Unlock()
	s.catalogErr = events.Redact(strings.TrimSpace(msg))
	s.catalogAt = time.Now().UnixMilli()
}
