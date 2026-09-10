package streams

import (
	"strings"
	"testing"

	"coog/internal/settings"
)

func TestDetectQuality(t *testing.T) {
	cases := map[string]string{
		"Movie.2024.2160p.WEB": "2160p",
		"Title UHD HDR":        "2160p",
		"Something 4K REMUX":   "2160p",
		"GroupRM4K-release":    "",
		"Movie.1080p.BluRay":   "1080p",
		"1920x1080 encode":     "1080p",
		"Show.S01E01.720p.WEB": "720p",
		"Old.480p.DVD":         "480p",
		"No resolution here":   "",
	}
	for in, want := range cases {
		if got := DetectQuality(in); got != want {
			t.Errorf("DetectQuality(%q)=%q want %q", in, got, want)
		}
	}
}

func TestParseSizeAndLabel(t *testing.T) {
	title := "Drawn.Together.2004.1080p\n👤 42 💾 1.46 GB ⚙️ YTS"
	size := ParseSizeBytes(title)
	if size < 1_400_000_000 || size > 1_500_000_000 {
		t.Fatalf("size %d", size)
	}
	if label := FormatSizeLabel(size); label != "1.46 GB" {
		t.Fatalf("label %q", label)
	}
	if ParseSizeBytes("💾 700 MB") < 600_000_000 {
		t.Fatal("mb")
	}
}

func TestSortCandidatesCachedFirst(t *testing.T) {
	cands := []Candidate{
		{Title: "uncached", Seeders: 500, Cached: false},
		{Title: "cached", Seeders: 2, Cached: true},
		{Title: "http", URL: "https://cdn.example/file", Seeders: 1},
	}
	sorted := SortCandidates(cands)
	if !sorted[0].Cached {
		t.Fatalf("want cached first: %+v", sorted)
	}
	if Score(sorted[0]) <= Score(sorted[1]) {
		t.Fatalf("scores %+v", sorted)
	}
	best := PickBest(cands)
	if !best.Cached {
		t.Fatalf("PickBest %+v", best)
	}
}

func TestParseSeedersAfterIcon(t *testing.T) {
	n := parseSeeders("Title 2024\n👤 87 💾 2 GB")
	if n != 87 {
		t.Fatalf("seeders %d", n)
	}
}

func TestTorrentioConfigPathIncludesRDLibrary(t *testing.T) {
	path := torrentioConfigPath(settings.Streaming{
		TorrentioProviders: []string{"yts"},
		ExcludeQualities:   []string{"cam"},
		RealDebridToken:    "secret-token",
	})
	if !strings.Contains(path, "realdebrid=secret-token") {
		t.Fatalf("missing token: %s", path)
	}
	if strings.Contains(path, "nocatalog") {
		t.Fatalf("should search RD catalog: %s", path)
	}
}

func TestCandidateFromRDCatalog(t *testing.T) {
	c := enrichCandidate(candidateFromStream(map[string]any{
		"name":     "[RD+] RD Catalog",
		"title":    "Movie 1080p",
		"infoHash": "0123456789abcdef0123456789abcdef01234567",
	}))
	if !c.Cached || c.Source != "rdcatalog" {
		t.Fatalf("%+v", c)
	}
}
