package events

import (
	"strings"
	"testing"
)

func TestActivityRing(t *testing.T) {
	h := NewHub()
	h.Record(Activity{Level: "error", Source: "tv", Type: "player.error", Message: "Bearer abcdefghijklmnop failed"})
	got := h.Recent()
	if len(got) != 1 {
		t.Fatalf("len %d", len(got))
	}
	if got[0].Source != "tv" || got[0].Type != "player.error" {
		t.Fatalf("%+v", got[0])
	}
	if strings.Contains(got[0].Message, "abcdefghijklmnop") {
		t.Fatalf("token leaked: %q", got[0].Message)
	}
	for i := 0; i < activityLimit+10; i++ {
		h.Record(Activity{Type: "n", Message: "x"})
	}
	if n := len(h.Recent()); n != activityLimit {
		t.Fatalf("cap %d", n)
	}
}
