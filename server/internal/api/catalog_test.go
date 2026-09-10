package api

import (
	"testing"

	"coog/internal/meta"
	"coog/internal/store"
)

func TestAttachLibraryMarksLocalWithoutDropping(t *testing.T) {
	local := map[string]string{"tt111": "media-1"}
	out := attachLibrary([]meta.CatalogItem{
		{ID: "catalog:tt111", ImdbID: "tt111", Title: "Local"},
		{ID: "catalog:tt222", ImdbID: "tt222", Title: "Remote"},
	}, local)
	if len(out) != 2 {
		t.Fatalf("dropped items: %d", len(out))
	}
	if !out[0].InLibrary || out[0].MediaID != "media-1" {
		t.Fatalf("%+v", out[0])
	}
	if out[1].InLibrary || out[1].MediaID != "" {
		t.Fatalf("remote marked local: %+v", out[1])
	}
}

func TestOverlayEpisodeLibraryMarksAndKeepsMissing(t *testing.T) {
	imdb := map[string]string{"ep1": "tt1", "ep-extra": "tt1", "other": "tt9"}
	out := overlayEpisodeLibrary(
		[]meta.CatalogItem{
			{ID: "catalog:tt1:1:1", Season: 1, Episode: 1, Title: "Pilot"},
			{ID: "catalog:tt1:1:2", Season: 1, Episode: 2, Title: "Next"},
		},
		"tt1",
		[]store.MediaItem{
			{ID: "ep1", Kind: "episode", Season: 1, Episode: 1, Title: "Pilot file"},
			{ID: "ep-extra", Kind: "episode", Season: 1, Episode: 3, Title: "Unaired"},
			{ID: "other", Kind: "episode", Season: 1, Episode: 1, Title: "Wrong show"},
		},
		func(item store.MediaItem) string { return imdb[item.ID] },
	)
	if len(out) != 3 {
		t.Fatalf("want catalog + extra local, got %d: %+v", len(out), out)
	}
	if !out[0].InLibrary || out[0].MediaID != "ep1" {
		t.Fatalf("pilot not local: %+v", out[0])
	}
	if out[1].InLibrary {
		t.Fatalf("missing ep marked local: %+v", out[1])
	}
	if !out[2].InLibrary || out[2].Season != 1 || out[2].Episode != 3 {
		t.Fatalf("extra local dropped: %+v", out[2])
	}
}
