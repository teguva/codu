package meta

import (
	"os"
	"path/filepath"
	"testing"
)

func TestSidecarRoundTrip(t *testing.T) {
	dir := t.TempDir()
	media := filepath.Join(dir, "Movie.mkv")
	if err := os.WriteFile(media, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	want := Sidecar{
		MatchStatus: "matched",
		ImdbID:      "tt0499549",
		Title:       "Avatar",
		Year:        2009,
		Poster:      "poster.jpg",
		Fanart:      "fanart.jpg",
		Logo:        "logo.png",
	}
	if err := WriteSidecar(media, want); err != nil {
		t.Fatal(err)
	}
	got, ok := ReadSidecar(media)
	if !ok {
		t.Fatal("expected sidecar")
	}
	if got.ImdbID != want.ImdbID || got.MatchStatus != "matched" || got.Poster != "poster.jpg" {
		t.Fatalf("got %+v", got)
	}
	if id := FindIMDB(media, "wrong", 0); id != "tt0499549" {
		t.Fatalf("FindIMDB from sidecar: %q", id)
	}
}

func TestSidecarUnmatchedBlocksIMDB(t *testing.T) {
	dir := t.TempDir()
	media := filepath.Join(dir, "Home Video.mkv")
	if err := os.WriteFile(media, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := WriteSidecar(media, Sidecar{MatchStatus: "unmatched", ImdbID: "tt0000001"}); err != nil {
		t.Fatal(err)
	}
	if id := FindIMDB(media, "Home Video", 0); id != "" {
		t.Fatalf("unmatched sidecar must not yield imdb, got %q", id)
	}
}

func TestSidecarWritePathManyVideos(t *testing.T) {
	dir := t.TempDir()
	a := filepath.Join(dir, "A.mkv")
	b := filepath.Join(dir, "B.mkv")
	if err := os.WriteFile(a, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(b, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	got := SidecarPath(a)
	if filepath.Base(got) != "A.coog.json" {
		t.Fatalf("got %s", got)
	}
}

func TestSidecarWritePathEpisodeUsesShowDir(t *testing.T) {
	show := filepath.Join(t.TempDir(), "A Show")
	season := filepath.Join(show, "Season 01")
	if err := os.MkdirAll(season, 0o755); err != nil {
		t.Fatal(err)
	}
	media := filepath.Join(season, "A Show S01E01.mkv")
	if err := os.WriteFile(media, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	got := SidecarPath(media)
	if got != filepath.Join(show, "coog.json") {
		t.Fatalf("got %s", got)
	}
	if err := WriteSidecar(media, Sidecar{MatchStatus: "unmatched", Title: "A Show"}); err != nil {
		t.Fatal(err)
	}
	sc, ok := ReadSidecar(media)
	if !ok || sc.MatchStatus != "unmatched" {
		t.Fatalf("got %+v ok=%v", sc, ok)
	}
}

func TestUnmatchedSidecarAllowsLaterNFO(t *testing.T) {
	dir := t.TempDir()
	media := filepath.Join(dir, "Clip.mkv")
	if err := os.WriteFile(media, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := WriteSidecar(media, Sidecar{MatchStatus: "unmatched"}); err != nil {
		t.Fatal(err)
	}
	if id := FindIMDB(media, "Clip", 0); id != "" {
		t.Fatalf("expected empty, got %q", id)
	}
	if err := os.WriteFile(filepath.Join(dir, "movie.nfo"), []byte("https://www.imdb.com/title/tt0499549/"), 0o644); err != nil {
		t.Fatal(err)
	}
	if id := FindIMDB(media, "Clip", 0); id != "tt0499549" {
		t.Fatalf("nfo should still win after unmatched sidecar, got %q", id)
	}
}
