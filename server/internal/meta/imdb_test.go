package meta

import (
	"regexp"
	"testing"
)

func TestFirstIMDB(t *testing.T) {
	if got := firstIMDB("Avatar (2009) {tt0499549}"); got != "tt0499549" {
		t.Fatalf("got %q", got)
	}
	if got := firstIMDB("/Videos/Movies/Avatar (2009)/Avatar.mkv"); got != "" {
		t.Fatalf("unexpected %q", got)
	}
}

func TestIMDBRegexLength(t *testing.T) {
	if !imdbIDRe.MatchString("tt1234567") {
		t.Fatal("7-digit")
	}
	if !regexp.MustCompile(`tt\d{7,}`).MatchString("tt12345678") {
		t.Fatal("8-digit")
	}
}

func TestNameScoreRejectsUnrelated(t *testing.T) {
	if nameScore("coog emulator test", "the substance", 2026, 2024) >= 2 {
		t.Fatal("unrelated title should not match")
	}
	if nameScore("avatar fire and ash", "avatar: fire and ash", 2025, 2025) < 2 {
		t.Fatal("avatar should match")
	}
}
