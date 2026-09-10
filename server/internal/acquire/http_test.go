package acquire

import "testing"

func TestParseContentRangeTotal(t *testing.T) {
	if got := parseContentRangeTotal("bytes 0-0/5003804672"); got != 5003804672 {
		t.Fatalf("got %d", got)
	}
	if parseContentRangeTotal("bytes 0-1/*") != 0 {
		t.Fatal("star")
	}
}
