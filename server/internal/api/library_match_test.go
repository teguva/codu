package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"coog/internal/config"
	"coog/internal/library"
	"coog/internal/meta"
	"coog/internal/probe"
	"coog/internal/store"
)

func TestLibraryIgnoreAndRematch(t *testing.T) {
	root := t.TempDir()
	lib := filepath.Join(root, "Videos", "Movies", "Some Film (2010)")
	if err := os.MkdirAll(lib, 0o755); err != nil {
		t.Fatal(err)
	}
	video := filepath.Join(lib, "Some Film (2010).mkv")
	if err := os.WriteFile(video, []byte("fake"), 0o644); err != nil {
		t.Fatal(err)
	}
	data := filepath.Join(root, "data")
	if err := os.MkdirAll(data, 0o755); err != nil {
		t.Fatal(err)
	}
	st, err := store.Open(filepath.Join(data, "coog.db"))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = st.Close() })
	item := store.MediaItem{
		ID:    "m1",
		Kind:  "movie",
		Title: "Some Film",
		Year:  2010,
		Path:  video,
	}
	if err := st.UpsertMedia(item); err != nil {
		t.Fatal(err)
	}
	cfg := config.Config{LibraryPath: filepath.Join(root, "Videos"), DataPath: data, Listen: ":0"}
	prober := probe.New("ffmpeg", "ffprobe")
	scanner := library.NewScanner(st, prober, cfg.LibraryPath)
	s := New(cfg, st, scanner, prober)

	req := httptest.NewRequest(http.MethodPost, "/api/v1/library/m1/ignore", nil)
	req.SetPathValue("id", "m1")
	rec := httptest.NewRecorder()
	s.handleLibraryIgnore(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("ignore: %d %s", rec.Code, rec.Body.String())
	}
	var ignored map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &ignored); err != nil {
		t.Fatal(err)
	}
	if ignored["matchStatus"] != "ignored" {
		t.Fatalf("want ignored, got %#v", ignored["matchStatus"])
	}
	if sc, ok := meta.ReadSidecar(video); !ok || sc.MatchStatus != "ignored" {
		t.Fatalf("sidecar %#v ok=%v", sc, ok)
	}

	body, _ := json.Marshal(map[string]string{"imdbId": "tt0499549"})
	req = httptest.NewRequest(http.MethodPost, "/api/v1/library/m1/rematch", bytes.NewReader(body))
	req.SetPathValue("id", "m1")
	rec = httptest.NewRecorder()
	s.handleLibraryRematch(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("rematch: %d %s", rec.Code, rec.Body.String())
	}
	var matched map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &matched); err != nil {
		t.Fatal(err)
	}
	if matched["matchStatus"] != "matched" || matched["imdbId"] != "tt0499549" {
		t.Fatalf("want matched tt0499549, got %#v", matched)
	}
}
