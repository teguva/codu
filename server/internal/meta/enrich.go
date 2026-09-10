package meta

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"coog/internal/store"
)

type Info struct {
	ImdbID      string   `json:"imdbId,omitempty"`
	Tagline     string   `json:"tagline,omitempty"`
	Plot        string   `json:"plot,omitempty"`
	Genres      []string `json:"genres,omitempty"`
	Rating      float64  `json:"rating,omitempty"`
	Year        int      `json:"year,omitempty"`
	PosterURL   string   `json:"posterUrl,omitempty"`
	BackdropURL string   `json:"backdropUrl,omitempty"`
	Source      string   `json:"source,omitempty"`
}

type Enricher struct {
	dir      string
	tmdbKey  string
	client   *http.Client
	mu       sync.Mutex
	mem      map[string]Info
	inflight map[string]chan struct{}
}

func New(dataPath, tmdbKey string) *Enricher {
	return &Enricher{
		dir:      filepath.Join(dataPath, "meta"),
		tmdbKey:  tmdbKey,
		client:   &http.Client{Timeout: 10 * time.Second},
		mem:      map[string]Info{},
		inflight: map[string]chan struct{}{},
	}
}

func (e *Enricher) Peek(id string) (Info, bool) {
	e.mu.Lock()
	if info, ok := e.mem[id]; ok {
		e.mu.Unlock()
		return info, true
	}
	e.mu.Unlock()
	info, ok := e.readDisk(id)
	if ok {
		e.mu.Lock()
		e.mem[id] = info
		e.mu.Unlock()
	}
	return info, ok
}

func (e *Enricher) Ensure(ctx context.Context, item store.MediaItem) Info {
	if info, ok := e.Peek(item.ID); ok {
		if info.ImdbID != "" {
			if e.tmdbEnabled() && info.Tagline == "" && !strings.Contains(info.Source, "tmdb") {
				title := item.Title
				if item.Kind == "episode" && item.ShowTitle != "" {
					title = item.ShowTitle
				}
				info = e.overlayTMDB(ctx, item.Kind, info, title, item.Year)
				e.store(item.ID, info)
			}
			return info
		}
		if info.Source == "none" && cacheAge(e.cachePath(item.ID)) < 24*time.Hour {
			return info
		}
	}
	e.mu.Lock()
	if ch, ok := e.inflight[item.ID]; ok {
		e.mu.Unlock()
		select {
		case <-ch:
		case <-ctx.Done():
			info, _ := e.Peek(item.ID)
			return info
		}
		info, _ := e.Peek(item.ID)
		return info
	}
	done := make(chan struct{})
	e.inflight[item.ID] = done
	e.mu.Unlock()
	info := e.resolve(ctx, item)
	e.store(item.ID, info)
	e.mu.Lock()
	delete(e.inflight, item.ID)
	e.mu.Unlock()
	close(done)
	return info
}

func (e *Enricher) Warm(ctx context.Context, items []store.MediaItem) {
	go func() {
		for _, item := range items {
			select {
			case <-ctx.Done():
				return
			default:
			}
			if _, ok := e.Peek(item.ID); ok {
				continue
			}
			e.Ensure(ctx, item)
		}
	}()
}

func (e *Enricher) resolve(ctx context.Context, item store.MediaItem) Info {
	title := item.Title
	if item.Kind == "episode" && item.ShowTitle != "" {
		title = item.ShowTitle
	}
	imdb := FindIMDB(item.Path, title, item.Year)
	var info Info
	var err error
	if imdb != "" {
		info, err = e.cinemetaByIMDB(ctx, item.Kind, imdb)
		if err != nil {
			slog.Debug("cinemeta imdb", "id", item.ID, "imdb", imdb, "err", err)
			info = Info{ImdbID: imdb, PosterURL: metahubPoster(imdb), BackdropURL: metahubBackdrop(imdb), Source: "metahub"}
		}
	} else {
		info, err = e.cinemetaSearch(ctx, item.Kind, title, item.Year)
		if err != nil {
			slog.Debug("cinemeta search", "id", item.ID, "title", title, "err", err)
		}
	}
	if info.ImdbID != "" {
		info = e.overlayTMDB(ctx, item.Kind, info, title, item.Year)
	}
	if info.ImdbID == "" && info.PosterURL == "" && info.Plot == "" {
		info.Source = "none"
	}
	return info
}

func (e *Enricher) store(id string, info Info) {
	e.mu.Lock()
	e.mem[id] = info
	e.mu.Unlock()
	if err := os.MkdirAll(e.dir, 0o755); err != nil {
		return
	}
	b, err := json.MarshalIndent(info, "", "  ")
	if err != nil {
		return
	}
	_ = os.WriteFile(e.cachePath(id), b, 0o644)
}

func (e *Enricher) readDisk(id string) (Info, bool) {
	b, err := os.ReadFile(e.cachePath(id))
	if err != nil {
		return Info{}, false
	}
	var info Info
	if json.Unmarshal(b, &info) != nil {
		return Info{}, false
	}
	return info, true
}

func (e *Enricher) cachePath(id string) string {
	return filepath.Join(e.dir, id+".json")
}

func cacheAge(path string) time.Duration {
	st, err := os.Stat(path)
	if err != nil {
		return 0
	}
	return time.Since(st.ModTime())
}

func (e *Enricher) getJSON(ctx context.Context, rawURL string, dest any) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "coog/0.1")
	resp, err := e.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return errStatus(resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 2<<20))
	if err != nil {
		return err
	}
	return json.Unmarshal(body, dest)
}

func (e *Enricher) FetchFile(ctx context.Context, rawURL, dest string) error {
	if rawURL == "" {
		return errStatus(http.StatusNotFound)
	}
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return err
	}
	req.Header.Set("User-Agent", "coog/0.1")
	resp, err := e.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode >= 400 {
		return errStatus(resp.StatusCode)
	}
	tmp := dest + ".tmp"
	f, err := os.Create(tmp)
	if err != nil {
		return err
	}
	_, err = io.Copy(f, io.LimitReader(resp.Body, 12<<20))
	closeErr := f.Close()
	if err != nil {
		_ = os.Remove(tmp)
		return err
	}
	if closeErr != nil {
		_ = os.Remove(tmp)
		return closeErr
	}
	return os.Rename(tmp, dest)
}

type statusErr int

func (e statusErr) Error() string { return http.StatusText(int(e)) }

func errStatus(code int) error { return statusErr(code) }
