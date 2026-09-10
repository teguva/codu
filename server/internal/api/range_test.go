package api

import (
	"net/http"
	"testing"
)

func TestClampRangeCuePastEOFUnchanged(t *testing.T) {
	r, _ := http.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Range", "bytes=54087104311-")
	asked, clamped := clampRangeForExoPlayer(r, 5003804672)
	if clamped {
		t.Fatal("truncated cue range should not be clamped")
	}
	if asked != 54087104311 {
		t.Fatalf("asked %d", asked)
	}
	if got := r.Header.Get("Range"); got != "bytes=54087104311-" {
		t.Fatalf("got %q", got)
	}
}

func TestClampRangeEqualSize(t *testing.T) {
	r, _ := http.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Range", "bytes=100-")
	asked, clamped := clampRangeForExoPlayer(r, 100)
	if !clamped || asked != 100 {
		t.Fatalf("asked %d clamped %v", asked, clamped)
	}
	if got := r.Header.Get("Range"); got != "bytes=99-99" {
		t.Fatalf("got %q", got)
	}
}

func TestClampRangeJustPastSize(t *testing.T) {
	r, _ := http.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Range", "bytes=102-")
	_, clamped := clampRangeForExoPlayer(r, 100)
	if !clamped {
		t.Fatal("size+2 EOF probe should clamp")
	}
}

func TestClampRangeValidUnchanged(t *testing.T) {
	r, _ := http.NewRequest(http.MethodGet, "/", nil)
	r.Header.Set("Range", "bytes=0-")
	_, clamped := clampRangeForExoPlayer(r, 100)
	if clamped {
		t.Fatal("valid range should stay")
	}
	if got := r.Header.Get("Range"); got != "bytes=0-" {
		t.Fatalf("got %q", got)
	}
}
