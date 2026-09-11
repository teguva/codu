package api

import (
	"testing"

	"coog/internal/meta"
	"coog/internal/store"
)

func TestCatalogTrailerRef(t *testing.T) {
	imdb, kind := catalogTrailerRef("catalog:tt123")
	if imdb != "tt123" || kind != "movie" {
		t.Fatalf("%s %s", imdb, kind)
	}
	imdb, kind = catalogTrailerRef("catalog:tt123:1:2")
	if imdb != "tt123" || kind != "series" {
		t.Fatalf("%s %s", imdb, kind)
	}
	if imdb, _ := catalogTrailerRef("abc"); imdb != "" {
		t.Fatal(imdb)
	}
}

func TestParseCatalogRefEpisode(t *testing.T) {
	imdb, kind, season, episode := parseCatalogRef("catalog:tt13990474:1:1")
	if imdb != "tt13990474" || kind != "series" || season != 1 || episode != 1 {
		t.Fatalf("%s %s %d %d", imdb, kind, season, episode)
	}
	req := sessionRequest{MediaID: "catalog:tt13990474:1:1"}
	applyCatalogMediaID(&req)
	if req.ImdbID != "tt13990474" || req.Kind != "episode" || req.Season != 1 || req.Episode != 1 {
		t.Fatalf("%+v", req)
	}
}

func TestViewItemIncludesOverviewMeta(t *testing.T) {
	item := store.MediaItem{ID: "m1", Kind: "movie", Title: "Foo", Path: "/Videos/Movies/Foo (2020) tt0111161/Foo.mkv"}
	info := meta.Info{
		ImdbID:         "tt0111161",
		MatchStatus:    "matched",
		Plot:           "A plot.",
		Genres:         []string{"Drama"},
		Rating:         8.2,
		RuntimeMinutes: 120,
		Certification:  "R",
		Country:        "USA",
		TMDBID:         99,
		Cast:           []meta.CastMember{{Name: "Tim"}},
		Director:       &meta.CastMember{Name: "Dir"},
	}
	out := viewItem(item, info, "http://x")
	if out["plot"] != "A plot." {
		t.Fatalf("plot %v", out["plot"])
	}
	if out["runtimeMinutes"] != 120 {
		t.Fatalf("runtime %v", out["runtimeMinutes"])
	}
	if out["certification"] != "R" || out["country"] != "USA" {
		t.Fatalf("%v", out)
	}
	if out["tmdbId"] != 99 {
		t.Fatalf("tmdb %v", out["tmdbId"])
	}
}
