package streams

import "testing"

func TestInfoHashMagnet(t *testing.T) {
	got := InfoHash("magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=x")
	if got != "0123456789abcdef0123456789abcdef01234567" {
		t.Fatalf("got %q", got)
	}
}

func TestParseTorrentioResolve(t *testing.T) {
	got, ok := parseTorrentioResolve("https://torrentio.strem.fun/resolve/realdebrid/TOKEN/0123456789abcdef0123456789abcdef01234567/null/2/Avatar.mkv")
	if !ok {
		t.Fatal("parse failed")
	}
	if got.InfoHash != "0123456789abcdef0123456789abcdef01234567" || got.FileIndex != 2 || got.Filename != "Avatar.mkv" {
		t.Fatalf("%+v", got)
	}
}
