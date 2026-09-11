package adminui

import (
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestHandlerServesIndex(t *testing.T) {
	fsys, src := Resolve("")
	if src == "" {
		t.Fatal("empty source")
	}
	rec := httptest.NewRecorder()
	Handler(fsys).ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
	if rec.Code != http.StatusOK {
		t.Fatalf("GET /: %d", rec.Code)
	}
	body, _ := io.ReadAll(rec.Body)
	if !strings.Contains(string(body), "Coog admin") {
		t.Fatalf("missing title: %s", body)
	}
}

func TestHandlerPrefersDiskOverEmbed(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "index.html"), []byte("<title>Disk admin</title>"), 0o644); err != nil {
		t.Fatal(err)
	}
	fsys, src := Resolve(dir)
	if src != dir {
		t.Fatalf("src=%s", src)
	}
	rec := httptest.NewRecorder()
	Handler(fsys).ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
	if !strings.Contains(rec.Body.String(), "Disk admin") {
		t.Fatalf("got %s", rec.Body.String())
	}
}

func TestHandlerSPAFallback(t *testing.T) {
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "index.html"), []byte("spa-index"), 0o644); err != nil {
		t.Fatal(err)
	}
	h := Handler(http.Dir(dir))
	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/library", nil))
	if rec.Code != http.StatusOK || rec.Body.String() != "spa-index" {
		t.Fatalf("%d %s", rec.Code, rec.Body.String())
	}
	rec = httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/assets/missing.js", nil))
	if rec.Code != http.StatusNotFound {
		t.Fatalf("missing asset: %d", rec.Code)
	}
}
