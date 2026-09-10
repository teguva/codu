package meta

import "testing"

func TestPickYouTubeTrailerPrefersOfficial(t *testing.T) {
	got := pickYouTubeTrailer([]tmdbVideo{
		{Key: "tease", Site: "YouTube", Type: "Teaser", Official: true},
		{Key: "off", Site: "YouTube", Type: "Trailer", Official: true, Name: "Official Trailer"},
		{Key: "vimeo", Site: "Vimeo", Type: "Trailer", Official: true},
	})
	if got != "https://www.youtube.com/watch?v=off" {
		t.Fatalf("got %q", got)
	}
}
