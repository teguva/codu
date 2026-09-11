package subtitles

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

const apiBase = "https://api.opensubtitles.com/api/v1"

// Track is a unified subtitle row for the TV / admin clients.
type Track struct {
	ID              string `json:"id"`
	Source          string `json:"source"` // none | sidecar | opensubtitles
	Language        string `json:"language,omitempty"`
	Label           string `json:"label"`
	Path            string `json:"path,omitempty"`
	Filename        string `json:"filename,omitempty"`
	FileID          int    `json:"fileId,omitempty"`
	DownloadCount   int    `json:"downloadCount,omitempty"`
	HearingImpaired bool   `json:"hearingImpaired,omitempty"`
}

type SearchQuery struct {
	ImdbID   string
	TmdbID   string
	Query    string
	Kind     string // movie | series | episode
	Season   int
	Episode  int
	Languages []string
}

type Client struct {
	DataPath string
	HTTP     *http.Client
}

func NewClient(dataPath string) *Client {
	return &Client{
		DataPath: dataPath,
		HTTP:     &http.Client{Timeout: 25 * time.Second},
	}
}

func (c *Client) settings() Settings {
	return Load(c.DataPath)
}

func (c *Client) Search(q SearchQuery) ([]Track, error) {
	cfg := c.settings()
	if !cfg.Enabled {
		return nil, fmt.Errorf("OpenSubtitles disabled in settings")
	}
	if strings.TrimSpace(cfg.APIKey) == "" {
		return nil, fmt.Errorf("Add an OpenSubtitles API key in Settings → Subtitles")
	}
	langs := q.Languages
	if len(langs) == 0 {
		langs = cfg.Languages
	}
	params := url.Values{}
	params.Set("order_by", "download_count")
	params.Set("order_direction", "desc")
	if len(langs) > 0 {
		params.Set("languages", strings.Join(langs, ","))
	}
	if imdb := normalizeImdb(q.ImdbID); imdb != "" {
		params.Set("imdb_id", imdb)
	}
	if q.TmdbID != "" && isDigits(q.TmdbID) {
		params.Set("tmdb_id", q.TmdbID)
	}
	if strings.TrimSpace(q.Query) != "" {
		params.Set("query", strings.TrimSpace(q.Query))
	}
	kind := strings.ToLower(strings.TrimSpace(q.Kind))
	switch kind {
	case "series", "episode", "tvshow":
		params.Set("type", "episode")
	case "movie":
		params.Set("type", "movie")
	}
	if q.Season > 0 {
		params.Set("season_number", strconv.Itoa(q.Season))
	}
	if q.Episode > 0 {
		params.Set("episode_number", strconv.Itoa(q.Episode))
	}

	token, _ := c.login(cfg)
	var payload struct {
		Data []struct {
			Attributes struct {
				Language        string `json:"language"`
				Release         string `json:"release"`
				DownloadCount   int    `json:"download_count"`
				HearingImpaired bool   `json:"hearing_impaired"`
				FeatureDetails  struct {
					Title     string `json:"title"`
					MovieName string `json:"movie_name"`
				} `json:"feature_details"`
				Files []struct {
					FileID   int    `json:"file_id"`
					FileName string `json:"file_name"`
				} `json:"files"`
			} `json:"attributes"`
		} `json:"data"`
	}
	if err := c.doJSON(http.MethodGet, "/subtitles", cfg, token, params, nil, &payload); err != nil {
		return nil, err
	}
	out := make([]Track, 0, len(payload.Data))
	for _, row := range payload.Data {
		if len(row.Attributes.Files) == 0 || row.Attributes.Files[0].FileID == 0 {
			continue
		}
		f := row.Attributes.Files[0]
		lang := strings.ToLower(strings.TrimSpace(row.Attributes.Language))
		release := strings.TrimSpace(row.Attributes.Release)
		if release == "" {
			release = strings.TrimSpace(row.Attributes.FeatureDetails.Title)
		}
		if release == "" {
			release = strings.TrimSpace(row.Attributes.FeatureDetails.MovieName)
		}
		labelBits := []string{}
		if lang != "" {
			labelBits = append(labelBits, strings.ToUpper(lang))
		} else {
			labelBits = append(labelBits, "SUB")
		}
		if release != "" {
			if len(release) > 60 {
				release = release[:60]
			}
			labelBits = append(labelBits, release)
		}
		out = append(out, Track{
			ID:              fmt.Sprintf("os:%d", f.FileID),
			Source:          "opensubtitles",
			Language:        lang,
			Label:           strings.Join(labelBits, " · ") + " · OpenSubtitles",
			Filename:        strings.TrimSpace(f.FileName),
			FileID:          f.FileID,
			DownloadCount:   row.Attributes.DownloadCount,
			HearingImpaired: row.Attributes.HearingImpaired,
		})
		if len(out) >= 20 {
			break
		}
	}
	return out, nil
}

func (c *Client) Download(fileID int) (string, error) {
	cfg := c.settings()
	if strings.TrimSpace(cfg.APIKey) == "" {
		return "", fmt.Errorf("OpenSubtitles API key required")
	}
	if err := os.MkdirAll(CacheDir(c.DataPath), 0o755); err != nil {
		return "", err
	}
	prefix := fmt.Sprintf("%d_", fileID)
	if entries, err := os.ReadDir(CacheDir(c.DataPath)); err == nil {
		for _, e := range entries {
			if !e.IsDir() && strings.HasPrefix(e.Name(), prefix) {
				path := filepath.Join(CacheDir(c.DataPath), e.Name())
				if st, err := os.Stat(path); err == nil && st.Size() > 0 {
					return path, nil
				}
			}
		}
	}
	token, err := c.login(cfg)
	if err != nil {
		return "", err
	}
	if token == "" {
		return "", fmt.Errorf("OpenSubtitles username/password required to download (API key alone is not enough)")
	}
	var payload struct {
		Link     string `json:"link"`
		FileName string `json:"file_name"`
	}
	body := map[string]any{"file_id": fileID}
	if err := c.doJSON(http.MethodPost, "/download", cfg, token, nil, body, &payload); err != nil {
		return "", err
	}
	link := strings.TrimSpace(payload.Link)
	if link == "" {
		return "", fmt.Errorf("OpenSubtitles download link missing")
	}
	fileName := strings.TrimSpace(payload.FileName)
	if fileName == "" {
		fileName = fmt.Sprintf("%d.srt", fileID)
	}
	safe := sanitizeName(fileName)
	dest := filepath.Join(CacheDir(c.DataPath), fmt.Sprintf("%d_%s", fileID, safe))
	req, err := http.NewRequest(http.MethodGet, link, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", cfg.UserAgent)
	req.Header.Set("X-User-Agent", cfg.UserAgent)
	req.Header.Set("Api-Key", cfg.APIKey)
	req.Header.Set("Authorization", "Bearer "+token)
	res, err := c.HTTP.Do(req)
	if err != nil {
		return "", err
	}
	defer res.Body.Close()
	if res.StatusCode >= 300 {
		b, _ := io.ReadAll(io.LimitReader(res.Body, 300))
		return "", fmt.Errorf("download HTTP %d: %s", res.StatusCode, string(b))
	}
	data, err := io.ReadAll(res.Body)
	if err != nil {
		return "", err
	}
	if err := os.WriteFile(dest, data, 0o644); err != nil {
		return "", err
	}
	return dest, nil
}

func (c *Client) ResolveFile(id string) (string, string, error) {
	id = strings.TrimSpace(id)
	switch {
	case strings.HasPrefix(id, "os:"):
		fileID, err := strconv.Atoi(strings.TrimPrefix(id, "os:"))
		if err != nil || fileID <= 0 {
			return "", "", fmt.Errorf("invalid OpenSubtitles id")
		}
		path, err := c.Download(fileID)
		if err != nil {
			return "", "", err
		}
		return path, contentTypeFor(path), nil
	case strings.HasPrefix(id, "file:"):
		path := strings.TrimPrefix(id, "file:")
		if !filepath.IsAbs(path) {
			return "", "", fmt.Errorf("subtitle path must be absolute")
		}
		if st, err := os.Stat(path); err != nil || st.IsDir() {
			return "", "", fmt.Errorf("subtitle file not found")
		}
		return path, contentTypeFor(path), nil
	default:
		return "", "", fmt.Errorf("unknown subtitle id")
	}
}

func (c *Client) login(cfg Settings) (string, error) {
	if strings.TrimSpace(cfg.Username) == "" || strings.TrimSpace(cfg.Password) == "" {
		return "", nil
	}
	var payload struct {
		Token string `json:"token"`
	}
	body := map[string]any{"username": cfg.Username, "password": cfg.Password}
	if err := c.doJSON(http.MethodPost, "/login", cfg, "", nil, body, &payload); err != nil {
		return "", err
	}
	token := strings.TrimSpace(payload.Token)
	if token == "" {
		return "", fmt.Errorf("OpenSubtitles login failed")
	}
	return token, nil
}

func (c *Client) doJSON(method, path string, cfg Settings, token string, params url.Values, body any, out any) error {
	u := apiBase + path
	if len(params) > 0 {
		// Stable query order like the Python client.
		keys := make([]string, 0, len(params))
		for k := range params {
			keys = append(keys, k)
		}
		sort.Strings(keys)
		parts := make([]string, 0, len(keys))
		for _, k := range keys {
			for _, v := range params[k] {
				parts = append(parts, url.QueryEscape(k)+"="+url.QueryEscape(v))
			}
		}
		u += "?" + strings.Join(parts, "&")
	}
	var bodyReader io.Reader
	if body != nil {
		b, err := json.Marshal(body)
		if err != nil {
			return err
		}
		bodyReader = strings.NewReader(string(b))
	}
	req, err := http.NewRequest(method, u, bodyReader)
	if err != nil {
		return err
	}
	req.Header.Set("Api-Key", cfg.APIKey)
	req.Header.Set("User-Agent", cfg.UserAgent)
	req.Header.Set("X-User-Agent", cfg.UserAgent)
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	res, err := c.HTTP.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	raw, err := io.ReadAll(io.LimitReader(res.Body, 2<<20))
	if err != nil {
		return err
	}
	if res.StatusCode >= 300 {
		detail := string(raw)
		if len(detail) > 300 {
			detail = detail[:300]
		}
		hint := ""
		if res.StatusCode == 403 && strings.Contains(strings.ToLower(detail), "user-agent") {
			hint = fmt.Sprintf(" — set User-Agent to the Application Name from your OpenSubtitles API consumer (sending %q)", cfg.UserAgent)
		}
		return fmt.Errorf("HTTP %d: %s%s", res.StatusCode, detail, hint)
	}
	if out == nil || len(raw) == 0 {
		return nil
	}
	return json.Unmarshal(raw, out)
}

func normalizeImdb(value string) string {
	text := strings.ToLower(strings.TrimSpace(value))
	re := regexp.MustCompile(`tt(\d+)`)
	if m := re.FindStringSubmatch(text); len(m) == 2 {
		return m[1]
	}
	if isDigits(text) {
		return text
	}
	return ""
}

func isDigits(s string) bool {
	if s == "" {
		return false
	}
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return true
}

var unsafeName = regexp.MustCompile(`[^\w.\-]+`)

func sanitizeName(name string) string {
	s := unsafeName.ReplaceAllString(name, "_")
	if len(s) > 120 {
		s = s[:120]
	}
	if s == "" {
		s = "subtitle.srt"
	}
	return s
}

func contentTypeFor(path string) string {
	switch strings.ToLower(filepath.Ext(path)) {
	case ".vtt":
		return "text/vtt"
	case ".ass", ".ssa":
		return "text/x-ssa"
	default:
		return "application/x-subrip"
	}
}
