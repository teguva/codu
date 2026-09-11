package meta

import "testing"

func TestEpisodeArtURLRejectsSeriesArt(t *testing.T) {
	poster := "https://img.example/series-poster.jpg"
	backdrop := "https://img.example/series-backdrop.jpg"
	if got := episodeArtURL(poster, poster, backdrop); got != "" {
		t.Fatalf("series poster leaked: %s", got)
	}
	if got := episodeArtURL(backdrop, poster, backdrop); got != "" {
		t.Fatalf("series backdrop leaked: %s", got)
	}
	if got := episodeArtURL("", poster, backdrop); got != "" {
		t.Fatalf("empty thumb: %s", got)
	}
	want := "https://img.example/s01e02-still.jpg"
	if got := episodeArtURL(want, poster, backdrop); got != want {
		t.Fatalf("still dropped: %s", got)
	}
}

func TestApplyEpisodeStillsUsesPerEpisodeArt(t *testing.T) {
	eps := []CatalogItem{
		{Kind: "episode", Title: "S01E01", ShowTitle: "Show", Season: 1, Episode: 1, InLibrary: true, MediaID: "m1"},
		{Kind: "episode", Title: "S01E02", ShowTitle: "Show", Season: 1, Episode: 2},
	}
	out := applyEpisodeStills(eps, map[[2]int]episodeStill{
		{1, 1}: {Title: "Pilot", Plot: "The start", Still: "https://img.example/e1.jpg", Runtime: 42, Year: 2011},
		{1, 2}: {Title: "Cat's in the Bag", Still: "https://img.example/e2.jpg"},
	})
	if out[0].PosterURL != "https://img.example/e1.jpg" || out[0].BackdropURL != out[0].PosterURL {
		t.Fatalf("ep1 art: %+v", out[0])
	}
	if out[0].Title != "Pilot" || out[0].Plot != "The start" || out[0].RuntimeMinutes != 42 {
		t.Fatalf("ep1 meta: %+v", out[0])
	}
	if !out[0].InLibrary || out[0].MediaID != "m1" {
		t.Fatalf("library stamp dropped: %+v", out[0])
	}
	if out[1].PosterURL != "https://img.example/e2.jpg" || out[1].Title != "Cat's in the Bag" {
		t.Fatalf("ep2: %+v", out[1])
	}
	if out[0].PosterURL == out[1].PosterURL {
		t.Fatal("episodes share the same still")
	}
}

func TestApplyOverviewMetaKeepsCatalogEpisodeCount(t *testing.T) {
	item := CatalogItem{Kind: "series", Title: "Show", EpisodeCount: 74}
	applyOverviewMeta(&item, tmdbMovie{NumberOfEpisodes: 73, Runtime: 42})
	if item.EpisodeCount != 74 {
		t.Fatalf("cinemeta count overwritten: %d", item.EpisodeCount)
	}
	blank := CatalogItem{Kind: "series"}
	applyOverviewMeta(&blank, tmdbMovie{NumberOfEpisodes: 74})
	if blank.EpisodeCount != 74 {
		t.Fatalf("tmdb count dropped: %d", blank.EpisodeCount)
	}
}
