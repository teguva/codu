package jobs

import (
	"os"
	"path/filepath"
	"testing"
)

func TestSafeName(t *testing.T) {
	if SafeName("") != "Untitled" {
		t.Fatal("empty")
	}
	if got := SafeName("a/b\\c"); got != "a-b-c" {
		t.Fatalf("got %q", got)
	}
}

func TestPlaylistBufferedMs(t *testing.T) {
	path := filepath.Join(t.TempDir(), "index.m3u8")
	body := "#EXTM3U\n#EXT-X-TARGETDURATION:4\n#EXTINF:4.0,\nseg_00000.ts\n#EXTINF:3.5,\nseg_00001.ts\n"
	if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
		t.Fatal(err)
	}
	ms, n := PlaylistBufferedMs(path)
	if n != 2 {
		t.Fatalf("segments %d", n)
	}
	if ms < 7400 || ms > 7600 {
		t.Fatalf("buffered %d", ms)
	}
}

func TestDownloadProgress(t *testing.T) {
	if p := DownloadProgress(0, 0, 50, 100); p < 0.49 || p > 0.51 {
		t.Fatalf("bytes %v", p)
	}
	if p := DownloadProgress(30_000, 100_000, 0, 0); p < 0.29 || p > 0.31 {
		t.Fatalf("time %v", p)
	}
	if p := DownloadProgress(1, 1, 1, 1); p != 0.99 {
		t.Fatalf("cap %v", p)
	}
}
