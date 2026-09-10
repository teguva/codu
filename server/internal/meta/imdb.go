package meta

import (
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

var imdbIDRe = regexp.MustCompile(`tt\d{7,}`)

func FindIMDB(mediaPath, title string, year int) string {
	if id := firstIMDB(mediaPath); id != "" {
		return id
	}
	dir := filepath.Dir(mediaPath)
	if id := firstIMDB(dir); id != "" {
		return id
	}
	if id := firstIMDB(filepath.Base(dir)); id != "" {
		return id
	}
	if id := scanNFO(dir); id != "" {
		return id
	}
	if id := scanNFO(filepath.Dir(dir)); id != "" {
		return id
	}
	_ = title
	_ = year
	return ""
}

func firstIMDB(s string) string {
	return imdbIDRe.FindString(s)
}

func scanNFO(dir string) string {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return ""
	}
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		name := strings.ToLower(e.Name())
		if !strings.HasSuffix(name, ".nfo") && name != "movie.xml" && name != "tvshow.nfo" {
			continue
		}
		b, err := os.ReadFile(filepath.Join(dir, e.Name()))
		if err != nil || len(b) > 1<<20 {
			continue
		}
		if id := imdbIDRe.Find(b); len(id) > 0 {
			return string(id)
		}
	}
	return ""
}

func CleanQuery(title string) string {
	s := strings.TrimSpace(title)
	s = strings.ReplaceAll(s, "_", " ")
	s = strings.ReplaceAll(s, ".", " ")
	return strings.Join(strings.Fields(s), " ")
}
