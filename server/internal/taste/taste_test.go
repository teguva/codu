package taste

import "testing"

var testCfg = DefaultConfig()

func TestScorePrefersOverlappingGenres(t *testing.T) {
	p := Build([]Signal{
		{ImdbID: "tt1", Genres: []string{"Drama", "Crime"}, Year: 2019, Country: "USA", Weight: 1},
		{ImdbID: "tt2", Genres: []string{"Drama", "Thriller"}, Year: 2021, Country: "USA", Weight: 1},
		{ImdbID: "tt3", Genres: []string{"Crime"}, Year: 2018, Country: "UK", Weight: 1},
		{ImdbID: "tt4", Genres: []string{"Drama"}, Year: 2020, Country: "USA", Weight: 1},
		{ImdbID: "tt5", Genres: []string{"Thriller"}, Year: 2017, Country: "USA", Weight: 1},
		{ImdbID: "tt6", Genres: []string{"Drama", "Mystery"}, Year: 2022, Country: "USA", Weight: 1},
		{ImdbID: "tt7", Genres: []string{"Crime", "Drama"}, Year: 2016, Country: "USA", Weight: 1},
		{ImdbID: "tt8", Genres: []string{"Thriller", "Drama"}, Year: 2023, Country: "USA", Weight: 1},
	})
	if p.Titles != 8 {
		t.Fatalf("titles: %d", p.Titles)
	}
	drama := p.Score(testCfg, []string{"Drama", "Crime"}, nil, 2021, "USA", 7.4)
	toon := p.Score(testCfg, []string{"Animation", "Family"}, nil, 2021, "USA", 7.4)
	if drama <= toon {
		t.Fatalf("expected drama %d > animation %d", drama, toon)
	}
	if drama < 50 {
		t.Fatalf("drama match too low: %d", drama)
	}
}

func TestColdStartUsesCrowdRating(t *testing.T) {
	p := Build([]Signal{
		{ImdbID: "tt1", Genres: []string{"Drama"}, Year: 2020, Weight: 1},
	})
	got := p.Score(testCfg, []string{"Animation"}, nil, 1995, "JP", 8.2)
	if got != 82 {
		t.Fatalf("cold start: %d", got)
	}
}

func TestDisabledConfigUsesCrowd(t *testing.T) {
	p := Build([]Signal{
		{ImdbID: "tt1", Genres: []string{"Drama"}, Year: 2019, Weight: 1},
		{ImdbID: "tt2", Genres: []string{"Drama"}, Year: 2020, Weight: 1},
		{ImdbID: "tt3", Genres: []string{"Drama"}, Year: 2021, Weight: 1},
		{ImdbID: "tt4", Genres: []string{"Drama"}, Year: 2018, Weight: 1},
		{ImdbID: "tt5", Genres: []string{"Drama"}, Year: 2017, Weight: 1},
		{ImdbID: "tt6", Genres: []string{"Drama"}, Year: 2022, Weight: 1},
		{ImdbID: "tt7", Genres: []string{"Drama"}, Year: 2016, Weight: 1},
		{ImdbID: "tt8", Genres: []string{"Drama"}, Year: 2023, Weight: 1},
	})
	off := Config{Enabled: false, PersonalWeight: 0.62, MinTitles: 8}
	if got := p.Score(off, []string{"Animation"}, nil, 1990, "JP", 5.0); got != 50 {
		t.Fatalf("disabled should use crowd: %d", got)
	}
	if !p.ColdStart(off) {
		t.Fatal("disabled should read as cold start")
	}
}

func TestTopGenresSorted(t *testing.T) {
	p := Build([]Signal{
		{ImdbID: "tt1", Genres: []string{"Drama", "Crime"}, Weight: 1},
		{ImdbID: "tt2", Genres: []string{"Drama"}, Weight: 1},
		{ImdbID: "tt3", Genres: []string{"Drama"}, Weight: 1},
	})
	tags := p.TopGenres(5)
	if len(tags) == 0 || tags[0].Name != "drama" {
		t.Fatalf("expected drama first: %+v", tags)
	}
	if tags[0].Share <= 0 || tags[0].Share > 1 {
		t.Fatalf("share out of range: %v", tags[0].Share)
	}
}

func TestGenreIDsMatchNames(t *testing.T) {
	p := Build([]Signal{
		{ImdbID: "tt1", Genres: []string{"Science Fiction"}, Year: 2020, Weight: 1},
		{ImdbID: "tt2", Genres: []string{"Science Fiction", "Drama"}, Year: 2021, Weight: 1},
		{ImdbID: "tt3", Genres: []string{"Drama"}, Year: 2019, Weight: 1},
		{ImdbID: "tt4", Genres: []string{"Science Fiction"}, Year: 2018, Weight: 1},
		{ImdbID: "tt5", Genres: []string{"Thriller"}, Year: 2017, Weight: 1},
		{ImdbID: "tt6", Genres: []string{"Drama"}, Year: 2022, Weight: 1},
		{ImdbID: "tt7", Genres: []string{"Science Fiction"}, Year: 2016, Weight: 1},
		{ImdbID: "tt8", Genres: []string{"Drama", "Science Fiction"}, Year: 2023, Weight: 1},
	})
	named := p.Score(testCfg, []string{"Science Fiction"}, nil, 2020, "", 7)
	byID := p.Score(testCfg, nil, []int{878}, 2020, "", 7)
	if named != byID {
		t.Fatalf("id vs name: %d vs %d", byID, named)
	}
}
