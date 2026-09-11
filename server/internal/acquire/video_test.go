package acquire

import "testing"

func TestPickVideoFileLargest(t *testing.T) {
	files := []torrentFileMeta{
		{Path: "Sample/sample.mkv", Length: 80_000_000, Index: 0},
		{Path: "Movie.2024.1080p.mkv", Length: 8_000_000_000, Index: 1},
		{Path: "nfo.txt", Length: 1200, Index: 2},
	}
	if got := pickVideoFile(files, "", 0, 0, -1); got != 1 {
		t.Fatalf("got %d", got)
	}
}

func TestPickVideoFileEpisode(t *testing.T) {
	files := []torrentFileMeta{
		{Path: "Show.S01E01.mkv", Length: 1_000_000_000, Index: 0},
		{Path: "Show.S01E02.mkv", Length: 1_200_000_000, Index: 1},
		{Path: "Show.S01E03.mkv", Length: 900_000_000, Index: 2},
	}
	if got := pickVideoFile(files, "", 1, 2, -1); got != 1 {
		t.Fatalf("got %d", got)
	}
}

func TestExtractEpisodeMarkers(t *testing.T) {
	if !episodeMarkersMatch("Show.S01.E02.1080p", 1, 2) {
		t.Fatal("sxxexx")
	}
	if !episodeMarkersMatch("Show.1x03.WEB", 1, 3) {
		t.Fatal("nxnn")
	}
}

func TestIsWebEmbed(t *testing.T) {
	if !isWebEmbed("https://vidhide.com/e/abc") {
		t.Fatal("embed")
	}
	if isWebEmbed("https://www.youtube.com/watch?v=1") {
		t.Fatal("youtube")
	}
}
