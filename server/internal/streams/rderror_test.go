package streams

import "testing"

func TestFormatRDHTTPError_Infringing(t *testing.T) {
	err := formatRDHTTPError(451, "/torrents/addMagnet", []byte(`{"error":"infringing_file","error_code":34}`))
	msg := err.Error()
	for _, want := range []string{"blocklist", "infringing_file", "addMagnet", "another source"} {
		if !containsFold(msg, want) {
			t.Fatalf("missing %q in %q", want, msg)
		}
	}
	if containsFold(msg, "real-debrid http 451") {
		t.Fatalf("still using the old terse form: %q", msg)
	}
}

func TestFormatRDHTTPError_Bare451(t *testing.T) {
	err := formatRDHTTPError(451, "/unrestrict/link", nil)
	msg := err.Error()
	if !containsFold(msg, "blocklist") || !containsFold(msg, "unrestrict") {
		t.Fatalf("got %q", msg)
	}
}

func TestFormatRDHTTPError_Token(t *testing.T) {
	err := formatRDHTTPError(401, "/user", []byte(`{"error":"bad_token"}`))
	if !containsFold(err.Error(), "token") {
		t.Fatalf("got %q", err.Error())
	}
}

func TestFormatRDTorrentStatus(t *testing.T) {
	msg := formatRDTorrentStatus("dead").Error()
	if !containsFold(msg, "dead") || !containsFold(msg, "another source") {
		t.Fatalf("got %q", msg)
	}
}

func TestAnnotateRD(t *testing.T) {
	base := formatRDHTTPError(451, "/torrents/addMagnet", []byte(`{"error":"infringing_file"}`))
	got := annotateRD(base, Candidate{Title: "Avatar 2025 2160p\n💾 12 GB"}).Error()
	if !containsFold(got, "Avatar 2025 2160p") {
		t.Fatalf("got %q", got)
	}
}
