package api

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"errors"
	"log/slog"
	"net"
	"net/http"
	"os"
	"time"

	"coog/internal/auth"
	"coog/internal/config"
	"coog/internal/library"
	"coog/internal/playback"
	"coog/internal/probe"
	"coog/internal/store"
)

type Server struct {
	cfg     config.Config
	store   *store.Store
	scanner *library.Scanner
	prober  *probe.Prober
	http    *http.Server
}

func New(cfg config.Config, st *store.Store, scanner *library.Scanner, prober *probe.Prober) *Server {
	s := &Server{cfg: cfg, store: st, scanner: scanner, prober: prober}
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", s.handleHealth)
	mux.HandleFunc("GET /api/v1/library", s.handleLibraryList)
	mux.HandleFunc("GET /api/v1/library/{id}", s.handleLibraryGet)
	mux.HandleFunc("POST /api/v1/library/rescan", s.handleLibraryRescan)
	mux.HandleFunc("GET /api/v1/media/{id}/stream", s.handleStream)
	mux.HandleFunc("POST /api/v1/playback/sessions", s.handlePlaybackSession)
	mux.HandleFunc("GET /api/v1/jobs", s.handleJobs)
	mux.HandleFunc("GET /api/v1/server/stats", s.handleStats)

	if cfg.AdminDir != "" {
		fs := http.FileServer(http.Dir(cfg.AdminDir))
		mux.Handle("/", fs)
	}

	handler := withCORS(auth.Bearer(cfg.AuthToken)(mux))
	s.http = &http.Server{
		Addr:              cfg.Listen,
		Handler:           handler,
		ReadHeaderTimeout: 10 * time.Second,
	}
	return s
}

func (s *Server) Run(ctx context.Context) error {
	errCh := make(chan error, 1)
	go func() {
		ln, err := net.Listen("tcp", s.cfg.Listen)
		if err != nil {
			errCh <- err
			return
		}
		slog.Info("coog-api listening",
			"addr", ln.Addr().String(),
			"library", s.cfg.LibraryPath,
			"data", s.cfg.DataPath,
			"auth", s.cfg.AuthToken != "",
			"version", config.Version,
		)
		errCh <- s.http.Serve(ln)
	}()
	select {
	case <-ctx.Done():
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = s.http.Shutdown(shutdownCtx)
		return nil
	case err := <-errCh:
		if errors.Is(err, http.ErrServerClosed) {
			return nil
		}
		return err
	}
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":  "ok",
		"version": config.Version,
		"ffmpeg":  s.prober.Version(r.Context()),
	})
}

func (s *Server) handleLibraryList(w http.ResponseWriter, r *http.Request) {
	items, err := s.store.ListMedia()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func (s *Server) handleLibraryGet(w http.ResponseWriter, r *http.Request) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, item)
}

func (s *Server) handleLibraryRescan(w http.ResponseWriter, r *http.Request) {
	result, err := s.scanner.Scan(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	writeJSON(w, http.StatusOK, result)
}

func (s *Server) handleStream(w http.ResponseWriter, r *http.Request) {
	item, err := s.store.GetMedia(r.PathValue("id"))
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	f, err := os.Open(item.Path)
	if err != nil {
		writeError(w, http.StatusNotFound, "file missing on disk")
		return
	}
	defer f.Close()
	info, err := f.Stat()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	ctype := item.ContentType
	if ctype == "" {
		ctype = library.ContentType(item.Path)
	}
	w.Header().Set("Content-Type", ctype)
	w.Header().Set("Accept-Ranges", "bytes")
	http.ServeContent(w, r, info.Name(), info.ModTime(), f)
}

type sessionRequest struct {
	MediaID            string                 `json:"mediaId"`
	JobID              string                 `json:"jobId"`
	ClientCapabilities *playback.Capabilities `json:"clientCapabilities"`
}

func (s *Server) handlePlaybackSession(w http.ResponseWriter, r *http.Request) {
	var req sessionRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid json")
		return
	}
	if req.JobID != "" {
		writeError(w, http.StatusNotImplemented, "progressive sessions require Phase 5")
		return
	}
	if req.MediaID == "" {
		writeError(w, http.StatusBadRequest, "mediaId is required")
		return
	}
	item, err := s.store.GetMedia(req.MediaID)
	if errors.Is(err, store.ErrNotFound) {
		writeError(w, http.StatusNotFound, "media not found")
		return
	}
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	result, err := playback.Negotiate(item, req.ClientCapabilities)
	if err != nil {
		slog.Info("playback session rejected", "playback_method", playback.MethodTranscode, "media_id", item.ID, "err", err)
		writeJSON(w, http.StatusConflict, map[string]any{
			"error":   err.Error(),
			"method":  playback.MethodTranscode,
			"mediaId": item.ID,
		})
		return
	}
	sid, err := randomID()
	if err != nil {
		writeError(w, http.StatusInternalServerError, err.Error())
		return
	}
	url := publicURL(r, "/api/v1/media/"+item.ID+"/stream")
	slog.Info("playback session", "playback_method", result.Method, "media_id", item.ID, "session_id", sid)
	writeJSON(w, http.StatusOK, map[string]any{
		"id":                 sid,
		"method":             result.Method,
		"reason":             result.Reason,
		"url":                url,
		"mediaId":            item.ID,
		"expectedDurationMs": item.DurationMs,
		"bufferedMs":         item.DurationMs,
	})
}

func (s *Server) handleJobs(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{"items": []any{}})
}

func (s *Server) handleStats(w http.ResponseWriter, r *http.Request) {
	count, _ := s.store.Stats()
	var disk any
	if usage, err := diskUsage(s.cfg.LibraryPath); err == nil {
		disk = usage
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"version":                  config.Version,
		"ffmpeg":                   s.prober.Version(r.Context()),
		"libraryPath":              s.cfg.LibraryPath,
		"dataPath":                 s.cfg.DataPath,
		"mediaCount":               count,
		"disk":                     disk,
		"concurrentTranscodeLimit": playback.ConcurrentTranscodeLimit,
	})
}

func publicURL(r *http.Request, path string) string {
	scheme := "http"
	if r.TLS != nil {
		scheme = "https"
	}
	if proto := r.Header.Get("X-Forwarded-Proto"); proto != "" {
		scheme = proto
	}
	host := r.Host
	if host == "" {
		host = "127.0.0.1:8090"
	}
	return scheme + "://" + host + path
}

func withCORS(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		w.Header().Set("Access-Control-Allow-Headers", "Authorization, Content-Type")
		w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
		if r.Method == http.MethodOptions {
			w.WriteHeader(http.StatusNoContent)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, msg string) {
	writeJSON(w, status, map[string]string{"error": msg})
}

func randomID() (string, error) {
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		return "", err
	}
	return hex.EncodeToString(b[:]), nil
}
