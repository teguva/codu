package meta

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"coog/internal/store"
)

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) { return f(req) }

func TestEnsureDoesNotSearchByTitle(t *testing.T) {
	dir := t.TempDir()
	media := filepath.Join(dir, "Up.mkv")
	if err := os.WriteFile(media, []byte("video"), 0o644); err != nil {
		t.Fatal(err)
	}
	e := New(t.TempDir(), "")
	searched := false
	e.client = &http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		if strings.Contains(req.URL.Path, "/catalog/") || strings.Contains(req.URL.RawQuery, "query=") {
			searched = true
		}
		return &http.Response{StatusCode: 404, Body: io.NopCloser(bytes.NewReader(nil)), Header: make(http.Header)}, nil
	})}
	info := e.Ensure(context.Background(), store.MediaItem{ID: "up1", Kind: "movie", Title: "Up", Path: media})
	if searched {
		t.Fatal("title search must not run")
	}
	if info.MatchStatus != "unmatched" || info.ImdbID != "" || info.Tagline != "" {
		t.Fatalf("got %+v", info)
	}
	sc, ok := ReadSidecar(media)
	if !ok || sc.MatchStatus != "unmatched" {
		t.Fatalf("sidecar %+v ok=%v", sc, ok)
	}
	if IdentityConfirmed(media, info) {
		t.Fatal("unmatched must not be confirmed")
	}
}

func TestEnsureUsesSidecarIMDB(t *testing.T) {
	dir := t.TempDir()
	media := filepath.Join(dir, "Avatar.mkv")
	if err := os.WriteFile(media, []byte("video"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := WriteSidecar(media, Sidecar{MatchStatus: "matched", ImdbID: "tt0499549", Title: "Avatar"}); err != nil {
		t.Fatal(err)
	}
	e := New(t.TempDir(), "")
	searched := false
	e.client = &http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		if strings.Contains(req.URL.Path, "/catalog/") {
			searched = true
		}
		body := `{"meta":{"id":"tt0499549","name":"Avatar","description":"plot","poster":"http://p","background":"http://b","logo":"http://l"}}`
		if !strings.Contains(req.URL.Path, "/meta/") {
			return &http.Response{StatusCode: 404, Body: io.NopCloser(bytes.NewReader(nil)), Header: make(http.Header)}, nil
		}
		return &http.Response{
			StatusCode: 200,
			Body:       io.NopCloser(strings.NewReader(body)),
			Header:     http.Header{"Content-Type": []string{"application/json"}},
		}, nil
	})}
	info := e.Ensure(context.Background(), store.MediaItem{ID: "av1", Kind: "movie", Title: "Avatar", Path: media})
	if searched {
		t.Fatal("must not search by title when sidecar has imdb")
	}
	if info.MatchStatus != "matched" || info.ImdbID != "tt0499549" || info.Plot != "plot" {
		t.Fatalf("got %+v", info)
	}
	if !IdentityConfirmed(media, info) {
		t.Fatal("sidecar imdb should confirm identity")
	}
}

func TestCacheNameSearchIsDropped(t *testing.T) {
	dir := t.TempDir()
	media := filepath.Join(dir, "Home Video.mkv")
	if err := os.WriteFile(media, []byte("video"), 0o644); err != nil {
		t.Fatal(err)
	}
	e := New(t.TempDir(), "")
	e.store("hv1", Info{ImdbID: "tt0110352", Tagline: "The circle of life", MatchStatus: "matched", Source: "cinemeta"})
	e.client = &http.Client{Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
		t.Fatalf("unexpected request %s", req.URL)
		return nil, nil
	})}
	info := e.Ensure(context.Background(), store.MediaItem{ID: "hv1", Kind: "movie", Title: "Home Video", Path: media})
	if info.MatchStatus != "unmatched" || info.ImdbID != "" || info.Tagline != "" {
		t.Fatalf("got %+v", info)
	}
}
