package events

import (
	"strings"
	"testing"
)

func TestRedact(t *testing.T) {
	in := "https://torrentio.strem.fun/realdebrid=AbCdEf123456/stream/movie/tt1.json Bearer secret-token-value token=supersecret https://torrentio.strem.fun/resolve/realdebrid/AbCdEf123456/abc/"
	out := Redact(in)
	if out == in {
		t.Fatal("expected redaction")
	}
	for _, leak := range []string{"AbCdEf123456", "secret-token-value", "supersecret"} {
		if strings.Contains(out, leak) {
			t.Fatalf("leaked %q in %q", leak, out)
		}
	}
}
