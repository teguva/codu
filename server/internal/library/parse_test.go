package library

import "testing"

func TestParseMovieFolder(t *testing.T) {
	p := ParseRelative("Movies/Dune (2021)/Dune (2021).mkv")
	if p.Kind != "movie" || p.Title != "Dune" || p.Year != 2021 {
		t.Fatalf("got %+v", p)
	}
}

func TestParseSeries(t *testing.T) {
	p := ParseRelative("Series/The Expanse/Season 01/The Expanse S01E02.mkv")
	if p.Kind != "episode" || p.ShowTitle != "The Expanse" || p.Season != 1 || p.Episode != 2 {
		t.Fatalf("got %+v", p)
	}
}

func TestMediaIDStable(t *testing.T) {
	a := MediaID("Movies/Dune (2021)/Dune.mkv")
	b := MediaID("Movies/Dune (2021)/Dune.mkv")
	if a != b || len(a) != 16 {
		t.Fatalf("id=%s", a)
	}
}

func TestContentType(t *testing.T) {
	if ContentType("x.mkv") != "video/x-matroska" {
		t.Fatal(ContentType("x.mkv"))
	}
}
