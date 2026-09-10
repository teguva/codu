package meta

import (
	"os"
	"path/filepath"
	"regexp"
	"testing"
)

func TestFirstIMDB(t *testing.T) {
	if got := firstIMDB("Avatar (2009) {tt0499549}"); got != "tt0499549" {
		t.Fatalf("got %q", got)
	}
	if got := firstIMDB("/Videos/Movies/Avatar (2009)/Avatar.mkv"); got != "" {
		t.Fatalf("unexpected %q", got)
	}
}

func TestIMDBRegexLength(t *testing.T) {
	if !imdbIDRe.MatchString("tt1234567") {
		t.Fatal("7-digit")
	}
	if !regexp.MustCompile(`tt\d{7,}`).MatchString("tt12345678") {
		t.Fatal("8-digit")
	}
}

func TestPickCinemetaExactOnly(t *testing.T) {
	metas := []cinemetaMeta{
		{ID: "tt1", Name: "The Substance", ReleaseInfo: "2024"},
		{ID: "tt2", Name: "Coog Emulator Test", ReleaseInfo: "2026"},
	}
	if got := pickCinemeta(metas, "coog emulator test", 2026); got.ID != "tt2" {
		t.Fatalf("exact+year: got %q", got.ID)
	}
	if got := pickCinemeta(metas, "coog", 0); got.ID != "" {
		t.Fatalf("substring must not match, got %q", got.ID)
	}
	if got := pickCinemeta(metas, "the substance", 0); got.ID != "tt1" {
		t.Fatalf("unique exact without year: got %q", got.ID)
	}

	remakes := []cinemetaMeta{
		{ID: "tt3", Name: "Dune", ReleaseInfo: "1984"},
		{ID: "tt4", Name: "Dune", ReleaseInfo: "2021"},
	}
	if got := pickCinemeta(remakes, "dune", 0); got.ID != "" {
		t.Fatalf("ambiguous without year must reject, got %q", got.ID)
	}
	if got := pickCinemeta(remakes, "dune", 2021); got.ID != "tt4" {
		t.Fatalf("year disambiguation: got %q", got.ID)
	}
	if got := pickCinemeta(remakes, "dune", 1999); got.ID != "" {
		t.Fatalf("wrong year must reject, got %q", got.ID)
	}
}

func TestNameScoreRejectsUnrelated(t *testing.T) {
	if nameScore("coog emulator test", "the substance", 2026, 2024) >= 2 {
		t.Fatal("unrelated title should not match")
	}
	if nameScore("avatar fire and ash", "avatar: fire and ash", 2025, 2025) < 2 {
		t.Fatal("avatar should match")
	}
}

func TestFindIMDBSkipsParentMovieNFO(t *testing.T) {
	root := t.TempDir()
	movieDir := filepath.Join(root, "Home Video")
	if err := os.MkdirAll(movieDir, 0o755); err != nil {
		t.Fatal(err)
	}
	media := filepath.Join(movieDir, "clip.mkv")
	if err := os.WriteFile(media, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(root, "movie.nfo"), []byte("tt0111161"), 0o644); err != nil {
		t.Fatal(err)
	}
	if id := FindIMDB(media, "clip", 0); id != "" {
		t.Fatalf("parent movie nfo must not apply, got %q", id)
	}
}

func TestFindIMDBShowNFOForEpisode(t *testing.T) {
	show := filepath.Join(t.TempDir(), "A Show")
	season := filepath.Join(show, "Season 01")
	if err := os.MkdirAll(season, 0o755); err != nil {
		t.Fatal(err)
	}
	media := filepath.Join(season, "A Show S01E01.mkv")
	if err := os.WriteFile(media, []byte("x"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(show, "tvshow.nfo"), []byte("tt0903747"), 0o644); err != nil {
		t.Fatal(err)
	}
	if id := FindIMDB(media, "A Show", 0); id != "tt0903747" {
		t.Fatalf("got %q", id)
	}
}
